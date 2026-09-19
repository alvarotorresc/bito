package com.alvarotc.bito.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SegmentedPills
import com.alvarotc.bito.ui.components.SpeechBubble
import com.alvarotc.bito.ui.habi.HabiStage
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/**
 * The full-screen focus session (spec §8.5), reachable from Hoy, from the tasks list and from the
 * permanent notification — full-screen and deliberately outside the bottom-bar route set, like
 * `review`: a live session isn't a place you tab away from, it's a place you finish or leave.
 *
 * At rest: [HabiStage] on the user's real [FocusUiState.spec] (no new pose, no new sound — the
 * stage is shared with [com.alvarotc.bito.ui.review.SealedDayContent]), the task's title and
 * first step, the three lengths ([FocusViewModel.OPTIONS]) and "Empezar". Running: the same Habi
 * and task copy, the mm:ss countdown next to the five actions — "he terminado", the three "+" extensions
 * ([FocusViewModel.EXTENSIONS]) and "lo dejo". At `00:00` nothing changes: [FocusUiState] never
 * decides FOR the user, so the same five actions stay live past the deadline (a "+" still works
 * on an already-finished countdown). [FocusUiState.busyWith] non-null means another task's session
 * is already running — a [ModalBottomSheet] the user must resolve, one way or the other, before
 * seeing anything else. [FocusUiState.gone] closes the screen without any extra copy: the state
 * itself already covers "finished", "gave up" and "the task was deleted from under it".
 *
 * `justStarted` gates the one-time entrance line ([HabiVoice.taskFocusRes]) to the act of starting
 * a session FROM this screen — pressing "Empezar" or "Dejarla y empezar esta". Reading it off
 * [FocusUiState.selectedMinutes] instead would be wrong on any other path into a running session
 * (the permanent notification's bare `"focus"`, re-opening on a session another screen started,
 * `onKeepOther`'s own re-entry): `selectedMinutes` is this VM's own pending choice, not what the
 * live session actually started with, and Habi never said anything on those paths anyway.
 *
 * [onKeepOther] is not in the original route sketch — [FocusViewModel.keepOther] writes nothing
 * (the conflict simply resolves in the user's favor), so this screen still needs a way back to the
 * LIVE session's own screen, which [FocusUiState] has no id for (only [FocusUiState.busyWith]'s
 * title). Reusing the bare `"focus"` route — the exact mechanism the permanent notification
 * already uses to resolve against whatever session happens to be live — avoids adding an id field
 * to a state Task 18 already shipped. Defaults to a no-op, same pattern as this screen's own
 * `onStartFocus` callers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onClose: () -> Unit,
    onKeepOther: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var justStarted by remember { mutableStateOf(false) }

    LaunchedEffect(state.gone) {
        if (state.gone) onClose()
    }

    Scaffold(containerColor = Papel) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize().testTag("focus-loading"))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp)
                .testTag("focus-screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The one place `focus_title` renders — constraints.md adds the string because the UI
            // needs it, not just the copy table. No back chevron here on purpose: `gone` above is
            // the only way out, so a user can't quietly abandon a live session without either
            // finishing it or writing an ATTEMPT through "lo dejo".
            Text(stringResource(R.string.focus_title), style = MaterialTheme.typography.labelMedium, color = TintaSuave)
            HabiStage(spec = state.spec)
            Text(state.title, style = MaterialTheme.typography.headlineMedium, color = Tinta)
            state.firstStep?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TintaSuave) }

            if (state.running) {
                if (justStarted) {
                    val fallbackName = stringResource(R.string.habi_name_fallback)
                    SpeechBubble(
                        speaker = stringResource(R.string.habi_speaker, stringResource(HabiVoice.labelRes(state.spec.personality))),
                        text =
                            stringResource(
                                HabiVoice.taskFocusRes(state.spec.personality),
                                state.userName.ifBlank { fallbackName },
                                state.selectedMinutes,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val totalSeconds = state.remainingMillis / 1_000L
                Text(
                    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Tinta,
                    modifier = Modifier.testTag("focus-clock"),
                )
                PillButton(
                    text = stringResource(R.string.focus_done),
                    onClick = viewModel::finish,
                    modifier = Modifier.fillMaxWidth().testTag("focus-done"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusViewModel.EXTENSIONS.forEach { minutes ->
                        GhostPillButton(
                            text = stringResource(R.string.focus_plus, minutes),
                            onClick = { viewModel.extend(minutes) },
                            modifier = Modifier.testTag("focus-plus-$minutes"),
                        )
                    }
                }
                GhostPillButton(
                    text = stringResource(R.string.focus_give_up),
                    onClick = viewModel::giveUp,
                    modifier = Modifier.testTag("focus-give-up"),
                )
            } else {
                val optionLabels = FocusViewModel.OPTIONS.map { stringResource(R.string.focus_minutes, it) }
                SegmentedPills(
                    options = optionLabels,
                    selectedIndex = FocusViewModel.OPTIONS.indexOf(state.selectedMinutes),
                    onSelect = { index -> viewModel.select(FocusViewModel.OPTIONS[index]) },
                )
                PillButton(
                    text = stringResource(R.string.task_start),
                    onClick = {
                        justStarted = true
                        viewModel.start()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("focus-start"),
                )
            }
        }
    }

    val busyWith = state.busyWith
    if (busyWith != null) {
        // A scrim tap or the system back button is a THIRD way out, not a rename of "seguir con
        // la otra": this screen's own requestedTaskId never changes, so keepOther() here would
        // leave busyWith non-null and the sheet would just reopen on the very next recomposition
        // — no way out while the other session stays alive (M10 review round 1). Dismissing backs
        // all the way out of the screen instead, writing nothing — the same as any other "changed
        // my mind before deciding" gesture.
        ModalBottomSheet(
            onDismissRequest = onClose,
            containerColor = Tarjeta,
        ) {
            Column(Modifier.padding(20.dp).testTag("focus-busy-sheet")) {
                Text(stringResource(R.string.focus_busy_title), style = MaterialTheme.typography.titleMedium, color = Tinta)
                Spacer(Modifier.height(16.dp))
                PillButton(
                    text = stringResource(R.string.focus_busy_keep, busyWith),
                    onClick = {
                        viewModel.keepOther()
                        onKeepOther()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("focus-busy-keep"),
                )
                Spacer(Modifier.height(8.dp))
                GhostPillButton(
                    text = stringResource(R.string.focus_busy_switch),
                    onClick = {
                        justStarted = true
                        viewModel.switchToRequested()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("focus-busy-switch"),
                )
            }
        }
    }
}
