package com.alvarotc.bito.ui.settings.estratos

import android.content.res.Resources
import com.alvarotc.bito.R

/** «8:30», «12:00»: la hora de reloj de 24 h sin cero a la izquierda en la hora. */
internal fun horaCorta(minutos: Int): String = "${minutos / 60}:${(minutos % 60).toString().padStart(2, '0')}"

/** «12:00», «12:00 y 19:00», «8:00, 12:00 y 19:00»: la lista de horas como se lee en voz alta. */
internal fun unirHoras(
    horas: List<Int>,
    resources: Resources,
): String {
    val textos = horas.map(::horaCorta)
    return when (textos.size) {
        0 -> ""
        1 -> textos[0]
        else -> resources.getString(R.string.ajustes_lista_y, textos.dropLast(1).joinToString(", "), textos.last())
    }
}
