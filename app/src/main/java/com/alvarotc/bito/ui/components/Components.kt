package com.alvarotc.bito.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.BitoTheme
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.BrasaTinte
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

@Composable
fun BitoCard(
    modifier: Modifier = Modifier,
    container: Color = Tarjeta,
    border: Color = Borde,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = container, border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(20.dp), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = container, border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Hoja,
) = Button(
    onClick = onClick,
    modifier = modifier.heightIn(min = 56.dp),
    enabled = enabled,
    shape = CircleShape,
    colors =
        ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Tarjeta,
            disabledContainerColor = HojaTinte,
            disabledContentColor = TintaSuave,
        ),
) { Text(text, style = MaterialTheme.typography.titleMedium) }

/** [GhostPillButton]'s icon-only twin, for actions whose label lives in the content description. */
@Composable
fun GhostIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = TintaSuave,
    borderColor: Color = Borde,
) = OutlinedButton(
    onClick = onClick,
    modifier = modifier,
    shape = CircleShape,
    border = BorderStroke(1.dp, borderColor),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    contentPadding = PaddingValues(horizontal = 12.dp),
) { Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(16.dp)) }

@Composable
fun GhostPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = TintaSuave,
    borderColor: Color = Borde,
) = OutlinedButton(
    onClick = onClick,
    modifier = modifier,
    shape = CircleShape,
    border = BorderStroke(1.dp, borderColor),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
) { Text(text, style = MaterialTheme.typography.labelMedium) }

/**
 * Bito-styled snackbar body. The M3 default reads the inverse* slots this theme deliberately
 * doesn't define, falling back to Material's near-black + purple — pass this to every
 * [androidx.compose.material3.SnackbarHost] so transient messages speak Crema like the rest.
 */
@Composable
fun BitoSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(12.dp).testTag("bito-snackbar"),
        shape = RoundedCornerShape(24.dp),
        color = Tarjeta,
        border = BorderStroke(1.dp, Borde),
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.bodyLarge,
                color = Tinta,
                modifier = Modifier.weight(1f),
            )
            data.visuals.actionLabel?.let { label ->
                Spacer(Modifier.size(12.dp))
                TextButton(
                    onClick = data::performAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = Hoja),
                ) { Text(label, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

/** «El progreso son puntitos»: filled dots toward small targets (≤ 12). */
@Composable
fun DotProgress(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < filled) Hoja else HojaTinte))
        }
    }
}

/** Rounded bar for durations and big quantities. */
@Composable
fun RoundedBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Hoja,
) {
    Box(modifier.height(10.dp).clip(CircleShape).background(HojaTinte)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** The day ring for the single accent card; content slots the "3 de 6". */
@Composable
fun DayRing(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sweep = if (total == 0) 0f else 360f * done / total
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            val inset = 5.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(Tarjeta.copy(alpha = 0.35f), 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            drawArc(Tarjeta, -90f, sweep, false, Offset(inset, inset), arcSize, style = stroke)
        }
        content()
    }
}

/** Streak chip: brasa is emotional heat, never interaction. */
@Composable
fun StreakChip(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(CircleShape).background(BrasaTinte).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(BitoIcons.Flame, contentDescription = null, tint = Brasa, modifier = Modifier.size(14.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = Brasa)
    }
}

@Composable
fun SegmentedPills(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.clip(CircleShape).background(Tarjeta).border(1.dp, Borde, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (selected) HojaTinte else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Tinta else TintaSuave)
            }
        }
    }
}

/** Habi speaks in a card-bubble; texts are provisional Neutra until M9. */
@Composable
fun SpeechBubble(
    speaker: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    BitoCard(modifier) {
        Text(speaker, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Tinta)
    }
}

@Preview(showBackground = true)
@Composable
private fun BitoCardPreview() {
    BitoTheme {
        BitoCard(modifier = Modifier.padding(16.dp)) {
            Text("Tarjeta", style = MaterialTheme.typography.titleMedium, color = Tinta)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PillButtonPreview() {
    BitoTheme {
        PillButton(text = "Guardar", onClick = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun GhostPillButtonPreview() {
    BitoTheme {
        GhostPillButton(text = "Cancelar", onClick = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun DotProgressPreview() {
    BitoTheme {
        DotProgress(filled = 3, total = 6, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun RoundedBarPreview() {
    BitoTheme {
        RoundedBar(progress = 0.6f, modifier = Modifier.padding(16.dp).fillMaxWidth())
    }
}

@Preview(showBackground = true)
@Composable
private fun DayRingPreview() {
    BitoTheme {
        DayRing(done = 3, total = 6, modifier = Modifier.padding(16.dp).size(120.dp)) {
            Text("3 de 6", style = MaterialTheme.typography.titleMedium, color = Tinta)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StreakChipPreview() {
    BitoTheme {
        StreakChip(count = 12, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SegmentedPillsPreview() {
    BitoTheme {
        SegmentedPills(
            options = listOf("Día", "Semana", "Mes"),
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SpeechBubblePreview() {
    BitoTheme {
        SpeechBubble(
            speaker = "Habi",
            text = "Vas muy bien hoy.",
            modifier = Modifier.padding(16.dp),
        )
    }
}
