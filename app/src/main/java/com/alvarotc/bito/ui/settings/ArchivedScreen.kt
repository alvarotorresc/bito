package com.alvarotc.bito.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.GhostIconButton
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Every archived habit, list-by-pattern like [com.alvarotc.bito.ui.stats.RecordsScreen]: a tap opens its (read-only) detail. */
@Composable
fun ArchivedScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenHabit: (String) -> Unit,
) {
    val archived by viewModel.archivedHabits.collectAsStateWithLifecycle()
    Scaffold(containerColor = Papel) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArchivedHeader(onBack)
            archived.forEach { habit ->
                ArchivedRow(habit, onClick = { onOpenHabit(habit.id) })
            }
        }
    }
}

@Composable
private fun ArchivedHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostIconButton(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), onClick = onBack)
        Text(
            stringResource(R.string.archived_title),
            style = MaterialTheme.typography.headlineLarge,
            color = Tinta,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun ArchivedRow(
    habit: ArchivedHabitUi,
    onClick: () -> Unit,
) {
    BitoCard(modifier = Modifier.fillMaxWidth().testTag("archived-${habit.id}"), onClick = onClick) {
        Text(habit.name, style = MaterialTheme.typography.bodyLarge, color = Tinta)
        habit.archivedOnDay?.let { day ->
            Spacer(Modifier.height(4.dp))
            val date =
                remember(day) {
                    LocalDate.ofEpochDay(day.toLong()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
                }
            Text(stringResource(R.string.archived_since, date), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
        }
    }
}
