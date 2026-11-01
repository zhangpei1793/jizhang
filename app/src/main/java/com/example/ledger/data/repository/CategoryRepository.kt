package com.example.ledger.data.repository

import com.example.ledger.data.local.SessionStore
import com.example.ledger.data.model.Category
import com.example.ledger.data.remote.DbProvider
import com.example.ledger.data.remote.getIsoTimestamp
import com.example.ledger.data.remote.mapAppError
import com.example.ledger.data.remote.requireUserId
import java.sql.ResultSet
import java.util.UUID

/** 分类数据仓库 */
class CategoryRepository(private val sessionStore: SessionStore) {

    suspend fun fetchAll(): Result<List<Category>> = runCatching {
        val userId = sessionStore.requireUserId()
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "select id, user_id, name, icon_key, type, is_default, created_at " +
                    "from public.categories where user_id = ? " +
                    "order by is_default desc, created_at asc"
            ).use { st ->
                st.setObject(1, UUID.fromString(userId))
                st.executeQuery().use { rs -> rs.toCategories() }
            }
        }
    }.mapAppError()

    suspend fun create(name: String, iconKey: String, type: Int): Result<Unit> = runCatching {
        val userId = sessionStore.requireUserId()
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "insert into public.categories (user_id, name, icon_key, type, is_default) " +
                    "values (?, ?, ?, ?, false)"
            ).use { st ->
                st.setObject(1, UUID.fromString(userId))
                st.setString(2, name)
                st.setString(3, iconKey)
                st.setInt(4, type)
                st.executeUpdate()
            }
            Unit
        }
    }.mapAppError()

    suspend fun update(id: String, name: String, iconKey: String, type: Int): Result<Unit> = runCatching {
        val userId = sessionStore.requireUserId()
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "update public.categories set name = ?, icon_key = ?, type = ? " +
                    "where user_id = ? and id = ?"
            ).use { st ->
                st.setString(1, name)
                st.setString(2, iconKey)
                st.setInt(3, type)
                st.setObject(4, UUID.fromString(userId))
                st.setObject(5, UUID.fromString(id))
                st.executeUpdate()
            }
            Unit
        }
    }.mapAppError()

    suspend fun delete(id: String): Result<Unit> = runCatching {
        val userId = sessionStore.requireUserId()
        DbProvider.withConnection { conn ->
            conn.prepareStatement(
                "delete from public.categories where user_id = ? and id = ?"
            ).use { st ->
                st.setObject(1, UUID.fromString(userId))
                st.setObject(2, UUID.fromString(id))
                st.executeUpdate()
            }
            Unit
        }
    }.mapAppError()
}

private fun ResultSet.toCategories(): List<Category> {
    val list = mutableListOf<Category>()
    while (next()) {
        list += Category(
            id = getString("id"),
            userId = getString("user_id"),
            name = getString("name"),
            iconKey = getString("icon_key"),
            type = getInt("type"),
            isDefault = getBoolean("is_default"),
            createdAt = getIsoTimestamp("created_at")
        )
    }
    return list
}
