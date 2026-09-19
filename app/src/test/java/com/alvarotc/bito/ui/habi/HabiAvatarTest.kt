package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCue
import com.alvarotc.bito.domain.model.HabiPose
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * [HabiAvatar]'s `contentDescription` used to be a single static "Habi" string regardless of
 * [Mood] — the [D]/[E] a11y finding this closes. `animated = false` avoids fighting the idle
 * bob/blink infinite transitions, same reasoning [StoreSectionTest]'s own kdoc gives for skipping
 * a full [HabiScreen] render.
 *
 * Los tests de [HabiMotion] son de CONTRATO, no de pixel: que el avatar se monte con y sin mango
 * externo, que el mango sobreviva a la recomposicion y que el sonido salga por el unico conducto.
 * El movimiento se mira en el Pixel, no se testea.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabiAvatarTest {
    @get:Rule
    val compose = createComposeRule()

    private companion object {
        const val TOLERANCE = 0.001f
        const val WAIT_MS = 10_000L

        // Sondeo del bucle de idle: 1200 pasos de 100 ms son 120 s de reloj virtual, una docena
        // larga de micro-gestos a 5-11 s cada uno. Como el reparto nunca repite gesto, esa docena
        // recorre los cuatro de sobra; si no se pillan al menos IDLE_MIN_HITS es que no corre.
        const val IDLE_PROBE_STEPS = 1200
        const val IDLE_PROBE_STEP_MS = 100L
        const val IDLE_MIN_HITS = 4
        const val MOVING_EPS = 0.01f

        /** El cuerpo se ha movido de su reposo: un micro-gesto esta en marcha. */
        fun HabiBodyMotion.movedFrom(still: HabiBodyMotion): Boolean =
            abs(tiltDeg - still.tiltDeg) > MOVING_EPS ||
                abs(scaleX - still.scaleX) > MOVING_EPS ||
                abs(shadowScale - still.shadowScale) > MOVING_EPS
    }

    private fun setContent(mood: Mood) {
        compose.setContent {
            BitoTheme {
                HabiAvatar(
                    spec = HabiSpec(mood, Personality.NEUTRA, EquippedSet()),
                    animated = false,
                )
            }
        }
    }

    /** La descripcion que [HabiAvatar] compone: el nombre y el animo, como los ve TalkBack. */
    private fun habiDescription(mood: Mood): String =
        when (mood) {
            Mood.RADIANT -> "Habi, feeling great"
            Mood.NORMAL -> "Habi, doing okay"
            Mood.WILTED -> "Habi, feeling low"
            Mood.DRAMATIC -> "Habi, having a hard time"
        }

    @Test
    fun `a radiant Habi describes its mood, not just its name`() {
        setContent(Mood.RADIANT)

        compose.onNodeWithContentDescription(habiDescription(Mood.RADIANT)).assertExists()
    }

    @Test
    fun `a wilted Habi describes a different mood`() {
        setContent(Mood.WILTED)

        compose.onNodeWithContentDescription(habiDescription(Mood.WILTED)).assertExists()
    }

    @Test
    fun `the avatar renders without an external motion handle`() {
        compose.setContent {
            BitoTheme { HabiAvatar(HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()), Modifier.size(72.dp)) }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription(habiDescription(Mood.NORMAL)).assertExists()
    }

    @Test
    fun `an external motion handle survives recomposition`() {
        var handle: HabiMotion? = null
        var pose by mutableStateOf(HabiPose.STANDING)
        compose.setContent {
            BitoTheme {
                val motion = rememberHabiMotion(pose, Mood.NORMAL, Personality.NEUTRA)
                handle = motion
                HabiAvatar(
                    HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(), pose = pose),
                    Modifier.size(72.dp),
                    motion = motion,
                )
            }
        }
        compose.waitForIdle()
        val first = handle

        pose = HabiPose.WAITING
        compose.waitForIdle()

        assertSame(first, handle)
    }

    @Test
    fun `a cue plays its sound through the one conduit`() {
        val played = mutableListOf<HabiSound>()
        var motion: HabiMotion? = null
        var scope: CoroutineScope? = null
        compose.setContent {
            BitoTheme {
                scope = rememberCoroutineScope()
                motion =
                    rememberHabiMotion(
                        HabiPose.STANDING,
                        Mood.NORMAL,
                        Personality.NEUTRA,
                        onSound = { played += it },
                    )
                HabiAvatar(
                    HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
                    Modifier.size(72.dp),
                    motion = motion,
                )
            }
        }
        compose.waitForIdle()

        // `play` suspende hasta que el gesto asienta, asi que se lanza y se espera al sonido.
        scope!!.launch { motion!!.play(HabiCue.LOGGED, Personality.NEUTRA) }
        compose.waitUntil { played.isNotEmpty() }

        assertEquals(listOf(HabiSound.LOG), played)
    }

    /**
     * La pose tiene UNA sola fuente: la pantalla, que la deriva de la fase del dia. Un cue mueve
     * los canales del gesto y nada mas — si la reaccion grande asentara la pose por su cuenta, las
     * dos fuentes se desincronizarian a la primera (crear un habito nuevo tras «todos hechos»
     * devuelve la fase a despierta mientras el gesto se habria quedado en la de espera).
     */
    @Test
    fun `a cue never moves the pose, only settleInto does`() {
        var motion: HabiMotion? = null
        var scope: CoroutineScope? = null
        compose.setContent {
            BitoTheme {
                scope = rememberCoroutineScope()
                motion = rememberHabiMotion(HabiPose.STANDING, Mood.NORMAL, Personality.NEUTRA)
                HabiAvatar(
                    HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
                    Modifier.size(72.dp),
                    motion = motion,
                )
            }
        }
        compose.waitForIdle()

        // TODOS los cues, no solo la reaccion grande: ninguno puede ser la segunda fuente.
        val standing = poseBodyMotion(HabiPose.STANDING)
        for (cue in HabiCue.entries) {
            var played = false
            scope!!.launch {
                motion!!.play(cue, Personality.NEUTRA)
                played = true
            }
            compose.waitUntil(timeoutMillis = WAIT_MS) { played }

            assertEquals("$cue movio la pose (scaleX)", standing.scaleX, motion!!.body().scaleX, TOLERANCE)
            assertEquals("$cue movio la pose (scaleY)", standing.scaleY, motion!!.body().scaleY, TOLERANCE)
        }

        var settled = false
        scope!!.launch {
            motion!!.settleInto(HabiPose.WAITING)
            settled = true
        }
        compose.waitUntil(timeoutMillis = WAIT_MS) { settled }

        val waiting = poseBodyMotion(HabiPose.WAITING)
        assertEquals(waiting.scaleX, motion!!.body().scaleX, TOLERANCE)
        assertEquals(waiting.scaleY, motion!!.body().scaleY, TOLERANCE)
    }

    /**
     * `Animatable.animateTo` corre bajo un `MutatorMutex`: cuando otra mutacion le quita el canal,
     * el `animateTo` preemptado lanza `CancellationException` a SU llamante. Con los micro-gestos
     * sueltos esa excepcion escapaba del `while (true)` y el bucle del idle moria para siempre —
     * Habi se quedaba sin micro-gestos el resto de la sesion en cuanto un toque pillaba uno a
     * medias, que es ~1 de cada 10. Ahora corren por el mismo `Job` que los gestos grandes y el
     * relevo es limpio.
     */
    @Test
    fun `a gesture that interrupts an idle micro-gesture leaves the idle loop alive`() {
        // Sin `autoAdvance` el bucle no se cancela por la politica de animaciones infinitas, que es
        // justo lo que hace falta para poder mirarlo de verdad; el reloj lo empujamos a mano.
        compose.mainClock.autoAdvance = false
        var motion: HabiMotion? = null
        var scope: CoroutineScope? = null
        compose.setContent {
            BitoTheme {
                scope = rememberCoroutineScope()
                // idle = false: los bucles los arranca este test, no la composicion, asi que
                // cualquier movimiento del cuerpo es del micro-gesto que estamos vigilando.
                motion = rememberHabiMotion(HabiPose.STANDING, Mood.NORMAL, Personality.NEUTRA, idle = false)
                HabiAvatar(
                    HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
                    Modifier.size(72.dp),
                    motion = motion,
                )
            }
        }
        compose.waitForIdle()
        val still = motion!!.body()

        val idle = scope!!.launch { motion!!.runIdleGestures(Personality.NEUTRA) }
        // Se interrumpe CADA micro-gesto que arranque, no solo el primero: el estiron mueve sus
        // canales desde dos `launch` hijos y una cancelacion de hijo no tumba al padre, asi que
        // solo mueren los que llaman a `animateTo` derechos sobre el bucle (curiosidad y balanceo).
        // Sondear uno solo puede caer justo en el inmune y no probar nada.
        var moving = false
        var interruptions = 0
        repeat(IDLE_PROBE_STEPS) {
            compose.mainClock.advanceTimeBy(IDLE_PROBE_STEP_MS)
            val nowMoving = motion!!.body().movedFrom(still)
            if (nowMoving && !moving) {
                motion!!.poke(Offset(0.5f, 0.5f), Personality.NEUTRA)
                interruptions++
            }
            moving = nowMoving
        }
        assertTrue("solo se interrumpieron $interruptions micro-gestos: el bucle no corre", interruptions >= IDLE_MIN_HITS)

        assertTrue("el bucle del idle murio al interrumpirle un micro-gesto", idle.isActive)
    }

    /**
     * El contrato de `play` como suspend: devuelve `true` si el gesto llego al final y `false` si
     * otro lo interrumpio. Quien encadene algo al gesto necesita poder distinguirlos.
     */
    @Test
    fun `an interrupted gesture reports that it did not finish`() {
        var motion: HabiMotion? = null
        var scope: CoroutineScope? = null
        compose.setContent {
            BitoTheme {
                scope = rememberCoroutineScope()
                motion = rememberHabiMotion(HabiPose.STANDING, Mood.NORMAL, Personality.NEUTRA, idle = false)
                HabiAvatar(
                    HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
                    Modifier.size(72.dp),
                    motion = motion,
                )
            }
        }
        compose.waitForIdle()

        // El dia perfecto es el mas largo del catalogo; el registro entra encima y se lo lleva.
        var interruptedResult: Boolean? = null
        var winnerResult: Boolean? = null
        scope!!.launch { interruptedResult = motion!!.play(HabiCue.PERFECT_DAY, Personality.NEUTRA) }
        scope!!.launch { winnerResult = motion!!.play(HabiCue.LOGGED, Personality.NEUTRA) }
        compose.waitUntil(timeoutMillis = WAIT_MS) { interruptedResult != null && winnerResult != null }

        assertEquals(false, interruptedResult)
        assertEquals(true, winnerResult)
    }

    /**
     * El conducto del sonido entra por `rememberUpdatedState`: un `onSound` nuevo (otra pantalla,
     * otro ViewModel) tiene que llegar al mismo `HabiMotion`, no quedarse congelado el primero.
     */
    @Test
    fun `a new sound conduit replaces the old one`() {
        val first = mutableListOf<HabiSound>()
        val second = mutableListOf<HabiSound>()
        var swapped by mutableStateOf(false)
        var motion: HabiMotion? = null
        var scope: CoroutineScope? = null
        compose.setContent {
            BitoTheme {
                scope = rememberCoroutineScope()
                val toFirst: (HabiSound) -> Unit = { first += it }
                val toSecond: (HabiSound) -> Unit = { second += it }
                motion =
                    rememberHabiMotion(
                        HabiPose.STANDING,
                        Mood.NORMAL,
                        Personality.NEUTRA,
                        onSound = if (swapped) toSecond else toFirst,
                        idle = false,
                    )
                HabiAvatar(
                    HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
                    Modifier.size(72.dp),
                    motion = motion,
                )
            }
        }
        compose.waitForIdle()

        swapped = true
        compose.waitForIdle()

        scope!!.launch { motion!!.play(HabiCue.LOGGED, Personality.NEUTRA) }
        compose.waitUntil(timeoutMillis = WAIT_MS) { second.isNotEmpty() }

        assertEquals(listOf(HabiSound.LOG), second)
        assertTrue("el conducto viejo seguia recibiendo", first.isEmpty())
    }

    @Test
    fun `the eye ritual is silent`() {
        val played = mutableListOf<HabiSound>()
        var motion: HabiMotion? = null
        var scope: CoroutineScope? = null
        compose.setContent {
            BitoTheme {
                scope = rememberCoroutineScope()
                motion =
                    rememberHabiMotion(
                        HabiPose.STANDING,
                        Mood.NORMAL,
                        Personality.NEUTRA,
                        onSound = { played += it },
                    )
                HabiAvatar(
                    HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(), eyesPainted = 0),
                    Modifier.size(72.dp),
                    motion = motion,
                )
            }
        }
        compose.waitForIdle()

        scope!!.launch { motion!!.playEyeRitual() }
        compose.waitForIdle()

        assertTrue(played.isEmpty())
    }
}
