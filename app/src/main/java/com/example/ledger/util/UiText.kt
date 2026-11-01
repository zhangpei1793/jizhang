package com.example.ledger.util

import android.content.Context
import androidx.annotation.StringRes
import com.example.ledger.R

/**
 * ViewModel 与 UI 之间的文本载体。
 * ViewModel 不持有 Context，因此只保存字符串资源 ID 或后端返回的原始文本。
 */
sealed interface UiText {

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val text: String) : UiText
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Res -> if (args.isEmpty()) {
        context.getString(id)
    } else {
        context.getString(id, *args.toTypedArray())
    }
    is UiText.Raw -> text
}

/**
 * 异常转可展示文本：优先使用业务层异常信息，缺失时回退到资源文案。
 */
fun Throwable.toUiText(@StringRes fallback: Int = R.string.error_generic): UiText =
    message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: UiText.Res(fallback)
