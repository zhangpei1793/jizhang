package com.example.ledger.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ledger.data.model.DailyStat
import com.example.ledger.ui.theme.DividerColor
import com.example.ledger.ui.theme.ExpenseRed
import com.example.ledger.ui.theme.IncomeGreen
import com.example.ledger.ui.theme.TextSecondary
import java.math.BigDecimal

/**
 * 当月每日收支折线图。
 * 两条曲线共用纵轴最大值，横轴按自然日平均分布，每 5 天标注一次日期。
 */
@Composable
fun DailyLineChart(
    daily: List<DailyStat>,
    modifier: Modifier = Modifier
) {
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            color = TextSecondary.toArgb()
        }
    }

    val maxValue = remember(daily) {
        daily.maxOfOrNull { maxOf(it.income, it.expense).toFloat() }?.coerceAtLeast(1f) ?: 1f
    }

    Canvas(modifier = modifier) {
        labelPaint.textSize = 10.sp.toPx()

        val leftPadding = 6.dp.toPx()
        val rightPadding = 6.dp.toPx()
        val topPadding = 10.dp.toPx()
        val bottomPadding = 24.dp.toPx()
        val chartWidth = size.width - leftPadding - rightPadding
        val chartHeight = size.height - topPadding - bottomPadding
        if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

        // 水平网格线
        for (index in 0..4) {
            val y = topPadding + chartHeight * index / 4f
            drawLine(
                color = DividerColor,
                start = Offset(leftPadding, y),
                end = Offset(size.width - rightPadding, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        fun xFor(index: Int): Float {
            val count = daily.size
            return if (count <= 1) {
                leftPadding + chartWidth / 2f
            } else {
                leftPadding + chartWidth * index / (count - 1f)
            }
        }

        fun yFor(value: BigDecimal): Float =
            topPadding + chartHeight - (value.toFloat() / maxValue) * chartHeight

        fun drawSeries(values: List<BigDecimal>, color: Color) {
            if (values.isEmpty()) return
            val path = Path()
            values.forEachIndexed { index, value ->
                val point = Offset(xFor(index), yFor(value))
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            values.forEachIndexed { index, value ->
                drawCircle(
                    color = color,
                    radius = 2.5.dp.toPx(),
                    center = Offset(xFor(index), yFor(value))
                )
            }
        }

        drawSeries(daily.map { it.income }, IncomeGreen)
        drawSeries(daily.map { it.expense }, ExpenseRed)

        daily.forEachIndexed { index, _ ->
            if (index == 0 || (index + 1) % 5 == 0 || index == daily.lastIndex) {
                drawContext.canvas.nativeCanvas.drawText(
                    (index + 1).toString(),
                    xFor(index),
                    size.height - 5.dp.toPx(),
                    labelPaint
                )
            }
        }
    }
}
