package com.example.ledger.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.sql.SQLException

/** 业务层统一异常，携带可直接展示给用户的中文信息 */
class AppException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 将底层网络/数据库异常转换为用户可读的异常 */
fun Throwable.toAppException(): AppException {
    if (this is AppException) return this

    // 数据库异常：P0 开头是函数里 raise 的业务提示（中文），
    // 23 开头是唯一/外键/检查约束冲突，42 开头是 SQL 语法或权限问题，
    // 这些都应直接展示服务端信息，不能被误判成网络故障
    val sqlError = generateSequence(this) { it.cause }
        .filterIsInstance<SQLException>()
        .firstOrNull()
    if (sqlError != null) {
        val state = sqlError.sqlState.orEmpty()
        val serverMessage = sqlError.message.orEmpty()
        return if (state.startsWith("P0") || state.startsWith("23") || state.startsWith("42")) {
            AppException(serverMessage.ifBlank { "操作失败，请稍后重试" }, this)
        } else {
            AppException("网络连接失败，请检查网络后重试", this)
        }
    }

    val raw = message.orEmpty()
    val isNetwork = this is IOException ||
        this is UnknownHostException ||
        this is ConnectException ||
        this is SocketTimeoutException ||
        raw.contains("timeout", ignoreCase = true) ||
        raw.contains("Unable to resolve host", ignoreCase = true) ||
        raw.contains("Failed to connect", ignoreCase = true) ||
        raw.contains("network", ignoreCase = true)
    return if (isNetwork) {
        AppException("网络连接失败，请检查网络后重试", this)
    } else {
        AppException(raw.ifBlank { "操作失败，请稍后重试" }, this)
    }
}

/** 统一包装 Result，确保失败信息已转换为用户可读文本 */
fun <T> Result<T>.mapAppError(): Result<T> = fold(
    onSuccess = { Result.success(it) },
    onFailure = { Result.failure(it.toAppException()) }
)
