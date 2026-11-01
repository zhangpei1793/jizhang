package com.example.ledger.data.repository

import com.example.ledger.data.local.SessionStore
import com.example.ledger.data.model.MonthBudget
import com.example.ledger.data.remote.DbProvider
import com.example.ledger.data.remote.getIsoTimestamp
import com.example.ledger.data.remote.mapAppError
import com.example.ledger.data.remote.requireUserId
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/** 月度预算仓库，每月仅一条记录（数据库联合唯一约束保证） */
class BudgetRepository(private val sessionStore: SessionStore) {

    suspend fun fetch(year: Int, month: Int): Result<MonthBudget?> = runCatching {
        val userId = sessionStore.requireUserId()
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "select * from public.month_budget " +
                    "where user_id = ? and year = ? and month = ? limit 1"
            ).use { st ->
                st.setObject(1, UUID.fromString(userId))
                st.setInt(2, year)
                st.setInt(3, month)
                st.executeQuery().use { rs ->
                    if (!rs.next()) {
                        null
                    } else {
                        MonthBudget(
                            id = rs.getString("id"),
                            userId = rs.getString("user_id"),
                            year = rs.getInt("year"),
                            month = rs.getInt("month"),
                            budgetAmount = rs.getBigDecimal("budget_amount"),
                            createdAt = rs.getIsoTimestamp("created_at"),
                            updatedAt = rs.getIsoTimestamp("updated_at")
                        )
                    }
                }
            }
        }
    }.mapAppError()

    /** 不存在则新增、存在则更新 budget_amount */
    suspend fun save(year: Int, month: Int, amount: BigDecimal): Result<Unit> = runCatching {
        val userId = sessionStore.requireUserId()
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "insert into public.month_budget (user_id, year, month, budget_amount) " +
                    "values (?, ?, ?, ?) " +
                    "on conflict (user_id, year, month) " +
                    "do update set budget_amount = excluded.budget_amount"
            ).use { st ->
                st.setObject(1, UUID.fromString(userId))
                st.setInt(2, year)
                st.setInt(3, month)
                st.setBigDecimal(4, amount.setScale(2, RoundingMode.HALF_UP))
                st.executeUpdate()
            }
            Unit
        }
    }.mapAppError()
}
