package com.example.ledger.data.model

import com.example.ledger.util.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/** 流水记录，对应 Supabase public.records */
@Serializable
data class Record(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: Int,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("category_id") val categoryId: String,
    @SerialName("record_date") val recordDate: String,
    @SerialName("record_time") val recordTime: String,
    val remark: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** 新增流水提交体，id/时间戳由数据库生成 */
@Serializable
data class RecordInsert(
    @SerialName("user_id") val userId: String,
    val type: Int,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    @SerialName("category_id") val categoryId: String,
    @SerialName("record_date") val recordDate: String,
    @SerialName("record_time") val recordTime: String,
    val remark: String? = null
)
