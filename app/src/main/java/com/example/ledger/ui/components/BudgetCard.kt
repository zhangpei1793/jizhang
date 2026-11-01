package com.example.ledger.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ledger.R
import com.example.ledger.data.model.BudgetStatus
import com.example.ledger.data.model.MonthBudget
import com.example.ledger.ui.theme.ExpenseRed
import com.example.ledger.ui.theme.NeutralTrack
import com.example.ledger.ui.theme.WarningAmber
import com.example.ledger.util.Money
import java.math.BigDecimal

/** 预算进度卡片：正常 / 即将超支（≥80%）/ 已超支 */
@Composable
fun BudgetProgressCard(
    monthLabel: String,
    budget: MonthBudget?,
    spent: BigDecimal,
    modifier: Modifier = Modifier,
    onSetBudget: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_budget_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (budget == null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.home_budget_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onSetBudget) {
                    Text(text = stringResource(R.string.home_set_budget))
                }
                return@Column
            }

            val budgetAmount = budget.budgetAmount
            val ratio = (Money.percent(spent, budgetAmount) / 100f).coerceIn(0f, 1f)
            val status = budgetStatus(spent, budgetAmount)
            val statusColor = when (status) {
                BudgetStatus.NORMAL -> MaterialTheme.colorScheme.primary
                BudgetStatus.WARNING -> WarningAmber
                BudgetStatus.EXCEEDED -> ExpenseRed
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_budget_spent, Money.format(spent)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.home_budget_total, Money.format(budgetAmount)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = statusColor,
                trackColor = NeutralTrack
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusLabel(status),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (status == BudgetStatus.EXCEEDED) {
                        stringResource(
                            R.string.home_budget_over,
                            Money.format(spent - budgetAmount)
                        )
                    } else {
                        stringResource(
                            R.string.home_budget_remaining,
                            Money.format(budgetAmount - spent)
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == BudgetStatus.EXCEEDED) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(onClick = onSetBudget, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(R.string.home_edit_budget))
            }
        }
    }
}

@Composable
private fun statusLabel(status: BudgetStatus): String = when (status) {
    BudgetStatus.NORMAL -> stringResource(R.string.budget_status_normal)
    BudgetStatus.WARNING -> stringResource(R.string.budget_status_warning)
    BudgetStatus.EXCEEDED -> stringResource(R.string.budget_status_exceeded)
}

/** 预算消耗状态：超过预算为超支，达到 80% 为即将超支 */
fun budgetStatus(spent: BigDecimal, budgetAmount: BigDecimal): BudgetStatus = when {
    budgetAmount <= BigDecimal.ZERO -> BudgetStatus.NORMAL
    spent > budgetAmount -> BudgetStatus.EXCEEDED
    Money.percent(spent, budgetAmount) >= 80f -> BudgetStatus.WARNING
    else -> BudgetStatus.NORMAL
}
