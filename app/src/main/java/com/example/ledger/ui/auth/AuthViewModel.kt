package com.example.ledger.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.R
import com.example.ledger.data.repository.AuthRepository
import com.example.ledger.util.UiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 登录 / 注册状态管理 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    data class UiState(
        val registerMode: Boolean = false,
        val email: String = "",
        val password: String = "",
        val confirmPassword: String = "",
        val loading: Boolean = false,
        val error: UiText? = null,
        val authenticated: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>()
    val messages = _messages.asSharedFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _state.update { it.copy(confirmPassword = value, error = null) }
    }

    fun toggleMode() {
        _state.update {
            it.copy(
                registerMode = !it.registerMode,
                confirmPassword = "",
                error = null
            )
        }
    }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        val email = current.email.trim()
        validate(current, email)?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            if (current.registerMode) {
                repository.signUp(email, current.password).fold(
                    onSuccess = { hasSession ->
                        _state.update { it.copy(loading = false) }
                        if (hasSession) {
                            _state.update { it.copy(authenticated = true) }
                            _messages.emit(UiText.Res(R.string.auth_register_success))
                        } else {
                            // Supabase 开启了邮箱确认：注册成功但暂无会话
                            _messages.emit(UiText.Res(R.string.auth_register_verify))
                        }
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(loading = false, error = error.asUiText())
                        }
                    }
                )
            } else {
                repository.signIn(email, current.password).fold(
                    onSuccess = {
                        _state.update { it.copy(loading = false, authenticated = true) }
                    },
                    onFailure = { error ->
                        _state.update {
                            it.copy(loading = false, error = error.asUiText())
                        }
                    }
                )
            }
        }
    }

    private fun validate(state: UiState, email: String): UiText? {
        if (!EMAIL_PATTERN.matches(email)) return UiText.Res(R.string.auth_email_invalid)
        if (state.password.length < 6) return UiText.Res(R.string.auth_password_short)
        if (state.registerMode && state.password != state.confirmPassword) {
            return UiText.Res(R.string.auth_password_mismatch)
        }
        return null
    }

    private fun Throwable.asUiText(): UiText =
        UiText.Raw(message?.takeIf { it.isNotBlank() } ?: "操作失败，请稍后重试")

    private companion object {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
