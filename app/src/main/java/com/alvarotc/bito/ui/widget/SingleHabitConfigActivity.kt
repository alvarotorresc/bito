package com.alvarotc.bito.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.componentes.ElegirConAgua
import com.alvarotc.bito.ui.sistema.componentes.OpcionAgua
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.launch

/**
 * Pick the ONE active habit a [SingleHabitWidget] instance follows. Mirrors
 * [TodayWidgetConfigActivity]'s flow (mandatory configure + reconfigure entry
 * point, `android:widgetFeatures="reconfigurable"` in `single_habit_widget_info.xml`)
 * but with a radio choice instead of a checklist.
 *
 * Saving is deliberately NOT gated on having picked something. Configuration here is mandatory,
 * so Android drops the widget unless this activity returns [RESULT_OK] — and the list only offers
 * ACTIVE habits, so someone who places the widget before creating their first habit (or with
 * everything paused/archived) would face a dead button and then watch the widget vanish with no
 * explanation. Saving with nothing chosen instead removes the stored id, which
 * [buildSingleHabitModel] renders as [SingleHabitModel.Missing]: a tappable "choose a habit"
 * that reopens this very screen. The widget stays on the home screen and heals itself.
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
        // (the VM's null default then just leaves the reconfigure screen with nothing preselected).
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                runCatching {
                    val glanceId = GlanceAppWidgetManager(this@SingleHabitConfigActivity).getGlanceIdBy(appWidgetId)
                    getAppWidgetState(this@SingleHabitConfigActivity, PreferencesGlanceStateDefinition, glanceId)
                }.onSuccess { existing -> viewModel.setInitial(existing[SingleHabitWidgetKeys.habitId]) }
            }
        }

        setContent {
            BitoTheme(sonido = container.sonido, tacto = container.tacto) {
                SingleHabitConfigScreen(
                    viewModel = viewModel,
                    onSave = {
                        lifecycleScope.launch {
                            val habitId = viewModel.selected.value
                            val manager = GlanceAppWidgetManager(this@SingleHabitConfigActivity)
                            val glanceId = manager.getGlanceIdBy(appWidgetId)
                            updateAppWidgetState(this@SingleHabitConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                                // Remove rather than store a blank: the model keys the "choose a
                                // habit" fallback on a null id (same shape as the sibling widget's
                                // empty selection).
                                val key = SingleHabitWidgetKeys.habitId
                                prefs.toMutablePreferences().apply {
                                    if (habitId == null) remove(key) else this[key] = habitId
                                }
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

    SingleHabitConfigContent(
        habits = habits,
        selected = selected,
        onSelect = viewModel::select,
        onSave = onSave,
    )
}

/** The screen itself, stateless so a test can drive the empty list the ViewModel can produce. */
@Composable
internal fun SingleHabitConfigContent(
    habits: List<HabitEntity>,
    selected: String?,
    onSelect: (String) -> Unit,
    onSave: () -> Unit,
) {
    MarcoConfigWidget(
        titulo = stringResource(R.string.widget_single_config_title),
        cuerpo = stringResource(R.string.widget_single_config_body),
        guardar = stringResource(R.string.widget_config_save),
        alGuardar = onSave,
    ) {
        if (habits.isEmpty()) {
            EmptyHabits(Modifier.weight(1f))
        } else {
            ElegirConAgua(
                opciones = habits.map { OpcionAgua(clave = it.id, texto = it.name) },
                elegida = selected,
                alElegir = onSelect,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            )
        }
    }
}

/**
 * Nothing to choose from — no habit yet, or every one of them paused/archived. Text only, no
 * "open the app" shortcut on purpose: leaving this activity before Save cancels the placement and
 * the widget is discarded, so a shortcut out of here would undo the very thing this screen is
 * protecting. It points at Save instead, which keeps the widget and lets it ask again later.
 */
@Composable
private fun EmptyHabits(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.widget_single_config_empty),
            style = BitoTema.tipos.t16,
            color = BitoTema.colores.tintaSuave,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("single-habit-config-empty"),
        )
    }
}
