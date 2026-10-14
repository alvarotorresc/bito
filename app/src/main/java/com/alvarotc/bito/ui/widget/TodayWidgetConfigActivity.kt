package com.alvarotc.bito.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.theme.BitoTheme
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
            BitoTheme(sonido = container.sonido, tacto = container.tacto) {
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

    MarcoConfigWidget(
        titulo = stringResource(R.string.widget_config_title),
        cuerpo = stringResource(R.string.widget_config_body),
        guardar = stringResource(R.string.widget_config_save),
        alGuardar = onSave,
    ) {
        ElegirVariasConAgua(
            habitos = habits,
            elegidos = selected,
            alAlternar = viewModel::toggle,
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        )
    }
}
