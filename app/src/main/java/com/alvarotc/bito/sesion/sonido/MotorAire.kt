package com.alvarotc.bito.sesion.sonido

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import com.alvarotc.bito.domain.model.BreathPhase
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.sesion.Aire
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.sonido.AjustesSonido
import com.alvarotc.bito.ui.sistema.sonido.Canal
import com.alvarotc.bito.ui.sistema.sonido.Ganancia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Adonde van las muestras: el `AudioTrack` en la app, un sumidero falso en los tests. */
interface SalidaAudio {
    fun abrir()

    /** Bloquea hasta que caben, así marca el paso del hilo. */
    fun escribir(muestras: FloatArray)

    fun cerrar()
}

/** `AudioTrack` en streaming: 44,1 kHz, mono, en coma flotante, `USAGE_MEDIA` y `CONTENT_TYPE_MUSIC`. */
class SalidaAudioTrack : SalidaAudio {
    private var pista: AudioTrack? = null

    override fun abrir() {
        val formato =
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(SintesisAire.MUESTREO)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        val minimo = AudioTrack.getMinBufferSize(SintesisAire.MUESTREO, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        pista =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setAudioFormat(formato)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minimo, BLOQUES_EN_COLA * SintesisAire.BLOQUE * Float.SIZE_BYTES))
                .build()
                .also { it.play() }
    }

    override fun escribir(muestras: FloatArray) {
        pista?.write(muestras, 0, muestras.size, AudioTrack.WRITE_BLOCKING)
    }

    override fun cerrar() {
        pista?.run {
            stop()
            release()
        }
        pista = null
    }

    private companion object {
        const val BLOQUES_EN_COLA = 4
    }
}

/**
 * El aire de la sesión y de respirar (spec §8.3, §8.4, §9.1, §11.5): el tono guía y la música que
 * respira, sintetizados por [SintesisAire] en bloques de 10 ms en su propio hilo. El llenado sale del
 * ancla y de [reloj] (`elapsedRealtime`) en ese hilo, no de la UI, así que suena igual con la pantalla
 * apagada. Al cambiar de fase vibra (Inhala `RISE`, Mantén `TICK`, Exhala `LOW`; la primera no),
 * salvo en una llamada. Al acabar los ciclos para solo.
 */
class MotorAire(
    private val audio: AudioManager,
    private val foco: FocoAudio,
    private val tacto: Tacto,
    /** Ganancia del canal ambiente, ya con sus silencios (ver [ganancia]). */
    private val ganancia: () -> Float,
    private val salida: () -> SalidaAudio = ::SalidaAudioTrack,
    private val reloj: () -> Long = SystemClock::elapsedRealtime,
    private val lanzar: (Runnable) -> Unit = ::hiloDeAudio,
) : Aire,
    FocoAudio.Usuario {
    private val _sonando = MutableStateFlow(false)
    override val sonando: StateFlow<Boolean> = _sonando.asStateFlow()

    private class Pedido(
        val modo: BreathingMode,
        val ancla: Long,
        val ciclos: Int,
    )

    private val cerrojo = Any()
    private var pedido: Pedido? = null
    private var version = 0
    private var musica = false
    private var pararPedido = false
    private var vivo = false
    private var sinFoco = false

    override fun respirar(
        modo: BreathingMode,
        anclaElapsed: Long,
        ciclos: Int,
        musica: Boolean,
    ) {
        val arrancar =
            synchronized(cerrojo) {
                pedido = Pedido(modo, anclaElapsed, ciclos)
                version++
                this.musica = musica
                pararPedido = false
                _sonando.value = true
                (!vivo).also { vivo = true }
            }
        if (arrancar) lanzar(Runnable { bucle() })
    }

    override fun musica(encendida: Boolean) = synchronized(cerrojo) { musica = encendida }

    override fun parar() = synchronized(cerrojo) { pararPedido = true }

    /** Pérdida permanente del foco: para y deja de contar como sonando. */
    override fun alPerder() =
        synchronized(cerrojo) {
            pararPedido = true
            sinFoco = true
            _sonando.value = false
        }

    private fun bucle() {
        // Bito callado (interruptor general o canal ambiente a 0) no pide el foco: no suena, así que
        // no debe pausar la música de otra app. La vibración por fase sigue.
        var conFoco = false
        if (ganancia() > 0f) {
            if (!foco.tomar(this)) {
                synchronized(cerrojo) {
                    vivo = false
                    _sonando.value = false
                }
                return
            }
            conFoco = true
        }
        val s = SintesisAire()
        val out = salida()
        val bloque = FloatArray(SintesisAire.BLOQUE)
        var visto = -1
        var ultimoPaso = -1
        var terminado = false
        try {
            out.abrir()
            while (true) {
                val p: Pedido
                var parar: Boolean
                var retomarFoco = false
                synchronized(cerrojo) {
                    p = checkNotNull(pedido)
                    if (version != visto) {
                        visto = version
                        ultimoPaso = -1
                        s.respirar(p.modo, p.ciclos, musica)
                        retomarFoco = sinFoco
                        sinFoco = false
                    }
                    s.musica(musica)
                    parar = pararPedido
                }
                // Otra respiración tras perder el foco para siempre, o el volumen que sube con el foco sin pedir:
                // se pide ahora (solo si Bito puede sonar).
                if (retomarFoco) conFoco = false
                if (!conFoco && ganancia() > 0f) {
                    if (foco.tomar(this)) conFoco = true else parar = true
                }
                if (parar) s.parar()
                s.volumen = ganancia() * foco.factor
                val punto = s.llenar(bloque, reloj() - p.ancla)
                if (punto != null && !punto.finished && punto.stepIndex != ultimoPaso) {
                    if (ultimoPaso >= 0 && audio.mode != AudioManager.MODE_IN_CALL) tacto.haptica(hapticaDe(punto.phase))
                    ultimoPaso = punto.stepIndex
                }
                out.escribir(bloque)
                if (s.callado && terminar(visto)) {
                    terminado = true
                    break
                }
            }
        } finally {
            out.cerrar()
            // Si el audio falla a medias, el siguiente respirar debe poder arrancar otro hilo.
            if (!terminado) terminar(null)
        }
    }

    /** Si no ha llegado otra respiración mientras callaba, el hilo se va (y suelta el foco antes de que otro lo pida). */
    private fun terminar(visto: Int?): Boolean =
        synchronized(cerrojo) {
            if (visto != null && version != visto) return false
            foco.soltar(this)
            vivo = false
            _sonando.value = false
            true
        }

    companion object {
        /** El canal ambiente con el interruptor general y su volumen; el modo silencio del móvil no lo calla (spec §9.1). */
        fun ganancia(a: AjustesSonido): Float = Ganancia.canal(Canal.AMBIENTE, a, timbreNormal = true)

        private fun hapticaDe(fase: BreathPhase): Haptica =
            when (fase) {
                BreathPhase.INHALE -> Haptica.RISE
                BreathPhase.HOLD -> Haptica.TICK
                BreathPhase.EXHALE -> Haptica.LOW
            }

        private fun hiloDeAudio(tarea: Runnable) {
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                tarea.run()
            }, "bito-aire").start()
        }
    }
}
