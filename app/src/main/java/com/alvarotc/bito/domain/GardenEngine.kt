package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.GardenLight
import com.alvarotc.bito.domain.model.GardenPlot
import com.alvarotc.bito.domain.model.GardenSeason
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LightPhase
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.TaskEventKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * El jardín de Habi (pieza 1 §6.1), puro: se deduce del historial, del reloj inyectado y de la
 * marca de agua máxima que guarda DataStore. No escribe nada.
 */
object GardenEngine {
    /** Agua de una parcela en árbol; el sobrante pasa a la siguiente. */
    const val TREE_WATER = 25.0
    const val MAX_WILT = 3

    // Guarda contra el error de redondeo acumulado al sumar fracciones (tercios, mitades...).
    private const val EPSILON = 1e-6

    fun gardenOf(
        state: DomainState,
        today: LogicalDay,
        now: LocalDateTime,
        lastActivityDay: LogicalDay?,
        unlockedBadges: Set<String>,
        waterHighMark: Double,
    ): GardenState {
        val water = maxOf(totalWater(state, today), waterHighMark)
        return GardenState(
            plots = plotsFor(water),
            water = water,
            wilt = wiltOf(today, lastActivityDay),
            light = lightAt(now.toLocalTime()),
            season = seasonOf(now.toLocalDate()),
            specials = specialsOf(unlockedBadges),
        )
    }

    /**
     * Los hábitos semanales que se cumplen justo en [day] (decisión del arquitecto, 2026-10-13): un
     * «al menos N» el primer día exigible en que el progreso de su semana llega al objetivo; uno de
     * límite o abstinencia, el último día de la semana si la semana queda cumplida.
     */
    fun weeklyCompletionsOn(
        state: DomainState,
        day: LogicalDay,
    ): Int = state.habits.count { it.period == Period.WEEK && weeklyCompletionDay(state, it, day) == day }

    private fun weeklyCompletionDay(
        state: DomainState,
        habit: Habit,
        day: LogicalDay,
    ): LogicalDay? {
        val week = LogicalDays.periodKeyOf(day, Period.WEEK)
        val lastDay = LogicalDays.daysOf(week, Period.WEEK).last
        val requirable = Compliance.requirableDaysOf(state, habit, week)
        if (requirable.isEmpty()) return null
        if (habit.direction != Direction.AT_LEAST) {
            return lastDay.takeIf { Compliance.complianceOf(state, habit, week, day) == ComplianceStatus.FULFILLED }
        }
        val target = Compliance.targetOf(state, habit, lastDay)
        val entries = state.entries.filter { it.habitId == habit.id && it.logicalDay in requirable }
        return requirable.firstOrNull { d -> Compliance.progressOf(habit, entries.filter { it.logicalDay <= d }) >= target }
    }

    /**
     * El agua de [day], de 0 a 1: ítems de hábito cumplidos sobre exigibles, donde los ítems son los
     * hábitos diarios exigibles más los semanales que se cumplen ese día (cada uno, exigible y
     * cumplido), con las tareas matizando como en [MoodEngine.moodOf] (como mucho un positivo y un
     * negativo por tarea y día, y en bloque nunca más de un tercio: t·w ≤ h/2; sin ítems de hábito,
     * el tope las deja en 0). Null solo si no había nada: ni diario exigible, ni semanal cumplido,
     * ni tarea.
     */
    fun dayWater(
        state: DomainState,
        day: LogicalDay,
        today: LogicalDay,
    ): Double? {
        val demandable = state.habits.filter { it.period == Period.DAY && Compliance.isRequirableOn(state, it, day) }
        val weekly = weeklyCompletionsOn(state, day)
        val fulfilled = demandable.count { Compliance.complianceOf(state, it, day, today) == ComplianceStatus.FULFILLED } + weekly
        val positives =
            (
                state.tasks.filter { it.doneOnDay == day }.map { it.id } +
                    state.taskEvents.filter { it.kind == TaskEventKind.ATTEMPT && it.logicalDay == day }.map { it.taskId }
            ).toSet().size
        val negatives =
            state.taskEvents.filter { it.kind == TaskEventKind.POSTPONED && it.logicalDay == day }.map { it.taskId }.toSet().size
        val units = positives + negatives
        val habits = (demandable.size + weekly).toDouble()
        if (habits == 0.0) return if (units == 0) null else 0.0
        val weight = if (units == 0) 0.0 else minOf(1.0, habits / 2.0 / units)
        return (fulfilled + positives * weight) / (habits + units * weight)
    }

    /** El agua acumulada desde el primer hábito hasta [today], incluido (el día en curso riega lo que lleve). */
    fun totalWater(
        state: DomainState,
        today: LogicalDay,
    ): Double {
        val first = state.habits.minOfOrNull { it.createdOnDay } ?: return 0.0
        if (first > today) return 0.0
        return (first..today).sumOf { dayWater(state, it, today) ?: 0.0 }
    }

    fun stageFor(water: Double): GrowthStage = GrowthStage.entries.last { water + EPSILON >= it.minWater }

    /** Las parcelas abiertas: la primera siempre; cada siguiente, solo con la anterior en árbol. */
    fun plotsFor(water: Double): List<GardenPlot> =
        GardenSpecies.entries.mapIndexedNotNull { index, species ->
            val start = index * TREE_WATER
            if (index > 0 && water + EPSILON < start) {
                null
            } else {
                GardenPlot(species, stageFor((water - start).coerceIn(0.0, TREE_WATER)))
            }
        }

    private const val MINUTES_PER_HOUR = 60
    private const val HALF_TRANSITION_MINUTES = 15f

    // Fronteras del día en minutos, con la fase que termina y la que empieza.
    private val BORDERS =
        listOf(
            6 * MINUTES_PER_HOUR to (LightPhase.NOCHE to LightPhase.MANANA),
            12 * MINUTES_PER_HOUR to (LightPhase.MANANA to LightPhase.DIA),
            17 * MINUTES_PER_HOUR to (LightPhase.DIA to LightPhase.TARDE),
            21 * MINUTES_PER_HOUR to (LightPhase.TARDE to LightPhase.NOCHE),
        )

    /**
     * Mustio de 0 a [MAX_WILT] por días naturales (decisión del arquitecto, 2026-10-13): 0 si hubo
     * actividad ayer o hoy y un nivel más por cada día natural sin actividad, hasta 3. Usa la misma
     * actividad ([StatsEngine.lastActivityDay]) y el mismo conteo que [MoodEngine] para DRAMATIC
     * (hoy − última ≥ 3), así que el mustio llega a 2 justo cuando el ánimo pasa a dramático. Los
     * días sin nada programado cuentan. Sin historia ninguna, 0: un jardín nuevo no está mustio.
     */
    fun wiltOf(
        today: LogicalDay,
        lastActivityDay: LogicalDay?,
    ): Int {
        val last = lastActivityDay ?: return 0
        return (today - last - 1).coerceIn(0, MAX_WILT)
    }

    fun lightAt(time: LocalTime): GardenLight {
        val minute = time.toSecondOfDay() / 60f
        val phase = phaseAt(minute)
        for ((border, change) in BORDERS) {
            val offset = minute - border
            if (offset >= -HALF_TRANSITION_MINUTES && offset < HALF_TRANSITION_MINUTES) {
                val progress = (offset + HALF_TRANSITION_MINUTES) / (2 * HALF_TRANSITION_MINUTES)
                return GardenLight(phase, change.first, change.second, progress)
            }
        }
        return GardenLight(phase, phase, phase, 0f)
    }

    private fun phaseAt(minute: Float): LightPhase =
        when {
            minute < 6 * MINUTES_PER_HOUR -> LightPhase.NOCHE
            minute < 12 * MINUTES_PER_HOUR -> LightPhase.MANANA
            minute < 17 * MINUTES_PER_HOUR -> LightPhase.DIA
            minute < 21 * MINUTES_PER_HOUR -> LightPhase.TARDE
            else -> LightPhase.NOCHE
        }

    /** Fechas astronómicas aproximadas: 21 de marzo, 21 de junio, 23 de septiembre y 21 de diciembre. */
    fun seasonOf(date: LocalDate): GardenSeason {
        val day = date.monthValue * 100 + date.dayOfMonth
        return when {
            day >= 1221 || day < 321 -> GardenSeason.WINTER
            day < 621 -> GardenSeason.SPRING
            day < 923 -> GardenSeason.SUMMER
            else -> GardenSeason.AUTUMN
        }
    }

    fun specialsOf(unlockedBadges: Set<String>): Set<GardenSpecial> =
        GardenSpecial.entries.filterTo(mutableSetOf()) { it.badgeId in unlockedBadges }
}
