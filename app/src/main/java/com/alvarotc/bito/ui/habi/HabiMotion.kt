package com.alvarotc.bito.ui.habi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import com.alvarotc.bito.domain.model.HabiCue
import com.alvarotc.bito.domain.model.HabiPose
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

// Real lids close faster than they open — that asymmetry is what reads as a blink instead of a
// camera shutter. An occasional double blink keeps the idle loop from feeling metronomic.
private const val BLINK_CLOSE_MS = 60
private const val BLINK_OPEN_MS = 110
private const val BLINK_MIN_DELAY_MS = 3000L
private const val BLINK_MAX_DELAY_MS = 5000L
private const val DOUBLE_BLINK_CHANCE = 4 // 1 in N idle blinks doubles up.
private const val DOUBLE_BLINK_GAP_MS = 90L

// QA 2026-08-23 bis («mascota, no robot»): everything anchors at the FEET (drawHabi pivots on
// FOOT_Y) so squash, stretch and landings read as weight; breathing is visible and mood-paced;
// idle micro-gestures fire every few seconds.
private const val BREATH_SCALE = 0.022f
private const val BREATH_PERIOD_RADIANT_MS = 2000
private const val BREATH_PERIOD_NORMAL_MS = 2700
private const val BREATH_PERIOD_LOW_MS = 3500

// La respiracion ya no LEVANTA el cuerpo (el `bob` desaparecio con el graphicsLayer): lo ensancha
// y lo estrecha desde el pie y lo balancea medio grado. Ese translationY senoidal permanente era
// literalmente lo que sonaba a robot.
private const val BREATH_TILT_DEG = 0.4f

// Dormida respira mas lento, y ningun ciclo dura exactamente lo que el anterior: sin ese jitter el
// pecho es un metronomo y el ojo lo caza en dos respiraciones.
private const val BREATH_SLEEP_FACTOR = 1.5f
private const val BREATH_JITTER = 0.08f

private const val IDLE_GESTURE_MIN_DELAY_MS = 5000L
private const val IDLE_GESTURE_MAX_DELAY_MS = 11000L
private const val IDLE_GESTURE_COUNT = 4 // curiosidad, estiron, mirada de reojo y balanceo minimo.
private const val IDLE_GESTURE_CURIOSITY = 0
private const val IDLE_GESTURE_STRETCH = 1
private const val IDLE_GESTURE_GLANCE = 2 // el unico que no toca el cuerpo: solo los ojos.
private const val IDLE_TILT_DEG = 5f
private const val IDLE_TILT_IN_MS = 240
private const val IDLE_TILT_HOLD_MS = 320L
private const val IDLE_STRETCH = 0.4f
private const val IDLE_STRETCH_IN_MS = 300
private const val IDLE_STRETCH_HOLD_MS = 260L
private const val IDLE_GLANCE_X = 0.6f
private const val IDLE_GLANCE_IN_MS = 140
private const val IDLE_SWAY_DEG = 2.5f // el cuarto gesto: un balanceo que casi no se ve.
private const val IDLE_SWAY_HOLD_MS = 700L

// Esperando a que acabe el dia los intervalos se alargan: menos cosas que contar.
private const val IDLE_WAITING_FACTOR = 1.5f
private const val IDLE_SARGENTO_FACTOR = 1.15f
private const val IDLE_CHEERLEADER_FACTOR = 0.85f

private const val SQUASH_SCALE_X = 0.10f
private const val SQUASH_SCALE_Y = 0.14f

// El bote ya no se mide en dp: el transform vive en `drawHabi`, cuyo `liftN` es fraccion del
// viewport (hereda el TAP_HOP_DP de 8 dp sobre un avatar de ~150 dp). Asi el widget y la
// notificacion, que dibujan por software, heredarian el mismo salto.
private const val LIFT_MAX_N = 0.02f

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
private const val POKE_GAZE_MS = 90
private const val HOP_CHANCE = 3 // 1 in N pokes hops, unless the previous poke already did.
private const val CHEERLEADER_HOP_CHANCE = 2 // amplia: bota mas a menudo.
private const val GAZE_HOLD_MS = 420L
private const val IDLE_GLANCE_HOLD_MS = 600L

// El bote compartido (toque, ALL_DONE, dia perfecto): estira al subir, cae acelerando y aterriza.
private const val HOP_RISE_MS = 150
private const val HOP_FALL_MS = 140
private const val HOP_STRETCH = 0.45f
private const val HOP_LAND_SQUASH = 0.35f

// Ojos primero (spec §3.6): en toda coreografia con direccion la mirada sale antes que el cuerpo.
// No es un canal nuevo, es el orden — y 70 ms es el retardo que lo hace legible sin partir el gesto.
private const val EYES_LEAD_MS = 70L

// La sombra es parte del gesto: se estrecha cuando el cuerpo sube y se ensancha y oscurece cuando
// se posa. `body()` la clampea a SHADOW_SCALE_MAX (~1,11), el techo que la deja dentro del viewport.
private const val SHADOW_SCALE_UP = 0.7f

// 1,10 y no 1,15: el clamp de `body()` es SHADOW_SCALE_MAX (~1,11), asi que un 1,15 se recortaba
// entero y el ensanchado al posarse no llegaba a verse. Este cabe por debajo del techo.
private const val SHADOW_SCALE_DOWN = 1.10f
private const val SHADOW_ALPHA_SETTLED = 0.32f

// Variacion por personalidad (spec §3.4): Sargento seco, Cheerleader amplio, Neutra al medio.
private const val SARGENTO_GAIN = 0.8f
private const val CHEERLEADER_GAIN = 1.25f

// --- Coreografia del registro (LOGGED, ~320 ms): mira al item y asiente.
private const val LOGGED_GAZE_Y = 0.55f
private const val LOGGED_GAZE_MS = 40
private const val LOGGED_TILT_DEG = -4f
private const val LOGGED_SQUASH = 0.22f
private const val LOGGED_SHADOW_NARROW = 0.92f
private const val LOGGED_SHADOW_WIDE = 1.05f
private const val LOGGED_SHADOW_MS = 160
private const val LOGGED_HOP = 0.55f // el bote de 1 de cada 3 registros: mas bajo que el de todo hecho.

// --- Coreografia de todo hecho (ALL_DONE, ~520 ms): estiron, bote y a esperar.
private const val ALL_DONE_STRETCH = 0.45f
private const val ALL_DONE_STRETCH_MS = 140
private const val ALL_DONE_HOP = 0.8f

// --- El vuelco (FAILED, ~1,4 s), el gesto insignia: anticipacion, caida, pausa volcada y un
// enderezado que SE PASA DE LARGO antes de asentarse.
private const val TIP_ANTICIPATION_DEG = 6f
private const val TIP_ANTICIPATION_SQUASH = 0.15f
private const val TIP_ANTICIPATION_MS = 120
private const val TIP_DEG = 78f
private const val TIP_FALL_MS = 240
private const val TIP_HOLD_MS = 420L
private const val TIP_OVERSHOOT_DEG = 12f
private const val TIP_LOOK_MS = 300L

// --- Asentarse y dormirse (SEALED, ~900 ms): bostezo, el cuerpo se rinde y los parpados caen.
private const val SEALED_YAWN_MS = 280
private const val SEALED_SQUASH = 0.30f
private const val SEALED_SINK_MS = 240
private const val SEALED_SINK_N = -0.012f // ~1,5 dp de hundimiento sobre un avatar de 120 dp.
private const val SEALED_LIDS_MS = 320

// --- Dia perfecto (PERFECT_DAY, ~2 s): la coreografia mas larga del catalogo.
private const val PERFECT_STRETCH = 0.5f
private const val PERFECT_STRETCH_MS = 180
private const val PERFECT_HOP_FIRST = 0.9f
private const val PERFECT_HOP_SECOND = 0.6f
private const val PERFECT_SPIN_DEG = 10f
private const val PERFECT_SPIN_MS = 220

// --- El rito del ojo (~900 ms): quieta, el ojo se pinta (lo trae el `spec`) y el cuerpo asiente.
private const val EYE_RITUAL_STILL_MS = 200L
private const val EYE_RITUAL_NOD_DEG = -5f
private const val EYE_RITUAL_SQUASH = 0.2f
private const val EYE_RITUAL_HOLD_MS = 300L

// One shared physics so every gesture reads as the same creature: JellySpring is the wobbly
// release of poked matter, SettleSpring the calm return of idle gestures and landings, GazeSpring
// the eyes easing back from a glance.
private val JellySpring = spring<Float>(dampingRatio = 0.32f, stiffness = 380f)
private val SettleSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
private val GazeSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 300f)

/** El enderezado tras el vuelco: SE PASA DE LARGO y vuelve. Es el gesto insignia. */
private val TipSpring = spring<Float>(dampingRatio = 0.45f, stiffness = 220f)

/** Cambio de pose del dia: lento, sin rebote, casi un suspiro. */
private val PoseSpring = spring<Float>(dampingRatio = 0.90f, stiffness = 140f)

/** El asentimiento al registrar: corto y seco. */
private val NudgeSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 500f)

// El catalogo de sonidos de hoy todavia nombra EMOCIONES; la tarea de la voz lo sustituye por
// nombres de MOMENTO (BUMP/MEEH/TICK/SIGH/JINGLE). Nombrar aqui el momento deja ese relevo en un
// renombrado de estas cinco lineas, en vez de una relectura de las coreografias.
private val SOUND_BUMP = HabiSound.GREETING
private val SOUND_MEEH = HabiSound.SAD
private val SOUND_TICK = HabiSound.LOG
private val SOUND_SIGH = HabiSound.HAPPY
private val SOUND_JINGLE = HabiSound.CELEBRATION

/**
 * El movimiento de Habi, fuera de la composicion.
 *
 * Los canales viven aqui como [Animatable] y **solo se leen dentro del `onDraw`** ([body], [gaze],
 * [blink], [mouthOpen]): una lectura de `State` en fase de dibujo invalida el dibujo, no la
 * composicion, asi que ningun muelle recompone nada. Leerlos en el cuerpo del composable —lo que
 * hacia [HabiAvatar] hasta ahora— recomponia el composable entero en cada fotograma de cada muelle.
 *
 * Todo gesto es anticipacion, pasada de largo y asentamiento, y todos son interrumpibles: cada
 * coreografia corre en su propio `Job` que la siguiente cancela, y el ultimo `animateTo` de cada
 * canal gana. Un gesto cancelado devuelve los canales transitorios a reposo, para que un registro
 * a mitad del vuelco no deje a Habi a medio levantar.
 */
@Stable
class HabiMotion internal constructor(
    private val scope: CoroutineScope,
    private val onSound: (HabiSound) -> Unit,
) {
    // Canales, todos en reposo a 0 salvo la sombra: squash >0 aplasta / <0 estira (desde el pie),
    // hop y lift levantan, tilt balancea sobre el pie, gaze dispara los ojos.
    private val breath = Animatable(0f)
    private val squash = Animatable(0f)
    private val tilt = Animatable(0f)
    private val hop = Animatable(0f)
    private val lift = Animatable(0f)
    private val shadowScale = Animatable(1f)
    private val shadowAlphaDelta = Animatable(0f)
    private val gazeX = Animatable(0f)
    private val gazeY = Animatable(0f)
    private val mouth = Animatable(0f)

    // Tres parpadeos independientes para que ninguno interrumpa a otro: el del bucle ocioso, el
    // pulso del toque y los parpados que el dormirse deja CAIDOS hasta que cambia la pose.
    private val idleBlink = Animatable(0f)
    private val pokeBlink = Animatable(0f)
    private val lidBlink = Animatable(0f)

    // El reposo de la pose se interpola entre dos HabiBodyMotion, no entre dos enums: asi una pose
    // interrumpida a mitad arranca de donde estaba y no da un salto.
    private var restFrom by mutableStateOf(poseBodyMotion(HabiPose.STANDING))
    private var restTo by mutableStateOf(poseBodyMotion(HabiPose.STANDING))
    private val poseBlend = Animatable(1f)
    private var pose by mutableStateOf(HabiPose.STANDING)
    private var poseSeeded = false

    // La presion del dedo: materia cediendo, no coreografia. La calcula el composable (cambia una
    // vez por pulsacion, no por fotograma) y se aplica aqui.
    private var pressX by mutableFloatStateOf(1f)
    private var pressY by mutableFloatStateOf(1f)

    private var gestureJob: Job? = null
    private var gazeJob: Job? = null
    private var lastIdleGesture = -1
    private var lastGestureHopped = false
    private var lidsHeld = false

    /** El transform del cuerpo: el reposo de la pose compuesto con los canales vivos. */
    fun body(): HabiBodyMotion {
        val rest = restPose()
        val phase = breath.value
        return HabiBodyMotion(
            tiltDeg = rest.tiltDeg + tilt.value + BREATH_TILT_DEG * (phase - 0.5f) * 2f,
            scaleX = rest.scaleX * pressX * (1f + SQUASH_SCALE_X * squash.value) * (1f - BREATH_SCALE * 0.5f * phase),
            scaleY = rest.scaleY * pressY * (1f - SQUASH_SCALE_Y * squash.value) * (1f + BREATH_SCALE * phase),
            liftN = rest.liftN + lift.value + LIFT_MAX_N * hop.value,
            // El techo de la sombra es el mismo que clampea `poseBodyMotion`: ninguna coreografia
            // puede sacarle el borde inferior del viewport.
            shadowScale = (rest.shadowScale * shadowScale.value).coerceIn(0f, SHADOW_SCALE_MAX),
            shadowAlpha = (rest.shadowAlpha + shadowAlphaDelta.value).coerceIn(0f, 1f),
        )
    }

    fun gaze(): Offset = Offset(gazeX.value, gazeY.value)

    fun blink(): Float = maxOf(idleBlink.value, pokeBlink.value, lidBlink.value)

    fun mouthOpen(): Float = mouth.value

    /** La presion del dedo (materia cediendo, no coreografia): la calcula el composable, la aplica [body]. */
    fun setPress(
        scaleX: Float,
        scaleY: Float,
    ) {
        pressX = scaleX
        pressY = scaleY
    }

    /**
     * Dispara la coreografia del cue y su sonido, cancelando la anterior. **Suspende hasta que el
     * CUERPO ha asentado**, como [settleInto]: quien secuencia «gesto y luego texto» lo necesita,
     * y quien no quiera esperar lo lanza en su propio scope. La mirada que el gesto dispare puede
     * seguir volviendo a su sitio despues: es un `Job` aparte, y esperar sus ~400 ms de sostenido
     * habria retrasado el texto por algo que el usuario ya no esta mirando.
     *
     * Devuelve `true` si el gesto llego al final y `false` si lo interrumpio otro: quien encadene
     * algo al gesto puede distinguirlos. Ignorar el valor es perfectamente legitimo.
     *
     * Un cue mueve los CANALES y nada mas: la pose es de la pantalla y solo entra por [settleInto].
     */
    suspend fun play(
        cue: HabiCue,
        personality: Personality,
    ): Boolean = awaitGesture { playCue(cue, personality) }

    /**
     * El rito del ojo: quieta, el ojo se pinta, el cuerpo asiente. SIN sonido (biblia §4).
     * Devuelve `true` si completo, `false` si otro gesto lo interrumpio.
     */
    suspend fun playEyeRitual(): Boolean = awaitGesture { runEyeRitual() }

    /** El toque: se tambalea desde la base y vuelve, con peso. No espera: el dedo ya se fue. */
    fun poke(
        touch: Offset,
        personality: Personality,
    ) {
        startGesture { runPoke(touch, personality) }
    }

    /**
     * La pose del dia cambia con [PoseSpring]: lento, sin rebote, casi un suspiro.
     *
     * **Esta es la UNICA via por la que la pose se mueve.** La manda siempre la pantalla, que la
     * deriva de la fase del dia; ninguna coreografia la toca por su cuenta. Dos fuentes de la pose
     * se desincronizan sola una vez: crear un habito nuevo despues de «todos hechos» devuelve la
     * fase a despierta mientras el gesto se habria quedado en la de espera.
     */
    suspend fun settleInto(pose: HabiPose) {
        if (poseSeeded && pose == this.pose) return
        this.pose = pose
        val target = poseBodyMotion(pose)
        if (!poseSeeded) {
            // La PRIMERA pose no se anima: quien abre la app de noche encuentra a Habi ya dormida,
            // no levantandose de la cama para volver a acostarse.
            poseSeeded = true
            restFrom = target
            restTo = target
            poseBlend.snapTo(1f)
            if (pose == HabiPose.SLEEPING) {
                lidsHeld = true
                lidBlink.snapTo(1f)
            }
            return
        }
        restFrom = restPose()
        restTo = target
        poseBlend.snapTo(0f)
        if (pose != HabiPose.SLEEPING) releaseLids()
        poseBlend.animateTo(1f, PoseSpring)
    }

    // --- Los bucles del idle -------------------------------------------------------------------

    /**
     * La respiracion: ensancha y estrecha desde el pie, sin levantar. Cada medio ciclo mide algo
     * distinto (y mas dormida) para que no se oiga el metronomo.
     */
    internal suspend fun runBreathing(mood: Mood) {
        while (true) {
            idleHeartbeat()
            breath.animateTo(1f, tween(breathHalfCycleMs(mood), easing = EaseInOut))
            breath.animateTo(0f, tween(breathHalfCycleMs(mood), easing = EaseInOut))
        }
    }

    /** Parpadeo irregular, con doble parpadeo 1 de cada [DOUBLE_BLINK_CHANCE]. Dormida no parpadea. */
    internal suspend fun runBlinking() {
        while (true) {
            idleHeartbeat()
            delay(Random.nextLong(BLINK_MIN_DELAY_MS, BLINK_MAX_DELAY_MS))
            if (asleep()) continue
            val blinks = if (Random.nextInt(DOUBLE_BLINK_CHANCE) == 0) 2 else 1
            repeat(blinks) { index ->
                if (index > 0) delay(DOUBLE_BLINK_GAP_MS)
                idleBlink.animateTo(1f, tween(BLINK_CLOSE_MS, easing = LinearEasing))
                idleBlink.animateTo(0f, tween(BLINK_OPEN_MS, easing = LinearEasing))
            }
        }
    }

    /**
     * Micro-gestos cada pocos segundos: una curiosidad, un estiron, una mirada de reojo o un
     * balanceo minimo — nunca dos iguales seguidos, nunca encima de una coreografia y nunca
     * dormida, que solo respira.
     */
    internal suspend fun runIdleGestures(personality: Personality) {
        while (true) {
            idleHeartbeat()
            delay(idleDelayMs(personality))
            if (asleep() || gestureJob?.isActive == true) continue
            val gain = amplitudeGain(personality)
            val gesture = nextIdleGesture()
            if (gesture == IDLE_GESTURE_GLANCE) {
                // La mirada de reojo no toca el cuerpo: corre por su propio Job, como toda mirada.
                val side = if (Random.nextBoolean()) 1f else -1f
                glanceAt(Offset(side * IDLE_GLANCE_X, 0f), IDLE_GLANCE_IN_MS, IDLE_GLANCE_HOLD_MS)
                continue
            }
            // Por la MISMA maquinaria que los gestos grandes, y no sueltos: asi un toque o un cue
            // que llegue a mitad de un micro-gesto lo cancela por su `Job` en vez de reventar el
            // `animateTo` desde el MutatorMutex. Esa excepcion escapaba del `while` y dejaba a Habi
            // sin micro-gestos el resto de la sesion; `join()` no la propaga, el bucle sigue vivo.
            awaitGesture {
                when (gesture) {
                    IDLE_GESTURE_CURIOSITY -> {
                        tilt.animateTo(-IDLE_TILT_DEG * gain, tween(IDLE_TILT_IN_MS, easing = EaseInOut))
                        delay(IDLE_TILT_HOLD_MS)
                        tilt.animateTo(0f, SettleSpring)
                    }
                    IDLE_GESTURE_STRETCH -> {
                        // El estiron, ahora CON sombra: subir sin que la sombra se estreche era
                        // exactamente el peso que le faltaba.
                        coroutineScope {
                            launch { squash.animateTo(-IDLE_STRETCH * gain, tween(IDLE_STRETCH_IN_MS, easing = EaseInOut)) }
                            launch { shadowScale.animateTo(SHADOW_SCALE_UP, tween(IDLE_STRETCH_IN_MS, easing = EaseInOut)) }
                        }
                        delay(IDLE_STRETCH_HOLD_MS)
                        coroutineScope {
                            launch { squash.animateTo(0f, SettleSpring) }
                            launch { shadowScale.animateTo(1f, SettleSpring) }
                        }
                    }
                    else -> {
                        val side = if (Random.nextBoolean()) 1f else -1f
                        tilt.animateTo(side * IDLE_SWAY_DEG, SettleSpring)
                        delay(IDLE_SWAY_HOLD_MS)
                        tilt.animateTo(0f, SettleSpring)
                    }
                }
            }
        }
    }

    // --- Las coreografias ----------------------------------------------------------------------

    private suspend fun playCue(
        cue: HabiCue,
        personality: Personality,
    ) {
        if (cue != HabiCue.SEALED) releaseLids()
        when (cue) {
            HabiCue.LOGGED -> playLogged(personality)
            HabiCue.ALL_DONE -> playAllDone(personality)
            HabiCue.FAILED -> playFailed()
            HabiCue.SEALED -> playSealed()
            HabiCue.PERFECT_DAY -> playPerfectDay()
        }
    }

    /** El asentimiento al registrar (~320 ms): los ojos bajan al item y el cuerpo los sigue. */
    private suspend fun playLogged(personality: Personality) {
        onSound(SOUND_TICK)
        val gain = amplitudeGain(personality)
        // Ojos primero, y por su cuenta: el cuerpo no espera a que la mirada vuelva del item.
        glanceAt(Offset(0f, LOGGED_GAZE_Y), LOGGED_GAZE_MS, GAZE_HOLD_MS)
        delay(EYES_LEAD_MS)
        coroutineScope {
            launch { tilt.animateTo(LOGGED_TILT_DEG * gain, NudgeSpring) }
            launch { squash.animateTo(LOGGED_SQUASH * gain, NudgeSpring) }
            launch {
                shadowScale.animateTo(LOGGED_SHADOW_NARROW, tween(LOGGED_SHADOW_MS, easing = FastOutSlowInEasing))
            }
        }
        if (rollHop(personality)) hopOnce(LOGGED_HOP * gain)
        coroutineScope {
            launch { tilt.animateTo(0f, NudgeSpring) }
            launch { squash.animateTo(0f, NudgeSpring) }
            launch {
                shadowScale.animateTo(LOGGED_SHADOW_WIDE, tween(LOGGED_SHADOW_MS, easing = FastOutSlowInEasing))
            }
        }
        shadowScale.animateTo(1f, SettleSpring)
    }

    /**
     * Todo hecho (~520 ms): estiron y bote, la reaccion grande. **No asienta la pose**: que el dia
     * ya no pida nada lo dice el spec, no este gesto. Cuando la pantalla re-derive su fase el
     * [settleInto] de turno traera la pose de espera.
     */
    private suspend fun playAllDone(personality: Personality) {
        val gain = amplitudeGain(personality)
        coroutineScope {
            launch { squash.animateTo(-ALL_DONE_STRETCH * gain, tween(ALL_DONE_STRETCH_MS, easing = LinearOutSlowInEasing)) }
            launch { shadowScale.animateTo(SHADOW_SCALE_UP, tween(ALL_DONE_STRETCH_MS, easing = LinearOutSlowInEasing)) }
        }
        // El unico golpecito del catalogo: el de ESTE aterrizaje.
        hopOnce(ALL_DONE_HOP * gain, landingSound = SOUND_BUMP)
    }

    /**
     * El vuelco (~1,4 s): se echa atras, cae de lado, se queda volcada la pausa que vende el golpe
     * y se endereza pasandose de largo. La pose TIPPED la impone el `tilt`, no [settleInto] — el
     * reposo de esa pose ES este mismo vuelco, y aplicarlo dos veces lo doblaria.
     */
    private suspend fun playFailed() {
        val side = if (Random.nextBoolean()) 1f else -1f
        coroutineScope {
            launch { tilt.animateTo(-TIP_ANTICIPATION_DEG * side, tween(TIP_ANTICIPATION_MS, easing = FastOutSlowInEasing)) }
            launch { squash.animateTo(TIP_ANTICIPATION_SQUASH, tween(TIP_ANTICIPATION_MS, easing = FastOutSlowInEasing)) }
        }
        // Ojos primero tambien aqui: la mirada se va hacia donde va a caer y el cuerpo la sigue
        // 70 ms despues. Aguanta volcada toda la pausa y vuelve al centro —al usuario— justo
        // cuando empieza el enderezado.
        glanceAt(Offset(side, 0f), TIP_FALL_MS, TIP_HOLD_MS)
        delay(EYES_LEAD_MS)
        coroutineScope {
            launch { tilt.animateTo(TIP_DEG * side, tween(TIP_FALL_MS, easing = FastOutLinearInEasing)) }
            launch { squash.animateTo(0f, tween(TIP_FALL_MS, easing = FastOutLinearInEasing)) }
            launch { shadowScale.animateTo(SHADOW_SCALE_DOWN, tween(TIP_FALL_MS, easing = FastOutLinearInEasing)) }
            launch {
                shadowAlphaDelta.animateTo(
                    SHADOW_ALPHA_SETTLED - SHADOW_ALPHA_REST,
                    tween(TIP_FALL_MS, easing = FastOutLinearInEasing),
                )
            }
        }
        onSound(SOUND_BUMP)
        delay(TIP_HOLD_MS)
        onSound(SOUND_MEEH)
        coroutineScope {
            launch { tilt.animateTo(-TIP_OVERSHOOT_DEG * side, TipSpring) }
            launch { shadowScale.animateTo(1f, TipSpring) }
            launch { shadowAlphaDelta.animateTo(0f, TipSpring) }
        }
        tilt.animateTo(0f, SettleSpring)
        delay(TIP_LOOK_MS)
    }

    /**
     * Asentarse y dormirse (~900 ms): bostezo, el cuerpo se rinde y los parpados caen.
     *
     * **No asienta la pose**, igual que la reaccion grande: dormirse lo dice el spec (la pantalla
     * deriva «sellado → dormida»), no este gesto. Lo sostenido —ancha, baja y con la sombra
     * quieta— ES esa pose cuando llegue; aqui el hundimiento vuelve a cero con [PoseSpring], que
     * es lento y sin rebote, para que el relevo no se note. Los parpados si se quedan caidos hasta
     * que otra pose o otro cue los levante: son el unico rastro que el gesto deja puesto.
     */
    private suspend fun playSealed() {
        onSound(SOUND_SIGH)
        mouth.animateTo(1f, tween(SEALED_YAWN_MS, easing = EaseInOut))
        coroutineScope {
            launch { mouth.animateTo(0f, tween(SEALED_YAWN_MS, easing = EaseInOut)) }
            launch { squash.animateTo(SEALED_SQUASH, tween(SEALED_SINK_MS, easing = FastOutSlowInEasing)) }
            launch { lift.animateTo(SEALED_SINK_N, tween(SEALED_SINK_MS, easing = FastOutSlowInEasing)) }
            launch { shadowScale.animateTo(SHADOW_SCALE_DOWN, tween(SEALED_SINK_MS, easing = FastOutSlowInEasing)) }
            launch {
                lidsHeld = true
                lidBlink.animateTo(1f, tween(SEALED_LIDS_MS, easing = LinearEasing))
            }
        }
        coroutineScope {
            launch { squash.animateTo(0f, PoseSpring) }
            launch { lift.animateTo(0f, PoseSpring) }
            launch { shadowScale.animateTo(1f, PoseSpring) }
        }
    }

    /** El dia perfecto (~2 s): la coreografia mas larga del catalogo. */
    private suspend fun playPerfectDay() {
        onSound(SOUND_JINGLE)
        coroutineScope {
            launch { squash.animateTo(-PERFECT_STRETCH, tween(PERFECT_STRETCH_MS, easing = LinearOutSlowInEasing)) }
            launch { shadowScale.animateTo(SHADOW_SCALE_UP, tween(PERFECT_STRETCH_MS, easing = LinearOutSlowInEasing)) }
        }
        hopOnce(PERFECT_HOP_FIRST)
        hopOnce(PERFECT_HOP_SECOND)
        tilt.animateTo(PERFECT_SPIN_DEG, tween(PERFECT_SPIN_MS, easing = EaseInOut))
        tilt.animateTo(-PERFECT_SPIN_DEG, tween(PERFECT_SPIN_MS * 2, easing = EaseInOut))
        tilt.animateTo(0f, SettleSpring)
    }

    /** El rito: quieta, el ojo aparece (lo trae el `spec`, no el motion) y el cuerpo asiente. */
    private suspend fun runEyeRitual() {
        delay(EYE_RITUAL_STILL_MS)
        coroutineScope {
            launch { tilt.animateTo(EYE_RITUAL_NOD_DEG, NudgeSpring) }
            launch { squash.animateTo(EYE_RITUAL_SQUASH, NudgeSpring) }
        }
        coroutineScope {
            launch { tilt.animateTo(0f, NudgeSpring) }
            launch { squash.animateTo(0f, NudgeSpring) }
        }
        delay(EYE_RITUAL_HOLD_MS)
    }

    /** El toque, portado tal cual: materia blanda bajo el dedo, con los ojos saliendo primero. */
    private suspend fun runPoke(
        touch: Offset,
        personality: Personality,
    ) = coroutineScope {
        launch {
            pokeBlink.animateTo(1f, tween(BLINK_CLOSE_MS, easing = LinearEasing))
            pokeBlink.animateTo(0f, tween(BLINK_OPEN_MS, easing = LinearEasing))
        }
        // Los ojos se van al dedo por su propio Job: un toque interrumpido no los congela a medio
        // camino, y el cuerpo no espera al sostenido para darse por asentado.
        glanceAt(
            Offset(
                ((touch.x - 0.5f) * 2f).coerceIn(-1f, 1f),
                ((touch.y - 0.5f) * 2f).coerceIn(-1f, 1f),
            ),
            POKE_GAZE_MS,
            GAZE_HOLD_MS,
        )
        launch {
            delay(EYES_LEAD_MS)
            // La compresion escala con lo alto que caiga el dedo (en la cabeza aprieta mas), la
            // inclinacion se aparta del toque y las dos llevan jitter para que no haya dos toques
            // iguales.
            val gain = amplitudeGain(personality)
            val jitter = 1f + POKE_JITTER * (Random.nextFloat() * 2f - 1f)
            val squashTarget = (POKE_SQUASH_BASE + POKE_SQUASH_HEAD_GAIN * (1f - touch.y)) * jitter * gain
            val tiltTarget = -(touch.x - 0.5f) * 2f * POKE_TILT_MAX_DEG * jitter * gain
            coroutineScope {
                launch { squash.animateTo(squashTarget, tween(POKE_PRESS_MS, easing = FastOutSlowInEasing)) }
                launch { tilt.animateTo(tiltTarget, tween(POKE_PRESS_MS, easing = FastOutSlowInEasing)) }
            }
            if (rollHop(personality)) {
                hopOnce(0.6f + Random.nextFloat() * 0.3f)
            } else {
                // La respuesta de siempre: soltarlo todo por el unico muelle poco amortiguado —
                // el temblor que decae ES la gelatina.
                coroutineScope {
                    launch { squash.animateTo(0f, JellySpring) }
                    launch { tilt.animateTo(0f, JellySpring) }
                }
            }
        }
    }

    /**
     * El bote compartido: estira al subir (decelerando), cae (acelerando) y aterriza con squish.
     * **Mudo por defecto**: el catalogo da un sonido por cue, no uno por bote, asi que solo quien
     * lo tenga en su guion pasa [landingSound] — y suena al tocar el suelo, no al saltar.
     */
    private suspend fun hopOnce(
        height: Float,
        landingSound: HabiSound? = null,
    ) {
        coroutineScope {
            launch { squash.animateTo(-HOP_STRETCH * height, tween(HOP_RISE_MS, easing = LinearOutSlowInEasing)) }
            launch { hop.animateTo(height, tween(HOP_RISE_MS, easing = LinearOutSlowInEasing)) }
            launch { tilt.animateTo(0f, tween(HOP_RISE_MS, easing = LinearOutSlowInEasing)) }
            launch { shadowScale.animateTo(SHADOW_SCALE_UP, tween(HOP_RISE_MS, easing = LinearOutSlowInEasing)) }
        }
        coroutineScope {
            launch { hop.animateTo(0f, tween(HOP_FALL_MS, easing = FastOutLinearInEasing)) }
            launch { squash.animateTo(HOP_LAND_SQUASH, tween(HOP_FALL_MS, easing = FastOutLinearInEasing)) }
            launch { shadowScale.animateTo(SHADOW_SCALE_DOWN, tween(HOP_FALL_MS, easing = FastOutLinearInEasing)) }
        }
        landingSound?.let(onSound)
        coroutineScope {
            launch { squash.animateTo(0f, JellySpring) }
            launch { shadowScale.animateTo(1f, JellySpring) }
        }
    }

    // --- Mecanica de los gestos ----------------------------------------------------------------

    /**
     * Lanza el gesto en su propio `Job`, cancelando el anterior. El `finally` devuelve los canales
     * transitorios a reposo SOLO si nadie tomo el relevo: si otro gesto ya es el vigente, el
     * reposo es suyo y no se le pisa.
     */
    private fun startGesture(block: suspend CoroutineScope.() -> Unit): Job {
        gestureJob?.cancel()
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    block()
                } finally {
                    if (gestureJob === coroutineContext[Job]) scope.launch { settleAfterGesture() }
                }
            }
        gestureJob = job
        job.start()
        return job
    }

    /** Espera a que el gesto asiente; si a quien espera lo cancelan, el gesto muere con el. */
    private suspend fun awaitGesture(block: suspend CoroutineScope.() -> Unit): Boolean {
        val job = startGesture(block)
        try {
            // `join` NO propaga la cancelacion del gesto, solo la de quien espera: por eso un
            // micro-gesto ocioso preemptado devuelve el control al bucle en vez de matarlo.
            job.join()
        } catch (cancellation: CancellationException) {
            job.cancel()
            throw cancellation
        }
        return !job.isCancelled
    }

    /**
     * El reposo tras un gesto: todo canal transitorio vuelve a 0. Tras una coreografia completa no
     * se nota (ya estaban), y tras una cancelada es lo que impide que un registro a mitad del
     * vuelco deje a Habi tumbada para siempre. Los parpados NO entran: dormirse los deja caidos a
     * proposito. **La mirada tampoco**: vive en su propio `Job` ([glanceAt]), que siempre termina
     * en el centro; tirar de ella aqui habria cortado el sostenido de una mirada recien lanzada.
     */
    private suspend fun settleAfterGesture() {
        coroutineScope {
            launch { tilt.animateTo(0f, SettleSpring) }
            launch { squash.animateTo(0f, SettleSpring) }
            launch { hop.animateTo(0f, SettleSpring) }
            launch { lift.animateTo(0f, SettleSpring) }
            launch { shadowScale.animateTo(1f, SettleSpring) }
            launch { shadowAlphaDelta.animateTo(0f, SettleSpring) }
            launch { mouth.animateTo(0f, SettleSpring) }
            launch { pokeBlink.animateTo(0f, tween(BLINK_OPEN_MS, easing = LinearEasing)) }
        }
    }

    /**
     * Una mirada: los ojos salen, aguantan y vuelven al centro. Corre en su propio `Job` —el que
     * cancela es la mirada SIGUIENTE, no el cuerpo— por dos razones: un gesto interrumpido no
     * congela los ojos a medio camino, y el cuerpo no tiene que esperar el sostenido para dar el
     * gesto por asentado. Siempre termina centrada, asi que nadie tiene que recogerla.
     */
    private fun glanceAt(
        target: Offset,
        inMs: Int,
        holdMs: Long,
    ) {
        gazeJob?.cancel()
        gazeJob =
            scope.launch {
                coroutineScope {
                    launch { gazeX.animateTo(target.x, tween(inMs, easing = LinearOutSlowInEasing)) }
                    launch { gazeY.animateTo(target.y, tween(inMs, easing = LinearOutSlowInEasing)) }
                }
                delay(holdMs)
                coroutineScope {
                    launch { gazeX.animateTo(0f, GazeSpring) }
                    launch { gazeY.animateTo(0f, GazeSpring) }
                }
            }
    }

    /** Levanta los parpados que dejo caidos el dormirse. No espera: el gesto que llega manda. */
    private fun releaseLids() {
        if (!lidsHeld) return
        lidsHeld = false
        scope.launch { lidBlink.animateTo(0f, tween(BLINK_OPEN_MS, easing = LinearEasing)) }
    }

    /**
     * Un latido de los bucles que no terminan nunca. Fuera de un test es un fotograma de espera y
     * nada mas; bajo la politica de animaciones infinitas de Compose —la misma que ya apagaba la
     * `rememberInfiniteTransition` de la respiracion— cancela el bucle, para que un `waitForIdle`
     * de cualquier pantalla con Habi delante siga volviendo. Los gestos, que si terminan, no pasan
     * por aqui.
     */
    private suspend fun idleHeartbeat() = withInfiniteAnimationFrameNanos { }

    private fun restPose(): HabiBodyMotion {
        val blend = poseBlend.value
        if (blend >= 1f) return restTo
        return HabiBodyMotion(
            tiltDeg = lerp(restFrom.tiltDeg, restTo.tiltDeg, blend),
            scaleX = lerp(restFrom.scaleX, restTo.scaleX, blend),
            scaleY = lerp(restFrom.scaleY, restTo.scaleY, blend),
            liftN = lerp(restFrom.liftN, restTo.liftN, blend),
            shadowScale = lerp(restFrom.shadowScale, restTo.shadowScale, blend),
            shadowAlpha = lerp(restFrom.shadowAlpha, restTo.shadowAlpha, blend),
        )
    }

    private fun asleep(): Boolean = pose == HabiPose.SLEEPING || lidsHeld

    private fun breathHalfCycleMs(mood: Mood): Int {
        val base =
            when (mood) {
                Mood.RADIANT -> BREATH_PERIOD_RADIANT_MS
                Mood.WILTED, Mood.DRAMATIC -> BREATH_PERIOD_LOW_MS
                else -> BREATH_PERIOD_NORMAL_MS
            }
        val sleeping = if (pose == HabiPose.SLEEPING) BREATH_SLEEP_FACTOR else 1f
        val jitter = 1f + BREATH_JITTER * (Random.nextFloat() * 2f - 1f)
        return (base * sleeping * jitter).toInt()
    }

    private fun idleDelayMs(personality: Personality): Long {
        val base = Random.nextLong(IDLE_GESTURE_MIN_DELAY_MS, IDLE_GESTURE_MAX_DELAY_MS)
        val posed = if (pose == HabiPose.WAITING) IDLE_WAITING_FACTOR else 1f
        val voiced =
            when (personality) {
                Personality.SARGENTO -> IDLE_SARGENTO_FACTOR
                Personality.CHEERLEADER -> IDLE_CHEERLEADER_FACTOR
                Personality.NEUTRA -> 1f
            }
        return (base * posed * voiced).toLong()
    }

    /** Nunca dos gestos ociosos iguales seguidos: repetir es lo que delata el bucle. */
    private fun nextIdleGesture(): Int {
        var pick = Random.nextInt(IDLE_GESTURE_COUNT)
        if (pick == lastIdleGesture) pick = (pick + 1 + Random.nextInt(IDLE_GESTURE_COUNT - 1)) % IDLE_GESTURE_COUNT
        lastIdleGesture = pick
        return pick
    }

    private fun amplitudeGain(personality: Personality): Float =
        when (personality) {
            Personality.SARGENTO -> SARGENTO_GAIN
            Personality.CHEERLEADER -> CHEERLEADER_GAIN
            Personality.NEUTRA -> 1f
        }

    /** El bote es la excepcion alegre: nunca dos seguidos, y el Sargento no bota nunca. */
    private fun rollHop(personality: Personality): Boolean {
        val chance =
            when (personality) {
                Personality.SARGENTO -> 0
                Personality.CHEERLEADER -> CHEERLEADER_HOP_CHANCE
                Personality.NEUTRA -> HOP_CHANCE
            }
        val hops = chance > 0 && !lastGestureHopped && Random.nextInt(chance) == 0
        lastGestureHopped = hops
        return hops
    }
}

/**
 * Crea (y recuerda) el movimiento de una Habi y arranca sus bucles de idle.
 *
 * La instancia sobrevive a la recomposicion; [onSound] no se congela con ella — un conducto nuevo
 * (otra pantalla, otro ViewModel) entra por el trampolin de [rememberUpdatedState] en vez de
 * perderse en silencio. [idle] en `false` deja a Habi quieta a proposito: ni respira, ni parpadea,
 * ni se mueve sola.
 */
@Composable
fun rememberHabiMotion(
    pose: HabiPose,
    mood: Mood,
    personality: Personality,
    onSound: (HabiSound) -> Unit = {},
    idle: Boolean = true,
): HabiMotion {
    val scope = rememberCoroutineScope()
    val latestSound = rememberUpdatedState(onSound)
    val motion = remember(scope) { HabiMotion(scope) { latestSound.value(it) } }
    LaunchedEffect(motion, pose) { motion.settleInto(pose) }
    LaunchedEffect(motion, mood, idle) { if (idle) motion.runBreathing(mood) }
    LaunchedEffect(motion, idle) { if (idle) motion.runBlinking() }
    LaunchedEffect(motion, personality, idle) { if (idle) motion.runIdleGestures(personality) }
    return motion
}
