package com.alvarotc.bito.ui.cierre

import com.alvarotc.bito.domain.model.BadgeDef
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.ui.today.HabitCardUi

/**
 * Lo que pinta el cierre del día. [filas] son las que el cierre pregunta hoy ([com.alvarotc.bito.ui.habla.reviewRowsOf]);
 * las que están en [decididas] se atenúan y dejan deshacer. [tareasManana] trae como mucho dos nombres y [tareasMas] las
 * demás: las tareas no se marcan en el cierre, solo pasan a mañana. [animo] es 0 bien, 1 regular o 2 duro, y es opcional.
 */
data class CierreUiState(
    val hoy: LogicalDay = 0,
    val filas: List<HabitCardUi> = emptyList(),
    val decididas: Set<String> = emptySet(),
    val tareasManana: List<String> = emptyList(),
    val tareasMas: Int = 0,
    val hechos: Int = 0,
    val total: Int = 0,
    val comentario: Propuesta? = null,
    val animo: Int? = null,
    val sellado: Boolean = false,
    val perfecto: Boolean = false,
    val bitosHoy: Int = 0,
    val rachasAvanzan: Int = 0,
    val logrosNuevos: List<BadgeDef> = emptyList(),
    val gordoSinHacer: List<String> = emptyList(),
    val loading: Boolean = true,
    /** Días anteriores sin sellar, para [CierreViewModel.sealPendingDays]. */
    val diasPendientes: List<LogicalDay> = emptyList(),
)
