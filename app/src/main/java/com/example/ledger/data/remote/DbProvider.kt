package com.example.ledger.data.remote

import com.example.ledger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * 直连 Supabase 数据库的连接提供者。
 *
 * 当前网络在 TLS SNI 阶段拦截 *.supabase.co 的 HTTPS 接口，
 * 因此放弃 HTTP 方式，改为连接 Supabase Session Pooler
 * （aws-0-<region>.pooler.supabase.com:5432），全程不使用代理。
 *
 * 全 App 复用同一条连接，并用互斥锁串行化访问，
 * 避免多个协程同时读写同一条 JDBC 连接。
 */
object DbProvider {

    private val host: String get() = BuildConfig.SUPABASE_DB_HOST.trim()
    private val port: Int get() = BuildConfig.SUPABASE_DB_PORT.trim().toIntOrNull() ?: 5432
    private val dbName: String get() = BuildConfig.SUPABASE_DB_NAME.trim()
    private val user: String get() = BuildConfig.SUPABASE_DB_USER.trim()
    private val password: String get() = BuildConfig.SUPABASE_DB_PASSWORD.trim()

    /**
     * local.properties 未填写真实参数时展示配置提示页，而不是直接崩溃。
     * Session Pooler 的用户名必须带项目 ref，形如 app_client.<project-ref>。
     */
    val isConfigured: Boolean
        get() = host.isNotBlank() &&
            !host.contains("REPLACE-ME") &&
            dbName.isNotBlank() &&
            user.contains('.') &&
            password.isNotBlank()

    private val jdbcUrl: String
        get() = "jdbc:postgresql://$host:$port/$dbName" +
            "?sslmode=require&prepareThreshold=0&connectTimeout=10&socketTimeout=30"

    private val mutex = Mutex()
    private var connection: Connection? = null

    @Volatile
    private var sessionToken: String? = null

    /** 只记录当前会话令牌，下一次取连接时写入 PostgreSQL 会话变量 */
    fun setSessionToken(token: String?) {
        sessionToken = token
    }

    /**
     * 串行执行一段数据库操作。
     * 每次使用前都会重新写入会话变量并检查连接可用性，断线时自动重连。
     */
    suspend fun <T> withConnection(block: (Connection) -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) {
            val conn = ensureConnection()
            applySession(conn)
            try {
                block(conn)
            } catch (e: Throwable) {
                closeIfBroken(conn, e)
                throw e
            }
        }
    }

    suspend fun close() = mutex.withLock {
        withContext(Dispatchers.IO) { closeQuietly(connection) }
    }

    private fun ensureConnection(): Connection {
        val existing = connection
        if (existing != null && existing.isUsable()) return existing
        closeQuietly(existing)

        val fresh = DriverManager.getConnection(jdbcUrl, user, password)
        fresh.autoCommit = true
        connection = fresh
        return fresh
    }

    private fun Connection.isUsable(): Boolean =
        runCatching { !isClosed && isValid(2) }.getOrDefault(false)

    /** 把会话令牌写进 PostgreSQL 会话变量，RLS 依靠 public.app_uid() 识别当前用户 */
    private fun applySession(conn: Connection) {
        conn.prepareStatement("select set_config('app.session_token', ?, false)").use { st ->
            st.setString(1, sessionToken.orEmpty())
            st.execute()
        }
    }

    /**
     * 只有连接级异常才丢弃连接：SQLState 为空（驱动层 IO 失败）或 08 开头（连接异常）。
     * 业务异常（P0/23/42 等）说明连接本身仍可用，保留连接。
     */
    private fun closeIfBroken(conn: Connection, error: Throwable) {
        val state = (error as? SQLException)?.sqlState
        val broken = state == null || state.startsWith("08")
        if (broken) closeQuietly(conn)
    }

    private fun closeQuietly(target: Connection?) {
        if (target == null) return
        if (connection === target) connection = null
        runCatching { target.close() }
    }
}
