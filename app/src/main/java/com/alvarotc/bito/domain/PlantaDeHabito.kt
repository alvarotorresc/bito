package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay

/** La planta de un hábito en su ficha (pieza 3, §8.3). Solo vive en la ficha: el jardín de Hoy es global (D2). */
data class PlantaFicha(val especie: GardenSpecies, val etapa: GrowthStage, val cumplidos: Int)

/** Estable entre ejecuciones (`String.hashCode` lo es) y sin depender del orden ni de otros hábitos. */
fun especieDe(habitId: String): GardenSpecies = GardenSpecies.entries[Math.floorMod(habitId.hashCode(), GardenSpecies.entries.size)]

/** Periodos (días, semanas o meses; en abstinencia, días limpios) cumplidos desde que nació hasta hoy. */
fun periodosCumplidos(
    habit: Habit,
    state: DomainState,
    today: LogicalDay,
): Int {
    if (habit.createdOnDay > today) return 0
    val desde = LogicalDays.periodKeyOf(habit.createdOnDay, habit.period)
    val hasta = LogicalDays.periodKeyOf(today, habit.period)
    return (desde..hasta).count { Compliance.complianceOf(state, habit, it, today) == ComplianceStatus.FULFILLED }
}

/**
 * Especie por el id (decisión 2) y etapa con los umbrales del jardín aplicados a los periodos
 * cumplidos. Puede bajar si se corrige un día pasado: la ficha es el registro honesto del hábito.
 */
fun plantaDeHabito(
    habit: Habit,
    state: DomainState,
    today: LogicalDay,
): PlantaFicha {
    val n = periodosCumplidos(habit, state, today)
    return PlantaFicha(especieDe(habit.id), GardenEngine.stageFor(n.toDouble()), n)
}
