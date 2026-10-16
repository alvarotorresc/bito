package com.alvarotc.bito.sesion.sonido

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/** Lo mínimo del foco de audio del sistema, para probar sin él. */
interface PlataformaFoco {
    /** Pide `AUDIOFOCUS_GAIN`; [escucha] recibe los cambios. Devuelve si se concedió. */
    fun pedir(escucha: (Int) -> Unit): Boolean

    fun soltar()
}

/**
 * El foco de verdad: `AUDIOFOCUS_GAIN` como música. Con `setWillPauseWhenDucked(true)` el sistema no
 * baja el volumen por su cuenta y avisa con `CAN_DUCK`, así el 30 % lo pone Bito.
 */
class PlataformaFocoAndroid(
    private val audio: AudioManager,
) : PlataformaFoco {
    private var pedido: AudioFocusRequest? = null

    override fun pedir(escucha: (Int) -> Unit): Boolean {
        val nuevo =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { escucha(it) }
                .build()
        pedido = nuevo
        return audio.requestAudioFocus(nuevo) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun soltar() {
        pedido?.let { audio.abandonAudioFocusRequest(it) }
        pedido = null
    }
}

/**
 * El foco de audio de la sesión, uno por proceso (spec §9.1): lo comparten el aire de respirar y el
 * fondo del foco. Android lleva el foco por petición, así que si cada uno pidiera el suyo se lo
 * quitarían entre ellos; aquí se pide con el primer usuario y se suelta con el último.
 *
 * Pérdida temporal: [factor] a 0 (quien suena funde y vuelve). `CAN_DUCK`: al 30 %. Pérdida
 * permanente: se avisa a todos con [Usuario.alPerder] y se suelta.
 */
class FocoAudio(
    private val plataforma: PlataformaFoco,
) {
    fun interface Usuario {
        fun alPerder()

        /** [factor] cambió (pérdida temporal, `CAN_DUCK` o vuelta del foco). Para quien aparca su bucle. */
        fun alCambiarFactor() = Unit
    }

    private val usuarios = mutableSetOf<Usuario>()

    /** Lo que multiplica el volumen de quien suena: 1, [AGACHADO] o 0. */
    @Volatile
    var factor: Float = 0f
        private set

    /** Devuelve si hay foco para sonar. */
    fun tomar(u: Usuario): Boolean =
        synchronized(this) {
            if (usuarios.isEmpty()) {
                if (!plataforma.pedir(::alCambiar)) return false
                factor = 1f
            }
            usuarios += u
            true
        }

    fun soltar(u: Usuario) =
        synchronized(this) {
            if (usuarios.remove(u) && usuarios.isEmpty()) {
                plataforma.soltar()
                factor = 0f
            }
        }

    private fun alCambiar(cambio: Int) {
        when (cambio) {
            AudioManager.AUDIOFOCUS_GAIN -> cambiarFactor(1f)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> cambiarFactor(0f)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> cambiarFactor(AGACHADO)
            AudioManager.AUDIOFOCUS_LOSS -> {
                val perdidos =
                    synchronized(this) {
                        factor = 0f
                        plataforma.soltar()
                        usuarios.toList().also { usuarios.clear() }
                    }
                perdidos.forEach { it.alPerder() }
            }
        }
    }

    private fun cambiarFactor(nuevo: Float) {
        val a =
            synchronized(this) {
                factor = nuevo
                usuarios.toList()
            }
        a.forEach { it.alCambiarFactor() }
    }

    companion object {
        /** Con `CAN_DUCK`, al 30 % (spec §9.1). */
        const val AGACHADO = 0.3f

        @Volatile
        private var unico: FocoAudio? = null

        /** El del proceso. */
        fun de(context: Context): FocoAudio =
            unico ?: synchronized(this) {
                unico ?: FocoAudio(
                    PlataformaFocoAndroid(context.applicationContext.getSystemService(AudioManager::class.java)),
                ).also { unico = it }
            }
    }
}
