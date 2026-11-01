package com.example.ledger.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.R
import com.example.ledger.data.model.Category
import com.example.ledger.data.model.Record
import com.example.ledger.data.model.TxType
import com.example.ledger.di.AppContainer
import com.example.ledger.util.DateUtils
import com.example.ledger.util.Money
import com.example.ledger.util.UiText
import com.example.ledger.util.toUiText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** 日历页：按月加载流水，按日期聚合，供日历打点与当日明细使用 */
class CalendarViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val yearMonth: YearMonth = DateUtils.currentMonth(),
        val loading: Boolean = true,
        val recordsByDay: Map<LocalDate, List<Record>> = emptyMap(),
        val categories: Map<String, Category> = emptyMap(),
        val monthIncome: BigDecimal = Money.ZERO,
        val monthExpense: BigDecimal = Money.ZERO,
        val selectedDate: LocalDate? = null
    ) {
        /** 当前选中日期的账单，按时间倒序 */
        val selectedRecords: List<Record>
            get() = selectedDate?.let { recordsByDay[it] }.orEmpty()

        val selectedIncome: BigDecimal
            get() = Money.sum(
                selectedRecords.filter { it.type == TxType.INCOME }.map { it.amount }
            )

        val selectedExpense: BigDecimal
            get() = Money.sum(
                selectedRecords.filter { it.type == TxType.EXPENSE }.map { it.amount }
            )
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>()
    val messages = _messages.asSharedFlow()

    init {
        load(_state.value.yearMonth)
    }

    /** 页面恢复时刷新，保证新增/修改账单后日历同步更新 */
    fun refresh() {
        load(_state.value.yearMonth)
    }

    fun previousMonth() {
        changeMonth(-1)
    }

    fun nextMonth() {
        changeMonth(1)
    }

    fun selectDate(date: LocalDate?) {
        _state.update { it.copy(selectedDate = date) }
    }

    private fun changeMonth(delta: Int) {
        val target = _state.value.yearMonth.plusMonths(delta.toLong())
        _state.update { it.copy(yearMonth = target, selectedDate = null) }
        load(target)
    }

    private fun load(month: YearMonth) {
        if (!container.isOnline() && _state.value.recordsByDay.isNotEmpty()) {
            send(UiText.Res(R.string.network_unavailable))
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true) }

            val result = runCatching {
                coroutineScope {
                    val records = async {
                        container.recordRepository.fetchByMonth(month.year, month.monthValue)
                    }
                    val categories = async { container.categoryRepository.fetchAll() }
                    records.await().getOrThrow() to categories.await().getOrThrow()
                }
            }

            result.fold(
                onSuccess = { (records, categories) ->
                    val recordsByDay = records
                        .mapNotNull { record ->
                            DateUtils.parseDate(record.recordDate)?.let { it to record }
                        }
                        .groupBy({ it.first }, { it.second })

                    _state.update {
                        it.copy(
                            loading = false,
                            recordsByDay = recordsByDay,
                            categories = categories.associateBy { category -> category.id },
                            monthIncome = Money.sum(
                                records.filter { record -> record.type == TxType.INCOME }
                                    .map { record -> record.amount }
                            ),
                            monthExpense = Money.sum(
                                records.filter { record -> record.type == TxType.EXPENSE }
                                    .map { record -> record.amount }
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false) }
                    send(error.toUiText())
                }
            )
        }
    }

    private fun send(text: UiText) {
        viewModelScope.launch { _messages.emit(text) }
    }
}
