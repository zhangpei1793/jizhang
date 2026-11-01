package com.example.ledger.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 收支分类，对应 Supabase public.categories */
@Serializable
data class Category(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("icon_key") val iconKey: String = "other",
    val type: Int = CategoryType.EXPENSE_ONLY,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
) {
    /** 是否适用于指定流水类型 */
    fun matches(txType: Int): Boolean = when (type) {
        CategoryType.BOTH -> true
        CategoryType.EXPENSE_ONLY -> txType == TxType.EXPENSE
        CategoryType.INCOME_ONLY -> txType == TxType.INCOME
        else -> false
    }
}

/** 新增分类时提交的字段，不包含服务端生成的主键与时间 */
@Serializable
data class CategoryInsert(
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("icon_key") val iconKey: String,
    val type: Int,
    @SerialName("is_default") val isDefault: Boolean = false
)

/** 修改分类时提交的字段 */
@Serializable
data class CategoryUpdate(
    val name: String,
    @SerialName("icon_key") val iconKey: String,
    val type: Int
)
