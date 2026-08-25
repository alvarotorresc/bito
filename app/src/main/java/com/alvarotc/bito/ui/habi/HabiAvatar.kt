package com.alvarotc.bito.ui.habi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.theme.BitoTheme
import com.alvarotc.bito.ui.theme.Mofletes
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// Real lids close faster than they open — that asymmetry is what reads as a blink instead of a
// camera shutter. An occasional double blink keeps the idle loop from feeling metronomic.
private const val BLINK_CLOSE_MS = 60
private const val BLINK_OPEN_MS = 110
private const val BLINK_MIN_DELAY_MS = 3000L
private const val BLINK_MAX_DELAY_MS = 5000L
private const val DOUBLE_BLINK_CHANCE = 4 // 1 in N idle blinks doubles up.
private const val DOUBLE_BLINK_GAP_MS = 90L
private const val TAP_SCALE_X = 1.06f
private const val TAP_SCALE_Y = 0.94f

// Expressive mode (animated && onTap != null) presses a touch deeper than the corner avatar — the
// finger-down preload of the poke reaction below. Toned down from 1.10/0.90 now that the release
// carries the real response; the press is just the matter giving way under the finger.
private const val EXPRESSIVE_TAP_SCALE_X = 1.07f
private const val EXPRESSIVE_TAP_SCALE_Y = 0.93f

// QA 2026-08-23 bis («mascota, no robot»): everything anchors at the FEET (bottom-center
// transform origin) so squash, stretch and landings read as weight; breathing is visible and
// mood-paced; idle micro-gestures fire every few seconds.
private const val BREATH_SCALE = 0.022f
private const val BREATH_LIFT_DP = 1.2f
private const val BREATH_PERIOD_RADIANT_MS = 2000
private const val BREATH_PERIOD_NORMAL_MS = 2700
private const val BREATH_PERIOD_LOW_MS = 3500
private const val IDLE_GESTURE_MIN_DELAY_MS = 5000L
private const val IDLE_GESTURE_MAX_DELAY_MS = 11000L
private const val SQUASH_SCALE_X = 0.10f
private const val SQUASH_SCALE_Y = 0.14f
private const val TAP_HOP_DP = 8f

// Art pass 2026-08-25 («Pou, no un robot»): a tap is answered with soft MATTER, not a canned
// choreography. The body compresses under the finger — harder for a poke on the head than on the
// belly — leans AWAY from the touch point, and releases through ONE under-damped spring whose
// wobble is the gelatin. Amplitudes jitter per poke so no two reactions are ever identical, and
// an occasional small hop (never twice in a row) is the only flourish left.
private const val POKE_PRESS_MS = 70
private const val POKE_SQUASH_BASE = 0.4f
private const val POKE_SQUASH_HEAD_GAIN = 0.35f
private const val POKE_TILT_MAX_DEG = 10f
private const val POKE_JITTER = 0.3f
private const val HOP_CHANCE = 3 // 1 in N pokes hops, unless the previous poke already did.
private const val GAZE_HOLD_MS = 420L
private const val IDLE_GLANCE_HOLD_MS = 600L

// One shared physics so every gesture reads as the same creature: JellySpring is the wobbly
// release of poked matter, SettleSpring the calm return of idle gestures and landings, GazeSpring
// the eyes easing back from a glance, FaceMorphSpring the expression morph between moods — a
// barely-bouncy bloom over ~a third of a second instead of a frame-to-frame snap.
private val JellySpring = spring<Float>(dampingRatio = 0.32f, stiffness = 380f)
private val SettleSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
private val GazeSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 300f)
private val FaceMorphSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 260f)

/**
 * The living Habi bean: breathing idle (mood-paced), periodic blink, idle micro-gestures (a
 * curious tilt, a tall stretch, a sideways glance), mood morphs (the face interpolates between
 * expressions instead of snapping) and (when [onTap] is given) a soft-body poke response at the
 * touch point. Purely presentational — [spec] already carries mood, personality and the equipped
 * set; this composable owns no state about what Habi wears or feels.
 *
 * [animated] gates every loop and morph at once, frozen to the resting frame when false — a
 * constant per call site (never toggled mid-lifetime here), so branching composable calls on it is
 * safe. Exists for compose tests: `captureToImage`/`waitUntil` never settle against an infinite
 * transition, so a test that needs a stable frame passes `animated = false` instead of fighting
 * the clock. Every real screen keeps the default `true`.
 *
 * When both [animated] and [onTap] apply, a tap also pulses a full blink (open -> closed -> open,
 * independent Animatable from the idle blink loop so neither interrupts the other), darts the eyes
 * toward the finger, and plays the jelly compression described above. Callers with
 * `animated = false` (the Today corner avatar) keep the plain press squash only.
 */
@Composable
fun HabiAvatar(
    spec: HabiSpec,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    onTap: (() -> Unit)? = null,
    delighted: Boolean = false,
) {
    val density = LocalDensity.current
    val hopPx = with(density) { TAP_HOP_DP.dp.toPx() }
    val breathLiftPx = with(density) { BREATH_LIFT_DP.dp.toPx() }
    val restingMotion = restingFaceMotion(spec, delighted)
    val blinkValue: Float
    var tapBlinkPulse: (() -> Unit)? = null
    var tapReactionPulse: (() -> Unit)? = null
    var breathValue = 0f
    // Response channels, all resting at 0: squash >0 flattens / <0 stretches (from the feet), hop
    // lifts, tilt rocks on the feet, gaze darts the eyes. Idle gestures and poke reactions share
    // them — the newest animateTo wins, which is exactly the interruption behavior a pet should
    // have.
    var squashValue = 0f
    var hopValue = 0f
    var tiltValue = 0f
    var gazeValue = Offset.Zero
    var heartsPhaseValue = 0f
    var motion = restingMotion
    // Normalized (0..1) position of the last finger-down inside the canvas, observed passively so
    // clickable still owns the click; the poke reaction reads it to compress toward the touch.
    var lastTouch by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    if (animated) {
        val breathPeriod =
            when (spec.mood) {
                Mood.RADIANT -> BREATH_PERIOD_RADIANT_MS
                Mood.WILTED, Mood.DRAMATIC -> BREATH_PERIOD_LOW_MS
                else -> BREATH_PERIOD_NORMAL_MS
            }
        val infiniteTransition = rememberInfiniteTransition(label = "habi-breath")
        val breathPhase by
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(breathPeriod, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "habi-breath-phase",
            )
        breathValue = breathPhase

        // The face MORPHS between moods: each continuous channel eases toward its resting value on
        // the shared spring, so lids, brow, curve and smirk bloom into the next expression instead
        // of snapping frame to frame. Discrete traits (cheek style, sparkle count) still switch —
        // they are small enough that the surrounding morph carries them.
        val face = restingMotion.face
        val mouthCurve by animateFloatAsState(face.mouthCurve, FaceMorphSpring, label = "habi-mouth-curve")
        val mouthOpen by animateFloatAsState(face.mouthOpen, FaceMorphSpring, label = "habi-mouth-open")
        val eyeScale by animateFloatAsState(face.eyeScale, FaceMorphSpring, label = "habi-eye-scale")
        val browAngle by animateFloatAsState(face.browAngleDeg ?: 0f, FaceMorphSpring, label = "habi-brow-angle")
        val smirk by animateFloatAsState(restingMotion.smirkProgress, FaceMorphSpring, label = "habi-smirk")
        val droop by animateFloatAsState(restingMotion.eyelidDroop, FaceMorphSpring, label = "habi-droop")
        val wobble by animateFloatAsState(restingMotion.mouthWobble, FaceMorphSpring, label = "habi-wobble")
        motion =
            HabiFaceMotion(
                face =
                    face.copy(
                        mouthCurve = mouthCurve,
                        mouthOpen = mouthOpen,
                        eyeScale = eyeScale,
                        browAngleDeg = face.browAngleDeg?.let { browAngle },
                    ),
                smirkProgress = smirk,
                eyelidDroop = droop,
                mouthWobble = wobble,
            )

        if (delighted) {
            val heartsTransition = rememberInfiniteTransition(label = "habi-hearts")
            val heartsPhase by
                heartsTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
                    label = "habi-hearts-phase",
                )
            heartsPhaseValue = heartsPhase
        }

        val idleBlink = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(BLINK_MIN_DELAY_MS, BLINK_MAX_DELAY_MS))
                val blinks = if (Random.nextInt(DOUBLE_BLINK_CHANCE) == 0) 2 else 1
                repeat(blinks) { index ->
                    if (index > 0) delay(DOUBLE_BLINK_GAP_MS)
                    idleBlink.animateTo(1f, tween(BLINK_CLOSE_MS, easing = LinearEasing))
                    idleBlink.animateTo(0f, tween(BLINK_OPEN_MS, easing = LinearEasing))
                }
            }
        }

        val squash = remember { Animatable(0f) }
        val hop = remember { Animatable(0f) }
        val tilt = remember { Animatable(0f) }
        val gazeX = remember { Animatable(0f) }
        val gazeY = remember { Animatable(0f) }
        squashValue = squash.value
        hopValue = hop.value
        tiltValue = tilt.value
        gazeValue = Offset(gazeX.value, gazeY.value)

        // Idle micro-gestures: a curious tilt, a tall stretch or a sideways glance, every few
        // seconds — all returning on the same calm settle spring.
        LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(IDLE_GESTURE_MIN_DELAY_MS, IDLE_GESTURE_MAX_DELAY_MS))
                when (Random.nextInt(3)) {
                    0 -> {
                        tilt.animateTo(-5f, tween(240, easing = EaseInOut))
                        delay(320)
                        tilt.animateTo(0f, SettleSpring)
                    }
                    1 -> {
                        squash.animateTo(-0.4f, tween(300, easing = EaseInOut))
                        delay(260)
                        squash.animateTo(0f, SettleSpring)
                    }
                    else -> {
                        val side = if (Random.nextBoolean()) 1f else -1f
                        gazeX.animateTo(side * 0.6f, tween(140, easing = LinearOutSlowInEasing))
                        delay(IDLE_GLANCE_HOLD_MS)
                        gazeX.animateTo(0f, GazeSpring)
                    }
                }
            }
        }

        if (onTap != null) {
            val tapBlink = remember { Animatable(0f) }
            val tapScope = rememberCoroutineScope()
            val tapJob = remember { arrayOfNulls<Job>(1) }
            val gazeJob = remember { arrayOfNulls<Job>(1) }
            val lastPokeHopped = remember { booleanArrayOf(false) }
            tapBlinkPulse = {
                tapScope.launch {
                    tapBlink.animateTo(1f, tween(BLINK_CLOSE_MS, easing = LinearEasing))
                    tapBlink.animateTo(0f, tween(BLINK_OPEN_MS, easing = LinearEasing))
                }
            }
            tapReactionPulse = {
                val touch = lastTouch
                // Eyes dart to the finger, hold a beat, ease back — a glance, run independently of
                // the body so an interrupted poke never freezes the gaze mid-dart.
                gazeJob[0]?.cancel()
                gazeJob[0] =
                    tapScope.launch {
                        val targetX = ((touch.x - 0.5f) * 2f).coerceIn(-1f, 1f)
                        val targetY = ((touch.y - 0.5f) * 2f).coerceIn(-1f, 1f)
                        coroutineScope {
                            launch { gazeX.animateTo(targetX, tween(90, easing = LinearOutSlowInEasing)) }
                            launch { gazeY.animateTo(targetY, tween(90, easing = LinearOutSlowInEasing)) }
                        }
                        delay(GAZE_HOLD_MS)
                        coroutineScope {
                            launch { gazeX.animateTo(0f, GazeSpring) }
                            launch { gazeY.animateTo(0f, GazeSpring) }
                        }
                    }
                tapJob[0]?.cancel()
                tapJob[0] =
                    tapScope.launch {
                        // Soft matter under the finger: compression scales with how high the poke
                        // lands (head pokes press down harder), the lean tips AWAY from the touch,
                        // and both carry a per-poke jitter so no two reactions repeat.
                        val jitter = 1f + POKE_JITTER * (Random.nextFloat() * 2f - 1f)
                        val squashTarget = (POKE_SQUASH_BASE + POKE_SQUASH_HEAD_GAIN * (1f - touch.y)) * jitter
                        val tiltTarget = -(touch.x - 0.5f) * 2f * POKE_TILT_MAX_DEG * jitter
                        coroutineScope {
                            launch { squash.animateTo(squashTarget, tween(POKE_PRESS_MS, easing = FastOutSlowInEasing)) }
                            launch { tilt.animateTo(tiltTarget, tween(POKE_PRESS_MS, easing = FastOutSlowInEasing)) }
                        }
                        val hops = !lastPokeHopped[0] && Random.nextInt(HOP_CHANCE) == 0
                        lastPokeHopped[0] = hops
                        if (hops) {
                            // The happy exception: the compression powers a small jump — stretch on
                            // the way up (decelerating), drop back (accelerating), land with a
                            // squish and let the jelly spring shake the rest out.
                            val height = 0.6f + Random.nextFloat() * 0.3f
                            coroutineScope {
                                launch { squash.animateTo(-0.45f * height, tween(150, easing = LinearOutSlowInEasing)) }
                                launch { hop.animateTo(height, tween(150, easing = LinearOutSlowInEasing)) }
                                launch { tilt.animateTo(0f, tween(150, easing = LinearOutSlowInEasing)) }
                            }
                            coroutineScope {
                                launch { hop.animateTo(0f, tween(140, easing = FastOutLinearInEasing)) }
                                launch { squash.animateTo(0.35f, tween(140, easing = FastOutLinearInEasing)) }
                            }
                            squash.animateTo(0f, JellySpring)
                        } else {
                            // The usual answer: release everything through the one under-damped
                            // spring — the decaying wobble IS the gelatin.
                            coroutineScope {
                                launch { squash.animateTo(0f, JellySpring) }
                                launch { tilt.animateTo(0f, JellySpring) }
                            }
                        }
                    }
            }
            blinkValue = maxOf(idleBlink.value, tapBlink.value)
        } else {
            blinkValue = idleBlink.value
        }
    } else {
        blinkValue = 0f
    }

    val expressiveTap = tapBlinkPulse != null
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scaleX by
        animateFloatAsState(
            targetValue =
                if (!pressed) {
                    1f
                } else if (expressiveTap) {
                    EXPRESSIVE_TAP_SCALE_X
                } else {
                    TAP_SCALE_X
                },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "habi-scale-x",
        )
    val scaleY by
        animateFloatAsState(
            targetValue =
                if (!pressed) {
                    1f
                } else if (expressiveTap) {
                    EXPRESSIVE_TAP_SCALE_Y
                } else {
                    TAP_SCALE_Y
                },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "habi-scale-y",
        )

    // [D]/[E]: was a single static "Habi" regardless of mood/personality/equipped — now folds in
    // the mood, the one enrichment that's cheap AND honest (see HabiVoice.moodLabelRes' kdoc for
    // why personality-flavored copy stays out of scope here). Still ONE aggregated description on
    // the Canvas container, never per drawn path — drawHabi()'s ~30 private drawX helpers below
    // correctly carry zero semantics of their own.
    val contentDescription =
        stringResource(
            R.string.habi_avatar_cd_mood,
            stringResource(R.string.habi_avatar_cd),
            stringResource(HabiVoice.moodLabelRes(spec.mood)),
        )

    val canvasModifier =
        modifier
            .semantics { this.contentDescription = contentDescription }
            .let { base ->
                if (onTap != null) {
                    base
                        .pointerInput(Unit) {
                            // Passive observer: records where the finger lands without consuming,
                            // so clickable below still owns the click (and its a11y action).
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                lastTouch =
                                    Offset(
                                        (down.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                        (down.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                                    )
                            }
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                onTap()
                                tapBlinkPulse?.invoke()
                                tapReactionPulse?.invoke()
                            },
                        )
                } else {
                    base
                }
            }
            .graphicsLayer {
                // Anchored at the feet: a creature with weight, not a balloon scaling around
                // its middle.
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = -breathLiftPx * breathValue - hopPx * hopValue
                rotationZ = tiltValue
                this.scaleX = scaleX * (1f + SQUASH_SCALE_X * squashValue) * (1f - BREATH_SCALE * 0.5f * breathValue)
                this.scaleY = scaleY * (1f - SQUASH_SCALE_Y * squashValue) * (1f + BREATH_SCALE * breathValue)
            }

    Canvas(canvasModifier) {
        drawHabi(spec, blink = blinkValue, delighted = delighted, motion = motion, gaze = gazeValue)
        if (delighted) drawHearts(heartsPhaseValue)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2ECE1)
@Composable
private fun HabiAvatarMoodGridPreview() {
    BitoTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            for (personality in Personality.entries) {
                Row {
                    for (mood in Mood.entries) {
                        HabiAvatar(
                            spec = HabiSpec(mood, personality, EquippedSet()),
                            modifier = Modifier.size(72.dp).padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2ECE1)
@Composable
private fun HabiAvatarDoradoPreview() {
    BitoTheme {
        HabiAvatar(
            spec = HabiSpec(Mood.RADIANT, Personality.CHEERLEADER, EquippedSet(bodyColor = "body-dorado")),
            modifier = Modifier.size(150.dp),
        )
    }
}

/** T10 catalog: every pattern, human-checked here — fidelity against the mockup is a visual call, not a test. */
@Preview(showBackground = true, backgroundColor = 0xFFF2ECE1)
@Composable
private fun HabiAvatarPatternsPreview() {
    val patterns =
        listOf(
            "pattern-motas",
            "pattern-rayitas",
            "pattern-corazones",
            "pattern-estrellas",
            "pattern-flores",
            "pattern-chispas",
            "pattern-llamas",
        )
    BitoTheme {
        Row(modifier = Modifier.padding(16.dp)) {
            for (pattern in patterns) {
                HabiAvatar(
                    spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(pattern = pattern)),
                    modifier = Modifier.size(72.dp).padding(4.dp),
                )
            }
        }
    }
}

/** T10 catalog: every upper + lower item. */
@Preview(showBackground = true, backgroundColor = 0xFFF2ECE1)
@Composable
private fun HabiAvatarAccessoriesPreview() {
    val uppers = listOf("upper-gorro-lana", "upper-lazo", "upper-copa", "upper-corona")
    val lowers = listOf("lower-calcetines", "lower-zapatillas")
    BitoTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                for (upper in uppers) {
                    HabiAvatar(
                        spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(upper = upper)),
                        modifier = Modifier.size(72.dp).padding(4.dp),
                    )
                }
            }
            Row {
                for (lower in lowers) {
                    HabiAvatar(
                        spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(lower = lower)),
                        modifier = Modifier.size(72.dp).padding(4.dp),
                    )
                }
            }
        }
    }
}

// Hearts (pet-streak delight): three lanes as (x offset, phase offset, tilt degrees) — hand-placed
// tilts so they read as scattered by hand, not stamped.
private val HEART_LANES = listOf(Triple(-0.26f, 0f, -12f), Triple(0.05f, 0.38f, 6f), Triple(0.28f, 0.72f, 14f))
private const val HEART_RISE_START_Y = 0.42f
private const val HEART_RISE_TRAVEL = 0.3f
private const val HEART_HALF_BASE = 0.026f
private const val HEART_HALF_GROWTH = 0.012f
private const val HEART_SWAY = 0.015f
private const val HEART_FADE_IN_END = 0.12f
private const val HEART_PEAK_ALPHA = 0.85f

/**
 * Three blush-pink hearts drifting up from the face while the pet-streak delight lasts
 * (QA 2026-08-24, «que saque corazones»). Refined 2026-08-25: smaller and lighter, rising on an
 * ease-out (they decelerate like bubbles), swaying gently sideways, fading IN at birth — the old
 * linear rise popped fully-opaque at the loop seam — and tilted per lane.
 */
private fun DrawScope.drawHearts(phase: Float) {
    for ((xFactor, offset, tiltDeg) in HEART_LANES) {
        val p = (phase + offset) % 1f
        val rise = 1f - (1f - p) * (1f - p)
        val alpha = (p / HEART_FADE_IN_END).coerceAtMost(1f) * (1f - rise) * HEART_PEAK_ALPHA
        if (alpha <= 0.01f) continue
        val sway = size.width * HEART_SWAY * sin((p * 1.5f + xFactor) * 2f * PI.toFloat())
        val cx = size.width * (0.5f + xFactor) + sway
        val cy = size.height * (HEART_RISE_START_Y - HEART_RISE_TRAVEL * rise)
        val half = size.width * (HEART_HALF_BASE + HEART_HALF_GROWTH * (p / 0.25f).coerceAtMost(1f))
        val heart =
            Path().apply {
                moveTo(cx, cy + half)
                cubicTo(cx - 1.6f * half, cy, cx - 0.9f * half, cy - 1.2f * half, cx, cy - 0.4f * half)
                cubicTo(cx + 0.9f * half, cy - 1.2f * half, cx + 1.6f * half, cy, cx, cy + half)
            }
        rotate(degrees = tiltDeg.toFloat(), pivot = Offset(cx, cy)) {
            drawPath(heart, color = Mofletes, alpha = alpha)
        }
    }
}
