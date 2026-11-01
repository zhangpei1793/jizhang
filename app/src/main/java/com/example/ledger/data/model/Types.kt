package com.example.ledger.data.model

/** 流水类型：0=支出，1=收入 */
object TxType {
    const val EXPENSE = 0
    const val INCOME = 1
}

/** 分类适用类型：0=仅支出，1=仅收入，2=通用 */
object CategoryType {
    const val EXPENSE_ONLY = 0
    const val INCOME_ONLY = 1
    const val BOTH = 2
}

/** 预算状态 */
enum class BudgetStatus {
    NORMAL,
    WARNING,
    EXCEEDED
}
