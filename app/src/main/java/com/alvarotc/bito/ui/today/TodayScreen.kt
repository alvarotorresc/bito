package com.alvarotc.bito.ui.today

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.DayRing
import com.alvarotc.bito.ui.components.DismissableBitoSnackbar
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.formatDayWithPattern
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** The flagship screen: today's ring, every requirable habit, and its registration flows. */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onCreateHabit: () -> Unit,
    onOpenHabit: (String) -> Unit,
    onOpenHabi: () -> Unit,
    onOpenReview: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val logged by viewModel.lastLogged.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var exactFor by remember { mutableStateOf<HabitCardUi?>(null) }
    var sealDismissed by rememberSaveable { mutableStateOf(false) }
    val loggedLabel = stringResource(R.string.logged_snackbar)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(logged) {
        if (logged != null) {
            try {
                val result = snackbar.showSnackbar(loggedLabel, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) viewModel.undo()
            } finally {
                // Also runs on cancellation (navigating away): the undo offer dies with the visit
                // instead of re-showing on every return to Hoy (QA 2026-08-24).
                viewModel.consumeLogged()
            }
        }
    }

    // Reordering works on a local copy so the drag previews instantly; the DB write happens once,
    // on drag end, and the re-emitted flow rebuilds this list in the exact same order (no jump).
    val orderedCards = remember(state.cards) { state.cards.toMutableStateList() }
    val listState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(listState) { from, to ->
            // Matching by key, not index: the list has two header items before the cards.
            val fromIndex = orderedCards.indexOfFirst { it.id == from.key }
            val toIndex = orderedCards.indexOfFirst { it.id == to.key }
            if (fromIndex != -1 && toIndex != -1) {
                orderedCards.add(toIndex, orderedCards.removeAt(fromIndex))
            }
        }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    // Registro feedback (QA 2026-08-23/24): a real Vibrator buzz on every log tap, gated ONLY by
    // the app's own Ajustes switch — performHapticFeedback obeyed the system touch-feedback
    // toggle, which most people keep off, so it read as "vibration doesn't work". The tick sound
    // half lives in the ViewModel (HabiSound.LOG).
    val vibrator =
        remember {
            if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }
    val logHaptic = {
        if (state.logHapticEnabled) {
            val effect =
                if (Build.VERSION.SDK_INT >= 29) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                } else {
                    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                }
            vibrator.vibrate(effect)
        }
    }

    Scaffold(
        containerColor = Papel,
        snackbarHost = { SnackbarHost(snackbar) { DismissableBitoSnackbar(it) } },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            state = listState,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TodayHeader(state.today, state.spec, state.userName, onOpenHabi) }
            // Empty is only true poverty when there is nothing at all — a habit merely paused
            // still has a home in the section below, so it must not trip "create your first habit".
            if (state.cards.isEmpty() && !state.loading && state.pausedHabits.isEmpty()) {
                item { EmptyToday(onCreateHabit) }
            } else {
                item { RingCard(state.ringDone, state.ringTotal, state.todaySealed, onOpenReview) }
            }
            items(orderedCards, key = { it.id }) { card ->
                ReorderableItem(reorderState, key = card.id) {
                    HabitCard(
                        card = card,
                        onPrimary = {
                            viewModel.tapPrimary(card)
                            logHaptic()
                        },
                        onAdd = {
                            viewModel.addAmount(card, it)
                            logHaptic()
                        },
                        onExact = { exactFor = card },
                        onOpen = { onOpenHabit(card.id) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                // Long press anywhere on the card starts the drag — except the
                                // duration bar and the counter's +, whose inner combinedClickable
                                // consumes its own long press (exact-value shortcut) first.
                                .longPressDraggableHandle(
                                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragStopped = { viewModel.reorder(orderedCards.map { it.id }) },
                                ),
                    )
                }
            }
            if (state.pausedHabits.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.paused_section_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = TintaSuave,
                    )
                }
                items(state.pausedHabits, key = { "paused-${it.id}" }) { paused ->
                    PausedHabitRow(paused, onOpen = { onOpenHabit(paused.id) })
                }
            }
        }
    }

    exactFor?.let { card ->
        ExactValueSheet(card, onConfirm = {
            viewModel.setExactToday(card, it)
            exactFor = null
        }, onDismiss = { exactFor = null })
    }
    if (state.pendingSealDays.isNotEmpty() && !sealDismissed) {
        BatchSealSheet(state.pendingSealDays.size, onSealAll = { viewModel.sealPendingDays() }, onDismiss = { sealDismissed = true })
    }
}

@Composable
private fun TodayHeader(
    today: LogicalDay,
    spec: HabiSpec,
    userName: String,
    onOpenHabi: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.today_title), style = MaterialTheme.typography.headlineLarge, color = Tinta)
            val pattern = stringResource(R.string.today_date_pattern)
            val date = remember(today, pattern) { formatDayWithPattern(today, pattern) }
            Text(date, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            val fallbackName = stringResource(R.string.habi_name_fallback)
            Text(
                stringResource(HabiVoice.greetingRes(spec.mood, spec.personality), userName.ifBlank { fallbackName }),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Static in the corner (T9's `animated` gate off): an infinite bob/blink here would
        // never let a plain waitForIdle() settle in TodayScreenTest — same reasoning that keeps
        // StatsScreen's embedded commentator avatar frozen. The tap squash-and-stretch is
        // untouched by this gate, so it still answers onOpenHabi.
        HabiAvatar(spec, Modifier.size(56.dp), animated = false, onTap = onOpenHabi)
    }
}

/** THE single solid-accent card on the screen: today's ring, "N of M", and the way into the review. */
@Composable
private fun RingCard(
    done: Int,
    total: Int,
    sealed: Boolean,
    onOpenReview: () -> Unit,
) {
    BitoCard(
        container = Hoja,
        border = Hoja,
        modifier = Modifier.fillMaxWidth().testTag("ring"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DayRing(done, total, Modifier.size(88.dp)) {
                Text(
                    stringResource(R.string.ring_of, done, total),
                    style = MaterialTheme.typography.titleMedium,
                    color = Tarjeta,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(R.string.ring_caption),
                    style = MaterialTheme.typography.labelMedium,
                    color = Tarjeta.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(8.dp))
                // Once sealed, the CTA flips to a calm "Día sellado ✓" (QA 2026-08-24) — still
                // tappable: it opens the sealed-day screen, which is a pleasant place to revisit.
                GhostPillButton(
                    text = stringResource(if (sealed) R.string.today_day_sealed else R.string.today_close_day),
                    onClick = onOpenReview,
                    color = Tarjeta,
                    borderColor = Tarjeta.copy(alpha = 0.6f),
                    icon = if (sealed) BitoIcons.Check else BitoIcons.ChevronRight,
                    modifier = Modifier.testTag("close-day"),
                )
            }
        }
    }
}

/** One compact row in the paused section: no progress, just a name and a way back into detail. */
@Composable
private fun PausedHabitRow(
    paused: PausedHabitUi,
    onOpen: () -> Unit,
) {
    // The section header above spells out "Paused" once for the whole list — a user who jumps
    // straight to a row via list navigation, past the header, would otherwise hear only the
    // habit name. stateDescription (not a second contentDescription) keeps the spoken habit name
    // itself intact while adding the state as a qualifier, same shape TalkBack already uses for
    // Switch/Checkbox state. Reuses the section header's own string (no new key).
    val pausedState = stringResource(R.string.paused_section_title)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onOpen)
            .semantics { stateDescription = pausedState }
            .testTag("paused-${paused.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(BitoIcons.Pause, contentDescription = null, tint = TintaSuave, modifier = Modifier.size(16.dp))
        Text(paused.name, style = MaterialTheme.typography.bodyLarge, color = Tinta)
    }
}

@Composable
private fun EmptyToday(onCreate: () -> Unit) {
    BitoCard(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.empty_today_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_today_body), style = MaterialTheme.typography.bodyLarge, color = TintaSuave)
        Spacer(Modifier.height(16.dp))
        PillButton(stringResource(R.string.create_habit), onClick = onCreate)
    }
}
