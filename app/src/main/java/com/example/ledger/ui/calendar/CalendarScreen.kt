package com.example.ledger.ui.calendar

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ledger.LedgerApplication
import com.example.ledger.R
import com.example.ledger.data.model.Record
import com.example.ledger.ui.components.EmptyState
import com.example.ledger.ui.components.MonthSelector
import com.example.ledger.ui.components.RecordRow
import com.example.ledger.ui.theme.AppBackground
import com.example.ledger.ui.theme.ExpenseRed
import com.example.ledger.ui.theme.IncomeGreen
import com.example.ledger.ui.theme.LedgerGreen
import com.example.ledger.ui.theme.TextPrimary
import com.example.ledger.ui.theme.TextSecondary
import com.example.ledger.util.DateUtils
import com.example.ledger.util.Money
import com.example.ledger.util.resolve
import java.time.LocalDate
import java.time.YearMonth

/** 日历页：月历 + 收支打点 + 当日明细 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onEditRecord: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LedgerApplication
    val viewModel: CalendarViewModel = viewModel { CalendarViewModel(app.container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    // 每次进入日历页都刷新：切换底部 Tab 或从记账页返回时，
    // Activity 并未 resume，用 LifecycleResumeEffect 会漏掉刷新
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message.resolve(context), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.calendar_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                MonthSelector(
                    yearMonth = state.yearMonth,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth
                )
            }

            Text(
                text = stringResource(
                    R.string.calendar_month_summary,
                    Money.format(state.monthIncome),
                    Money.format(state.monthExpense)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            if (state.loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = LedgerGreen
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            WeekdayHeader()

            MonthGrid(
                yearMonth = state.yearMonth,
                recordsByDay = state.recordsByDay,
                selectedDate = state.selectedDate,
                onSelectDate = viewModel::selectDate
            )

            if (state.loading && state.recordsByDay.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(26.dp)
                            .align(Alignment.Center),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }

    state.selectedDate?.let { date ->
        val records = state.selectedRecords
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectDate(null) },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = DateUtils.formatDate(date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.calendar_day_income,
                        Money.format(state.selectedIncome)
                    ) + " · " + stringResource(
                        R.string.calendar_day_expense,
                        Money.format(state.selectedExpense)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (records.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        EmptyState(text = stringResource(R.string.calendar_day_empty))
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(items = records, key = { record: Record -> record.id }) { record ->
                            RecordRow(
                                record = record,
                                category = state.categories[record.categoryId],
                                onClick = {
                                    viewModel.selectDate(null)
                                    onEditRecord(record.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 周标题：周一至周日 */
@Composable
private fun WeekdayHeader() {
    val labels = listOf(
        R.string.weekday_mon,
        R.string.weekday_tue,
        R.string.weekday_wed,
        R.string.weekday_thu,
        R.string.weekday_fri,
        R.string.weekday_sat,
        R.string.weekday_sun
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = stringResource(label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** 月历网格：周一为每周第一天 */
@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    recordsByDay: Map<LocalDate, List<Record>>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate?) -> Unit
) {
    val firstDay = yearMonth.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + 6) / 7
    val today = DateUtils.today()

    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(7) { column ->
                    val day = row * 7 + column - leadingBlanks + 1
                    if (day in 1..daysInMonth) {
                        val date = yearMonth.atDay(day)
                        val dayRecords = recordsByDay[date].orEmpty()
                        DayCell(
                            modifier = Modifier.weight(1f),
                            day = day,
                            selected = selectedDate == date,
                            isToday = date == today,
                            hasIncome = dayRecords.any { it.type == com.example.ledger.data.model.TxType.INCOME },
                            hasExpense = dayRecords.any { it.type == com.example.ledger.data.model.TxType.EXPENSE },
                            onClick = {
                                onSelectDate(if (selectedDate == date) null else date)
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier = Modifier,
    day: Int,
    selected: Boolean,
    isToday: Boolean,
    hasIncome: Boolean,
    hasExpense: Boolean,
    onClick: () -> Unit
) {
    val dotColor = when {
        hasExpense -> ExpenseRed
        hasIncome -> IncomeGreen
        else -> Color.Transparent
    }
    val circleModifier = when {
        selected -> Modifier.background(LedgerGreen, CircleShape)
        isToday -> Modifier.border(1.dp, LedgerGreen, CircleShape)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .then(circleModifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.White else TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(dotColor, CircleShape)
            )
        }
    }
}
