package com.alvarotc.bito.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.BitoSnackbar
import com.alvarotc.bito.ui.components.DayRing
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The flagship screen: today's ring, every requirable habit, and its registration flows. */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onCreateHabit: () -> Unit,
    onEditHabit: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val logged by viewModel.lastLogged.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var exactFor by remember { mutableStateOf<HabitCardUi?>(null) }
    var relapseFor by remember { mutableStateOf<HabitCardUi?>(null) }
    var sealDismissed by rememberSaveable { mutableStateOf(false) }
    val loggedLabel = stringResource(R.string.logged_snackbar)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(logged) {
        if (logged != null) {
            val result = snackbar.showSnackbar(loggedLabel, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.consumeLogged()
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

    Scaffold(
        containerColor = Papel,
        snackbarHost = { SnackbarHost(snackbar) { BitoSnackbar(it) } },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            state = listState,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TodayHeader(state.today) }
            if (state.cards.isEmpty() && !state.loading) {
                item { EmptyToday(onCreateHabit) }
            } else {
                item { RingCard(state.ringDone, state.ringTotal) }
            }
            items(orderedCards, key = { it.id }) { card ->
                ReorderableItem(reorderState, key = card.id) {
                    HabitCard(
                        card = card,
                        onPrimary = { viewModel.tapPrimary(card) },
                        onAdd = { viewModel.addAmount(card, it) },
                        onExact = { exactFor = card },
                        onRelapse = { relapseFor = card },
                        onEdit = { onEditHabit(card.id) },
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
        }
    }

    exactFor?.let { card ->
        ExactValueSheet(card, onConfirm = {
            viewModel.setExactToday(card, it)
            exactFor = null
        }, onDismiss = { exactFor = null })
    }
    relapseFor?.let { card ->
        RelapseSheet(card.name, onConfirm = {
            viewModel.logRelapse(card)
            relapseFor = null
        }, onDismiss = { relapseFor = null })
    }
    if (state.pendingSealDays.isNotEmpty() && !sealDismissed) {
        BatchSealSheet(state.pendingSealDays.size, onSealAll = { viewModel.sealPendingDays() }, onDismiss = { sealDismissed = true })
    }
}

@Composable
private fun TodayHeader(today: LogicalDay) {
    Column {
        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.headlineLarge, color = Tinta)
        val pattern = stringResource(R.string.today_date_pattern)
        val date =
            remember(today, pattern) {
                LocalDate.ofEpochDay(today.toLong()).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
            }
        Text(date, style = MaterialTheme.typography.labelMedium, color = TintaSuave)
    }
}

/** THE single solid-accent card on the screen: today's ring, "N of M". */
@Composable
private fun RingCard(
    done: Int,
    total: Int,
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
            }
        }
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
