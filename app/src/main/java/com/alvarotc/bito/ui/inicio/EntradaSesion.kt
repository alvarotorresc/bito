package com.alvarotc.bito.ui.inicio

import com.alvarotc.bito.domain.propuestas.Destino

/** «Ahora» y «Sesión ahora» (pieza 3, §4.5). La pieza 5 solo cambia esta función. */
object EntradaSesion {
    fun ruta(destino: Destino?): String =
        when (destino) {
            is Destino.Tarea -> "focus?taskId=${destino.id}"
            Destino.Respirar -> "breathing"
            is Destino.Habito, null -> "focus"
        }
}
