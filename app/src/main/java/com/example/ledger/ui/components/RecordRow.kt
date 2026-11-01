package com.example.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ledger.R
import com.example.ledger.data.model.Category
import com.example.ledger.data.model.Record
import com.example.ledger.data.model.TxType
import com.example.ledger.ui.theme.ExpenseRed
import com.example.ledger.ui.theme.ExpenseRedContainer
import com.example.ledger.ui.theme.IncomeGreen
import com.example.ledger.ui.theme.IncomeGreenContainer
import com.example.ledger.util.DateUtils
import com.example.ledger.util.Money

/** 单条流水展示，首页、日历、分类明细共用 */
@Composable
fun RecordRow(
    record: Record,
    category: Category?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isExpense = record.type == TxType.EXPENSE
    val accent = if (isExpense) ExpenseRed else IncomeGreen
    val iconBackground = if (isExpense) ExpenseRedContainer else IncomeGreenContainer

    val subtitle = buildString {
        append(DateUtils.displayShortDate(record.recordDate))
        val time = DateUtils.displayTime(record.recordTime)
        if (time.isNotBlank()) {
            append(" ")
            append(time)
        }
        if (!record.remark.isNullOrBlank()) {
            append(" · ")
            append(record.remark)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CategoryIcons.iconFor(category?.iconKey ?: "other"),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category?.name ?: stringResource(R.string.record_uncategorized),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = if (isExpense) {
                stringResource(R.string.money_expense, Money.format(record.amount))
            } else {
                stringResource(R.string.money_income, Money.format(record.amount))
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
    }
}
