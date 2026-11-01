package com.example.ledger.data.repository

import com.example.ledger.data.local.SessionStore
import com.example.ledger.data.remote.AppException
import com.example.ledger.data.remote.DbProvider
import com.example.ledger.data.remote.mapAppError

/**
 * 账号认证：密码由数据库端 pgcrypto 校验，App 只保存会话令牌。
 * 登录/注册/退出都是数据库里的 SECURITY DEFINER 函数，
 * 因此不依赖被网络封锁的 Supabase Auth HTTP 接口。
 */
class AuthRepository(private val sessionStore: SessionStore) {

    init {
        // 冷启动时把本地令牌挂到连接上，后续查询 RLS 才认得当前用户
        DbProvider.setSessionToken(sessionStore.token)
    }

    fun currentUserId(): String? = sessionStore.userId

    fun currentEmail(): String? = sessionStore.email

    fun isLoggedIn(): Boolean = sessionStore.token != null

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val token = callAuthFunction("app_login", email, password)
            ?: throw AppException("邮箱或密码不正确")
        applySession(token, email)
    }.mapAppError()

    /**
     * 注册账号并直接建立会话。
     * @return 恒为 true（邮箱确认在库内已自动完成），保持与原调用方兼容。
     */
    suspend fun signUp(email: String, password: String): Result<Boolean> = runCatching {
        val token = callAuthFunction("app_signup", email, password)
            ?: throw AppException("注册失败，请稍后重试")
        applySession(token, email)
        true
    }.mapAppError()

    /** 退出登录：服务端尽力撤销令牌，本地会话一定清除，断网也能退出 */
    suspend fun signOut(): Result<Unit> = runCatching {
        runCatching {
            DbProvider.withConnection { conn ->
                conn.prepareStatement("select public.app_logout()").use { it.execute() }
            }
        }
        sessionStore.clear()
        DbProvider.setSessionToken(null)
    }.mapAppError()

    /**
     * 启动时用数据库校验会话是否仍有效。
     * 网络异常时保留本地会话，避免弱网/离线被强制退回登录页。
     */
    suspend fun validateSession(): Boolean {
        if (!isLoggedIn()) return false
        return runCatching {
            DbProvider.withConnection { conn -> conn.queryUid() }
        }.fold(
            onSuccess = { userId ->
                if (userId == null) {
                    sessionStore.clear()
                    DbProvider.setSessionToken(null)
                    false
                } else {
                    true
                }
            },
            onFailure = { true }
        )
    }

    private suspend fun callAuthFunction(name: String, email: String, password: String): String? =
        DbProvider.withConnection { conn ->
            conn.prepareStatement("select public.$name(?, ?)").use { st ->
                st.setString(1, email)
                st.setString(2, password)
                st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }

    private suspend fun applySession(token: String, email: String) {
        DbProvider.setSessionToken(token)
        val userId = try {
            DbProvider.withConnection { conn -> conn.queryUid() }
        } catch (e: Exception) {
            DbProvider.setSessionToken(sessionStore.token)
            throw e
        }
        if (userId == null) {
            DbProvider.setSessionToken(sessionStore.token)
            throw AppException("登录状态初始化失败，请重试")
        }
        sessionStore.save(token, userId, email)
    }

    private fun java.sql.Connection.queryUid(): String? =
        createStatement().use { st ->
            st.executeQuery("select public.app_uid()").use { rs ->
                if (rs.next()) rs.getString(1) else null
            }
        }
}
