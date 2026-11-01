package com.example.ledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.R
import com.example.ledger.di.AppContainer
import com.example.ledger.util.UiText
import com.example.ledger.util.toUiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 设置页：展示账号信息、退出登录 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val email: String? = null,
        val signingOut: Boolean = false,
        val signedOut: Boolean = false
    )

    private val _state = MutableStateFlow(
        UiState(email = container.authRepository.currentEmail())
    )
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>()
    val messages = _messages.asSharedFlow()

    fun refreshAccount() {
        _state.update { it.copy(email = container.authRepository.currentEmail()) }
    }

    fun signOut() {
        if (_state.value.signingOut) return
        val userId = container.authRepository.currentUserId()
        _state.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            container.authRepository.signOut().fold(
                onSuccess = {
                    // 退出后清空该账号的本地只读缓存，避免在登录页残留旧数据
                    container.recordRepository.clearCache(userId)
                    _state.update { it.copy(signingOut = false, signedOut = true) }
                    _messages.emit(UiText.Res(R.string.auth_logged_out))
                },
                onFailure = { error ->
                    _state.update { it.copy(signingOut = false) }
                    _messages.emit(error.toUiText())
                }
            )
        }
    }
}
