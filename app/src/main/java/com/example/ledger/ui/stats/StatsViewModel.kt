package com.example.ledger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.data.model.MonthStats
import com.example.ledger.di.AppContainer
import com.example.ledger.util.DateUtils
import com.example.ledger.util.UiText
import com.example.ledger.util.toUiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/** 统计页：按自然月汇总，聚合逻辑全部在 StatsRepository */
class StatsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val yearMonth: YearMonth = DateUtils.currentMonth(),
        val loading: Boolean = true,
        val stats: MonthStats? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>()
    val messages = _messages.asSharedFlow()

    init {
        load(_state.value.yearMonth)
    }

    fun refresh() {
        load(_state.value.yearMonth)
    }

    fun previousMonth() {
        changeMonth(-1)
    }

    fun nextMonth() {
        changeMonth(1)
    }

    private fun changeMonth(delta: Int) {
        val target = _state.value.yearMonth.plusMonths(delta.toLong())
        _state.update { it.copy(yearMonth = target) }
        load(target)
    }

    private fun load(month: YearMonth) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            container.statsRepository.loadMonth(month.year, month.monthValue).fold(
                onSuccess = { stats ->
                    _state.update { it.copy(loading = false, stats = stats) }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false) }
                    _messages.emit(error.toUiText())
                }
            )
        }
    }
}
