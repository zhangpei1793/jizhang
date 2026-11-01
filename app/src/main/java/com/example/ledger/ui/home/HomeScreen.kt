package com.example.ledger.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ledger.LedgerApplication
import com.example.ledger.R
import com.example.ledger.data.model.MonthBudget
import com.example.ledger.ui.components.BudgetProgressCard
import com.example.ledger.ui.components.EmptyState
import com.example.ledger.ui.components.LoadingBox
import com.example.ledger.ui.components.RecordRow
import com.example.ledger.ui.theme.AppBackground
import com.example.ledger.ui.theme.LedgerGreen
import com.example.ledger.ui.theme.TextSecondary
import com.example.ledger.util.DateUtils
import com.example.ledger.util.Money
import com.example.ledger.util.resolve
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.YearMonth

/** 首页：快速记账入口 + 预算卡片 + 近期流水列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddRecord: () -> Unit,
    onEditRecord: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LedgerApplication
    val viewModel: HomeViewModel = viewModel { HomeViewModel(app.container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val month = remember { YearMonth.now() }
    var showBudgetDialog by remember { mutableStateOf(false) }

    // 每次回到首页都刷新，保证新增/修改账单后预算与列表同步
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message.resolve(context), Toast.LENGTH_SHORT).show()
        }
    }

    // 滑到倒数第三条时预加载下一页
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= viewModel.state.value.records.size - 3) {
                    viewModel.loadMore()
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        if (state.refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.action_refresh)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "budget") {
                    BudgetProgressCard(
                        monthLabel = stringResource(
                            R.string.month_title,
                            month.year,
                            month.monthValue
                        ),
                        budget = state.budget,
                        spent = state.monthSpent,
                        onSetBudget = { showBudgetDialog = true }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item(key = "records_title") {
                    Text(
                        text = stringResource(R.string.home_records_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                when {
                    state.loading -> item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            LoadingBox()
                        }
                    }

                    state.records.isEmpty() -> item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            EmptyState(text = stringResource(R.string.home_empty))
                        }
                    }

                    else -> {
                        itemsIndexed(
                            items = state.records,
                            key = { _, record -> record.id }
                        ) { _, record ->
                            RecordRow(
                                record = record,
                                category = state.categories[record.categoryId],
                                onClick = { onEditRecord(record.id) }
                            )
                        }

                        item(key = "footer") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else if (!state.hasMore) {
                                    Text(
                                        text = stringResource(R.string.home_no_more),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddRecord,
            icon = {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            },
            text = { Text(stringResource(R.string.home_add_record)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            containerColor = LedgerGreen,
            contentColor = Color.White
        )
    }

    if (showBudgetDialog) {
        BudgetEditDialog(
            month = month,
            currentBudget = state.budget,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { input ->
                viewModel.saveBudget(input)
                showBudgetDialog = false
            }
        )
    }
}

/** 预算设置弹窗 */
@Composable
private fun BudgetEditDialog(
    month: YearMonth,
    currentBudget: MonthBudget?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    var input by remember {
        mutableStateOf(
            currentBudget?.budgetAmount
                ?.setScale(2, java.math.RoundingMode.HALF_UP)
                ?.toPlainString()
                ?: ""
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    R.string.budget_dialog_title,
                    month.year,
                    month.monthValue
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { char -> char.isDigit() || char == '.' }
                        error = null
                    },
                    label = { Text(stringResource(R.string.budget_amount_label)) },
                    leadingIcon = { Text("¥") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                error?.let { message ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (Money.parse(input) == null) {
                        error = context.getString(R.string.budget_amount_invalid)
                    } else {
                        onConfirm(input)
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
