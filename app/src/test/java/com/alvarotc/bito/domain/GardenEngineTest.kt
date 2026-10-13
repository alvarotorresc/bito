package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.TaskEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** El jardín del §6.1: agua proporcional, etapas exactas, sobrante y marca de agua máxima. */
class GardenEngineTest {
    private val bed = RealHabits.makeBed.createdOn(TODAY - 30)
    private val meditate = RealHabits.meditate.createdOn(TODAY - 30)
    private val noon: LocalDateTime = LocalDateTime.of(2026, 8, 14, 12, 0)
    private val delta = 1e-9

    @Test
    fun `a fully met day gives one unit of water and a half met day gives half`() {
        val half = domainState(habits = listOf(bed, meditate), entries = listOf(entryOn(bed, TODAY - 1)))
        val full = domainState(habits = listOf(bed, meditate), entries = listOf(entryOn(bed, TODAY - 1), entryOn(meditate, TODAY - 1)))

        assertEquals(0.5, GardenEngine.dayWater(half, TODAY - 1, TODAY)!!, delta)
        assertEquals(1.0, GardenEngine.dayWater(full, TODAY - 1, TODAY)!!, delta)
    }

    @Test
    fun `done tasks weigh at most a third of a day`() {
        val tasks = (1..5).map { task(id = "t$it", doneOnDay = TODAY - 1) }
        val state = domainState(habits = listOf(bed, meditate), tasks = tasks)

        assertEquals(1.0 / 3.0, GardenEngine.dayWater(state, TODAY - 1, TODAY)!!, delta)
    }

    @Test
    fun `a postponed task pulls the day down within the same cap`() {
        val postponed = taskEvent("t1", TaskEventKind.POSTPONED, TODAY - 1)
        val state =
            domainState(
                habits = listOf(bed),
                entries = listOf(entryOn(bed, TODAY - 1)),
                tasks = listOf(task(id = "t1")),
                taskEvents = listOf(postponed),
            )

        // Un hábito: el tope deja a las tareas medio voto (t·w ≤ h/2), así que 1 / (1 + 0,5).
        assertEquals(2.0 / 3.0, GardenEngine.dayWater(state, TODAY - 1, TODAY)!!, delta)
    }

    @Test
    fun `a day with nothing due, nothing completed and no task gives no water`() {
        val weekly = RealHabits.strengthTraining.createdOn(TODAY - 30)
        val state = domainState(habits = listOf(weekly), entries = listOf(entryOn(weekly, TODAY - 1)))

        assertNull(GardenEngine.dayWater(state, TODAY - 1, TODAY))
        assertEquals(0.0, GardenEngine.totalWater(state, TODAY), delta)
    }

    @Test
    fun `a weekly habit completed on a day waters that day`() {
        // Fuerza 3 veces por semana: lunes, miércoles y viernes de la semana pasada; se cumple el viernes.
        val weekly = RealHabits.strengthTraining.createdOn(TODAY - 30)
        val entries = entriesOn(weekly, listOf(LAST_MONDAY, LAST_WEDNESDAY, LAST_FRIDAY))
        val alone = domainState(habits = listOf(weekly), entries = entries)

        assertEquals(1, GardenEngine.weeklyCompletionsOn(alone, LAST_FRIDAY))
        assertEquals(1.0, GardenEngine.dayWater(alone, LAST_FRIDAY, TODAY)!!, delta)
        assertNull(GardenEngine.dayWater(alone, LAST_WEDNESDAY, TODAY))

        // Con un diario sin cumplir ese viernes, el semanal es un ítem cumplido más: 1 de 2.
        val withDaily = domainState(habits = listOf(weekly, bed), entries = entries)
        assertEquals(0.5, GardenEngine.dayWater(withDaily, LAST_FRIDAY, TODAY)!!, delta)
    }

    @Test
    fun `a user with only weekly habits still waters the garden`() {
        val weekly = RealHabits.strengthTraining.createdOn(TODAY - 30)
        val twoWeeks =
            entriesOn(weekly, listOf(TWO_WEEKS_AGO_MONDAY, TWO_WEEKS_AGO_MONDAY + 2, TWO_WEEKS_AGO_MONDAY + 4)) +
                entriesOn(weekly, listOf(LAST_MONDAY, LAST_WEDNESDAY, LAST_FRIDAY))
        val state = domainState(habits = listOf(weekly), entries = twoWeeks)

        assertEquals(2.0, GardenEngine.totalWater(state, TODAY), delta)
    }

    @Test
    fun `tasks alone count as something scheduled but the third cap leaves them dry`() {
        val weekly = RealHabits.strengthTraining.createdOn(TODAY - 30)
        val state = domainState(habits = listOf(weekly), tasks = listOf(task(id = "t1", doneOnDay = TODAY - 1)))

        assertEquals(0.0, GardenEngine.dayWater(state, TODAY - 1, TODAY)!!, delta)
    }

    @Test
    fun `total water adds up every day since the first habit`() {
        val young = RealHabits.makeBed.createdOn(TODAY - 4)
        val state = domainState(habits = listOf(young), entries = entriesOn(young, listOf(TODAY - 4, TODAY - 2, TODAY)))

        assertEquals(3.0, GardenEngine.totalWater(state, TODAY), delta)
    }

    @Test
    fun `stage thresholds are exact at 3, 8, 15 and 25`() {
        assertEquals(GrowthStage.SEED, GardenEngine.stageFor(2.99))
        assertEquals(GrowthStage.SPROUT, GardenEngine.stageFor(3.0))
        assertEquals(GrowthStage.SPROUT, GardenEngine.stageFor(7.99))
        assertEquals(GrowthStage.PLANT, GardenEngine.stageFor(8.0))
        assertEquals(GrowthStage.PLANT, GardenEngine.stageFor(14.99))
        assertEquals(GrowthStage.FLOWERS, GardenEngine.stageFor(15.0))
        assertEquals(GrowthStage.FLOWERS, GardenEngine.stageFor(24.99))
        assertEquals(GrowthStage.TREE, GardenEngine.stageFor(25.0))
    }

    @Test
    fun `nine thirds reach a sprout despite floating point`() {
        val nineThirds = (1..9).sumOf { 1.0 / 3.0 }
        assertEquals(GrowthStage.SPROUT, GardenEngine.stageFor(nineThirds))
        // Y una suma que sí se queda un pelo por debajo del umbral también cuenta.
        assertEquals(GrowthStage.SPROUT, GardenEngine.stageFor(3.0 - 1e-9))
    }

    @Test
    fun `the next plot opens only with the previous one a tree and takes the overflow`() {
        assertEquals(listOf(GrowthStage.FLOWERS), GardenEngine.plotsFor(24.9).map { it.stage })
        assertEquals(listOf(GrowthStage.TREE, GrowthStage.SEED), GardenEngine.plotsFor(25.0).map { it.stage })
        assertEquals(listOf(GrowthStage.TREE, GrowthStage.PLANT), GardenEngine.plotsFor(33.0).map { it.stage })
        assertEquals(listOf(GardenSpecies.ROUND_LEAVES, GardenSpecies.SUNFLOWERS), GardenEngine.plotsFor(33.0).map { it.species })
    }

    @Test
    fun `six trees fill the garden and more water opens nothing else`() {
        val full = GardenEngine.plotsFor(150.0)
        assertEquals(GardenSpecies.entries, full.map { it.species })
        assertTrue(full.all { it.stage == GrowthStage.TREE })
        assertEquals(full, GardenEngine.plotsFor(1000.0))
        assertTrue(GardenEngine.gardenOf(domainState(), TODAY, noon, null, emptySet(), waterHighMark = 150.0).full)
    }

    @Test
    fun `the high mark keeps a stage that the corrected history no longer reaches`() {
        val state = domainState(habits = listOf(bed), entries = entriesOn(bed, listOf(TODAY - 2, TODAY - 1)))

        val garden = GardenEngine.gardenOf(state, TODAY, noon, TODAY - 1, emptySet(), waterHighMark = 9.0)

        assertEquals(9.0, garden.water, delta)
        assertEquals(GrowthStage.PLANT, garden.plots.single().stage)
    }

    @Test
    fun `the high mark never lowers water the history already has`() {
        val state = domainState(habits = listOf(bed), entries = entriesOn(bed, (TODAY - 11)..TODAY))

        assertEquals(12.0, GardenEngine.gardenOf(state, TODAY, noon, TODAY, emptySet(), waterHighMark = 9.0).water, delta)
    }
}
