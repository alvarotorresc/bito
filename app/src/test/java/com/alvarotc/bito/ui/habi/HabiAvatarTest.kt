package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

        var played = false
        scope!!.launch {
            motion!!.play(HabiCue.ALL_DONE, Personality.NEUTRA)
            played = true
        }
        compose.waitUntil(timeoutMillis = WAIT_MS) { played }

        val standing = poseBodyMotion(HabiPose.STANDING)
        assertEquals(standing.scaleX, motion!!.body().scaleX, TOLERANCE)
        assertEquals(standing.scaleY, motion!!.body().scaleY, TOLERANCE)

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
