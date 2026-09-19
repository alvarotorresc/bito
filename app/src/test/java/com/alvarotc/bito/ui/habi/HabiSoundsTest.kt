package com.alvarotc.bito.ui.habi

import android.content.Context
import android.media.AudioManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [HabiSounds.shouldPlay] is the real unit under test — a pure gate, checked directly, no
 * Robolectric shadow involved. Per the architect's 2026-08-19 ruling (class KDoc), it depends
 * ONLY on `habiSoundsEnabled` (`logSoundEnabled` for TICK): these cues are routed as `USAGE_GAME`
 * (media stream), so the ringer mode is never a factor — a QA Pixel in vibrate mode stayed silent
 * under the previous `USAGE_ASSISTANCE_SONIFICATION` routing, which this ruling fixes. The rest of
 * this suite is a thinner smoke check: a real [HabiSounds] wired to a real (Robolectric) [Context]
 * and [SettingsRepository] never crashes calling [HabiSounds.play], on or off, for every
 * [HabiSound], and — explicitly — across every ringer mode while enabled, demonstrating the ringer
 * is no longer consulted. Its `SoundPool` is a private lazy field with no handle exposed outside
 * the class, so there's no way to assert it was actually told to play — "doesn't crash and reaches
 * the SoundPool call" is the ceiling of what's checkable here (docs §5.3 / task-16 brief), which
 * is exactly why [HabiSounds.shouldPlay] is split out as pure in the first place. [HabiSounds]
 * takes an injectable dispatcher for exactly this: so `play`'s internally-launched coroutine can
 * be driven deterministically by [advanceUntilIdle] instead of racing a real background thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabiSoundsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun audioManager(): AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // --- shouldPlay: the pure gate — habiSoundsEnabled for Habi's voice, logSoundEnabled for
    // the registro tick (QA 2026-08-23) ------------------------------------------------------

    @Test
    fun `shouldPlay is true when sounds are enabled`() {
        assertTrue(HabiSounds.shouldPlay(Settings(habiSoundsEnabled = true), HabiSound.BUMP))
    }

    @Test
    fun `shouldPlay is false when the toggle is off`() {
        assertFalse(HabiSounds.shouldPlay(Settings(habiSoundsEnabled = false), HabiSound.BUMP))
    }

    @Test
    fun `the registro switch governs the tick, and nothing else`() {
        assertTrue(HabiSounds.shouldPlay(Settings(habiSoundsEnabled = false, logSoundEnabled = true), HabiSound.TICK))
        assertFalse(HabiSounds.shouldPlay(Settings(habiSoundsEnabled = true, logSoundEnabled = false), HabiSound.TICK))
    }

    @Test
    fun `the other five obey Habi's own switch`() {
        val voice = HabiSound.entries.filter { it != HabiSound.TICK }
        assertEquals(5, voice.size)
        for (sound in voice) {
            assertTrue(HabiSounds.shouldPlay(Settings(habiSoundsEnabled = true, logSoundEnabled = false), sound))
            assertFalse(HabiSounds.shouldPlay(Settings(habiSoundsEnabled = false, logSoundEnabled = true), sound))
        }
    }

    // --- construction: preloads eagerly, not lazily on the first play() ----------------------

    /**
     * Robolectric's `SoundPool` shadow doesn't model the real async decode this preload targets
     * (see class KDoc "ceiling of what's checkable here"), so this can only prove construction
     * itself never throws now that it eagerly builds the pool and calls `load` six times — not
     * that the decode actually finishes before a same-instant `play()` on a real device. The fix
     * itself is that `soundPool`/`soundIds` are no longer `by lazy`: this line alone loads them.
     */
    @Test
    fun `constructing HabiSounds preloads the six samples without a play call first`() {
        HabiSounds(context, SettingsRepository(settingsStore("habi-sounds-preload")))
    }

    // --- play: never crashes, respects the flag, ignores the ringer -------------------------

    @Test
    fun `play no-ops without crashing when habiSoundsEnabled is false`() =
        runTest(dispatcher) {
            val settings = SettingsRepository(settingsStore("habi-sounds-off"))
            settings.update { it.copy(habiSoundsEnabled = false) }
            val habiSounds = HabiSounds(context, settings, dispatcher = dispatcher)

            HabiSound.entries.forEach { habiSounds.play(it) }
            advanceUntilIdle()
        }

    @Test
    fun `play does not crash for every sound when enabled`() =
        runTest(dispatcher) {
            val settings = SettingsRepository(settingsStore("habi-sounds-on"))
            settings.update { it.copy(habiSoundsEnabled = true) }
            val habiSounds = HabiSounds(context, settings, dispatcher = dispatcher)

            HabiSound.entries.forEach { habiSounds.play(it) }
            advanceUntilIdle()
        }

    /**
     * The architect's ruling in one exercised path: media-stream routing means the ringer mode is
     * simply never read by [HabiSounds.play] any more (no [android.media.AudioManager] dependency
     * left in production code at all) — enabled sounds proceed through every ringer mode alike.
     * This can only prove "doesn't crash across ringer modes" (see suite header), not that audio
     * is actually audible on vibrate — that's the Pixel check the architect already ran.
     */
    @Test
    fun `play proceeds across every ringer mode when enabled — ringer mode is not consulted`() =
        runTest(dispatcher) {
            val settings = SettingsRepository(settingsStore("habi-sounds-ringer-independent"))
            settings.update { it.copy(habiSoundsEnabled = true) }
            val habiSounds = HabiSounds(context, settings, dispatcher = dispatcher)

            listOf(AudioManager.RINGER_MODE_NORMAL, AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_SILENT).forEach { mode ->
                audioManager().ringerMode = mode
                habiSounds.play(HabiSound.BUMP)
            }
            advanceUntilIdle()
        }

    // --- the six assets themselves ------------------------------------------------------------

    /**
     * Reads each `res/raw` OGG back through [Context.getResources] — available on the classpath
     * under Robolectric the same way it is on device — and checks the `OggS` magic
     * `tools/habi_cues.py` produces (via `ffmpeg -c:a libvorbis`) plus the per-clip and
     * whole-family size budget from spec §10.2 (≤12 KB each, ≤70 KB the six together). This
     * replaces the old WAV-header check: the family is OGG Vorbis now, not 16-bit PCM.
     */
    @Test
    fun `every raw Habi sound is a valid OGG within the size budget`() {
        val ids =
            mapOf(
                "habi_bump" to R.raw.habi_bump,
                "habi_meeh" to R.raw.habi_meeh,
                "habi_tick" to R.raw.habi_tick,
                "habi_sigh" to R.raw.habi_sigh,
                "habi_jingle" to R.raw.habi_jingle,
                "habi_purr" to R.raw.habi_purr,
            )
        var totalBytes = 0
        ids.forEach { (name, id) ->
            val bytes = context.resources.openRawResource(id).use { it.readBytes() }
            assertEquals("$name: OggS magic", "OggS", bytes.ascii(0, 4))
            assertTrue("$name: ${bytes.size} bytes should be at most $MAX_CLIP_BYTES", bytes.size <= MAX_CLIP_BYTES)
            totalBytes += bytes.size
        }
        assertTrue("family: $totalBytes bytes should be at most $MAX_FAMILY_BYTES", totalBytes <= MAX_FAMILY_BYTES)
    }

    private fun ByteArray.ascii(
        offset: Int,
        length: Int,
    ) = String(this, offset, length, Charsets.US_ASCII)

    companion object {
        private const val MAX_CLIP_BYTES = 12 * 1024
        private const val MAX_FAMILY_BYTES = 70 * 1024
    }
}
