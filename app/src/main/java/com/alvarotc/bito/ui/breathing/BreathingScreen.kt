package com.alvarotc.bito.ui.breathing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.model.BreathPhase
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.ui.components.GhostPillButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SegmentedPills
import com.alvarotc.bito.ui.components.SpeechBubble
import com.alvarotc.bito.ui.components.rememberSoftBuzz
import com.alvarotc.bito.ui.habi.HabiBreath
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.HabiStage
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave
import java.util.Locale

/**
 * La pantalla del ejercicio de respiracion (spec §5.3), fuera de la barra inferior como review,
 * tasks y focus. Tres estados: reposo (Habi despierto y «Empezar»), en marcha (Habi guia con los
 * ojos en rendija, palabra de fase y restante) y final (frase de cierre y contador).
 *
 * El ViewModel es la fuente de verdad del ritmo; esta pantalla solo lo pinta. Con [animated] el
 * fill de Habi se recalcula por fotograma desde el ancla de elapsedRealtime para que no respire a
 * saltos de 100 ms; los tests pasan false y Habi toma el fill grueso del estado (un bucle de
 * fotogramas infinito no deja asentarse a waitForIdle). [buzz] es inyectable para contar pulsos
 * sin tocar el Vibrator.
 */
@Composable
fun BreathingScreen(
    viewModel: BreathingViewModel,
    onClose: () -> Unit,
    animated: Boolean = true,
    buzz: () -> Unit = rememberSoftBuzz(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phaseChanges by viewModel.phaseChanges.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val running = state.stage == BreathingStage.RUNNING

    // Se cierra solo cuando la ultima sesion ya esta en Room (Review Focus 1).
    LaunchedEffect(state.gone) {
        if (state.gone) onClose()
    }

    // Un pulso por cambio de paso. Mismo guardia que `nudge` en HabiAvatar: recuerda el ultimo
    // valor atendido, sembrado con el actual, asi una recreacion no repite el pulso.
    val hapticEnabled by rememberUpdatedState(state.hapticEnabled)
    var lastAnsweredPhase by remember { mutableIntStateOf(phaseChanges) }
    LaunchedEffect(phaseChanges) {
        if (phaseChanges > lastAnsweredPhase && hapticEnabled) buzz()
        lastAnsweredPhase = phaseChanges
    }

    // Pantalla encendida solo mientras se respira: en reposo o en el final se apaga como siempre.
    DisposableEffect(running, activity) {
        val window = activity?.window
        if (running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (running) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Dejar de verse (segundo plano, boton de encendido) para la sesion y la guarda si pasa de 10 s.
    // MainActivity no declara configChanges: girar el movil tambien dispara ON_STOP, y sin este
    // guardia rotar pararia la sesion. El ViewModel sobrevive al giro y el ancla sigue valiendo.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (isRealStop(activity)) viewModel.onBackgrounded()
    }

    // Atras del sistema en plena sesion = «Parar» y salir, guardando antes de cerrar.
    BackHandler(enabled = running) { viewModel.leave() }

    Scaffold(containerColor = Papel) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize().testTag("breathing-loading"))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp)
                .testTag("breathing-screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::leave, modifier = Modifier.testTag("breathing-back")) {
                    Icon(BitoIcons.ChevronLeft, contentDescription = stringResource(R.string.back), tint = Tinta)
                }
                Text(
                    stringResource(R.string.breathing_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Tinta,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::toggleMusic, modifier = Modifier.testTag("breathing-music")) {
                    Icon(
                        BitoIcons.Music,
                        contentDescription =
                            stringResource(if (state.musicEnabled) R.string.breathing_music_on_cd else R.string.breathing_music_off_cd),
                        tint = if (state.musicEnabled) Hoja else TintaSuave,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            val modes = BreathingMode.entries
            SegmentedPills(
                options = modes.map { stringResource(modeLabelRes(it)) },
                selectedIndex = modes.indexOf(state.mode),
                onSelect = { viewModel.selectMode(modes[it]) },
                enabled = !running,
                modifier = Modifier.alpha(if (running) 0.4f else 1f).testTag("breathing-modes"),
            )
            Text(
                stringResource(rhythmRes(state.mode)),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave,
                modifier = Modifier.testTag("breathing-rhythm"),
            )
            Spacer(Modifier.weight(1f))
            GuidedHabi(
                spec = state.spec,
                mode = state.mode,
                anchorElapsed = state.anchorElapsed,
                fallbackFill = state.fill,
                running = running,
                animated = animated,
            )
            Spacer(Modifier.weight(1f))
            when (state.stage) {
                BreathingStage.IDLE ->
                    PillButton(
                        text = stringResource(R.string.breathing_start),
                        onClick = viewModel::start,
                        modifier = Modifier.fillMaxWidth().testTag("breathing-start"),
                    )
                BreathingStage.RUNNING -> {
                    // Cambia en seco en cada paso, sin fundido: el cambio es la senal.
                    Text(
                        stringResource(phaseRes(state.phase)),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Tinta,
                        modifier = Modifier.testTag("breathing-phase"),
                    )
                    Text(
                        if (state.mode == BreathingMode.SLEEP) {
                            stringResource(R.string.breathing_cycle_of, state.cycle, state.totalCycles)
                        } else {
                            formatRemaining(state.remainingSeconds)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = TintaSuave,
                        modifier = Modifier.testTag("breathing-remaining"),
                    )
                    GhostPillButton(
                        text = stringResource(R.string.breathing_stop),
                        onClick = viewModel::stop,
                        modifier = Modifier.fillMaxWidth().testTag("breathing-stop"),
                    )
                }
                BreathingStage.FINISHED -> {
                    val fallbackName = stringResource(R.string.habi_name_fallback)
                    SpeechBubble(
                        speaker = stringResource(R.string.habi_speaker, stringResource(HabiVoice.labelRes(state.spec.personality))),
                        text = stringResource(HabiVoice.breathingDoneRes(state.spec.personality), state.userName.ifBlank { fallbackName }),
                        modifier = Modifier.fillMaxWidth().testTag("breathing-done-bubble"),
                    )
                    Text(
                        stringResource(
                            R.string.breathing_week,
                            pluralStringResource(R.plurals.breathing_sessions, state.week.sessions, state.week.sessions),
                            state.week.minutes,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = TintaSuave,
                        modifier = Modifier.testTag("breathing-week"),
                    )
                    Text(
                        stringResource(
                            R.string.breathing_all_time,
                            pluralStringResource(R.plurals.breathing_sessions, state.allTime.sessions, state.allTime.sessions),
                            state.allTime.minutes,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = TintaSuave,
                        modifier = Modifier.testTag("breathing-all-time"),
                    )
                    PillButton(
                        text = stringResource(R.string.breathing_again),
                        onClick = viewModel::again,
                        modifier = Modifier.fillMaxWidth().testTag("breathing-again"),
                    )
                    GhostPillButton(
                        text = stringResource(R.string.breathing_done),
                        onClick = viewModel::leave,
                        modifier = Modifier.fillMaxWidth().testTag("breathing-done"),
                    )
                }
            }
        }
    }
}

/**
 * Habi en el escenario. Solo esto se recompone por fotograma: el bucle de withFrameMillis vive
 * aqui dentro y no arriba en BreathingScreen, asi chips, textos y botones no se repintan en cada
 * fotograma. Con [animated] el fill sale de elapsedRealtime por fotograma; sin el (tests), del
 * fill grueso del ultimo tick.
 */
@Composable
private fun GuidedHabi(
    spec: HabiSpec,
    mode: BreathingMode,
    anchorElapsed: Long,
    fallbackFill: Float,
    running: Boolean,
    animated: Boolean,
) {
    val fill =
        if (animated && running) {
            val frameElapsed by produceState(SystemClock.elapsedRealtime(), anchorElapsed) {
                while (true) {
                    withFrameMillis { value = SystemClock.elapsedRealtime() }
                }
            }
            BreathingRhythm.at(mode, frameElapsed - anchorElapsed).fill
        } else {
            fallbackFill
        }
    HabiStage(
        spec = spec,
        stageSize = 260.dp,
        avatarSize = 200.dp,
        animated = animated,
        breath = if (running) HabiBreath(fill = fill) else null,
    )
}

@StringRes
private fun modeLabelRes(mode: BreathingMode): Int =
    when (mode) {
        BreathingMode.CALM -> R.string.breathing_mode_calm
        BreathingMode.SLEEP -> R.string.breathing_mode_sleep
        BreathingMode.FOCUS -> R.string.breathing_mode_focus
    }

@StringRes
private fun rhythmRes(mode: BreathingMode): Int =
    when (mode) {
        BreathingMode.CALM -> R.string.breathing_rhythm_calm
        BreathingMode.SLEEP -> R.string.breathing_rhythm_sleep
        BreathingMode.FOCUS -> R.string.breathing_rhythm_focus
    }

@StringRes
private fun phaseRes(phase: BreathPhase): Int =
    when (phase) {
        BreathPhase.INHALE -> R.string.breathing_phase_inhale
        BreathPhase.HOLD -> R.string.breathing_phase_hold
        BreathPhase.EXHALE -> R.string.breathing_phase_exhale
    }

/** La Activity que hay debajo de un Context de Compose, que puede venir envuelto en varios ContextWrapper. */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** ON_STOP de verdad (segundo plano, pantalla apagada) y no el de un giro de pantalla. */
internal fun isRealStop(activity: Activity?): Boolean = activity?.isChangingConfigurations != true

/** «m:ss» para Calmarme y Centrarme; los segundos ya vienen redondeados hacia arriba. */
internal fun formatRemaining(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
