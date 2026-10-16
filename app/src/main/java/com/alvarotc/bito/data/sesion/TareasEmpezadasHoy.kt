package com.alvarotc.bito.data.sesion

import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.TareasEmpezadas
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import java.time.ZoneId

/**
 * Las tareas con una sesión hoy, cerrada o viva (RF1): para Habi, algo en lo que ya se está no está «sin empezar».
 * El día lógico se recalcula con los ajustes y en cada tick de [ticks] (el corte del día llega con la app abierta,
 * sin que cambie ningún ajuste).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun tareasEmpezadasHoy(
    ajustes: Flow<Settings>,
    ticks: Flow<Unit>,
    ahora: () -> Long,
    zona: () -> ZoneId,
    cerradasDe: (LogicalDay) -> Flow<List<SesionCerrada>>,
    viva: Flow<RegistroSesion?>,
): Flow<Set<String>> =
    combine(ajustes, ticks) { a, _ -> LogicalDays.logicalDayOf(ahora(), a.dayCutoffMinutes, zona()) }
        .distinctUntilChanged()
        .flatMapLatest { dia -> combine(cerradasDe(dia), viva) { cerradas, v -> TareasEmpezadas.de(cerradas, v) } }
