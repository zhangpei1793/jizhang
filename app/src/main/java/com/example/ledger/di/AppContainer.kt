package com.example.ledger.di

import android.content.Context
import com.example.ledger.data.local.RecordCache
import com.example.ledger.data.local.SessionStore
import com.example.ledger.data.model.Record
import com.example.ledger.data.repository.AuthRepository
import com.example.ledger.data.repository.BudgetRepository
import com.example.ledger.data.repository.CategoryRepository
import com.example.ledger.data.repository.RecordRepository
import com.example.ledger.data.repository.StatsRepository
import com.example.ledger.util.NetworkUtils

/**
 * 轻量手动依赖注入容器。
 * 相比 Hilt 减少注解处理器与版本对齐成本，适合当前单模块规模。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val recordCache = RecordCache(appContext)
    private val sessionStore = SessionStore(appContext)

    val authRepository = AuthRepository(sessionStore)
    val categoryRepository = CategoryRepository(sessionStore)
    val recordRepository = RecordRepository(recordCache, sessionStore)
    val budgetRepository = BudgetRepository(sessionStore)
    val statsRepository = StatsRepository(categoryRepository, recordRepository)

    fun isOnline(): Boolean = NetworkUtils.isOnline(appContext)

    fun cachedRecords(): List<Record> =
        recordRepository.cached(authRepository.currentUserId())
}
