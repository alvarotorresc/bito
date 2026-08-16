@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/** Manual correction of today's total for a COUNTER/DURATION card. */
@Composable
fun ExactValueSheet(
    card: HabitCardUi,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(card.progress.toString()) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.exact_value_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.exact_value_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            PillButton(
                text = stringResource(R.string.save),
                onClick = { text.toIntOrNull()?.let(onConfirm) },
                enabled = text.toIntOrNull() != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Prompts to seal every day left un-sealed while the user was away. */
@Composable
fun BatchSealSheet(
    dayCount: Int,
    onSealAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.seal_sheet_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Spacer(Modifier.height(8.dp))
            val body =
                if (dayCount == 1) {
                    stringResource(R.string.seal_sheet_body_one)
                } else {
                    stringResource(R.string.seal_sheet_body_many, dayCount)
                }
            Text(body, style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
            Spacer(Modifier.height(16.dp))
            val confirmLabel =
                if (dayCount == 1) {
                    stringResource(R.string.seal_sheet_confirm_one)
                } else {
                    stringResource(R.string.seal_sheet_confirm_many, dayCount)
                }
            PillButton(confirmLabel, onClick = onSealAll, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GhostPillButton(stringResource(R.string.seal_sheet_later), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Confirms a relapse before it starts the streak over — no drama, one deliberate tap. */
@Composable
fun RelapseSheet(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.relapse_confirm_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.relapse_confirm_body), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
            Spacer(Modifier.height(16.dp))
            // QA: destructive action, so it reads red, not the usual confirm green.
            PillButton(
                stringResource(R.string.relapse_confirm_yes),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Peligro,
            )
            Spacer(Modifier.height(8.dp))
            GhostPillButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
