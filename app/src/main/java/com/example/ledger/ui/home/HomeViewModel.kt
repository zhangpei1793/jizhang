package com.example.ledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.R
import com.example.ledger.data.model.Category
import com.example.ledger.data.model.MonthBudget
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
import java.time.YearMonth

/** 首页：最近流水分页 + 本月预算进度 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val records: List<Record> = emptyList(),
        val categories: Map<String, Category> = emptyMap(),
        val budget: MonthBudget? = null,
        val monthSpent: BigDecimal = Money.ZERO,
        val hasMore: Boolean = true,
        val loadingMore: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>()
    val messages = _messages.asSharedFlow()

    init {
        // 先展示本地缓存，网络数据回来后再覆盖（一期不做离线写入）
        val cached = container.cachedRecords()
        if (cached.isNotEmpty()) {
            _state.update {
                it.copy(records = cached, loading = false, hasMore = cached.size >= PAGE_SIZE)
            }
        }
    }

    /** 下拉/回到首页时刷新：首屏列表、分类、预算、本月支出并行拉取 */
    fun refresh() {
        val userId = container.authRepository.currentUserId()
        if (userId == null) {
            viewModelScope.launch { _messages.emit(UiText.Res(R.string.auth_logged_out)) }
            return
        }
        if (!container.isOnline() && _state.value.records.isNotEmpty()) {
            viewModelScope.launch { _messages.emit(UiText.Res(R.string.network_unavailable)) }
            return
        }

        viewModelScope.launch {
            val hasData = _state.value.records.isNotEmpty()
            _state.update { it.copy(loading = !hasData, refreshing = hasData) }
            val month = YearMonth.now()

            val result = runCatching {
                coroutineScope {
                    val page = async { container.recordRepository.fetchPage(0, PAGE_SIZE) }
                    val categories = async { container.categoryRepository.fetchAll() }
                    val budget = async { container.budgetRepository.fetch(month.year, month.monthValue) }
                    val monthRecords = async {
                        container.recordRepository.fetchByMonth(month.year, month.monthValue)
                    }
                    HomeSnapshot(
                        page = page.await().getOrThrow(),
                        categories = categories.await().getOrThrow(),
                        budget = budget.await().getOrThrow(),
                        monthRecords = monthRecords.await().getOrThrow()
                    )
                }
            }

            result.fold(
                onSuccess = { snapshot ->
                    val spent = Money.sum(
                        snapshot.monthRecords
                            .filter { it.type == TxType.EXPENSE }
                            .map { it.amount }
                    )
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            records = snapshot.page,
                            categories = snapshot.categories.associateBy { category -> category.id },
                            budget = snapshot.budget,
                            monthSpent = spent,
                            hasMore = snapshot.page.size >= PAGE_SIZE
                        )
                    }
                    container.recordRepository.saveCache(userId, snapshot.page)
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false, refreshing = false) }
                    _messages.emit(error.toUiText())
                }
            )
        }
    }

    /** 滑到底部加载下一页，按 id 去重避免新增账单导致重复 */
    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || !current.hasMore || current.records.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            container.recordRepository.fetchPage(current.records.size, PAGE_SIZE).fold(
                onSuccess = { more ->
                    _state.update { state ->
                        state.copy(
                            records = (state.records + more).distinctBy { it.id },
                            loadingMore = false,
                            hasMore = more.size >= PAGE_SIZE
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(loadingMore = false) }
                    _messages.emit(error.toUiText())
                }
            )
        }
    }

    fun saveBudget(input: String) {
        val amount = Money.parse(input)
        if (amount == null) {
            viewModelScope.launch { _messages.emit(UiText.Res(R.string.budget_amount_invalid)) }
            return
        }
        if (!container.isOnline()) {
            viewModelScope.launch { _messages.emit(UiText.Res(R.string.network_unavailable)) }
            return
        }
        val month = YearMonth.now()
        viewModelScope.launch {
            container.budgetRepository.save(month.year, month.monthValue, amount).fold(
                onSuccess = {
                    _messages.emit(UiText.Res(R.string.saved_success))
                    refresh()
                },
                onFailure = { error ->
                    _messages.emit(error.toUiText())
                }
            )
        }
    }

    private data class HomeSnapshot(
        val page: List<Record>,
        val categories: List<Category>,
        val budget: MonthBudget?,
        val monthRecords: List<Record>
    )

    private companion object {
        const val PAGE_SIZE = 20
    }
}
