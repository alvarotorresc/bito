package com.alvarotc.bito.ui.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
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
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.ui.sistema.BitoClaro
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.TodayUiState
import com.alvarotc.bito.ui.today.buildTodayUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import java.time.ZoneId

/** Offscreen render size (px) for the header's mini Habi — Glance only paints bitmaps (tech doc §6.1). */
private const val HABI_BITMAP_SIZE_PX = 192

class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(STRIP, NARROW, FULL))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val container = (context.applicationContext as BitoApp).container
        // QA 2026-08-23: data used to be loaded once out here and captured by provideContent, so a
        // live Glance session recomposed with stale values (the tapped counter never moved until
        // the session died). The content now collects a Flow of the same four sources
        // WidgetRefresher watches — any DB/settings write repaints, updateAll or not.
        // `today` recomputes only on emissions; the midnight rollover self-heals because idle
        // Glance sessions expire, so WidgetMidnightReceiver's updateAll restarts provideGlance
        // (fresh first()) — any write in between heals it sooner.
        val dataFlow =
            combine(
                container.settings.settings,
                container.domainState.observe(),
                container.habits.observeHabits(),
                container.rewards.observeOwnedItems(),
            ) { prefs, domain, entities, owned ->
                val today = LogicalDays.logicalDayOf(System.currentTimeMillis(), prefs.dayCutoffMinutes, ZoneId.systemDefault())
                val state =
                    buildTodayUiState(
                        domain,
                        entities.associate { it.id to it.sortOrder },
                        today,
                        prefs.personality,
                        owned,
                    )
                WidgetData(
                    state,
                    HabiWidgetCache.bitmap(context, state.spec, HABI_BITMAP_SIZE_PX),
                    HabiWidgetCache.bitmap(context, state.spec.copy(mood = Mood.RADIANT), HABI_BITMAP_SIZE_PX),
                )
            }.conflate().flowOn(Dispatchers.Default)
        // First paint stays synchronous-ish: seed with a real emission so the widget never shows
        // an empty frame while the flow warms up.
        val initial = dataFlow.first()
        provideContent {
            val data by dataFlow.collectAsState(initial = initial)
            val widgetPrefs = currentState<Preferences>()
            val selected = widgetPrefs[TodayWidgetKeys.selectedIds]
            WidgetContent(buildWidgetModel(data.state, selected), data.habiBitmap, data.radiantBitmap)
        }
    }

    private data class WidgetData(val state: TodayUiState, val habiBitmap: Bitmap, val radiantBitmap: Bitmap)

    companion object {
        /** 2x1-ish: no room for rows — a header strip (Habi + n/n) that opens the app. */
        val STRIP = DpSize(110.dp, 48.dp)

        /** 2x2-ish: the list fits but the title doesn't — compact header and rows. */
        val NARROW = DpSize(110.dp, 110.dp)

        /** Wide enough (minWidth of the widget info, 250dp) for the full layout with habit names. */
        val FULL = DpSize(250.dp, 110.dp)
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
    radiantBitmap: Bitmap,
) {
    val context = LocalContext.current
    // SizeMode.Responsive hands LocalSize exactly one of the declared buckets, so
    // comparing against them picks the layout the host actually has room for.
    val size = LocalSize.current
    val todoHecho = model.items.isEmpty() && model.total > 0
    val habi = if (todoHecho) radiantBitmap else habiBitmap
    if (size.height < TodayWidget.NARROW.height) {
        StripContent(model, habi)
        return
    }
    val full = size.width >= TodayWidget.FULL.width
    val colores = BitoClaro
    Row(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_fondo))
                .padding(10.dp),
    ) {
        if (full) {
            // El nicho: Habi en su hornacina, sin accesorios (se ve igual de pequeño).
            Box(
                modifier =
                    GlanceModifier
                        .width(80.dp)
                        .fillMaxHeight()
                        .background(ImageProvider(R.drawable.widget_nicho))
                        .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Image(
                    provider = ImageProvider(habi),
                    contentDescription = null,
                    modifier = GlanceModifier.size(72.dp),
                )
            }
            Box(modifier = GlanceModifier.width(10.dp)) {}
        }
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!full) {
                    Image(
                        provider = ImageProvider(habi),
                        contentDescription = null,
                        modifier = GlanceModifier.size(36.dp),
                    )
                    Box(modifier = GlanceModifier.width(6.dp)) {}
                }
                if (full) {
                    Text(
                        text = context.getString(R.string.widget_title),
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = ColorProvider(colores.tinta), fontSize = 19.sp, fontWeight = FontWeight.Bold),
                    )
                } else {
                    Box(modifier = GlanceModifier.defaultWeight()) {}
                }
                Text(
                    text = context.getString(R.string.widget_progress, model.done, model.total),
                    style = TextStyle(color = ColorProvider(colores.tinta), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
            }
            if (todoHecho) {
                Column(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = context.getString(R.string.widget_all_done),
                        style = TextStyle(color = ColorProvider(colores.tinta), fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    )
                }
            } else {
                Puntitos(model.done, model.total)
                model.items.take(FILAS_MAX).forEach { item -> WidgetRow(item) }
                val ocultos = pendientesOcultos(model.items.size)
                if (ocultos > 0) {
                    Text(
                        text = context.getString(R.string.widget2_more, ocultos),
                        modifier = GlanceModifier.padding(start = 8.dp),
                        style = TextStyle(color = ColorProvider(colores.tintaSuave), fontSize = 14.sp),
                    )
                }
            }
        }
    }
}

/** Un puntito por hábito del día: de agua los hechos, de hornacina los pendientes (hasta doce). */
@Composable
private fun Puntitos(
    hechos: Int,
    total: Int,
) {
    val n = total.coerceIn(0, 12)
    if (n == 0) return
    Row(modifier = GlanceModifier.padding(vertical = 4.dp)) {
        for (i in 0 until n) {
            Image(
                provider = ImageProvider(if (i < hechos) R.drawable.widget_punto_hecho else R.drawable.widget_punto),
                contentDescription = null,
                modifier = GlanceModifier.size(8.dp),
            )
            Box(modifier = GlanceModifier.width(4.dp)) {}
        }
    }
}

/** The smallest bucket: just the mini Habi and today's count, the whole strip opens the app. */
@Composable
private fun StripContent(
    model: WidgetModel,
    habi: Bitmap,
) {
    val context = LocalContext.current
    val colores = BitoClaro
    val description = context.getString(R.string.widget_open_action, context.getString(R.string.widget_title))
    Row(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_fondo))
                .clickable(actionStartActivity<MainActivity>())
                .semantics { contentDescription = description }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(habi),
            contentDescription = null,
            modifier = GlanceModifier.size(36.dp),
        )
        Box(modifier = GlanceModifier.defaultWeight()) {}
        val text =
            if (model.items.isEmpty() && model.total > 0) {
                context.getString(R.string.widget_all_done)
            } else {
                context.getString(R.string.widget_progress, model.done, model.total)
            }
        Text(
            text = text,
            style = TextStyle(color = ColorProvider(colores.tinta), fontSize = 19.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** Una fila: el nombre abre Bito; el círculo (44 dp de toque) registra. */
@Composable
private fun WidgetRow(item: WidgetItem) {
    val context = LocalContext.current
    val colores = BitoClaro
    val action =
        actionRunCallback<LogHabitAction>(
            actionParametersOf(
                LogHabitAction.HABIT_ID to item.habitId,
                LogHabitAction.AMOUNT to item.step,
            ),
        )
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                GlanceModifier
                    .defaultWeight()
                    .padding(start = 8.dp)
                    .clickable(actionStartActivity<MainActivity>())
                    .semantics { contentDescription = context.getString(R.string.widget_open_action, item.name) },
        ) {
            Text(
                text = item.name,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(colores.tinta), fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
            if (item.kind == CardKind.COUNTER) {
                Text(
                    text = context.getString(R.string.widget_progress, item.progress, item.target),
                    style = TextStyle(color = ColorProvider(colores.tintaSuave), fontSize = 12.sp),
                )
            }
        }
        if (item.tapLogs) {
            Box(
                modifier =
                    GlanceModifier
                        .size(44.dp)
                        .clickable(action)
                        .semantics { contentDescription = context.getString(R.string.widget_log_action, item.name) },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_circulo),
                    contentDescription = null,
                    modifier = GlanceModifier.size(26.dp),
                )
            }
        }
    }
}
