package com.alvarotc.bito.ui.habi

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.PI
import kotlin.math.sin

private const val TAP_SCALE_X = 1.06f
private const val TAP_SCALE_Y = 0.94f

// Expressive mode (animated && onTap != null) presses a touch deeper than the corner avatar — the
// finger-down preload of the poke reaction. Toned down from 1.10/0.90 now that the release
// carries the real response; the press is just the matter giving way under the finger.
private const val EXPRESSIVE_TAP_SCALE_X = 1.07f
private const val EXPRESSIVE_TAP_SCALE_Y = 0.93f

// El muelle de la CARA, el unico que se queda aqui: el morfeo entre expresiones es cara, no
// cuerpo. Los del cuerpo viven en HabiMotion, con las coreografias que los usan. Una floracion
// apenas rebotada sobre un tercio de segundo en vez de un salto de fotograma a fotograma.
private val FaceMorphSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 260f)

/**
 * The living Habi bean: breathing idle (mood-paced), irregular blink, idle micro-gestures (a
 * curious tilt, a tall stretch, a sideways glance, a minimal sway), mood morphs (the face
 * interpolates between expressions instead of snapping), the cue choreographies and (when [onTap]
 * is given) a soft-body poke response at the touch point. Purely presentational — [spec] already
 * carries mood, personality, pose and the equipped set; this composable owns no state about what
 * Habi wears or feels.
 *
 * **Todo el movimiento vive en [HabiMotion] y se lee dentro del `onDraw`**, nunca en el cuerpo del
 * composable: una lectura de `State` en fase de dibujo invalida el dibujo, asi que ni la
 * respiracion, ni un parpadeo, ni una coreografia entera recomponen nada. [motion] es opcional
 * porque las pantallas que reciben cues (Hoy, repaso) necesitan el mango para llamar a
 * `play`; las que no, pasan `null` y esta funcion se crea el suyo. Que lo haya o no es una
 * constante por sitio de llamada, igual que [animated].
 *
 * [animated] congela cada bucle y cada morfeo a la vez, dejando el fotograma en reposo. No es un
 * apaño de tests: es arte. Lo usan las celebraciones (que ya tienen su propia animación de
 * entrada), la escena 7b del onboarding (Habi hundida en el sofá) y el comentarista de Stats.
 * Cualquier superficie donde la quietud NO sea deliberada debe dejar el `true` por defecto.
 *
 * Una Habi quieta conserva la presion del dedo: la materia cede bajo el dedo aunque no haya
 * ningun bucle vivo.
 *
 * [groundShadow] = false para una escena que ya pinta la suya a mano contra un mockup
 * (`StoryScenes`), unica razon legitima para apagarla.
 */
@Composable
fun HabiAvatar(
    spec: HabiSpec,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    onTap: (() -> Unit)? = null,
    delighted: Boolean = false,
    groundShadow: Boolean = true,
    motion: HabiMotion? = null,
) {
    val grain = HabiGrain.brush(LocalContext.current)
    val restingMotion = restingFaceMotion(spec, delighted)
    var faceMotion = restingMotion
    var heartsPhase: State<Float>? = null
    // Normalized (0..1) position of the last finger-down inside the canvas, observed passively so
    // clickable still owns the click; the poke reaction reads it to compress toward the touch.
    var lastTouch by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    val resolvedMotion = motion ?: rememberHabiMotion(spec.pose, spec.mood, spec.personality, idle = animated)

    if (animated) {
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
        faceMotion =
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
            // Sin `by`: el State se pasa entero al `onDraw` y se lee ALLI, para que los corazones
            // tampoco recompongan el composable en cada fotograma.
            heartsPhase =
                heartsTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
                    label = "habi-hearts-phase",
                )
        }
    }

    val expressiveTap = animated && onTap != null
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
    // La presion SI se lee en composicion, y es deliberado: cambia con la pulsacion, no con cada
    // gesto de Habi, asi que su muelle recompone medio segundo por toque y nada mas. Las copias
    // locales son las que hacen la lectura observable; el SideEffect solo la publica.
    val pressX = scaleX
    val pressY = scaleY
    SideEffect { resolvedMotion.setPress(pressX, pressY) }

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
                                if (animated) resolvedMotion.poke(lastTouch, spec.personality)
                            },
                        )
                } else {
                    base
                }
            }

    Canvas(canvasModifier) {
        // Los canales se leen AQUI, dentro de onDraw: una lectura de State en fase de dibujo
        // invalida el dibujo, no la composicion. Leerlos arriba recomponia el composable entero
        // en cada fotograma de cada muelle.
        val rest = poseBodyMotion(spec.pose)
        drawHabi(
            spec = spec,
            blink = if (animated) resolvedMotion.blink() else 0f,
            delighted = delighted,
            motion =
                faceMotion.copy(
                    face =
                        faceMotion.face.copy(
                            mouthOpen =
                                if (animated) {
                                    maxOf(faceMotion.face.mouthOpen, resolvedMotion.mouthOpen())
                                } else {
                                    faceMotion.face.mouthOpen
                                },
                        ),
                ),
            gaze = if (animated) resolvedMotion.gaze() else Offset.Zero,
            body =
                if (animated) {
                    resolvedMotion.body()
                } else {
                    // Quieta a proposito — pero la materia sigue cediendo bajo el dedo.
                    rest.copy(scaleX = rest.scaleX * pressX, scaleY = rest.scaleY * pressY)
                },
            groundShadow = groundShadow,
            grain = grain,
        )
        if (delighted) heartsPhase?.let { drawHearts(it.value) }
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

// Los corazones nacen en la CARA, asi que arrastran el −0,03 que subio la silueta entera cuando
// el viewport reservo su franja inferior para la sombra: se habian quedado tres centesimas bajos.
private const val HEART_RISE_START_Y = 0.39f
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
