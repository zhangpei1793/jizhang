package com.example.ledger.data.model

import com.example.ledger.util.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/** 月度支出预算，对应 Supabase public.month_budget */
@Serializable
data class MonthBudget(
    val id: String,
    @SerialName("user_id") val userId: String,
    val year: Int,
    val month: Int,
    @SerialName("budget_amount")
    @Serializable(with = BigDecimalSerializer::class)
    val budgetAmount: BigDecimal,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** 预算 upsert 提交体，依赖 (user_id, year, month) 联合唯一约束 */
@Serializable
data class MonthBudgetUpsert(
    @SerialName("user_id") val userId: String,
    val year: Int,
    val month: Int,
    @SerialName("budget_amount")
    @Serializable(with = BigDecimalSerializer::class)
    val budgetAmount: BigDecimal
)
