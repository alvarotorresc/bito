package com.alvarotc.bito.ui.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.MainActivity
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.ui.habi.renderHabiBitmap
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.buildTodayUiState
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/** Offscreen render size (px) for the header's mini Habi — Glance only paints bitmaps (tech doc §6.1). */
private const val HABI_BITMAP_SIZE_PX = 96

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val container = (context.applicationContext as BitoApp).container
        val prefs = container.settings.settings.first()
        val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), prefs.dayCutoffMinutes, ZoneId.systemDefault())
        val entities = container.habits.observeHabits().first()
        val owned = container.rewards.observeOwnedItems().first()
        val state =
            buildTodayUiState(
                container.domainState.snapshot(),
                entities.associate { it.id to it.sortOrder },
                today,
                prefs.personality,
                owned,
            )
        // Rendered once here, outside provideContent's composable scope, so a recomposition
        // triggered by currentState<Preferences>() (the per-instance selection) never re-paints it.
        val habiBitmap = renderHabiBitmap(state.spec, HABI_BITMAP_SIZE_PX)
        provideContent {
            val widgetPrefs = currentState<Preferences>()
            val selected = widgetPrefs[TodayWidgetKeys.selectedIds]
            WidgetContent(buildWidgetModel(state, selected), habiBitmap)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

object TodayWidgetKeys {
    val selectedIds = stringSetPreferencesKey("selected_habit_ids")
}

@Composable
private fun WidgetContent(
    model: WidgetModel,
    habiBitmap: Bitmap,
) {
    val context = LocalContext.current
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Papel))
                .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(habiBitmap),
                contentDescription = null,
                modifier = GlanceModifier.size(36.dp),
            )
            Text(
                text = context.getString(R.string.widget_title),
                modifier = GlanceModifier.defaultWeight().padding(start = 8.dp),
                style = TextStyle(color = ColorProvider(Tinta), fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = context.getString(R.string.widget_progress, model.done, model.total),
                style = TextStyle(color = ColorProvider(TintaSuave), fontSize = 12.sp),
            )
        }
        if (model.items.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = context.getString(R.string.widget_all_done),
                    style = TextStyle(color = ColorProvider(Hoja), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(model.items) { item ->
                    // A row-level padding modifier would land inside the card background
                    // (Glance maps one composable to one RemoteViews padding call, unlike
                    // Compose's nesting), so the inter-card gap needs its own wrapper.
                    Box(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        WidgetRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(item: WidgetItem) {
    val context = LocalContext.current
    val action =
        if (item.tapLogs) {
            actionRunCallback<LogHabitAction>(
                actionParametersOf(
                    LogHabitAction.HABIT_ID to item.habitId,
                    LogHabitAction.AMOUNT to item.step,
                ),
            )
        } else {
            actionStartActivity<MainActivity>()
        }
    val description =
        if (item.tapLogs) {
            context.getString(R.string.widget_log_action, item.name)
        } else {
            context.getString(R.string.widget_open_action, item.name)
        }
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(Tarjeta))
                .cornerRadius(16.dp)
                .clickable(action)
                .semantics { contentDescription = description }
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.name,
                style = TextStyle(color = ColorProvider(Tinta), fontSize = 14.sp),
            )
            if (item.kind == CardKind.COUNTER) {
                Text(
                    text = context.getString(R.string.widget_progress, item.progress, item.target),
                    style = TextStyle(color = ColorProvider(TintaSuave), fontSize = 12.sp),
                )
            }
        }
        if (item.tapLogs) {
            Box(
                modifier =
                    GlanceModifier
                        .size(28.dp)
                        .cornerRadius(14.dp)
                        .background(ColorProvider(Hoja)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    style = TextStyle(color = ColorProvider(Tarjeta), fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}
