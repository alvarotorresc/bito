package com.alvarotc.bito.sesion

import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.sesion.SesionCerrada
import com.alvarotc.bito.data.sesion.SesionesRepository
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.sesion.FormatoSesion
import com.alvarotc.bito.domain.sesion.Instante
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.ResultadoSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.domain.sesion.Tramos
import com.alvarotc.bito.ui.notifications.QuickActionUseCase

/** Lo que el libro apuntó al marcar: los bitos del propio objetivo y si hay celebraciones esperando a Hoy. */
data class Marcado(val bitos: Int, val celebraciones: Boolean)

/**
 * Lo que la sesión escribe en los datos de siempre: marcar la tarea o el hábito, el intento y la
 * fila de `sesiones` (spec §7.6, §14.1, §15). Los bitos se leen del libro, no se calculan.
 */
class EfectosSesion(
    private val tasks: TasksRepository,
    private val habits: HabitsRepository,
    private val domainState: DomainStateRepository,
    private val sesiones: SesionesRepository,
    private val reconciler: PointsReconciler,
    private val acciones: QuickActionUseCase,
) {
    /** Tarea: `markDone` + `reconcile`. Hábito de marcar: un toque; de duración, el foco real al minuto (mínimo 1). */
    suspend fun marcar(
        objetivo: Objetivo,
        focoMillis: Long,
        hoy: LogicalDay,
        ahoraMillis: Long,
    ): Marcado {
        val antes = domainState.snapshot().pointsLedger.map { it.id }.toSet()
        var celebrar = false
        when (objetivo) {
            is Objetivo.Tarea -> {
                tasks.markDone(objetivo.id, hoy, ahoraMillis)
                celebrar = reconciler.reconcile(hoy, ahoraMillis).newBadges.isNotEmpty()
            }
            is Objetivo.Habito -> {
                val habito = habits.habit(objetivo.id) ?: return Marcado(0, false)
                val cantidad =
                    when {
                        habito.logMode == LogMode.BINARY || habito.metric == Metric.CHECK -> 1
                        habito.metric == Metric.COUNT -> habito.step
                        else -> minutosRedondeados(focoMillis)
                    }
                celebrar = acciones.log(objetivo.id, cantidad).perfectDayReached
            }
            Objetivo.Libre -> return Marcado(0, false)
        }
        val nuevos = domainState.snapshot().pointsLedger.filter { it.id !in antes }
        val propios =
            when (objetivo) {
                is Objetivo.Tarea -> nuevos.filter { it.reason == PointsReason.TASK_DONE && it.refId == "task:${objetivo.id}" }
                else -> nuevos.filter { it.reason == PointsReason.HABIT_DONE }
            }
        val celebraLibro = nuevos.any { it.reason == PointsReason.PERFECT_DAY || it.reason == PointsReason.STREAK_MILESTONE }
        return Marcado(propios.sumOf { it.delta }.coerceAtLeast(0), celebrar || celebraLibro)
    }

    /** Solo las tareas apuntan intento (`ATTEMPT`), que vale 0 bitos. */
    suspend fun intento(
        objetivo: Objetivo,
        hoy: LogicalDay,
        ahoraMillis: Long,
    ) {
        if (objetivo !is Objetivo.Tarea) return
        tasks.recordAttempt(objetivo.id, hoy, ahoraMillis)
        reconciler.reconcile(hoy, ahoraMillis)
    }

    /** La fila de `sesiones`, solo con un minuto de foco o más. */
    suspend fun guardarFila(
        r: RegistroSesion,
        instante: Instante,
        finPared: Long,
        resultado: ResultadoSesion,
    ) {
        if (instante.focoMillis < MILLIS_POR_MINUTO) return
        sesiones.guardar(
            SesionCerrada(
                id = r.id,
                objetivo = r.plan.objetivo,
                dia = r.inicioDia,
                inicioMillis = r.inicioPared,
                finMillis = finPared,
                minutosFoco = FormatoSesion.minutosDeFoco(instante.focoMillis),
                bloquesHechos = Tramos.de(r.plan).take(instante.tramo).count { it is Tramo.Foco },
                bloquesPlan = r.plan.bloques,
                resultado = resultado,
            ),
        )
    }

    suspend fun nombre(objetivo: Objetivo): String =
        when (objetivo) {
            is Objetivo.Tarea -> tasks.task(objetivo.id)?.title.orEmpty()
            is Objetivo.Habito -> habits.habit(objetivo.id)?.name.orEmpty()
            Objetivo.Libre -> ""
        }

    private companion object {
        const val MILLIS_POR_MINUTO = 60_000L

        fun minutosRedondeados(focoMillis: Long): Int = ((focoMillis + MILLIS_POR_MINUTO / 2) / MILLIS_POR_MINUTO).toInt().coerceAtLeast(1)
    }
}

/** Los minutos que «Tus {m} min cuentan» puede decir: 0 por debajo de un minuto («Otra vez será.»). */
internal fun minutosQueCuentan(focoMillis: Long): Int = if (focoMillis < 60_000L) 0 else FormatoSesion.minutosDeFoco(focoMillis)
