package com.alvarotc.bito.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.BitoTheme
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.BrasaTinte
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

@Composable
fun BitoCard(
    modifier: Modifier = Modifier,
    container: Color = Tarjeta,
    border: Color = Borde,
    // 20dp on every call site but the habit-detail heatmap, which needs its day grid tighter than
    // the card's usual inset to clear the ≥44dp touch floor — see DetailScreen.kt's HeatmapSection.
    contentPadding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = container, border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = container, border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(contentPadding), content = content)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) = OutlinedButton(
    onClick = onClick,
    modifier = modifier,
    shape = CircleShape,
    border = BorderStroke(1.dp, borderColor),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
    }
    Text(text, style = MaterialTheme.typography.labelMedium)
}

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
    Row(
        // Copies DayRing's own [D] pattern below: mergeDescendants = true is a no-op here (no
        // dot carries its own semantics to fold in) but keeps this component consistent with
        // the file's canonical progress-visualization shape, and the range info itself is real
        // — every call site already shows the same numbers as adjacent Text, so this is
        // robustness, not a fix for a value that is otherwise lost.
        modifier.semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(current = if (total == 0) 0f else filled.toFloat() / total, range = 0f..1f)
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            key(i) {
                AnimatedDot(filled = i < filled)
            }
        }
    }
}

/**
 * One dot of [DotProgress]. [key]ing the caller's loop by index gives every dot its own
 * remembered slot, so only the dot whose filled state actually flips pops — its neighbors' scale
 * [Animatable]s never see a change and stay put.
 */
@Composable
private fun AnimatedDot(filled: Boolean) {
    val scale = remember { Animatable(1f) }
    var wasFilled by remember { mutableStateOf(filled) }
    LaunchedEffect(filled) {
        if (filled != wasFilled) {
            wasFilled = filled
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    Box(
        Modifier
            .size(12.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(if (filled) Hoja else HojaTinte),
    )
}

/** Rounded bar for durations and big quantities. */
@Composable
fun RoundedBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Hoja,
) {
    Box(
        modifier
            .height(10.dp)
            .clip(CircleShape)
            .background(HojaTinte)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(current = progress.coerceIn(0f, 1f), range = 0f..1f)
            },
    ) {
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
    val targetSweep = if (total == 0) 0f else 360f * done / total
    // animateFloatAsState seeds its Animatable at the FIRST targetValue it ever sees, so the very
    // first frame already lands exactly on targetSweep with no draw-in animation — only a LATER
    // recomposition with a different done/total re-targets it and animates the sweep between the
    // old and new fractions.
    val sweep by animateFloatAsState(targetValue = targetSweep, animationSpec = tween(250, easing = LinearOutSlowInEasing))
    Box(
        modifier
            .testTag("day-ring")
            // mergeDescendants: true folds `content`'s own text (the "3 of 6" label) into this
            // same node instead of leaving the progress role as a second, unlabeled TalkBack
            // stop next to it — one node, one announcement: role + value + label together.
            .semantics(mergeDescendants = true) {
                // The fraction actually being drawn right now, not the value it may still be
                // animating toward — ComponentsTest drives the clock and asserts against this.
                progressBarRangeInfo = ProgressBarRangeInfo(current = sweep / 360f, range = 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
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

/**
 * A pill-shaped option switcher, canon per design/components/pills.html: a Papel trough holding
 * Tarjeta-raised selected options.
 *
 * [fillWidth] opts into the compliance-window canon — each option gets equal [RowScope.weight]
 * and the whole control stretches to the caller's width (DetailScreen's `Modifier.fillMaxWidth()`
 * call site). It defaults to `false` so [options] keep sizing to their own text everywhere else
 * (HabitFormScreen's quit-mode/limit-metric/period toggles) — weight-based children always
 * consume the Row's full incoming width regardless of the modifier passed in, so making that the
 * default would stretch those compact toggles across their card too.
 */
@Composable
fun SegmentedPills(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
) {
    Row(
        modifier.clip(CircleShape).background(Papel).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                (if (fillWidth) Modifier.weight(1f) else Modifier)
                    .shadow(elevation = if (selected) 2.dp else 0.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(if (selected) Tarjeta else Color.Transparent)
                    // selectable (not plain clickable), same canon as OnboardingScreen's
                    // LanguageChip/PersonalityCard — announces which option is active instead of
                    // every pill reading as a bare "<label>, Button". role = Tab: these are
                    // segmented window/mode switches, not a single-choice radio group.
                    .selectable(selected = selected, enabled = enabled, onClick = { onSelect(i) }, role = Role.Tab)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = if (selected) Tinta else TintaSuave,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Habi speaks in a card-bubble; texts are personality-voiced provisional copy until M9
 * ([com.alvarotc.bito.ui.habi.HabiVoice]). [avatar] is an optional leading element — a mini Habi
 * face — rendered to the left of the speaker/text column; default null keeps every existing call
 * site's layout unchanged.
 */
@Composable
fun SpeechBubble(
    speaker: String,
    text: String,
    modifier: Modifier = Modifier,
    avatar: (@Composable () -> Unit)? = null,
) {
    BitoCard(modifier) {
        if (avatar != null) {
            Row(verticalAlignment = Alignment.Top) {
                avatar()
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(speaker, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
                    Spacer(Modifier.height(4.dp))
                    Text(text, style = MaterialTheme.typography.bodyLarge, color = Tinta)
                }
            }
        } else {
            Text(speaker, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Tinta)
        }
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
