package com.alvarotc.bito.ui.breathing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.alvarotc.bito.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La musica de fondo del ejercicio (D7), con la misma separacion que FocusPresence: el ViewModel
 * habla con esta interfaz y la plataforma (MediaPlayer, foco de audio) queda en la
 * implementacion. [start] arranca con fundido; [stop] funde a cero y libera. Las dos son
 * idempotentes: parar sin haber empezado no hace nada.
 */
interface BreathingMusic {
    fun start()

    fun stop()
}

/** Es fondo, no protagonista: la pista ya viene normalizada a unos -28 LUFS y esto la baja otra vez a la mitad. */
const val MUSIC_VOLUME = 0.5f
const val FADE_IN_MS = 1_500L
const val FADE_OUT_MS = 800L
const val RAMP_STEP_MS = 50L

/** Lo que hace la musica ante cada cambio de foco de audio. */
internal enum class FocusAction { PAUSE, RESUME, STOP, NONE }

/**
 * Perdida temporal (una llamada, una notificacion con sonido, un «bajar volumen» — que con
 * setWillPauseWhenDucked tambien llega aqui) pausa; recuperarlo reanuda con fundido; perderlo del
 * todo (otra app de musica) para y no vuelve sola.
 */
internal fun focusActionFor(focusChange: Int): FocusAction =
    when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS -> FocusAction.STOP
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusAction.PAUSE
        AudioManager.AUDIOFOCUS_GAIN -> FocusAction.RESUME
        else -> FocusAction.NONE
    }

/** Los volumenes intermedios de un fundido lineal de [from] a [to], uno cada [stepMs]; el ultimo es [to]. */
internal fun volumeRamp(
    from: Float,
    to: Float,
    durationMs: Long,
    stepMs: Long = RAMP_STEP_MS,
): List<Float> {
    val steps = (durationMs / stepMs).toInt().coerceAtLeast(1)
    return (1..steps).map { from + (to - from) * it / steps }
}

/**
 * MediaPlayer sin dependencias nuevas. Se construye con MediaPlayer() y no con MediaPlayer.create
 * porque los atributos de audio tienen que ir ANTES de preparar. isLooping sobre OGG Vorbis es el
 * camino sin huecos de Android; el fundido cruzado ya viene hecho en la pista. USAGE_MEDIA:
 * HabiSounds sigue en USAGE_GAME, la musica es otra cosa.
 *
 * Todo corre en el hilo principal ([scope] en Main.immediate; el oyente de foco llega por el
 * looper principal), asi que el estado no necesita sincronizacion. Si la pista no se puede cargar,
 * no suena y devuelve el foco: la musica es opcional y nunca tumba el ejercicio.
 */
class AndroidBreathingMusic(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : BreathingMusic {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    private val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { onFocusChange(it) }
            .build()

    private var player: MediaPlayer? = null
    private var ramp: Job? = null
    private var volume = 0f

    /** true entre un start() con foco concedido y el siguiente stop() o perdida definitiva del foco. */
    internal var wantsToPlay = false
        private set

    override fun start() {
        if (wantsToPlay) return
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        wantsToPlay = true
        volume = 0f
        player = runCatching { newPlayer() }.getOrNull()
        if (player == null) {
            wantsToPlay = false
            abandonFocus()
        }
    }

    override fun stop() {
        wantsToPlay = false
        ramp?.cancel()
        val fading = player ?: return abandonFocus()
        // Se suelta ya: un start() durante el fundido de salida crea otro reproductor sin pisar este.
        player = null
        val from = volume
        volume = 0f
        scope.launch {
            rampVolume(fading, from, 0f, FADE_OUT_MS)
            runCatching { fading.stop() }
            fading.release()
            if (!wantsToPlay) abandonFocus()
        }
    }

    private fun newPlayer(): MediaPlayer =
        MediaPlayer().apply {
            setAudioAttributes(attributes)
            appContext.resources.openRawResourceFd(R.raw.breathing_pad).use { fd ->
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            isLooping = true
            setVolume(0f, 0f)
            setOnPreparedListener { prepared ->
                if (prepared === player && wantsToPlay) {
                    prepared.start()
                    fadeIn(prepared)
                }
            }
            prepareAsync()
        }

    private fun fadeIn(target: MediaPlayer) {
        ramp?.cancel()
        val from = volume
        ramp = scope.launch { rampVolume(target, from, MUSIC_VOLUME, FADE_IN_MS) }
    }

    private suspend fun rampVolume(
        target: MediaPlayer,
        from: Float,
        to: Float,
        durationMs: Long,
    ) {
        for (value in volumeRamp(from, to, durationMs)) {
            runCatching { target.setVolume(value, value) }
            if (target === player) volume = value
            delay(RAMP_STEP_MS)
        }
    }

    private fun onFocusChange(focusChange: Int) {
        when (focusActionFor(focusChange)) {
            FocusAction.PAUSE -> {
                ramp?.cancel()
                player?.let { current -> if (current.isPlaying) current.pause() }
            }
            FocusAction.RESUME ->
                player?.let { current ->
                    if (wantsToPlay && !current.isPlaying) {
                        volume = 0f
                        current.setVolume(0f, 0f)
                        current.start()
                        fadeIn(current)
                    }
                }
            FocusAction.STOP -> stop()
            FocusAction.NONE -> Unit
        }
    }

    private fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
