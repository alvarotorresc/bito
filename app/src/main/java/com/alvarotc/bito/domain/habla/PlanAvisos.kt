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

/**
 * El planificador de avisos (spec §4.7, §6.1–§6.3). Puro.
 *
 * Los minutos son de reloj (0–1439). Dentro del día lógico se ordenan desde [Contexto.corteDia]:
 * un minuto anterior al corte va detrás de la medianoche, al final del día lógico.
 */
object PlanAvisos {
    private const val MINUTOS_DIA = 24 * 60

    /** El aviso de la mañana no sale más tarde de esta hora, ni siquiera al terminar el silencio (§6.3). */
    private const val MANANA_HASTA = 11 * 60

    const val CLAVE_MANANA = "manana"
    const val CLAVE_NOCHE = "noche"

    /** Los avisos de Habi de hoy que aún no han pasado: mañana, noche y aceptados. */
    fun de(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<AvisoPlan> {
        val plan = mutableListOf<AvisoPlan>()
        minutoManana(h, m, ctx)?.let { plan += AvisoPlan(TipoAviso.MANANA, CLAVE_MANANA, it, ctx.hoy) }
        minutoNoche(h, ctx)?.let { plan += AvisoPlan(TipoAviso.NOCHE, CLAVE_NOCHE, it, ctx.hoy) }
        if (!h.diaSellado) {
            val pendientes = h.habitos.filterNot { it.hechoHoy }.map { it.id }.toSet()
            m.avisos
                .filter { it.dia == ctx.hoy && it.estado == EstadoAviso.PENDIENTE && it.habitId in pendientes }
                .forEach { plan += AvisoPlan(TipoAviso.ACEPTADO, it.id, it.minuto, ctx.hoy) }
        }
        return plan.filterNot { yaPasado(it.minuto, ctx) }
    }

    /**
     * El minuto de un aviso que se conserva (franjas, hábito, tareas, respirar) con el silencio (§6.3):
     * fuera del silencio, el mismo; dentro, el final del silencio si cae en el mismo día lógico; si no, null.
     */
    fun ajustarConservado(
        minuto: Int,
        ctx: Contexto,
    ): Int? {
        val silencio = ctx.silencio
        if (silencio == null || !silencio.contiene(minuto)) return minuto
        return silencio.hasta.takeIf { orden(it, ctx) > orden(minuto, ctx) }
    }

    /**
     * «Más tarde» en un aviso aceptado (§6.2, decisión 1 de §15): [Ritmo.MAS_TARDE_MIN] más, como mucho
     * [Ritmo.MAS_TARDE_VECES] veces, sin cruzar el silencio y al menos [Ritmo.HORA_ANTES_NOCHE] antes de la noche.
     */
    fun posponer(
        aviso: AvisoAceptado,
        ctx: Contexto,
    ): Int? {
        if (aviso.pospuesto >= Ritmo.MAS_TARDE_VECES) return null
        val desde = orden(aviso.minuto, ctx)
        val hasta = desde + Ritmo.MAS_TARDE_MIN
        if (hasta > orden(ctx.horaNoche, ctx) - Ritmo.HORA_ANTES_NOCHE) return null
        val silencio = ctx.silencio
        if (silencio != null && (desde + 1..hasta).any { silencio.contiene(it % MINUTOS_DIA) }) return null
        return hasta % MINUTOS_DIA
    }

    private fun minutoManana(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): Int? {
        val algoQueHacer = h.habitos.any { it.exigibleHoy } || h.tareas.isNotEmpty()
        if (!algoQueHacer || ausenteSinTurno(h)) return null
        val respondidaHoy = m.eventos.any { it.tipo == TipoMemoria.RESPUESTA && it.dia == ctx.hoy }
        val gordoResuelto = h.gordo.isNotEmpty() || !m.gordo[ctx.hoy].isNullOrEmpty() || h.candidatosGordo == 0
        if (respondidaHoy && gordoResuelto) return null
        return ajustarConservado(ctx.horaManana, ctx)?.takeIf { it == ctx.horaManana || orden(it, ctx) <= MANANA_HASTA }
    }

    /** La noche pasa por [NocheEnSilencio.minuto] y nada más: la decisión 10 vive allí. */
    private fun minutoNoche(
        h: Hechos,
        ctx: Contexto,
    ): Int? {
        if (h.diaSellado || h.habitos.none { it.exigibleHoy } || h.diasSinActividad >= Ritmo.DIAS_AUSENCIA) return null
        return NocheEnSilencio.minuto(ctx.horaNoche, ctx.silencio, ctx.corteDia)
    }

    /**
     * Día de ausencia en que no le toca al aviso de la mañana (§6.2, decisión 2 de §15): con
     * [Ritmo.DIAS_AUSENCIA] días sale en días alternos; con [Ritmo.DIAS_AUSENCIA_SEMANAL], el mismo día de la
     * semana que el último activo. `diasSinActividad` es `hoy - últimoActivo - 1`.
     */
    private fun ausenteSinTurno(h: Hechos): Boolean {
        val dias = h.diasSinActividad
        if (dias < Ritmo.DIAS_AUSENCIA) return false
        val desdeUltimo = dias + 1
        return if (dias >= Ritmo.DIAS_AUSENCIA_SEMANAL) desdeUltimo % 7 != 0 else desdeUltimo % 2 != 0
    }

    /** Un minuto del día lógico pasado ya: el minuto de ahora todavía cuenta (la alarma puede estar sonando). */
    private fun yaPasado(
        minuto: Int,
        ctx: Contexto,
    ): Boolean = orden(minuto, ctx) < orden(ctx.ahoraMin, ctx)

    /** La posición de un minuto de reloj dentro del día lógico, que empieza en [Contexto.corteDia]. */
    private fun orden(
        minuto: Int,
        ctx: Contexto,
    ): Int = if (minuto < ctx.corteDia) minuto + MINUTOS_DIA else minuto
}
