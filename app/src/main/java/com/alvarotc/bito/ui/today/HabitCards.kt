@file:OptIn(ExperimentalFoundationApi::class)

package com.alvarotc.bito.ui.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.DotProgress
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.RoundedBar
import com.alvarotc.bito.ui.components.StreakChip
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/** Single entry point: dispatches on [HabitCardUi.kind] to the right visual variant. */
@Composable
fun HabitCard(
    card: HabitCardUi,
    onPrimary: () -> Unit,
    onAdd: (Int) -> Unit,
    onExact: () -> Unit,
    onRelapse: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(R.string.edit_habit_hint, card.name)
    BitoCard(
        modifier =
            modifier
                .testTag("card-${card.id}")
                .semantics { contentDescription = editLabel },
        onClick = onEdit,
    ) {
        when (card.kind) {
            CardKind.CHECK -> CheckBody(card, onPrimary)
            CardKind.COUNTER -> CounterBody(card, onPrimary, onExact)
            CardKind.DURATION -> DurationBody(card, onAdd, onExact, onEdit)
            CardKind.ABSTINENCE -> AbstinenceBody(card, onRelapse)
        }
    }
}

/** Name (playful strikethrough when done) plus the racha chip, shared by every variant. */
@Composable
private fun HabitNameRow(
    name: String,
    strikeThrough: Boolean,
    streak: Int,
    minStreakToShow: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (strikeThrough) TintaSuave else Tinta,
            textDecoration = if (strikeThrough) TextDecoration.LineThrough else null,
        )
        if (streak >= minStreakToShow) {
            Spacer(Modifier.width(8.dp))
            StreakChip(streak)
        }
    }
}

@Composable
private fun CheckBody(
    card: HabitCardUi,
    onPrimary: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                card.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (card.nameStruckThrough) TintaSuave else Tinta,
                textDecoration = if (card.nameStruckThrough) TextDecoration.LineThrough else null,
            )
            if (card.streak >= 2) {
                Spacer(Modifier.width(8.dp))
                StreakChip(card.streak)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (card.doneToday) Hoja else HojaTinte)
                    .testTag("primary-${card.id}")
                    .clickable(onClick = onPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    BitoIcons.Check,
                    contentDescription = null,
                    tint = if (card.doneToday) Tarjeta else Hoja,
                )
            }
        }
        // WEEK/MONTH habits also carry a period tally. The form only clamps 1..7 for
        // WEEKLY_TIMES — any other binary-logged preset (e.g. a QUANTITY habit switched to
        // "solo cumplido") can carry an uncapped target, so dots only render up to 12; wider
        // targets fall back to the bar the way CounterBody already does.
        if (card.period != Period.DAY) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (card.target <= 12) {
                    DotProgress(
                        card.progress.coerceIn(0, card.target),
                        card.target,
                        modifier = Modifier.testTag("period-dots-${card.id}"),
                    )
                } else {
                    RoundedBar(
                        progress = if (card.target == 0) 0f else card.progress.toFloat() / card.target,
                        modifier = Modifier.width(72.dp).testTag("period-bar-${card.id}"),
                    )
                }
                Spacer(Modifier.width(8.dp))
                val caption =
                    when (card.period) {
                        Period.WEEK -> stringResource(R.string.week_progress, card.progress, card.target)
                        Period.MONTH -> stringResource(R.string.month_progress, card.progress, card.target)
                        Period.DAY -> ""
                    }
                Text(caption, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            }
        }
    }
}

@Composable
private fun CounterBody(
    card: HabitCardUi,
    onPrimary: () -> Unit,
    onExact: () -> Unit,
) {
    val isLimit = card.direction == Direction.AT_MOST
    val overLimit = isLimit && card.failed
    val accent = if (overLimit) Brasa else Hoja
    val unitSuffix = card.unit?.let { " $it" } ?: ""
    Column {
        HabitNameRow(card.name, strikeThrough = card.nameStruckThrough, streak = card.streak, minStreakToShow = 2)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${card.progress}", style = MaterialTheme.typography.displayLarge, color = accent)
                    Spacer(Modifier.width(6.dp))
                    val caption =
                        if (isLimit) {
                            stringResource(R.string.limit_caption, "${card.target}$unitSuffix")
                        } else {
                            "/ ${card.target}$unitSuffix"
                        }
                    Text(caption, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
                }
                Spacer(Modifier.height(8.dp))
                if (card.target <= 12) {
                    DotProgress(card.progress.coerceIn(0, card.target), card.target)
                } else {
                    RoundedBar(
                        progress = if (card.target == 0) 0f else card.progress.toFloat() / card.target,
                        color = accent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(HojaTinte)
                    .testTag("primary-${card.id}")
                    .combinedClickable(onClick = onPrimary, onLongClick = onExact),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BitoIcons.Plus, contentDescription = null, tint = Hoja)
            }
        }
    }
}

@Composable
private fun DurationBody(
    card: HabitCardUi,
    onAdd: (Int) -> Unit,
    onExact: () -> Unit,
    onEdit: () -> Unit,
) {
    val isLimit = card.direction == Direction.AT_MOST
    val overLimit = isLimit && card.failed
    val accent = if (overLimit) Brasa else Hoja
    val minLabel = stringResource(R.string.unit_min)
    Column {
        HabitNameRow(card.name, strikeThrough = card.nameStruckThrough, streak = card.streak, minStreakToShow = 2)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${card.progress}", style = MaterialTheme.typography.displayLarge, color = accent)
            Spacer(Modifier.width(6.dp))
            val caption =
                if (isLimit) {
                    stringResource(R.string.limit_caption, "${card.target} $minLabel")
                } else {
                    "/ ${card.target} $minLabel"
                }
            Text(caption, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        }
        Spacer(Modifier.height(8.dp))
        // Padding sits after combinedClickable so it grows the tap/long-press target without
        // inflating the bar's own visual height (RoundedBar stays 10dp, drawn by GUIA).
        Box(
            Modifier
                .fillMaxWidth()
                .testTag("bar-${card.id}")
                .combinedClickable(onClick = onEdit, onLongClick = onExact)
                .padding(vertical = 12.dp),
        ) {
            RoundedBar(
                progress = if (card.target == 0) 0f else card.progress.toFloat() / card.target,
                color = accent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (card.showsLoggingChips) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostPillButton(stringResource(R.string.add_amount, 5), onClick = { onAdd(5) })
                GhostPillButton(stringResource(R.string.add_amount, 15), onClick = { onAdd(15) })
                GhostPillButton(
                    stringResource(R.string.add_target),
                    onClick = { onAdd((card.target - card.progress).coerceAtLeast(1)) },
                )
            }
        }
    }
}

@Composable
private fun AbstinenceBody(
    card: HabitCardUi,
    onRelapse: () -> Unit,
) {
    Column {
        HabitNameRow(card.name, strikeThrough = false, streak = card.streak, minStreakToShow = 1)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (card.failed) stringResource(R.string.relapse_today) else stringResource(R.string.clean_today),
                style = MaterialTheme.typography.labelMedium,
                color = if (card.failed) Brasa else Hoja,
            )
            Spacer(Modifier.weight(1f))
            if (!card.failed) {
                GhostPillButton(stringResource(R.string.relapse_action), onClick = onRelapse, color = TintaSuave)
            }
        }
    }
}
