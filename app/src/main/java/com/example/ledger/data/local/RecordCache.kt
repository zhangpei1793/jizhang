package com.example.ledger.data.local

import android.content.Context
import com.example.ledger.data.model.Record
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 最近账单只读缓存。
 * 一期不做离线写入：缓存仅用于启动时先展示旧数据、降低等待感，网络请求成功后覆盖。
 */
class RecordCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(userId: String): List<Record> {
        val raw = prefs.getString(key(userId), null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Record>>(raw) }.getOrDefault(emptyList())
    }

    fun save(userId: String, records: List<Record>) {
        runCatching {
            prefs.edit()
                .putString(key(userId), json.encodeToString(records.take(MAX_CACHE_SIZE)))
                .apply()
        }
    }

    fun clear(userId: String) {
        prefs.edit().remove(key(userId)).apply()
    }

    private fun key(userId: String): String = "records_$userId"

    private companion object {
        const val PREFS_NAME = "ledger_record_cache"
        const val MAX_CACHE_SIZE = 30
    }
}
