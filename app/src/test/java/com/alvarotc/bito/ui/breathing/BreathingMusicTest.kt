package com.alvarotc.bito.ui.breathing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BreathingMusicTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audioManager = context.getSystemService(AudioManager::class.java)

    // Un proveedor de MediaInfo global (no ligado a un DataSource concreto) evita tener que
    // adivinar la clave interna con la que ShadowMediaPlayer identifica el FileDescriptor del
    // recurso crudo: sin el, prepareAsync() lanza IllegalStateException y wantsToPlay se queda en
    // false pese a un foco concedido.
    @Before
    fun setUp() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(150_000, 0) }
    }

    private fun currentPlayer(music: AndroidBreathingMusic): MediaPlayer? {
        val field = AndroidBreathingMusic::class.java.getDeclaredField("player")
        field.isAccessible = true
        return field.get(music) as MediaPlayer?
    }

    @Test
    fun `start asks for media focus that pauses instead of ducking`() {
        val music = AndroidBreathingMusic(context)

        music.start()

        val request = shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, request.focusGain)
        assertEquals(AudioAttributes.USAGE_MEDIA, request.audioAttributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_MUSIC, request.audioAttributes.contentType)
        assertTrue(request.willPauseWhenDucked())
        music.stop()
    }

    @Test
    fun `start with granted focus actually plays the track`() {
        val music = AndroidBreathingMusic(context)

        music.start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FADE_IN_MS + 100))

        assertTrue(music.wantsToPlay)
        assertTrue(currentPlayer(music)?.isPlaying == true)
        music.stop()
    }

    @Test
    fun `a denied focus plays nothing`() {
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        val music = AndroidBreathingMusic(context)

        music.start()

        assertFalse(music.wantsToPlay)
    }

    @Test
    fun `stopping gives the focus back once the fade is over`() {
        val music = AndroidBreathingMusic(context)
        music.start()

        music.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FADE_OUT_MS + 500))

        assertFalse(music.wantsToPlay)
        assertNotNull(shadowOf(audioManager).lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `stopping without starting is a no-op`() {
        val music = AndroidBreathingMusic(context)

        music.stop()

        assertFalse(music.wantsToPlay)
        assertNull(shadowOf(audioManager).lastAudioFocusRequest)
    }

    @Test
    fun `each focus change maps to what the music does`() {
        assertEquals(FocusAction.STOP, focusActionFor(AudioManager.AUDIOFOCUS_LOSS))
        assertEquals(FocusAction.PAUSE, focusActionFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertEquals(FocusAction.PAUSE, focusActionFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertEquals(FocusAction.RESUME, focusActionFor(AudioManager.AUDIOFOCUS_GAIN))
        assertEquals(FocusAction.NONE, focusActionFor(12345))
    }

    @Test
    fun `a fade in climbs in fifty-millisecond steps and lands on the target`() {
        val ramp = volumeRamp(0f, MUSIC_VOLUME, FADE_IN_MS)

        assertEquals((FADE_IN_MS / RAMP_STEP_MS).toInt(), ramp.size)
        assertEquals(MUSIC_VOLUME, ramp.last(), 1e-6f)
        assertTrue(ramp.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `a fade out falls to silence`() {
        val ramp = volumeRamp(MUSIC_VOLUME, 0f, FADE_OUT_MS)

        assertEquals(0f, ramp.last(), 1e-6f)
        assertTrue(ramp.zipWithNext().all { (a, b) -> b < a })
    }
}
