package com.alvarotc.bito.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.MainActivity
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.buildTodayUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import java.time.ZoneId

/**
 * One habit, one tap (compact 2x1/2x2 sibling of [TodayWidget]). The chosen habit id lives in
 * this instance's Glance state ([SingleHabitWidgetKeys.habitId], written by
 * [SingleHabitConfigActivity]); the content collects the same four sources [WidgetRefresher]
 * watches, so an in-app log repaints this widget too, and a tap logs through the exact same
 * [LogHabitAction] path the list widget uses.
 */
class SingleHabitWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, TALL))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val container = (context.applicationContext as BitoApp).container
        // The "choose another habit" fallback reopens this instance's configure activity, which
        // needs the host-side widget id — resolved once here, not in composition.
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val stateFlow =
            combine(
                container.settings.settings,
                container.domainState.observe(),
                container.habits.observeHabits(),
                container.rewards.observeOwnedItems(),
            ) { prefs, domain, entities, owned ->
                val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), prefs.dayCutoffMinutes, ZoneId.systemDefault())
                buildTodayUiState(
                    domain,
                    entities.associate { it.id to it.sortOrder },
                    today,
                    prefs.personality,
                    owned,
                )
            }.conflate().flowOn(Dispatchers.Default)
        val initial = stateFlow.first()
        provideContent {
            val state by stateFlow.collectAsState(initial = initial)
            val widgetPrefs = currentState<Preferences>()
            SingleHabitContent(buildSingleHabitModel(state, widgetPrefs[SingleHabitWidgetKeys.habitId]), appWidgetId)
        }
    }

    companion object {
        /** 2x1: a one-line strip. */
        val COMPACT = DpSize(110.dp, 48.dp)

        /** 2x2 and up: centered, with a bigger progress readout. */
        val TALL = DpSize(110.dp, 110.dp)
    }
}

class SingleHabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleHabitWidget()
}

object SingleHabitWidgetKeys {
    val habitId = stringPreferencesKey("single_habit_id")
}

@Composable
private fun SingleHabitContent(
    model: SingleHabitModel,
    appWidgetId: Int,
) {
    val context = LocalContext.current
    when (model) {
        is SingleHabitModel.Missing ->
            MessageContent(
                context.getString(R.string.widget_single_missing),
                reconfigureAction(context, appWidgetId),
            )
        is SingleHabitModel.Paused ->
            MessageContent(
                context.getString(R.string.widget_single_paused, model.name),
                actionStartActivity<MainActivity>(),
            )
        is SingleHabitModel.Active -> ActiveContent(model)
    }
}

/** The archived-or-gone and paused fallbacks: one friendly line, whole surface tappable. */
@Composable
private fun MessageContent(
    message: String,
    action: Action,
) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Papel))
                .clickable(action)
                .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = TextStyle(color = ColorProvider(TintaSuave), fontSize = 13.sp, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun ActiveContent(model: SingleHabitModel.Active) {
    val context = LocalContext.current
    val action =
        if (model.tapLogs) {
            actionRunCallback<LogHabitAction>(
                actionParametersOf(
                    LogHabitAction.HABIT_ID to model.habitId,
                    LogHabitAction.AMOUNT to model.step,
                ),
            )
        } else {
            actionStartActivity<MainActivity>()
        }
    val description =
        if (model.tapLogs) {
            context.getString(R.string.widget_log_action, model.name)
        } else {
            context.getString(R.string.widget_open_action, model.name)
        }
    val root =
        GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Papel))
            .clickable(action)
            .semantics { contentDescription = description }
            .padding(12.dp)
    if (LocalSize.current.height >= SingleHabitWidget.TALL.height) {
        Column(
            modifier = root,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = model.name,
                maxLines = 2,
                style =
                    TextStyle(
                        color = ColorProvider(Tinta),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
            )
            captionOf(model)?.let { (caption, color) ->
                Box(modifier = GlanceModifier.padding(top = 4.dp)) {
                    Text(text = caption, style = TextStyle(color = ColorProvider(color), fontSize = 12.sp))
                }
            }
            StatusBubble(model, sizeDp = 32, modifier = GlanceModifier.padding(top = 8.dp))
        }
    } else {
        Row(
            modifier = root,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = model.name,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Tinta), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                captionOf(model)?.let { (caption, color) ->
                    Text(text = caption, style = TextStyle(color = ColorProvider(color), fontSize = 11.sp))
                }
            }
            StatusBubble(model, sizeDp = 26, modifier = GlanceModifier.padding(start = 8.dp))
        }
    }
}

/**
 * One caption line per kind, or null when the name alone says it all (a pending CHECK).
 * Colors: quiet by default, [Hoja] for a clean abstinence day, [Brasa] for a failed one or a
 * blown AT_MOST limit — the same accents the Today cards use.
 */
@Composable
private fun captionOf(model: SingleHabitModel.Active): Pair<String, Color>? {
    val context = LocalContext.current
    return when (model.kind) {
        CardKind.CHECK -> if (model.doneToday) context.getString(R.string.widget_single_done) to Hoja else null
        CardKind.COUNTER ->
            context.getString(R.string.widget_progress, model.progress, model.target) to
                if (model.failed) Brasa else TintaSuave
        CardKind.DURATION ->
            context.getString(R.string.widget_progress_min, model.progress, model.target) to
                if (model.failed) Brasa else TintaSuave
        CardKind.ABSTINENCE ->
            if (model.failed) {
                context.getString(R.string.relapse_today) to Brasa
            } else {
                context.getString(R.string.clean_today) to Hoja
            }
    }
}

/** The trailing "+" (tap logs) or "✓" (nothing left today) circle; abstinence carries neither. */
@Composable
private fun StatusBubble(
    model: SingleHabitModel.Active,
    sizeDp: Int,
    modifier: GlanceModifier,
) {
    val glyph =
        when {
            model.tapLogs -> "+"
            model.doneToday && model.kind != CardKind.ABSTINENCE -> "✓"
            else -> return
        }
    Box(modifier = modifier) {
        Box(
            modifier =
                GlanceModifier
                    .size(sizeDp.dp)
                    .cornerRadius((sizeDp / 2).dp)
                    .background(ColorProvider(Hoja)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyph,
                style = TextStyle(color = ColorProvider(Tarjeta), fontSize = (sizeDp / 2).sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

/** Reopens this instance's configure activity — the friendly way out of a gone habit. */
private fun reconfigureAction(
    context: Context,
    appWidgetId: Int,
): Action =
    actionStartActivity(
        Intent(context, SingleHabitConfigActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            // Distinct data keeps each instance's PendingIntent from colliding with its siblings'.
            .setData(Uri.parse("bito://widget/single/$appWidgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
    )
