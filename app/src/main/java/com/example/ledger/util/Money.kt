package com.example.ledger.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

/** 金额工具：格式化、校验与汇总，全部基于 BigDecimal。 */
object Money {

    val ZERO: BigDecimal = BigDecimal("0.00")

    /** 数据库 numeric(12,2) 允许的最大金额 */
    private val MAX_AMOUNT: BigDecimal = BigDecimal("9999999999.99")

    private val AMOUNT_PATTERN = Regex("^\\d+(\\.\\d{1,2})?$")

    /** 千分位格式化，保留两位小数 */
    fun format(value: BigDecimal): String =
        DecimalFormat("#,##0.00").format(value.setScale(2, RoundingMode.HALF_UP))

    /**
     * 解析用户输入金额。
     * 仅接受正数、最多两位小数、且不超过数据库字段上限，非法输入返回 null。
     */
    fun parse(input: String): BigDecimal? {
        val text = input.trim()
        if (!AMOUNT_PATTERN.matches(text)) return null
        val value = text.toBigDecimalOrNull() ?: return null
        if (value <= BigDecimal.ZERO || value > MAX_AMOUNT) return null
        return value.setScale(2, RoundingMode.HALF_UP)
    }

    /** 输入数值是否超过数据库字段上限（用于区分“格式错误”与“金额过大”） */
    fun exceedsMax(input: String): Boolean =
        input.trim().toBigDecimalOrNull()?.let { it > MAX_AMOUNT } ?: false

    fun sum(values: Iterable<BigDecimal>): BigDecimal =
        values.fold(ZERO) { acc, item -> acc + item }

    /** 占比（百分比数值），总数为 0 时返回 0 */
    fun percent(part: BigDecimal, total: BigDecimal): Float {
        if (total <= ZERO) return 0f
        return part.divide(total, 6, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toFloat()
    }
}
