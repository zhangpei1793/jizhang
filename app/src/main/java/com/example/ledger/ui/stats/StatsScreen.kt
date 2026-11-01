package com.example.ledger.ui.stats

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ledger.LedgerApplication
import com.example.ledger.R
import com.example.ledger.data.model.CategoryStat
import com.example.ledger.data.model.MonthStats
import com.example.ledger.data.model.Record
import com.example.ledger.data.model.TxType
import com.example.ledger.ui.components.DailyLineChart
import com.example.ledger.ui.components.DonutChart
import com.example.ledger.ui.components.EmptyState
import com.example.ledger.ui.components.LoadingBox
import com.example.ledger.ui.components.MonthSelector
import com.example.ledger.ui.components.RecordRow
import com.example.ledger.ui.theme.AppBackground
import com.example.ledger.ui.theme.ChartColors
import com.example.ledger.ui.theme.ExpenseRed
import com.example.ledger.ui.theme.IncomeGreen
import com.example.ledger.ui.theme.TextPrimary
import com.example.ledger.ui.theme.TextSecondary
import com.example.ledger.util.Money
import com.example.ledger.util.resolve
import java.math.BigDecimal

/** 统计页：月度汇总、收支构成饼图、每日趋势折线图 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onEditRecord: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LedgerApplication
    val viewModel: StatsViewModel = viewModel { StatsViewModel(app.container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    var detail by remember { mutableStateOf<CategoryDetail?>(null) }
    val uncategorized = stringResource(R.string.record_uncategorized)

    // 每次进入统计页都刷新，避免切换 Tab 后仍展示旧汇总
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
                        text = stringResource(R.string.stats_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val stats = state.stats
            when {
                stats == null && state.loading -> LoadingBox()
                stats == null -> EmptyState(text = stringResource(R.string.stats_no_data))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "month") {
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
                    }

                    item(key = "summary") {
                        SummaryCard(stats)
                    }

                    item(key = "expense_title") {
                        SectionTitle(stringResource(R.string.stats_expense_breakdown))
                    }

                    item(key = "expense_breakdown") {
                        BreakdownSection(
                            items = stats.expenseByCategory,
                            uncategorizedName = uncategorized,
                            onClick = { stat ->
                                detail = CategoryDetail(
                                    stat.categoryId,
                                    stat.name.ifEmpty { uncategorized },
                                    TxType.EXPENSE
                                )
                            }
                        )
                    }

                    item(key = "income_title") {
                        SectionTitle(stringResource(R.string.stats_income_breakdown))
                    }

                    item(key = "income_breakdown") {
                        BreakdownSection(
                            items = stats.incomeByCategory,
                            uncategorizedName = uncategorized,
                            onClick = { stat ->
                                detail = CategoryDetail(
                                    stat.categoryId,
                                    stat.name.ifEmpty { uncategorized },
                                    TxType.INCOME
                                )
                            }
                        )
                    }

                    item(key = "trend_title") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(stringResource(R.string.stats_daily_trend))
                            Spacer(modifier = Modifier.weight(1f))
                            TrendLegend()
                        }
                    }

                    item(key = "trend_chart") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            DailyLineChart(
                                daily = stats.daily,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .padding(12.dp)
                            )
                        }
                    }

                    item(key = "bottom") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            if (state.loading && stats != null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }

    detail?.let { current ->
        val records = state.stats?.records.orEmpty().filter {
            it.categoryId == current.categoryId && it.type == current.type
        }
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = stringResource(R.string.stats_category_detail, current.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
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
                                category = state.stats?.categories?.get(record.categoryId),
                                onClick = {
                                    detail = null
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** 收支汇总卡片：收入 / 支出 / 净结余 */
@Composable
private fun SummaryCard(stats: MonthStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 8.dp)
        ) {
            SummaryItem(
                label = stringResource(R.string.stats_income),
                amount = stats.income,
                color = IncomeGreen,
                modifier = Modifier.weight(1f)
            )
            SummaryItem(
                label = stringResource(R.string.stats_expense),
                amount = stats.expense,
                color = ExpenseRed,
                modifier = Modifier.weight(1f)
            )
            SummaryItem(
                label = stringResource(R.string.stats_balance),
                amount = stats.balance,
                color = if (stats.balance < BigDecimal.ZERO) ExpenseRed else TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    amount: BigDecimal,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = Money.format(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

/** 分类占比：环形饼图 + 图例，图例可点击查看明细 */
@Composable
private fun BreakdownSection(
    items: List<CategoryStat>,
    uncategorizedName: String,
    onClick: (CategoryStat) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.stats_legend_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            return@Card
        }

        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DonutChart(
                slices = items.map { it.amount.toFloat() },
                colors = ChartColors,
                modifier = Modifier.size(128.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                items.forEachIndexed { index, stat ->
                    val color = ChartColors[index % ChartColors.size]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClick(stat) }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stat.name.ifEmpty { uncategorizedName },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(
                                R.string.stats_percent,
                                String.format("%.1f", stat.percent)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendDot(color = IncomeGreen)
        Spacer(modifier = Modifier.width(4.dp))
        LegendDot(color = ExpenseRed)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.stats_trend_legend),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}

/** 分类明细弹窗所需的上下文 */
private data class CategoryDetail(
    val categoryId: String,
    val name: String,
    val type: Int
)
