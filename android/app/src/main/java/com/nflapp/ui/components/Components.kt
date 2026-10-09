package com.nflapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nflapp.ui.theme.LossColor
import com.nflapp.ui.theme.TieColor
import com.nflapp.ui.theme.WinColor
import com.nflapp.util.Format

@Composable
fun TeamBadge(abbr: String, color: Color, size: Dp = 40.dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = abbr,
            color = if (color.luminance() > 0.5f) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.32f).sp,
        )
    }
}

/** Horizontal bar split by win probability: away on the left, home on the right. */
@Composable
fun ProbabilityBar(pHome: Double, awayColor: Color, homeColor: Color, modifier: Modifier = Modifier) {
    val pAway = (1.0 - pHome).toFloat().coerceIn(0.02f, 0.98f)
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(Format.percent(1.0 - pHome), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(Format.percent(pHome), style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            Box(Modifier.weight(pAway).fillMaxHeight().background(awayColor))
            Box(Modifier.weight(1f - pAway).fillMaxHeight().background(homeColor))
        }
    }
}

@Composable
fun FormRow(form: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (form.isEmpty()) {
            Text("–", style = MaterialTheme.typography.labelSmall)
        }
        form.forEach { c ->
            val color = when (c) {
                'W' -> WinColor
                'L' -> LossColor
                else -> TieColor
            }
            Box(
                Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(c.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Simple line chart without third-party libraries. */
@Composable
fun LineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    secondValues: List<Float> = emptyList(),
    secondColor: Color = Color.Gray,
    baseline: Float? = 1505f,
) {
    if (values.size < 2 && secondValues.size < 2) {
        Text("Zu wenige Datenpunkte", modifier, style = MaterialTheme.typography.bodySmall)
        return
    }
    val all = values + secondValues + listOfNotNull(baseline)
    val min = all.min() - 10f
    val max = all.max() + 10f
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Row {
            Text(max.toInt().toString(), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 4.dp)) {
            fun y(v: Float) = size.height * (1f - (v - min) / (max - min))
            baseline?.let {
                drawLine(gridColor, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 2f)
            }
            fun series(points: List<Float>, c: Color) {
                if (points.size < 2) return
                val step = size.width / (points.size - 1)
                val path = Path()
                points.forEachIndexed { i, v ->
                    if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * step, y(v))
                }
                drawPath(path, c, style = Stroke(width = 5f))
            }
            series(secondValues, secondColor)
            series(values, color)
        }
        Text(min.toInt().toString(), style = MaterialTheme.typography.labelSmall)
    }
}

/** Label in the middle, values left and right; the better side is bold. */
@Composable
fun CompareRow(label: String, left: String, right: String, leftBetter: Boolean? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            left,
            Modifier.width(96.dp),
            fontWeight = if (leftBetter == true) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Start,
        )
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            right,
            Modifier.width(96.dp),
            fontWeight = if (leftBetter == false) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun EmptyState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Erneut versuchen") }
        }
    }
}
