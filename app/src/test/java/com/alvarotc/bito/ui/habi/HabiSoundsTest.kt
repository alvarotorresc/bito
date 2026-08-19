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
 * [HabiSounds.shouldPlay] is the real unit under test — a pure gate, checked over every
 * enabled/ringer combination directly, no Robolectric shadow involved. The rest of this suite is
 * a thinner smoke check: a real [HabiSounds] wired to a real (Robolectric) [Context] and
 * [SettingsRepository] never crashes calling [HabiSounds.play], on or off, for every [HabiSound].
 * Its `SoundPool` is a private lazy field with no handle exposed outside the class, so there's no
 * way to assert it was actually told to play — "doesn't crash and reaches the SoundPool call" is
 * the ceiling of what's checkable here (docs §5.3 / task-16 brief), which is exactly why
 * [HabiSounds.shouldPlay] is split out as pure in the first place. [HabiSounds] takes an
 * injectable dispatcher for exactly this: so `play`'s internally-launched coroutine can be driven
 * deterministically by [advanceUntilIdle] instead of racing a real background thread.
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

    // --- shouldPlay: the pure gate --------------------------------------------------------

    @Test
    fun `shouldPlay is true only when sounds are enabled and the ringer is normal`() {
        assertTrue(HabiSounds.shouldPlay(Settings(habiSoundsEnabled = true), AudioManager.RINGER_MODE_NORMAL))
    }

    @Test
    fun `shouldPlay is false when the toggle is off, even with a normal ringer`() {
        assertFalse(HabiSounds.shouldPlay(Settings(habiSoundsEnabled = false), AudioManager.RINGER_MODE_NORMAL))
    }

    @Test
    fun `shouldPlay is false in silent mode, even with the toggle on`() {
        assertFalse(HabiSounds.shouldPlay(Settings(habiSoundsEnabled = true), AudioManager.RINGER_MODE_SILENT))
    }

    @Test
    fun `shouldPlay is false in vibrate mode, even with the toggle on`() {
        assertFalse(HabiSounds.shouldPlay(Settings(habiSoundsEnabled = true), AudioManager.RINGER_MODE_VIBRATE))
    }

    @Test
    fun `shouldPlay is false when the ringer mode is unknown`() {
        assertFalse(HabiSounds.shouldPlay(Settings(habiSoundsEnabled = true), ringerMode = null))
    }

    // --- play: never crashes, respects the flag ---------------------------------------------

    @Test
    fun `play no-ops without crashing when habiSoundsEnabled is false`() =
        runTest(dispatcher) {
            val settings = SettingsRepository(settingsStore("habi-sounds-off"))
            settings.update { it.copy(habiSoundsEnabled = false) }
            audioManager().ringerMode = AudioManager.RINGER_MODE_NORMAL
            val habiSounds = HabiSounds(context, settings, dispatcher = dispatcher)

            HabiSound.entries.forEach { habiSounds.play(it) }
            advanceUntilIdle()
        }

    @Test
    fun `play does not crash for every sound when enabled and the ringer is normal`() =
        runTest(dispatcher) {
            val settings = SettingsRepository(settingsStore("habi-sounds-on"))
            settings.update { it.copy(habiSoundsEnabled = true) }
            audioManager().ringerMode = AudioManager.RINGER_MODE_NORMAL
            val habiSounds = HabiSounds(context, settings, dispatcher = dispatcher)

            HabiSound.entries.forEach { habiSounds.play(it) }
            advanceUntilIdle()
        }

    @Test
    fun `play no-ops without crashing in silent mode even when enabled`() =
        runTest(dispatcher) {
            val settings = SettingsRepository(settingsStore("habi-sounds-silent"))
            settings.update { it.copy(habiSoundsEnabled = true) }
            audioManager().ringerMode = AudioManager.RINGER_MODE_SILENT
            val habiSounds = HabiSounds(context, settings, dispatcher = dispatcher)

            habiSounds.play(HabiSound.GREETING)
            advanceUntilIdle()
        }

    // --- the four assets themselves ----------------------------------------------------------

    /**
     * Reads each `res/raw` WAV back through [Context.getResources] — available on the classpath
     * under Robolectric the same way it is on device — and checks the RIFF/WAVE/fmt/data header
     * `tools/habi_sounds.py` writes (the plain 44-byte layout Python's `wave` module produces, no
     * extra chunks) plus the sub-one-second duration the brief requires.
     */
    @Test
    fun `every raw Habi sound is a valid sub-second mono 16-bit WAV`() {
        val ids =
            mapOf(
                "habi_meeh" to R.raw.habi_meeh,
                "habi_cheer" to R.raw.habi_cheer,
                "habi_sad" to R.raw.habi_sad,
                "habi_pop" to R.raw.habi_pop,
            )
        ids.forEach { (name, id) ->
            val bytes = context.resources.openRawResource(id).use { it.readBytes() }
            assertEquals("$name: RIFF header", "RIFF", bytes.ascii(0, 4))
            assertEquals("$name: WAVE format", "WAVE", bytes.ascii(8, 4))
            assertEquals("$name: fmt chunk", "fmt ", bytes.ascii(12, 4))
            val channels = bytes.le16(22)
            val sampleRate = bytes.le32(24)
            val bitsPerSample = bytes.le16(34)
            assertEquals("$name: mono", 1, channels)
            assertEquals("$name: 22050 Hz", 22050, sampleRate)
            assertEquals("$name: 16-bit", 16, bitsPerSample)
            assertEquals("$name: data chunk", "data", bytes.ascii(36, 4))
            val dataSize = bytes.le32(40)
            val byteRate = bytes.le32(28)
            val durationSeconds = dataSize.toDouble() / byteRate
            assertTrue("$name: duration ${durationSeconds}s should be under 1s", durationSeconds < 1.0)
        }
    }

    private fun ByteArray.ascii(
        offset: Int,
        length: Int,
    ) = String(this, offset, length, Charsets.US_ASCII)

    private fun ByteArray.le16(offset: Int): Int = (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.le32(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
}
