package com.example.ledger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SurfaceVariantColor = androidx.compose.ui.graphics.Color(0xFFF1F4F3)

private val LightColorScheme = lightColorScheme(
    primary = LedgerGreen,
    onPrimary = SurfaceWhite,
    primaryContainer = LedgerGreenContainer,
    onPrimaryContainer = LedgerGreenDark,
    secondary = TextSecondary,
    onSecondary = SurfaceWhite,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantColor,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor,
    error = ExpenseRed,
    onError = SurfaceWhite,
    errorContainer = ExpenseRedContainer,
    onErrorContainer = ExpenseRed
)

/** 应用主题：浅色、简洁，主色为记账绿 */
@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
