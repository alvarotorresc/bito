@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta

/** The "+" button's first question: a habit, or a task. */
@Composable
fun CreateChoiceSheet(
    onHabit: () -> Unit,
    onTask: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Tarjeta) {
        Column(Modifier.padding(20.dp).testTag("create-choice-sheet")) {
            Text(stringResource(R.string.create_choice_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
            Spacer(Modifier.height(16.dp))
            PillButton(stringResource(R.string.create_choice_habit), onClick = onHabit, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PillButton(stringResource(R.string.create_choice_task), onClick = onTask, modifier = Modifier.fillMaxWidth())
        }
    }
}
