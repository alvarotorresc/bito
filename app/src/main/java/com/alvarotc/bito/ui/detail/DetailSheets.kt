@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.NumberInputSheet
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.PeligroTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import com.alvarotc.bito.ui.today.CardKind

/** The retroactive/day-edit registration actions [DaySheet] can trigger for one day. */
data class DaySheetActions(
    val onMarkDone: () -> Unit,
    val onMarkNotDone: () -> Unit,
    val onSetValue: (Int) -> Unit,
    val onRelapse: () -> Unit,
    val onStayedClean: () -> Unit,
    val onUseFreezer: () -> Unit,
)

/**
 * Retroactive registration for one heatmap day. The body varies by [kind]: CHECK/BINARY toggles
 * done/not-done; COUNTER/DURATION delegates to [NumberInputSheet]; ABSTINENCE offers "I stayed
 * clean" (seals the day) or "I slipped that day". A freezer row only appears when the day is
 * [freezerOffered] (eligible AND at least one is owned) — every variant, including COUNTER/
 * DURATION, offers it via [NumberInputSheet]'s `extraContent` slot.
 */
@Composable
fun DaySheet(
    day: LogicalDay,
    kind: CardKind,
    currentValue: Int,
    freezerOffered: Boolean,
    freezersOwned: Int,
    actions: DaySheetActions,
    onDismiss: () -> Unit,
) {
    when (kind) {
        CardKind.CHECK ->
            CheckDaySheet(freezerOffered, freezersOwned, actions.onMarkDone, actions.onMarkNotDone, actions.onUseFreezer, onDismiss)
        CardKind.COUNTER, CardKind.DURATION ->
            NumberInputSheet(
                title = stringResource(R.string.day_value_title),
                initial = currentValue,
                onConfirm = actions.onSetValue,
                onDismiss = onDismiss,
                extraContent =
                    if (freezerOffered) {
                        {
                            Spacer(Modifier.height(16.dp))
                            GhostPillButton(
                                stringResource(R.string.use_freezer_action, freezersOwned),
                                onClick = {
                                    actions.onUseFreezer()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        null
                    },
            )
        CardKind.ABSTINENCE ->
            AbstinenceDaySheet(freezerOffered, freezersOwned, actions.onRelapse, actions.onStayedClean, actions.onUseFreezer, onDismiss)
    }
    // `day` is not read directly here: every action closure above already carries the day it
    // targets (bound by the caller), so the sheet body itself has nothing day-specific to render
    // beyond routing on `kind` — keeping `day` in the signature documents what the sheet is for.
}

@Composable
private fun CheckDaySheet(
    freezerOffered: Boolean,
    freezersOwned: Int,
    onMarkDone: () -> Unit,
    onMarkNotDone: () -> Unit,
    onUseFreezer: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("day-sheet"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                stringResource(R.string.day_done),
                onClick = {
                    onMarkDone()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            GhostPillButton(
                stringResource(R.string.day_not_done),
                onClick = {
                    onMarkNotDone()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (freezerOffered) {
                GhostPillButton(
                    stringResource(R.string.use_freezer_action, freezersOwned),
                    onClick = {
                        onUseFreezer()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AbstinenceDaySheet(
    freezerOffered: Boolean,
    freezersOwned: Int,
    onRelapse: () -> Unit,
    onStayedClean: () -> Unit,
    onUseFreezer: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("day-sheet"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.seal_day_hint), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            PillButton(
                stringResource(R.string.stayed_clean),
                onClick = {
                    onStayedClean()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            GhostPillButton(
                stringResource(R.string.relapsed_that_day),
                onClick = {
                    onRelapse()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                color = Peligro,
                borderColor = PeligroTinte,
            )
            if (freezerOffered) {
                GhostPillButton(
                    stringResource(R.string.use_freezer_action, freezersOwned),
                    onClick = {
                        onUseFreezer()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Buying a freezer: shows the running inventory and a price disabled when the balance is short. */
@Composable
fun FreezerSheet(
    owned: Int,
    price: Int,
    balance: Int,
    onBuy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canSpend = balance >= price
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("freezer-sheet"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.freezer_sheet_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Text(stringResource(R.string.freezer_sheet_body), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
            Text(stringResource(R.string.freezers_owned_label, owned), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            PillButton(
                text =
                    if (canSpend) {
                        stringResource(R.string.buy_freezer_action, price)
                    } else {
                        stringResource(R.string.buy_freezer_missing, price - balance)
                    },
                onClick = {
                    onBuy()
                    onDismiss()
                },
                enabled = canSpend,
                modifier = Modifier.fillMaxWidth().testTag("buy-freezer"),
            )
        }
    }
}

/**
 * What freezers are and how to spend them — pure info, dismissed by its own "Entendido"/"Got it"
 * button, no write of any kind. Body copy is provisional Neutra voice; the per-personality voice
 * (sergeant/cheerleader/etc.) arrives with M6.
 */
@Composable
fun FreezerInfoSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("freezer-info-sheet"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.freezer_info_sheet_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Text(stringResource(R.string.freezer_info_sheet_body), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
            Spacer(Modifier.height(4.dp))
            PillButton(stringResource(R.string.got_it), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Pausing suspends registration and streak judgment until resumed; the note is optional context. */
@Composable
fun PauseSheet(
    onPause: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("pause-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.pause_sheet_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(stringResource(R.string.pause_note_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            PillButton(
                stringResource(R.string.pause_habit),
                onClick = {
                    onPause(note.trim().ifBlank { null })
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Archiving keeps all history — it is not destructive, so it confirms in `hoja`, not `peligro`. */
@Composable
fun ArchiveSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("archive-sheet"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.archive_sheet_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Text(stringResource(R.string.archive_sheet_body), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
            PillButton(
                stringResource(R.string.archive_habit),
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                containerColor = Hoja,
            )
            GhostPillButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
