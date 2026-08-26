package com.alvarotc.bito.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.R
import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.theme.BitoTheme
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import kotlinx.coroutines.launch

/**
 * Pick the ONE active habit a [SingleHabitWidget] instance follows. Mirrors
 * [TodayWidgetConfigActivity]'s flow (mandatory configure + reconfigure entry
 * point, `android:widgetFeatures="reconfigurable"` in `single_habit_widget_info.xml`)
 * but with a radio choice instead of a checklist — saving is gated on having one.
 */
class SingleHabitConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId =
            intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // A back-press (or any exit before Save) must cancel placement cleanly.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        val container = (application as BitoApp).container
        val viewModel =
            ViewModelProvider(this, SingleHabitConfigViewModel.factory(container))[SingleHabitConfigViewModel::class.java]

        // Only on first creation, same reasoning as TodayWidgetConfigActivity: the ViewModel
        // survives rotation, and runCatching guards the glanceId lookup's untested failure path
        // (the VM's null default then simply keeps Save disabled).
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                runCatching {
                    val glanceId = GlanceAppWidgetManager(this@SingleHabitConfigActivity).getGlanceIdBy(appWidgetId)
                    getAppWidgetState(this@SingleHabitConfigActivity, PreferencesGlanceStateDefinition, glanceId)
                }.onSuccess { existing -> viewModel.setInitial(existing[SingleHabitWidgetKeys.habitId]) }
            }
        }

        setContent {
            BitoTheme {
                SingleHabitConfigScreen(
                    viewModel = viewModel,
                    onSave = {
                        lifecycleScope.launch {
                            val habitId = viewModel.selected.value ?: return@launch
                            val manager = GlanceAppWidgetManager(this@SingleHabitConfigActivity)
                            val glanceId = manager.getGlanceIdBy(appWidgetId)
                            updateAppWidgetState(this@SingleHabitConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                                prefs.toMutablePreferences().apply { this[SingleHabitWidgetKeys.habitId] = habitId }
                            }
                            SingleHabitWidget().update(this@SingleHabitConfigActivity, glanceId)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                            finish()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SingleHabitConfigScreen(
    viewModel: SingleHabitConfigViewModel,
    onSave: () -> Unit,
) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    Scaffold(containerColor = Papel) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.widget_single_config_title), style = MaterialTheme.typography.headlineLarge, color = Tinta)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.widget_single_config_body), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                Modifier
                    .weight(1f)
                    .selectableGroup(),
            ) {
                items(habits, key = { it.id }) { habit ->
                    HabitRadioRow(
                        habit = habit,
                        selected = selected == habit.id,
                        onSelect = { viewModel.select(habit.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            PillButton(
                text = stringResource(R.string.widget_config_save),
                onClick = onSave,
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HabitRadioRow(
    habit: HabitEntity,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    BitoCard(modifier = Modifier.fillMaxWidth(), border = if (selected) Hoja else Borde, onClick = onSelect) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(habit.name, style = MaterialTheme.typography.titleMedium, color = Tinta)
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = Hoja, unselectedColor = Borde),
            )
        }
    }
}
