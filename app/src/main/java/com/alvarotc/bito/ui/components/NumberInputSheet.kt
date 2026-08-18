@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta

/**
 * Direct numeric entry for any target/stepper value across the app — the shared twin of
 * `TodaySheets.ExactValueSheet`'s visual pattern, but without a dependency on `HabitCardUi`.
 * [extraContent], when given, renders below the Save button inside the same [Column] — e.g.
 * [com.alvarotc.bito.ui.detail.DaySheet]'s freezer row for a failed COUNTER/DURATION day —
 * leaving every other caller's plain input-then-save layout untouched.
 */
@Composable
fun NumberInputSheet(
    title: String,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    extraContent: @Composable (ColumnScope.() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Tinta)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("target-input"),
            )
            Spacer(Modifier.height(16.dp))
            PillButton(
                text = stringResource(R.string.save),
                onClick = { text.toIntOrNull()?.let(onConfirm) },
                enabled = text.toIntOrNull() != null,
                modifier = Modifier.fillMaxWidth(),
            )
            extraContent?.invoke(this)
        }
    }
}
