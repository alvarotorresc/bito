package com.alvarotc.bito.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.DotProgress
import com.alvarotc.bito.ui.components.GhostIconButton
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.RoundedBar
import com.alvarotc.bito.ui.components.StreakChip
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.PeligroTinte
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.HabitCardUi

/**
 * One habit's row on the nightly review (E1): what the review actually asks about, in the
 * visual language of [com.alvarotc.bito.ui.today.HabitCards] but reduced to the review's own
 * quick-decision controls — no card-open affordance, since a review row is not a shortcut to
 * the habit's detail.
 */
@Composable
fun ReviewRowCard(
    card: HabitCardUi,
    onDone: () -> Unit,
    onAdd: (Int) -> Unit,
    onExact: () -> Unit,
    onAck: () -> Unit,
    onRelapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BitoCard(modifier = modifier.testTag("review-row-${card.id}")) {
        when {
            card.kind == CardKind.ABSTINENCE -> AbstinenceRow(card, onAck, onRelapse)
            card.kind == CardKind.CHECK -> CheckRow(card, onDone, onAck)
            card.direction == Direction.AT_MOST -> LimitRow(card, onAdd, onAck)
            else -> LoggingRow(card, onAdd, onExact, onAck)
        }
    }
}

/** Name plus the racha chip, shared by every row variant below. */
@Composable
private fun RowNameHeader(
    name: String,
    streak: Int,
    minStreakToShow: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyLarge, color = Tinta)
        if (streak >= minStreakToShow) {
            Spacer(Modifier.width(8.dp))
            StreakChip(streak)
        }
    }
}

/** CHECK (AT_LEAST): a binary yes/no, answered in one tap either way. */
@Composable
private fun CheckRow(
    card: HabitCardUi,
    onDone: () -> Unit,
    onAck: () -> Unit,
) {
    Column {
        RowNameHeader(card.name, card.streak, minStreakToShow = 2)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                stringResource(R.string.review_done),
                onClick = onDone,
                modifier = Modifier.weight(1f).testTag("review-done-${card.id}"),
            )
            GhostPillButton(
                stringResource(R.string.review_skip),
                onClick = onAck,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("review-ack-${card.id}"),
            )
        }
    }
}

/** COUNTER/DURATION AT_LEAST: still open — offer the same quick-log chips Today does. */
@Composable
private fun LoggingRow(
    card: HabitCardUi,
    onAdd: (Int) -> Unit,
    onExact: () -> Unit,
    onAck: () -> Unit,
) {
    val minLabel = stringResource(R.string.unit_min)
    val unitSuffix = card.unit?.let { " $it" } ?: ""
    Column {
        RowNameHeader(card.name, card.streak, minStreakToShow = 2)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${card.progress}", style = MaterialTheme.typography.displayLarge, color = Hoja)
            Spacer(Modifier.width(6.dp))
            val caption = if (card.kind == CardKind.DURATION) "/ ${card.target} $minLabel" else "/ ${card.target}$unitSuffix"
            Text(caption, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        }
        Spacer(Modifier.height(8.dp))
        if (card.target <= 12) {
            DotProgress(card.progress.coerceIn(0, card.target), card.target)
        } else {
            RoundedBar(
                progress = if (card.target == 0) 0f else card.progress.toFloat() / card.target,
                color = Hoja,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (card.kind == CardKind.COUNTER) {
                GhostPillButton(
                    stringResource(R.string.add_amount, card.step),
                    onClick = { onAdd(card.step) },
                    modifier = Modifier.heightIn(min = 56.dp).testTag("review-add-${card.id}"),
                )
            } else {
                GhostPillButton(
                    stringResource(R.string.add_amount, 5),
                    onClick = { onAdd(5) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
                GhostPillButton(
                    stringResource(R.string.add_amount, 15),
                    onClick = { onAdd(15) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
                GhostPillButton(
                    stringResource(R.string.add_target),
                    onClick = { onAdd((card.target - card.progress).coerceAtLeast(1)) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
            GhostIconButton(
                BitoIcons.Pencil,
                contentDescription = stringResource(R.string.exact_value_action),
                onClick = onExact,
                modifier = Modifier.testTag("review-exact-${card.id}"),
            )
        }
        Spacer(Modifier.height(10.dp))
        GhostPillButton(
            stringResource(R.string.review_leave),
            onClick = onAck,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("review-ack-${card.id}"),
        )
    }
}

/** AT_MOST (COUNTER/DURATION), clean and unsealed: an honest +step, and "within limit" to close it out. */
@Composable
private fun LimitRow(
    card: HabitCardUi,
    onAdd: (Int) -> Unit,
    onAck: () -> Unit,
) {
    val minLabel = stringResource(R.string.unit_min)
    val unitSuffix = card.unit?.let { " $it" } ?: ""
    Column {
        RowNameHeader(card.name, card.streak, minStreakToShow = 2)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${card.progress}", style = MaterialTheme.typography.displayLarge, color = Hoja)
            Spacer(Modifier.width(6.dp))
            val limitText = if (card.kind == CardKind.DURATION) "${card.target} $minLabel" else "${card.target}$unitSuffix"
            Text(
                stringResource(R.string.limit_caption, limitText),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave,
            )
        }
        Spacer(Modifier.height(10.dp))
        GhostPillButton(
            stringResource(R.string.add_amount, card.step),
            onClick = { onAdd(card.step) },
            modifier = Modifier.heightIn(min = 56.dp).testTag("review-add-${card.id}"),
        )
        Spacer(Modifier.height(10.dp))
        GhostPillButton(
            stringResource(R.string.review_within_limit),
            onClick = onAck,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("review-ack-${card.id}"),
        )
    }
}

/** ABSTINENCE (ZERO), clean and unsealed: "clean day" acknowledges it, relapse opens the confirm sheet. */
@Composable
private fun AbstinenceRow(
    card: HabitCardUi,
    onAck: () -> Unit,
    onRelapse: () -> Unit,
) {
    Column {
        RowNameHeader(card.name, card.streak, minStreakToShow = 1)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                stringResource(R.string.review_clean),
                onClick = onAck,
                modifier = Modifier.weight(1f).testTag("review-ack-${card.id}"),
            )
            GhostPillButton(
                stringResource(R.string.relapse_action),
                onClick = onRelapse,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("review-relapse-${card.id}"),
                color = Peligro,
                borderColor = PeligroTinte,
            )
        }
    }
}
