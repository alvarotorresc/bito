package com.alvarotc.bito.ui.sistema.sonido

import com.alvarotc.bito.data.settings.Settings
import kotlin.math.min
import kotlin.math.pow

/** Lo que el motor necesita de los ajustes. */
data class AjustesSonido(
    val general: Boolean,
    val habi: Boolean,
    val registro: Boolean,
    val volHabi: Int,
    val volEfectos: Int,
    val volAmbiente: Int,
) {
    companion object {
        fun de(s: Settings) = AjustesSonido(s.soundEnabled, s.habiSoundsEnabled, s.logSoundEnabled, s.volHabi, s.volEffects, s.volAmbient)
    }
}

object Ganancia {
    /** `min(1, 1,4·(v/10)^1,6)`: `SoundPool` no pasa de 1 (decisión 6; se afina en el Pixel). */
    fun deNivel(v: Int): Float = min(1f, 1.4f * (v.coerceIn(0, 10) / 10f).pow(1.6f))

    fun nivel(
        canal: Canal,
        a: AjustesSonido,
    ): Int =
        when (canal) {
            Canal.HABI -> a.volHabi
            Canal.EFECTOS -> a.volEfectos
            Canal.AMBIENTE -> a.volAmbiente
        }

    /** Ganancia de un canal con sus silencios (sin los de [Snd.registro]). */
    fun canal(
        canal: Canal,
        a: AjustesSonido,
        timbreNormal: Boolean,
    ): Float {
        if (!a.general) return 0f
        // Spec §4.4.1 y catálogo §1.7: el móvil en silencio o vibración calla a Bito. Invierte la decisión
        // del 2026-08-19 (los sonidos de Habi seguían al volumen multimedia); se confirma en el Pixel.
        if (!timbreNormal) return 0f
        if (canal == Canal.HABI && !a.habi) return 0f
        return deNivel(nivel(canal, a))
    }

    /** Volumen final de [snd], o 0 si algo lo calla, en el orden de §4.4.1. */
    fun de(
        snd: Snd,
        intensidad: Float,
        a: AjustesSonido,
        timbreNormal: Boolean,
    ): Float {
        if (snd.registro && !a.registro) return 0f
        return canal(snd.canal, a, timbreNormal) * intensidad.coerceIn(0f, 1f)
    }
}
