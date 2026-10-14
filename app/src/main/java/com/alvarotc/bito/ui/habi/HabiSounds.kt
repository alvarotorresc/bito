package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import com.alvarotc.bito.ui.sistema.sonido.Snd

/**
 * Las señales de Habi de siempre, ahora con los sonidos del catálogo (decisión 4): saludar y alegrarse
 * son `HABI`; celebrar, `DIA_COMPLETO`; la recaída, `VACIAR`; comprar, `GASTAR`; registrar, `GORGOTEO`.
 * Los silencios (incluido «Sonidos de Habi» y «Sonido al registrar») los aplica el motor.
 */
enum class HabiSound(val snd: Snd) {
    GREETING(Snd.HABI),
    CELEBRATION(Snd.DIA_COMPLETO),
    SAD(Snd.VACIAR),
    PURCHASE(Snd.GASTAR),
    LOG(Snd.GORGOTEO),
    HAPPY(Snd.HABI),
}

/** Fachada fina sobre el motor (spec §4.4.1) para no tocar a quien ya la llama. Nunca desde una notificación. */
class HabiSounds(private val motor: MotorSonido) {
    fun play(sound: HabiSound) = motor.play(sound.snd)
}
