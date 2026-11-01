package com.example.ledger.ui.navigation

/** 页面路由常量，集中管理避免字符串散落各处 */
object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val CATEGORY = "category"

    const val RECORD_NEW = "recordNew"
    const val RECORD_EDIT = "recordEdit/{recordId}"

    fun recordEdit(recordId: String): String = "recordEdit/$recordId"
}
