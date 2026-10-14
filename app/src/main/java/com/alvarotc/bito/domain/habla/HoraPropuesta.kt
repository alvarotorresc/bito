package com.alvarotc.bito.domain.habla

/** La hora del «Vale, a las…» (spec §5.3). */
object HoraPropuesta {
    /** Provisional: sin hora. La Tarea 12 escribe el cálculo. */
    fun de(
        habito: HabitoHoy,
        ctx: Contexto,
    ): Int? = null

    /**
     * Los minutos válidos de hoy para una hora (spec §5.3), o null si no queda ninguno.
     * Provisional: de ahora + [Ritmo.HORA_MIN_DELANTE] a la noche − [Ritmo.HORA_ANTES_NOCHE].
     */
    fun ventana(ctx: Contexto): IntRange? {
        val desde = ctx.ahoraMin + Ritmo.HORA_MIN_DELANTE
        val hasta = ctx.horaNoche - Ritmo.HORA_ANTES_NOCHE
        return if (desde <= hasta) desde..hasta else null
    }
}
