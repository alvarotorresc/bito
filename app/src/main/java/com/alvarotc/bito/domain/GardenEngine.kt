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

    // Provisionales hasta la Tarea 6, que las sustituye con sus tests.
    fun wiltOf(
        today: LogicalDay,
        lastActivityDay: LogicalDay?,
    ): Int = 0

    fun lightAt(time: LocalTime): GardenLight = GardenLight(LightPhase.DIA, LightPhase.DIA, LightPhase.DIA, 0f)

    fun seasonOf(date: LocalDate): GardenSeason = GardenSeason.SUMMER

    fun specialsOf(unlockedBadges: Set<String>): Set<GardenSpecial> = emptySet()
}
