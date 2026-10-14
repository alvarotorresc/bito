package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.LogicalDay

/** Un aviso de Habi planificado para [dia] a [minuto]. [clave] lo identifica para no repetirlo. */
data class AvisoPlan(
    val tipo: TipoAviso,
    val clave: String,
    val minuto: Int,
    val dia: LogicalDay,
)

enum class TipoAviso { MANANA, NOCHE, ACEPTADO }

/** El planificador de avisos (spec §4.7, §6.1–§6.3). Puro. */
object PlanAvisos {
    /** Provisional: el aviso de la noche a la hora de la noche si el día no está sellado. */
    fun de(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<AvisoPlan> =
        if (h.diaSellado) {
            emptyList()
        } else {
            listOf(AvisoPlan(TipoAviso.NOCHE, "noche:${ctx.hoy}", ctx.horaNoche, ctx.hoy))
        }

    /** El minuto de un aviso que se conserva (franjas, hábito, tareas, respirar) con el silencio (§6.3). Provisional: el mismo. */
    fun ajustarConservado(
        minuto: Int,
        ctx: Contexto,
    ): Int? = minuto
}
