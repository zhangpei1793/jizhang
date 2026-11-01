package com.example.ledger.data.local

import android.content.Context

/**
 * 登录会话本地存储。
 * 只保存数据库下发的会话令牌与用户标识，不保存明文密码。
 */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val token: String? get() = prefs.getString(KEY_TOKEN, null)

    val userId: String? get() = prefs.getString(KEY_USER_ID, null)

    val email: String? get() = prefs.getString(KEY_EMAIL, null)

    fun save(token: String, userId: String, email: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "ledger_session"
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
    }
}
