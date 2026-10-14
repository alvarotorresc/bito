package com.alvarotc.bito.ui.camino

import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.BadgeEngine
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.PerfectDays
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.BadgeDef
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.Progreso
import com.alvarotc.bito.ui.stats.NumbersUiState
import com.alvarotc.bito.ui.stats.RecordsUiState
import com.alvarotc.bito.ui.stats.StatsUiState
import com.alvarotc.bito.ui.stats.buildNumbersUiState
import com.alvarotc.bito.ui.stats.buildRecordsUiState
import com.alvarotc.bito.ui.stats.buildStatsUiState
import java.time.LocalDate
import java.time.ZoneId

/** Un logro tal como lo pinta Camino: su día de desbloqueo, lo que falta y el especial del jardín que da. */
data class LogroUi(
    val def: BadgeDef,
    val conseguido: LogicalDay?,
    val progreso: Progreso?,
    val especial: GardenSpecial?,
)

/** Todo lo que enseña Camino: lo de las cuatro pantallas de estadísticas de antes y los logros. */
data class CaminoUiState(
    val stats: StatsUiState = StatsUiState(),
    val records: RecordsUiState = RecordsUiState(),
    val numbers: NumbersUiState = NumbersUiState(),
    val logros: List<LogroUi> = emptyList(),
    val sinHabitos: Boolean = true,
    val loading: Boolean = true,
    // El día lógico de hoy como fecha: «lo que falta» y «El 1 de noviembre empieza otro mes entero.»
    // lo necesitan y CaminoContenido no tiene otra forma de saberlo. (No LocalDate.EPOCH: pide API 33.)
    val hoy: LocalDate = LocalDate.ofEpochDay(0),
) {
    val conseguidos: Int get() = logros.count { it.conseguido != null }
}

/** Pura: sin efectos, sin almacenamiento y sin leer el reloj. */
fun buildCaminoUiState(
    state: DomainState,
    personality: Personality,
    today: LogicalDay,
    owned: List<CustomizationItemEntity>,
    badges: List<BadgeEntity>,
    dayCutoffMinutes: Int,
    zone: ZoneId,
): CaminoUiState {
    val perfectDays = PerfectDays.perfectDaysUpTo(state, today)
    val desbloqueoPorId = badges.associate { it.badgeId to it.unlockedAtMillis }
    val logros =
        BadgeCatalog.all.map { def ->
            LogroUi(
                def = def,
                conseguido = desbloqueoPorId[def.id]?.let { diaLogico(it, dayCutoffMinutes, zone) },
                progreso = BadgeEngine.progressOf(def, state, today, perfectDays),
                especial = GardenSpecial.entries.firstOrNull { it.badgeId == def.id },
            )
        }
    return CaminoUiState(
        stats = buildStatsUiState(state, personality, today, owned, badges),
        records = buildRecordsUiState(state, today),
        numbers = buildNumbersUiState(state, today),
        logros = logros,
        sinHabitos = state.habits.isEmpty(),
        loading = false,
        hoy = LocalDate.ofEpochDay(today.toLong()),
    )
}

private fun diaLogico(
    millis: Long,
    dayCutoffMinutes: Int,
    zone: ZoneId,
): LogicalDay = LogicalDays.logicalDayOf(millis, dayCutoffMinutes, zone)
