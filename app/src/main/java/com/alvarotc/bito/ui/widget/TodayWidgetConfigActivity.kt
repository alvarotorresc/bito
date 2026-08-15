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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
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
 * Per-instance habit checklist (spec §3.3): pick which ACTIVE habits this
 * widget shows, or leave every habit unchecked to show all pending ones.
 * Also the reconfigure entry point (`android:widgetFeatures="reconfigurable"`
 * in `today_widget_info.xml`), so it preloads any selection already stored
 * for this widget instance.
 */
class TodayWidgetConfigActivity : ComponentActivity() {
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
            ViewModelProvider(this, WidgetConfigViewModel.factory(container))[WidgetConfigViewModel::class.java]

        // Only on first creation: the ViewModel survives config changes (e.g. rotation), so
        // re-running this on every onCreate would stomp on an in-progress selection with the
        // stored one. runCatching guards a glanceId lookup that has no test coverage of its
        // failure path; falling back to the VM's already-empty default is a safe no-op.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                runCatching {
                    val glanceId = GlanceAppWidgetManager(this@TodayWidgetConfigActivity).getGlanceIdBy(appWidgetId)
                    getAppWidgetState(this@TodayWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
                }.onSuccess { existing -> viewModel.setInitial(existing[TodayWidgetKeys.selectedIds] ?: emptySet()) }
            }
        }

        setContent {
            BitoTheme {
                WidgetConfigScreen(
                    viewModel = viewModel,
                    onSave = {
                        lifecycleScope.launch {
                            val manager = GlanceAppWidgetManager(this@TodayWidgetConfigActivity)
                            val glanceId = manager.getGlanceIdBy(appWidgetId)
                            val ids = viewModel.selected.value
                            updateAppWidgetState(this@TodayWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                                prefs.toMutablePreferences().apply {
                                    if (ids.isEmpty()) remove(TodayWidgetKeys.selectedIds) else this[TodayWidgetKeys.selectedIds] = ids
                                }
                            }
                            TodayWidget().update(this@TodayWidgetConfigActivity, glanceId)
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
private fun WidgetConfigScreen(
    viewModel: WidgetConfigViewModel,
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
            Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineLarge, color = Tinta)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.widget_config_body), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(habits, key = { it.id }) { habit ->
                    HabitCheckRow(
                        habit = habit,
                        checked = selected.contains(habit.id),
                        onToggle = { viewModel.toggle(habit.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            PillButton(
                text = stringResource(R.string.widget_config_save),
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HabitCheckRow(
    habit: HabitEntity,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    BitoCard(modifier = Modifier.fillMaxWidth(), border = Borde, onClick = onToggle) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(habit.name, style = MaterialTheme.typography.titleMedium, color = Tinta)
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Hoja, uncheckedColor = Borde),
            )
        }
    }
}
