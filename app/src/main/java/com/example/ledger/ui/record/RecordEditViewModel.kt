package com.example.ledger.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.R
import com.example.ledger.data.model.Category
import com.example.ledger.data.model.Record
import com.example.ledger.data.model.RecordInsert
import com.example.ledger.data.model.TxType
import com.example.ledger.di.AppContainer
import com.example.ledger.util.DateUtils
import com.example.ledger.util.Money
import com.example.ledger.util.UiText
import com.example.ledger.util.toUiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.LocalDate

/**
 * 记账编辑页：新增与修改共用。
 * @param recordId 为 null 时表示新增，否则加载对应账单并进入编辑模式。
 */
class RecordEditViewModel(
    private val container: AppContainer,
    private val recordId: String?
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val isEdit: Boolean = false,
        val type: Int = TxType.EXPENSE,
        val amount: String = "",
        val categoryId: String? = null,
        val date: LocalDate = DateUtils.today(),
        val remark: String = "",
        val categories: List<Category> = emptyList(),
        val saving: Boolean = false,
        val deleting: Boolean = false,
        val finished: Boolean = false
    ) {
        /** 分类下拉/选择区只展示适配当前收支类型的分类 */
        val filteredCategories: List<Category>
            get() = categories.filter { it.matches(type) }
    }

    private val _state = MutableStateFlow(UiState(isEdit = recordId != null))
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>()
    val messages = _messages.asSharedFlow()

    /** 编辑模式下保留原始记账时间，仅当日期被修改时才重新生成 */
    private var originalRecord: Record? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val categoriesResult = container.categoryRepository.fetchAll()
            val categories = categoriesResult.getOrElse { error ->
                _state.update { it.copy(loading = false) }
                send(error.toUiText())
                return@launch
            }

            if (recordId == null) {
                val firstCategory = categories.firstOrNull { it.matches(_state.value.type) }
                _state.update {
                    it.copy(
                        loading = false,
                        categories = categories,
                        categoryId = firstCategory?.id
                    )
                }
                return@launch
            }

            container.recordRepository.fetchById(recordId).fold(
                onSuccess = { record ->
                    originalRecord = record
                    _state.update {
                        it.copy(
                            loading = false,
                            categories = categories,
                            type = record.type,
                            amount = record.amount
                                .setScale(2, RoundingMode.HALF_UP)
                                .toPlainString(),
                            categoryId = record.categoryId,
                            date = DateUtils.parseDate(record.recordDate) ?: DateUtils.today(),
                            remark = record.remark.orEmpty()
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false, finished = true) }
                    send(error.toUiText(R.string.record_load_failed))
                }
            )
        }
    }

    fun selectType(type: Int) {
        _state.update { current ->
            val selected = current.categories.firstOrNull { it.id == current.categoryId }
            val categoryId = if (selected != null && selected.matches(type)) {
                selected.id
            } else {
                current.categories.firstOrNull { it.matches(type) }?.id
            }
            current.copy(type = type, categoryId = categoryId)
        }
    }

    fun onAmountChange(value: String) {
        // 仅保留数字与小数点，长度上限对应 numeric(12,2) 的最大整数位
        val filtered = value.filter { it.isDigit() || it == '.' }
        if (filtered.length > MAX_AMOUNT_INPUT_LENGTH) return
        _state.update { it.copy(amount = filtered) }
    }

    fun onCategorySelect(categoryId: String) {
        _state.update { it.copy(categoryId = categoryId) }
    }

    fun onDateChange(date: LocalDate) {
        _state.update { it.copy(date = date) }
    }

    fun onRemarkChange(value: String) {
        _state.update { it.copy(remark = value) }
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.deleting) return

        validateAmount(current.amount)?.let { error ->
            send(error)
            return
        }
        val categoryId = current.categoryId
        if (categoryId == null) {
            send(UiText.Res(R.string.record_category_required))
            return
        }
        val userId = container.authRepository.currentUserId()
        if (userId == null) {
            send(UiText.Res(R.string.auth_logged_out))
            return
        }
        // 一期无离线写入：断网时直接提示，不落地任何数据
        if (!container.isOnline()) {
            send(UiText.Res(R.string.network_unavailable))
            return
        }

        val amount = Money.parse(current.amount) ?: return
        val recordDate = DateUtils.formatDate(current.date)
        val recordTime = resolveRecordTime(recordDate, current.date)
        val remark = current.remark.trim().takeIf { it.isNotEmpty() }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = if (recordId == null) {
                container.recordRepository.add(
                    RecordInsert(
                        userId = userId,
                        type = current.type,
                        amount = amount,
                        categoryId = categoryId,
                        recordDate = recordDate,
                        recordTime = recordTime,
                        remark = remark
                    )
                )
            } else {
                container.recordRepository.update(
                    id = recordId,
                    type = current.type,
                    amount = amount,
                    categoryId = categoryId,
                    recordDate = recordDate,
                    recordTime = recordTime,
                    remark = remark
                )
            }

            result.fold(
                onSuccess = {
                    _state.update { it.copy(saving = false, finished = true) }
                    send(UiText.Res(R.string.record_saved))
                },
                onFailure = { error ->
                    _state.update { it.copy(saving = false) }
                    send(error.toUiText())
                }
            )
        }
    }

    fun delete() {
        val id = recordId ?: return
        val current = _state.value
        if (current.saving || current.deleting) return
        if (!container.isOnline()) {
            send(UiText.Res(R.string.network_unavailable))
            return
        }

        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            container.recordRepository.delete(id).fold(
                onSuccess = {
                    _state.update { it.copy(deleting = false, finished = true) }
                    send(UiText.Res(R.string.record_deleted))
                },
                onFailure = { error ->
                    _state.update { it.copy(deleting = false) }
                    send(error.toUiText())
                }
            )
        }
    }

    private fun validateAmount(input: String): UiText? = when {
        input.isBlank() -> UiText.Res(R.string.record_amount_required)
        Money.exceedsMax(input) -> UiText.Res(R.string.record_amount_too_large)
        Money.parse(input) == null -> UiText.Res(R.string.record_amount_invalid)
        else -> null
    }

    private fun resolveRecordTime(recordDate: String, date: LocalDate): String {
        val original = originalRecord
        return if (original != null && original.recordDate == recordDate) {
            // 日期未变：保留原有时分秒，避免列表排序跳动
            original.recordTime
        } else {
            DateUtils.recordTimeFor(date)
        }
    }

    private fun send(text: UiText) {
        viewModelScope.launch { _messages.emit(text) }
    }

    private companion object {
        const val MAX_AMOUNT_INPUT_LENGTH = 13
    }
}
