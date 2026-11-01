package com.example.ledger.data.remote

import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * timestamptz 列转成带时区的 ISO 字符串，DateUtils.displayTime 依赖该格式。
 * Android 的 java.sql 不支持 getObject(col, Class)，因此先取 Timestamp 再转 UTC。
 */
internal fun ResultSet.getIsoTimestamp(column: String): String? {
    val timestamp = getTimestamp(column) ?: return null
    return Instant.ofEpochMilli(timestamp.time)
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

/** date 列本身就以 yyyy-MM-dd 输出，直接用字符串即可，避免时区偏移 */
internal fun ResultSet.getIsoDate(column: String): String = getString(column)
