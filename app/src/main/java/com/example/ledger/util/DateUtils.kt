package com.example.ledger.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 日期与时间工具，统一使用 java.time（minSdk 26 原生支持）。 */
object DateUtils {

    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val SHORT_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun today(): LocalDate = LocalDate.now()

    fun currentMonth(): YearMonth = YearMonth.now()

    fun formatDate(date: LocalDate): String = DATE_FORMATTER.format(date)

    fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value, DATE_FORMATTER) }.getOrNull()

    /** 返回某月第一天与最后一天的日期字符串，用于 PostgREST 的日期区间过滤 */
    fun monthRange(year: Int, month: Int): Pair<String, String> {
        val yearMonth = YearMonth.of(year, month)
        return DATE_FORMATTER.format(yearMonth.atDay(1)) to
            DATE_FORMATTER.format(yearMonth.atEndOfMonth())
    }

    /** 组合记账日期与当前时刻，生成带时区的 timestamptz 字符串（补记时仍保留选择日期） */
    fun recordTimeFor(date: LocalDate): String =
        date.atTime(LocalTime.now())
            .atZone(ZoneId.systemDefault())
            .toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /** 将 timestamptz 字符串转成本地 HH:mm */
    fun displayTime(recordTime: String): String {
        val local = try {
            OffsetDateTime.parse(recordTime).atZoneSameInstant(ZoneId.systemDefault())
        } catch (e: Exception) {
            try {
                Instant.parse(recordTime).atZone(ZoneId.systemDefault())
            } catch (e: Exception) {
                return ""
            }
        }
        return TIME_FORMATTER.format(local)
    }

    /** 列表中的日期显示为 MM-dd */
    fun displayShortDate(recordDate: String): String =
        parseDate(recordDate)?.let { SHORT_DATE_FORMATTER.format(it) } ?: recordDate

    /** 日历维度：某天是否属于指定年月 */
    fun isSameMonth(date: LocalDate, yearMonth: YearMonth): Boolean =
        date.year == yearMonth.year && date.monthValue == yearMonth.monthValue
}
