package com.example.ledger.data.remote

import com.example.ledger.data.local.SessionStore

/**
 * 获取当前登录用户 ID。
 * 所有业务查询都显式带上 user_id 过滤，与数据库 RLS 形成双重隔离。
 */
internal fun SessionStore.requireUserId(): String =
    userId ?: throw AppException("登录状态已失效，请重新登录")
