package com.alvarotc.bito.ui.sistema

import android.view.View
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** Vocabulario háptico cerrado del catálogo §1.8. */
enum class Haptica { TICK, TICKS, CLICK, LOW, FALL, RISE, THUD, SEG, CONFIRM, REJECT }

/** La háptica (spec §4.5). «Vibración» apagado o la fuerza a 0 callan todo. */
interface Tacto {
    fun haptica(h: Haptica)

    /** Vista previa del riel de vibración: un tick a la fuerza [nivel] (0–10), sin esperar a que se guarde. */
    fun probar(nivel: Int)

    /** El mismo tacto atado a [vista], para las constantes de `performHapticFeedback`. */
    fun conVista(vista: View): Tacto = this
}

object TactoMudo : Tacto {
    override fun haptica(h: Haptica) = Unit

    override fun probar(nivel: Int) = Unit
}

val LocalTacto: ProvidableCompositionLocal<Tacto> = staticCompositionLocalOf { TactoMudo }
