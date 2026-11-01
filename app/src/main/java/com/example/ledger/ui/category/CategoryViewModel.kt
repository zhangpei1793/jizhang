package com.example.ledger.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.R
import com.example.ledger.data.model.Category
import com.example.ledger.data.model.CategoryType
import com.example.ledger.data.model.TxType
import com.example.ledger.di.AppContainer
import com.example.ledger.util.UiText
import com.example.ledger.util.toUiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 分类管理：按支出/收入两个 Tab 展示，支持新增、修改、删除自定义分类 */
class CategoryViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val tab: Int = TxType.EXPENSE,
        val loading: Boolean = true,
        val categories: List<Category> = emptyList(),
        val saving: Boolean = false
    ) {
        /** 当前 Tab 下适用的分类（含通用分类） */
        val visibleCategories: List<Category>
            get() = categories.filter { it.matches(tab) }
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>()
    val messages = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            container.categoryRepository.fetchAll().fold(
                onSuccess = { categories ->
                    _state.update { it.copy(loading = false, categories = categories) }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false) }
                    send(error.toUiText())
                }
            )
        }
    }

    fun selectTab(tab: Int) {
        _state.update { it.copy(tab = tab) }
    }

    /** @param editingId 为 null 表示新增 */
    fun save(editingId: String?, name: String, iconKey: String, type: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            send(UiText.Res(R.string.category_name_required))
            return
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            send(UiText.Res(R.string.category_name_too_long))
            return
        }
        if (!container.isOnline()) {
            send(UiText.Res(R.string.network_unavailable))
            return
        }
        if (_state.value.saving) return

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = if (editingId == null) {
                container.categoryRepository.create(trimmed, iconKey, type)
            } else {
                container.categoryRepository.update(editingId, trimmed, iconKey, type)
            }
            _state.update { it.copy(saving = false) }
            result.fold(
                onSuccess = {
                    send(UiText.Res(R.string.category_saved))
                    load()
                },
                onFailure = { error ->
                    send(error.toUiText())
                }
            )
        }
    }

    fun delete(category: Category) {
        if (category.isDefault) return
        if (!container.isOnline()) {
            send(UiText.Res(R.string.network_unavailable))
            return
        }
        viewModelScope.launch {
            container.categoryRepository.delete(category.id).fold(
                onSuccess = {
                    send(UiText.Res(R.string.category_deleted))
                    load()
                },
                onFailure = { error ->
                    val message = error.message.orEmpty()
                    // Postgres 外键冲突（23503）说明分类下仍有关联账单
                    val text = if (message.contains("23503") || message.contains("foreign key", true)) {
                        UiText.Res(R.string.category_delete_in_use)
                    } else {
                        error.toUiText()
                    }
                    send(text)
                }
            )
        }
    }

    /** 新增分类时按当前 Tab 给出默认适用类型 */
    fun defaultTypeForTab(): Int =
        if (_state.value.tab == TxType.EXPENSE) {
            CategoryType.EXPENSE_ONLY
        } else {
            CategoryType.INCOME_ONLY
        }

    private fun send(text: UiText) {
        viewModelScope.launch { _messages.emit(text) }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 20
    }
}
