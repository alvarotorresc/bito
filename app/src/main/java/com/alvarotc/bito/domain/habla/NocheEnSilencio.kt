package com.alvarotc.bito.domain.habla

/** Qué hacer con el aviso de la noche si cae en el silencio de Bito (spec §6.3, decisión 10 de §15). */
enum class PoliticaNocheEnSilencio { ADELANTAR, ESPERAR_AL_FINAL }

object NocheEnSilencio {
    /** Decisión 10 de §15 (2026-10-14). Cambiarla es cambiar esta línea. */
    val POLITICA = PoliticaNocheEnSilencio.ADELANTAR

    /**
     * El minuto al que sale el aviso de la noche, o null si no sale hoy. [ADELANTAR][PoliticaNocheEnSilencio.ADELANTAR]:
     * al empezar el silencio si empezó ese mismo día; si viene del día anterior, nada (nunca pasa a la víspera).
     * [ESPERAR_AL_FINAL][PoliticaNocheEnSilencio.ESPERAR_AL_FINAL]: al terminar el silencio si termina antes del
     * corte del día lógico; si no, nada.
     */
    fun minuto(
        noche: Int,
        silencio: Silencio?,
        corteDia: Int,
        politica: PoliticaNocheEnSilencio = POLITICA,
    ): Int? {
        if (silencio == null || !silencio.contiene(noche)) return noche
        return when (politica) {
            PoliticaNocheEnSilencio.ADELANTAR -> silencio.desde.takeIf { it <= noche }
            PoliticaNocheEnSilencio.ESPERAR_AL_FINAL -> {
                val mismoDia = silencio.hasta > noche && (noche >= corteDia || silencio.hasta <= corteDia)
                val trasMedianocheAntesDelCorte = corteDia > 0 && silencio.hasta <= corteDia
                silencio.hasta.takeIf { mismoDia || trasMedianocheAntesDelCorte }
            }
        }
    }
}
