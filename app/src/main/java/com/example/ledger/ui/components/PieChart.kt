package com.example.ledger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ledger.ui.theme.NeutralTrack

/**
 * 环形饼图，用 Canvas 自绘。
 * @param slices 各分类金额，内部按总额计算角度
 * @param colors 与 slices 对应的颜色（不足时循环）
 */
@Composable
fun DonutChart(
    slices: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.20f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)
        val total = slices.sum()

        if (total <= 0f) {
            drawArc(
                color = NeutralTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            return@Canvas
        }

        var startAngle = -90f
        slices.forEachIndexed { index, value ->
            if (value <= 0f) return@forEachIndexed
            val sweep = value / total * 360f
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            startAngle += sweep
        }
    }
}
