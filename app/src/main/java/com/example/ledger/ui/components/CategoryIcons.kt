package com.example.ledger.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类图标映射。
 * 数据库只存 icon_key 字符串，渲染时映射为本地 Material 图标，便于后续扩展而不改表结构。
 */
object CategoryIcons {

    val options: List<String> = listOf(
        "restaurant",
        "transport",
        "shopping",
        "housing",
        "salary",
        "gift",
        "parttime",
        "sports",
        "health",
        "study",
        "fun",
        "other"
    )

    fun iconFor(key: String): ImageVector = when (key) {
        "restaurant" -> Icons.Filled.Restaurant
        "transport" -> Icons.Filled.DirectionsBus
        "shopping" -> Icons.Filled.ShoppingBag
        "housing" -> Icons.Filled.Home
        "salary" -> Icons.Filled.Payments
        "gift" -> Icons.Filled.CardGiftcard
        "parttime" -> Icons.Filled.Work
        "sports" -> Icons.Filled.FitnessCenter
        "health" -> Icons.Filled.LocalHospital
        "study" -> Icons.Filled.MenuBook
        "fun" -> Icons.Filled.SportsEsports
        else -> Icons.Filled.Category
    }
}
