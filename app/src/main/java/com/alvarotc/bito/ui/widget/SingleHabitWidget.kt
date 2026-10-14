package com.alvarotc.bito.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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

/**
 * Below this width the mini Habi eats the habit name: at the 110 dp minimum, a 28 dp Habi left
 * about 16 dp for the name and the Missing line did not fit at all.
 */
internal val HABI_MIN_WIDTH = 150.dp

/** Whether the one-line layouts (compact Active, Missing, Paused) have room for the mini Habi. */
internal fun showsHabi(width: Dp): Boolean = width >= HABI_MIN_WIDTH

/** Offscreen render size (px) for the mini Habi beside the habit name — same technique as [TodayWidget]. */
private const val HABI_BITMAP_SIZE_PX = 192

/**
 * One habit, one tap (compact 2x1/2x2 sibling of [TodayWidget]). The chosen habit id lives in
 * this instance's Glance state ([SingleHabitWidgetKeys.habitId], written by
 * [SingleHabitConfigActivity]); the content collects the same four sources [WidgetRefresher]
 * watches, so an in-app log repaints this widget too, and a tap logs through the exact same
 * [LogHabitAction] path the list widget uses.
 */
class SingleHabitWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, COMPACT_WIDE, TALL, TALL_WIDE))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val container = (context.applicationContext as BitoApp).container
        // The "choose another habit" fallback reopens this instance's configure activity, which
        // needs the host-side widget id — resolved once here, not in composition.
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
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
                SingleHabitData(
                    state,
                    HabiWidgetCache.bitmap(context, state.spec, HABI_BITMAP_SIZE_PX),
                    HabiWidgetCache.bitmap(context, state.spec.copy(mood = Mood.RADIANT), HABI_BITMAP_SIZE_PX),
                )
            }.conflate().flowOn(Dispatchers.Default)
        val initial = dataFlow.first()
        provideContent {
            val data by dataFlow.collectAsState(initial = initial)
            val widgetPrefs = currentState<Preferences>()
            val model = buildSingleHabitModel(data.state, widgetPrefs[SingleHabitWidgetKeys.habitId])
            SingleHabitContent(model, appWidgetId, data.habiBitmap, data.radiantBitmap)
        }
    }

    private data class SingleHabitData(val state: TodayUiState, val habiBitmap: Bitmap, val radiantBitmap: Bitmap)

    companion object {
        /** 2x1: a one-line strip. */
        val COMPACT = DpSize(110.dp, 48.dp)

        /**
         * 2x1 wide enough for Habi. With [SizeMode.Responsive], `LocalSize` is always one of the
         * declared sizes, never the real one — without a wide sibling the width check in
         * [showsHabi] would only ever see 110 dp.
         */
        val COMPACT_WIDE = DpSize(HABI_MIN_WIDTH, 48.dp)

        /** 2x2 and up: centered, with a bigger progress readout. */
        val TALL = DpSize(110.dp, 110.dp)

        /** 2x2 wide enough for Habi in the message states (see [COMPACT_WIDE]). */
        val TALL_WIDE = DpSize(HABI_MIN_WIDTH, 110.dp)
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
    habiBitmap: Bitmap,
    radiantBitmap: Bitmap,
) {
    val context = LocalContext.current
    when (model) {
        is SingleHabitModel.Missing ->
            MessageContent(
                context.getString(R.string.widget_single_missing),
                null,
                reconfigureAction(context, appWidgetId),
                habiBitmap,
                R.drawable.widget_fondo,
            )
        is SingleHabitModel.Paused ->
            MessageContent(
                model.name,
                context.getString(R.string.widget2_paused_label),
                actionStartActivity<MainActivity>(),
                habiBitmap,
                R.drawable.widget_fondo_pausa,
            )
        is SingleHabitModel.Active -> ActiveContent(model, habiBitmap, radiantBitmap)
    }
}

/** The archived-or-gone and paused fallbacks: mini Habi (when [showsHabi]) beside one friendly line, whole surface tappable. */
@Composable
private fun MessageContent(
    message: String,
    caption: String?,
    action: Action,
    habiBitmap: Bitmap,
    @DrawableRes fondo: Int,
) {
    val colores = BitoClaro
    Row(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(fondo))
                .clickable(action)
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val withHabi = showsHabi(LocalSize.current.width)
        if (withHabi) {
            Image(
                provider = ImageProvider(habiBitmap),
                contentDescription = null,
                modifier = GlanceModifier.size(36.dp),
            )
        }
        Column(
            modifier = GlanceModifier.defaultWeight().padding(start = if (withHabi) 8.dp else 0.dp),
            horizontalAlignment = if (withHabi) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                maxLines = 2,
                style =
                    TextStyle(
                        color = ColorProvider(if (caption == null) colores.tintaSuave else colores.tinta),
                        fontSize = if (caption == null) 14.sp else 16.sp,
                        fontWeight = if (caption == null) FontWeight.Normal else FontWeight.Bold,
                        textAlign = if (withHabi) TextAlign.Start else TextAlign.Center,
                    ),
            )
            if (caption != null) {
                Text(text = caption, style = TextStyle(color = ColorProvider(colores.tintaSuave), fontSize = 14.sp))
            }
        }
    }
}

@Composable
private fun ActiveContent(
    model: SingleHabitModel.Active,
    habiBitmap: Bitmap,
    radiantBitmap: Bitmap,
) {
    val context = LocalContext.current
    val colores = BitoClaro
    val logAction =
        actionRunCallback<LogHabitAction>(
            actionParametersOf(
                LogHabitAction.HABIT_ID to model.habitId,
                LogHabitAction.AMOUNT to model.step,
            ),
        )
    val ringAction = if (model.tapLogs) logAction else actionStartActivity<MainActivity>()
    val ringDescription =
        if (model.tapLogs) {
            context.getString(R.string.widget_log_action, model.name)
        } else {
            context.getString(R.string.widget_open_action, model.name)
        }
    val hecho = model.doneToday && model.kind != CardKind.ABSTINENCE
    val fondo = if (hecho) R.drawable.widget_fondo_hecho else R.drawable.widget_fondo
    val tall = LocalSize.current.height >= SingleHabitWidget.TALL.height
    val ringSize = if (tall) 64.dp else 48.dp
    val root =
        GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(fondo))
            .padding(if (tall) 12.dp else 6.dp)
    val ring: @Composable () -> Unit = {
        // Dos zonas: el círculo registra (o abre la app si ya no hay nada que registrar).
        Box(
            modifier =
                GlanceModifier
                    .size(ringSize)
                    .clickable(ringAction)
                    .semantics { contentDescription = ringDescription },
            contentAlignment = Alignment.Center,
        ) {
            if (hecho) {
                Image(
                    provider = ImageProvider(radiantBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.size(ringSize),
                )
            } else {
                Image(
                    provider = ImageProvider(aroDrawable(model.aro())),
                    contentDescription = null,
                    modifier = GlanceModifier.size(ringSize),
                )
                cifraOf(model)?.let { cifra ->
                    Text(
                        text = cifra,
                        style =
                            TextStyle(
                                color = ColorProvider(colores.tinta),
                                fontSize = if (tall) 16.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                }
            }
        }
    }
    val openApp = actionStartActivity<MainActivity>()
    val openDescription = context.getString(R.string.widget_open_action, model.name)
    if (tall) {
        Column(
            modifier = root,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ring()
            Column(
                modifier =
                    GlanceModifier
                        .padding(top = 6.dp)
                        .clickable(openApp)
                        .semantics { contentDescription = openDescription },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = model.name,
                    maxLines = 2,
                    style =
                        TextStyle(
                            color = ColorProvider(colores.tinta),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                )
                captionOf(model)?.let { (caption, color) ->
                    Text(text = caption, style = TextStyle(color = ColorProvider(color), fontSize = 14.sp, textAlign = TextAlign.Center))
                }
            }
        }
    } else {
        Row(
            modifier = root,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ring()
            Column(
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .padding(start = 6.dp)
                        .clickable(openApp)
                        .semantics { contentDescription = openDescription },
            ) {
                Text(
                    text = model.name,
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(colores.tinta), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                captionOf(model)?.let { (caption, color) ->
                    Text(text = caption, maxLines = 1, style = TextStyle(color = ColorProvider(color), fontSize = 14.sp))
                }
            }
        }
    }
}

/** El número que va dentro del aro: lo hecho de un contador o de una duración; un check no lleva cifra. */
private fun cifraOf(model: SingleHabitModel.Active): String? =
    when (model.kind) {
        CardKind.COUNTER, CardKind.DURATION -> model.progress.toString()
        else -> null
    }

/**
 * One caption line per kind, or null when the name alone says it all (a pending CHECK).
 * Colors: quiet by default, [BitoColores.okTexto] for a clean abstinence day, [BitoColores.peligroTexto]
 * for a failed one or a blown AT_MOST limit.
 */
@Composable
private fun captionOf(model: SingleHabitModel.Active): Pair<String, androidx.compose.ui.graphics.Color>? {
    val context = LocalContext.current
    val colores = BitoClaro
    return when (model.kind) {
        CardKind.CHECK -> if (model.doneToday) context.getString(R.string.widget_single_done) to colores.okTexto else null
        CardKind.COUNTER ->
            if (model.doneToday) {
                context.getString(R.string.widget_single_done) to colores.okTexto
            } else {
                context.getString(R.string.widget_progress, model.progress, model.target) to
                    if (model.failed) colores.peligroTexto else colores.tintaSuave
            }
        CardKind.DURATION ->
            if (model.doneToday) {
                context.getString(R.string.widget_single_done) to colores.okTexto
            } else {
                context.getString(R.string.widget_progress_min, model.progress, model.target) to
                    if (model.failed) colores.peligroTexto else colores.tintaSuave
            }
        CardKind.ABSTINENCE ->
            if (model.failed) {
                context.getString(R.string.relapse_today) to colores.peligroTexto
            } else {
                context.getString(R.string.clean_today) to colores.okTexto
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
