package com.alvarotc.bito.domain.habla

/** La hora del «Vale, a las…» (spec §5.3). */
object HoraPropuesta {
    private const val PRIMER_MOMENTO = 14 * 60 + 30
    private const val SEGUNDO_MOMENTO = 18 * 60 + 30
    private const val TERCER_MOMENTO = 21 * 60
    private const val PRIMER_MOMENTO_ANTES_DE = 14 * 60
    private const val SEGUNDO_MOMENTO_ANTES_DE = 18 * 60
    private const val TERCER_MOMENTO_ANTES_DE = 20 * 60 + 30

    /**
     * La primera que sirva, por este orden: la hora habitual del hábito, su recordatorio propio y los
     * momentos del día (14:30 antes de las 14:00, 18:30 antes de las 18:00, 21:00 antes de las 20:30).
     * Sirve si está en [ventana] y fuera del silencio. Null si ninguna sirve: la propuesta sale sin hora. Con un aviso
     * aceptado pendiente hoy, tampoco: ya tiene su hora y un segundo «Vale» daría dos avisos del mismo hábito.
     */
    fun de(
        habito: HabitoHoy,
        ctx: Contexto,
    ): Int? {
        if (habito.avisoPendienteHoy) return null
        val ventana = ventana(ctx) ?: return null
        val momentos =
            listOfNotNull(
                PRIMER_MOMENTO.takeIf { ctx.ahoraMin < PRIMER_MOMENTO_ANTES_DE },
                SEGUNDO_MOMENTO.takeIf { ctx.ahoraMin < SEGUNDO_MOMENTO_ANTES_DE },
                TERCER_MOMENTO.takeIf { ctx.ahoraMin < TERCER_MOMENTO_ANTES_DE },
            )
        return (listOfNotNull(habito.horaHabitual, habito.recordatorio) + momentos)
            .firstOrNull { it in ventana && ctx.silencio?.contiene(it) != true }
    }

    /**
     * Los minutos válidos de hoy para una hora (spec §5.3), o null si no queda ninguno: de ahora +
     * [Ritmo.HORA_MIN_DELANTE] a la noche − [Ritmo.HORA_ANTES_NOCHE]. El silencio se mira aparte.
     */
    fun ventana(ctx: Contexto): IntRange? {
        val desde = ctx.ahoraMin + Ritmo.HORA_MIN_DELANTE
        val hasta = ctx.horaNoche - Ritmo.HORA_ANTES_NOCHE
        return if (desde <= hasta) desde..hasta else null
    }
}
