@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/**
 * Shared minute-of-day picker: a Material3 [TimePicker] in 24h format, recolored to Bito's
 * tokens, inside the same [ModalBottomSheet] shell as [NumberInputSheet]. Speaks minutes-since-
 * midnight in and out, matching [com.alvarotc.bito.data.db.HabitEntity.reminderMinutes] — no
 * dependency on the habit form, so the settings screen can reuse it for its own reminder time.
 */
@Composable
fun TimePickerSheet(
    title: String,
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initialMinutes / 60,
            initialMinute = initialMinutes % 60,
            is24Hour = true,
        )
    // skipPartiallyExpanded: the clock face plus the Save button don't fit in the half-expanded
    // sheet height, so a partial state clips the confirm button below the viewport.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Tarjeta) {
        Column(
            Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Tinta, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            TimePicker(
                state = state,
                colors =
                    TimePickerDefaults.colors(
                        containerColor = Tarjeta,
                        selectorColor = Hoja,
                        clockDialColor = HojaTinte,
                        clockDialSelectedContentColor = Tarjeta,
                        clockDialUnselectedContentColor = Tinta,
                        timeSelectorSelectedContainerColor = HojaTinte,
                        timeSelectorUnselectedContainerColor = HojaTinte,
                        timeSelectorSelectedContentColor = Tinta,
                        timeSelectorUnselectedContentColor = TintaSuave,
                    ),
            )
            Spacer(Modifier.height(16.dp))
            PillButton(
                text = stringResource(R.string.save),
                onClick = { onConfirm(state.hour * 60 + state.minute) },
                modifier = Modifier.fillMaxWidth().testTag("time-picker-confirm"),
            )
        }
    }
}
