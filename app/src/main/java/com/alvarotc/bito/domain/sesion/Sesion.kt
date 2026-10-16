package com.alvarotc.bito.domain.sesion

import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.model.LogicalDay

/** A qué va la sesión: una tarea, un hábito o nada en concreto (spec §21.3). */
sealed interface Objetivo {
    data class Tarea(val id: String) : Objetivo

    data class Habito(val id: String) : Objetivo

    data object Libre : Objetivo
}

/** Cómo se entra al primer foco: con un minuto de respiración o directo. */
enum class Entrada { RESPIRAR, DIRECTO }

/** Lo que se elige en Preparar. Nada empieza hasta que se guarda como [RegistroSesion]. */
data class PlanSesion(
    val objetivo: Objetivo,
    /** 5..180, de 5 en 5 (salvo `pomo_focus`, 5..90). */
    val minutosFoco: Int,
    val entrada: Entrada,
    /** 1..8. */
    val bloques: Int,
    val descansoCorto: Int,
    val descansoLargo: Int,
    val rondas: Int,
    /** `pomo_auto_break`: con false el descanso espera a [Evento.EmpezarDescanso] (spec §21.5). */
    val descansoSolo: Boolean,
    val fondo: SessionBackground,
    val noMolestar: Boolean,
)

/** Un trozo de la secuencia de la sesión. */
sealed interface Tramo {
    /** Calmarme para entrar: 6 ciclos de 4 + 6 s, 60 s. */
    data class Respirar(val ciclos: Int = 6) : Tramo

    data class Foco(val bloque: Int, val minutos: Int) : Tramo

    data class Descanso(val trasBloque: Int, val largo: Boolean, val minutos: Int) : Tramo
}

/** La única fuente de la secuencia de la sesión (spec §7.2). */
object Tramos {
    fun de(plan: PlanSesion): List<Tramo> =
        buildList {
            if (plan.entrada == Entrada.RESPIRAR) add(Tramo.Respirar())
            val n = plan.bloques
            for (b in 1..n) {
                add(Tramo.Foco(b, plan.minutosFoco))
                val cierraRonda = b % plan.rondas == 0
                if (b < n) {
                    add(Tramo.Descanso(b, cierraRonda, if (cierraRonda) plan.descansoLargo else plan.descansoCorto))
                } else if (cierraRonda) {
                    // Si el último bloque cierra una ronda, el descanso largo va antes de Terminada.
                    add(Tramo.Descanso(b, largo = true, minutos = plan.descansoLargo))
                }
            }
        }
}

/** Lo que pasa durante la sesión. [enMillis]: ms de sesión desde Empezar (no reloj de pared). */
sealed interface Evento {
    val enMillis: Long

    data class Pausa(override val enMillis: Long) : Evento

    data class Seguir(override val enMillis: Long) : Evento

    /** Suma minutos al tramo de foco en curso. */
    data class Sumar(override val enMillis: Long, val minutos: Int) : Evento

    /** Acaba el Respirar o el Descanso en curso. */
    data class Saltar(override val enMillis: Long) : Evento

    /** Arranca el descanso que espera, con `descansoSolo = false` (spec §21.5). */
    data class EmpezarDescanso(override val enMillis: Long) : Evento
}

/** La sesión en marcha tal como vive en disco: el plan, lo que ha pasado y dónde empezó. */
data class RegistroSesion(
    /** UUID. */
    val id: String,
    val plan: PlanSesion,
    val eventos: List<Evento>,
    /** currentTimeMillis al Empezar. */
    val inicioPared: Long,
    /** elapsedRealtime al Empezar. */
    val anclaElapsed: Long,
    /** `FocusClock.bootSignatureOf(pared, elapsed)` al Empezar. */
    val arranque: Long,
    /** getCurrentInterruptionFilter() antes de poner PRIORITY; null = Bito no lo tocó. */
    val filtroPrevio: Int?,
    val inicioDia: LogicalDay,
    /** `Settings.Global.BOOT_COUNT` al Empezar; null si el sistema no lo dio. */
    val arranques: Int? = null,
)

/**
 * Los dos relojes leídos a la vez, con el contador de arranques del sistema ([arranques], null si
 * no se pudo leer). En Android se construye con `ahoraDelSistema(context)`.
 */
data class Ahora(
    val paredMillis: Long,
    val elapsedMillis: Long,
    val arranques: Int? = null,
)

/** Dónde está la sesión en un momento dado. Lo calcula [RelojSesion], nadie lo guarda. */
data class Instante(
    /** Índice en `Tramos.de(plan)`; == size cuando todo está hecho. */
    val tramo: Int,
    /** null = Terminada. */
    val actual: Tramo?,
    val restaMillis: Long,
    /** Con los [Evento.Sumar] ya aplicados. */
    val duracionTramoMillis: Long,
    /** Foco real, sin pausas ni descansos ni respirar. */
    val focoMillis: Long,
    val pausado: Boolean,
    /** Descanso parado hasta [Evento.EmpezarDescanso] (spec §21.5). */
    val esperandoDescanso: Boolean,
    /** Solo en [Tramo.Respirar]. */
    val respiracion: BreathingRhythm.Point?,
    val transcurridoMillis: Long,
    /** ms de sesión de la próxima frontera; null si pausado, esperando o Terminada. */
    val finTramoEnSesion: Long?,
)

enum class ResultadoSesion { HECHA, SIGO_LUEGO, LO_DEJO, CAMBIADA, SIN_RESPUESTA }
