package com.alvarotc.bito.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.GhostIconButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SpeechBubble
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.today.ExactValueSheet
import com.alvarotc.bito.ui.today.HabitCardUi
import com.alvarotc.bito.ui.today.RelapseSheet

/**
 * The nightly review, entry point of the `review` route (no bottom nav — this is its own flow).
 * E1 ([OpenState]) is built here: pending rows, the away-days batch seal and the "seal the day"
 * CTA. E2 ([SealedState]) renders once today is actually sealed — Habi on stage, the final ring,
 * points, streaks and any new badge (see [SealedDayContent]).
 */
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.loading) {
        // First frame while the cold combine warms up: calm paper — never a flash of an empty
        // E1 that then swaps to E2 (QA 2026-08-24: entry read as transparent/stuck).
        Box(Modifier.fillMaxSize().background(Papel).testTag("review-loading"))
        return
    }
    if (state.todaySealed) {
        SealedState(viewModel, state, onClose)
    } else {
        OpenState(viewModel, state, onClose)
    }
}

/** E1: what is left to answer for today, plus the past days left unsealed. */
@Composable
private fun OpenState(
    viewModel: ReviewViewModel,
    state: ReviewUiState,
    onClose: () -> Unit,
) {
    var exactFor by remember { mutableStateOf<HabitCardUi?>(null) }
    var relapseFor by remember { mutableStateOf<HabitCardUi?>(null) }

    Scaffold(containerColor = Papel) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ReviewHeader(state.remaining, onClose) }
                if (state.pendingSealDays.isNotEmpty()) {
                    item {
                        PendingSealCard(state.pendingSealDays.size, onSeal = { viewModel.sealPendingDays() })
                    }
                }
                // «El vacío es recompensa»: nothing left to answer and nothing left unsealed
                // is a celebratory empty state, not a poverty one.
                if (state.rows.isEmpty() && state.pendingSealDays.isEmpty()) {
                    item { EmptyReviewState(state.spec, state.userName) }
                } else {
                    items(state.rows, key = { it.id }) { card ->
                        ReviewRowCard(
                            card = card,
                            onDone = { viewModel.markDone(card) },
                            onAdd = { amount -> viewModel.addAmount(card, amount) },
                            onExact = { exactFor = card },
                            onAck = { viewModel.acknowledge(card.id) },
                            onRelapse = { relapseFor = card },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.rows.isNotEmpty()) {
                        item { ReviewHabiBubble(state.spec, state.userName) }
                    }
                }
            }
            PillButton(
                text = stringResource(R.string.review_seal_day),
                onClick = { viewModel.sealToday() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .testTag("review-seal"),
            )
        }
    }

    exactFor?.let { card ->
        ExactValueSheet(
            card,
            onConfirm = {
                viewModel.setExact(card, it)
                exactFor = null
            },
            onDismiss = { exactFor = null },
        )
    }
    relapseFor?.let { card ->
        RelapseSheet(
            name = card.name,
            onConfirm = {
                viewModel.logRelapse(card)
                relapseFor = null
            },
            onDismiss = { relapseFor = null },
        )
    }
}

/**
 * E2: today is already sealed. Renders [SealedDayContent] and owns its two effects — the
 * celebration cue fires once per perfect-day entry (the `markCelebrated` marker keeps the global
 * celebration sheet elsewhere in the app from repeating it), and leaving — by the close button OR
 * the system back gesture — marks the badge shelf seen before handing off to the NavHost's own
 * `onClose`. [close] is not a `DisposableEffect`: the marker write launches in `viewModelScope`,
 * which is cancelled the moment this entry pops, so it has to run BEFORE `onClose()` navigates
 * away rather than on composition teardown.
 */
@Composable
private fun SealedState(
    viewModel: ReviewViewModel,
    state: ReviewUiState,
    onClose: () -> Unit,
) {
    LaunchedEffect(state.todaySealed, state.perfectToday) {
        if (state.todaySealed && state.perfectToday) {
            viewModel.cue()
            viewModel.markCelebrated()
        }
    }
    val close = {
        viewModel.markBadgesSeen()
        onClose()
    }
    BackHandler(onBack = close)
    Scaffold(containerColor = Papel) { padding ->
        SealedDayContent(
            state = state,
            onClose = close,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun ReviewHeader(
    remaining: Int,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GhostIconButton(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), onClick = onClose)
        Text(
            stringResource(R.string.review_title),
            style = MaterialTheme.typography.headlineLarge,
            color = Tinta,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        if (remaining > 0) {
            Text(
                pluralStringResource(R.plurals.review_remaining, remaining, remaining),
                style = MaterialTheme.typography.labelMedium,
                color = Tinta,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(HojaTinte)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("review-remaining"),
            )
        }
    }
}

/** "You were away": the past days left unsealed, batch-sealed right here — part of the ritual. */
@Composable
private fun PendingSealCard(
    dayCount: Int,
    onSeal: () -> Unit,
) {
    BitoCard(modifier = Modifier.fillMaxWidth().testTag("review-away")) {
        Text(stringResource(R.string.seal_sheet_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
        Spacer(Modifier.height(8.dp))
        val body =
            if (dayCount == 1) {
                stringResource(R.string.seal_sheet_body_one)
            } else {
                stringResource(R.string.seal_sheet_body_many, dayCount)
            }
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Tinta)
        Spacer(Modifier.height(16.dp))
        val confirmLabel =
            if (dayCount == 1) {
                stringResource(R.string.seal_sheet_confirm_one)
            } else {
                stringResource(R.string.seal_sheet_confirm_many, dayCount)
            }
        PillButton(confirmLabel, onClick = onSeal, modifier = Modifier.fillMaxWidth())
    }
}

/** Nothing left to answer and nothing left unsealed: the reward is silence. */
@Composable
private fun EmptyReviewState(
    spec: HabiSpec,
    userName: String,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp).testTag("review-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HabiAvatar(spec, Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        val fallbackName = stringResource(R.string.habi_name_fallback)
        SpeechBubble(
            speaker = stringResource(R.string.habi_speaker, stringResource(HabiVoice.labelRes(spec.personality))),
            text = stringResource(HabiVoice.reviewClearRes(spec.personality), userName.ifBlank { fallbackName }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Habi mini, quietly observing under the pending rows. */
@Composable
private fun ReviewHabiBubble(
    spec: HabiSpec,
    userName: String,
) {
    val fallbackName = stringResource(R.string.habi_name_fallback)
    SpeechBubble(
        speaker = stringResource(R.string.habi_speaker, stringResource(HabiVoice.labelRes(spec.personality))),
        text = stringResource(HabiVoice.reviewRes(spec.personality), userName.ifBlank { fallbackName }),
        modifier = Modifier.fillMaxWidth(),
        avatar = { HabiAvatar(spec, Modifier.size(40.dp), animated = false) },
    )
}
