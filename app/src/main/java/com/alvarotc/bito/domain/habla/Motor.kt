package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.propuestas.Ambito

/**
 * Elige qué dice Habi en un ámbito (spec §4.3, §5.2): la primera situación de [Prioridades] con
 * candidatas que pase los enfriamientos. Provisional: un saludo en Hoy, la constancia en la
 * ficha, el comentario normal en Camino, el sencillo en el cierre y nada en el descanso.
 */
object Motor {
    fun elegir(
        ambito: Ambito,
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
        habitId: String? = null,
    ): Candidata? =
        when (ambito) {
            Ambito.HOY -> Candidata(Situacion.SALUDO, null, emptyMap(), emptyList())
            Ambito.FICHA -> Candidata(Situacion.FI_CONSTANCIA, habitId, emptyMap(), emptyList())
            Ambito.CAMINO -> Candidata(Situacion.CA_NORMAL, null, emptyMap(), emptyList())
            Ambito.CIERRE -> Candidata(Situacion.CIE_SENCILLO, null, emptyMap(), emptyList())
            Ambito.DESCANSO -> null
        }
}

/** El motor como dependencia: la fuente recibe [Motor.elegir] y los tests, un falso. */
fun interface ElegirCandidata {
    fun elegir(
        ambito: Ambito,
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
        habitId: String?,
    ): Candidata?
}
