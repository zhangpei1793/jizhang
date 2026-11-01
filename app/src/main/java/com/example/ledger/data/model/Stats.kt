package com.example.ledger.data.model

import java.math.BigDecimal

/** 单分类统计项 */
data class CategoryStat(
    val categoryId: String,
    val name: String,
    val iconKey: String,
    val amount: BigDecimal,
    val percent: Float
)

/** 单日收入/支出，用于趋势折线图 */
data class DailyStat(
    val day: Int,
    val income: BigDecimal,
    val expense: BigDecimal
)

/** 某月完整统计结果，统计逻辑在 StatsRepository 中完成 */
data class MonthStats(
    val year: Int,
    val month: Int,
    val income: BigDecimal,
    val expense: BigDecimal,
    val expenseByCategory: List<CategoryStat>,
    val incomeByCategory: List<CategoryStat>,
    val daily: List<DailyStat>,
    val records: List<Record>,
    val categories: Map<String, Category>
) {
    val balance: BigDecimal get() = income - expense
}
