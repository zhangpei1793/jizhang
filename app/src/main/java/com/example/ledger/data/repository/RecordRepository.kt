package com.example.ledger.data.repository

import com.example.ledger.data.local.RecordCache
import com.example.ledger.data.local.SessionStore
import com.example.ledger.data.model.Record
import com.example.ledger.data.model.RecordInsert
import com.example.ledger.data.remote.DbProvider
import com.example.ledger.data.remote.getIsoDate
import com.example.ledger.data.remote.getIsoTimestamp
import com.example.ledger.data.remote.mapAppError
import com.example.ledger.data.remote.requireUserId
import com.example.ledger.util.DateUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/** 流水数据仓库：数据库读写 + 最近账单只读缓存 */
class RecordRepository(
    private val cache: RecordCache,
    private val sessionStore: SessionStore
) {

    fun cached(userId: String?): List<Record> =
        if (userId.isNullOrBlank()) emptyList() else cache.load(userId)

    fun saveCache(userId: String, records: List<Record>) = cache.save(userId, records)

    fun clearCache(userId: String?) {
        if (!userId.isNullOrBlank()) cache.clear(userId)
    }

    /** 分页拉取流水，按记账时间倒序 */
    suspend fun fetchPage(offset: Int, pageSize: Int): Result<List<Record>> = runCatching {
        val userId = sessionStore.requireUserId()
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "select * from public.records where user_id = ? " +
                    "order by record_time desc offset ? limit ?"
            ).use { st ->
                st.setObject(1, UUID.fromString(userId))
                st.setInt(2, offset)
                st.setInt(3, pageSize)
                st.executeQuery().use { rs -> rs.toRecords() }
            }
        }
    }.mapAppError()

    suspend fun fetchById(id: String): Result<Record> = runCatching {
        val userId = sessionStore.requireUserId()
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "select * from public.records where user_id = ? and id = ? limit 1"
            ).use { st ->
                st.setObject(1, UUID.fromString(userId))
                st.setObject(2, UUID.fromString(id))
                st.executeQuery().use { rs ->
                    if (rs.next()) rs.toRecord() else throw IllegalStateException("账单不存在或已被删除")
                }
            }
        }
    }.mapAppError()

    suspend fun add(input: RecordInsert): Result<Unit> = runCatching {
        sessionStore.requireUserId()
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "insert into public.records " +
                    "(user_id, type, amount, category_id, record_date, record_time, remark) " +
                    "values (?, ?, ?, ?, ?, ?, ?)"
            ).use { st ->
                st.setObject(1, UUID.fromString(input.userId))
                st.setInt(2, input.type)
                // numeric 字段按两位小数提交，避免浮点误差
                st.setBigDecimal(3, input.amount.setScale(2, RoundingMode.HALF_UP))
                st.setObject(4, UUID.fromString(input.categoryId))
                st.setObject(5, LocalDate.parse(input.recordDate))
                st.setObject(6, OffsetDateTime.parse(input.recordTime))
                st.setString(7, input.remark)
                st.executeUpdate()
            }
            Unit
        }
    }.mapAppError()

    suspend fun update(
        id: String,
        type: Int,
        amount: BigDecimal,
        categoryId: String,
        recordDate: String,
        recordTime: String,
        remark: String?
    ): Result<Unit> = runCatching {
        val userId = sessionStore.requireUserId()
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "update public.records set type = ?, amount = ?, category_id = ?, " +
                    "record_date = ?, record_time = ?, remark = ? " +
                    "where user_id = ? and id = ?"
            ).use { st ->
                st.setInt(1, type)
                st.setBigDecimal(2, amount.setScale(2, RoundingMode.HALF_UP))
                st.setObject(3, UUID.fromString(categoryId))
                st.setObject(4, LocalDate.parse(recordDate))
                st.setObject(5, OffsetDateTime.parse(recordTime))
                st.setString(6, remark)
                st.setObject(7, UUID.fromString(userId))
                st.setObject(8, UUID.fromString(id))
                st.executeUpdate()
            }
            Unit
        }
    }.mapAppError()

    suspend fun delete(id: String): Result<Unit> = runCatching {
        val userId = sessionStore.requireUserId()
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "delete from public.records where user_id = ? and id = ?"
            ).use { st ->
                st.setObject(1, UUID.fromString(userId))
                st.setObject(2, UUID.fromString(id))
                st.executeUpdate()
            }
            Unit
        }
    }.mapAppError()

    /** 查询某月全部流水（日历、统计、预算共用） */
    suspend fun fetchByMonth(year: Int, month: Int): Result<List<Record>> = runCatching {
        val userId = sessionStore.requireUserId()
        val (start, end) = DateUtils.monthRange(year, month)
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "select * from public.records where user_id = ? " +
                    "and record_date >= ? and record_date <= ? " +
                    "order by record_time desc"
            ).use { st ->
                st.setObject(1, UUID.fromString(userId))
                st.setObject(2, LocalDate.parse(start))
                st.setObject(3, LocalDate.parse(end))
                st.executeQuery().use { rs -> rs.toRecords() }
            }
        }
    }.mapAppError()
}

private fun ResultSet.toRecords(): List<Record> {
    val list = mutableListOf<Record>()
    while (next()) list += toRecord()
    return list
}

private fun ResultSet.toRecord(): Record = Record(
    id = getString("id"),
    userId = getString("user_id"),
    type = getInt("type"),
    amount = getBigDecimal("amount"),
    categoryId = getString("category_id"),
    recordDate = getIsoDate("record_date"),
    recordTime = getIsoTimestamp("record_time").orEmpty(),
    remark = getString("remark"),
    createdAt = getIsoTimestamp("created_at"),
    updatedAt = getIsoTimestamp("updated_at")
)
