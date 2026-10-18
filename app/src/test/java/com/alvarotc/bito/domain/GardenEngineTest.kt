package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.GardenLight
import com.alvarotc.bito.domain.model.GardenSeason
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.LightPhase
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.TaskEventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
    fun `the stage epsilon absorbs sums that fall just short of a threshold`() {
        val nineThirds = (1..9).sumOf { 1.0 / 3.0 }
        assertEquals(GrowthStage.SPROUT, GardenEngine.stageFor(nineThirds))
        // Y una suma que sí se queda un pelo por debajo del umbral cuenta como alcanzada.
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

    @Test
    fun `a weekly limit habit waters only the last day of its week and only if still fulfilled`() {
        val limit = RealHabits.foodDelivery.createdOn(TODAY - 30)
        val week = LAST_MONDAY..LAST_SUNDAY
        val withinLimit = domainState(habits = listOf(limit), entries = listOf(entryOn(limit, LAST_WEDNESDAY)), sealedDays = week.toList())

        assertEquals(0, GardenEngine.weeklyCompletionsOn(withinLimit, LAST_FRIDAY))
        assertEquals(1, GardenEngine.weeklyCompletionsOn(withinLimit, LAST_SUNDAY))
        assertEquals(1.0, GardenEngine.dayWater(withinLimit, LAST_SUNDAY, TODAY)!!, delta)
        assertNull(GardenEngine.dayWater(withinLimit, LAST_FRIDAY, TODAY))

        // Pasarse del límite durante la semana: ya no se cumple ese último día.
        val over = withinLimit.copy(entries = withinLimit.entries + entryOn(limit, LAST_FRIDAY))
        assertEquals(0, GardenEngine.weeklyCompletionsOn(over, LAST_SUNDAY))
        assertNull(GardenEngine.dayWater(over, LAST_SUNDAY, TODAY))

        // Sin sellar los días de la semana, tampoco se cumple.
        val unsealed = withinLimit.copy(daySeals = emptyList())
        assertEquals(0, GardenEngine.weeklyCompletionsOn(unsealed, LAST_SUNDAY))
    }

    // -----------------------------------------------------------------------
    // Mustio
    // -----------------------------------------------------------------------

    @Test
    fun `activity yesterday or today keeps the garden fresh`() {
        assertEquals(0, GardenEngine.wiltOf(TODAY, TODAY))
        assertEquals(0, GardenEngine.wiltOf(TODAY, TODAY - 1))
    }

    @Test
    fun `each silent natural day adds one level up to three`() {
        assertEquals(1, GardenEngine.wiltOf(TODAY, TODAY - 2))
        assertEquals(2, GardenEngine.wiltOf(TODAY, TODAY - 3))
        assertEquals(3, GardenEngine.wiltOf(TODAY, TODAY - 4))
        assertEquals(3, GardenEngine.wiltOf(TODAY, TODAY - 20))
    }

    @Test
    fun `wilt level follows the same silent-day count as MoodEngine DRAMATIC`() {
        // Días pausados o sin nada programado también cuentan: mustio y ánimo van por días naturales.
        val state = domainState(habits = listOf(bed), pauses = listOf(pauseOn(bed, TODAY - 10)))
        for (gap in 0..10) {
            val last = TODAY - gap
            val wilt = GardenEngine.wiltOf(TODAY, last)
            assertEquals("gap $gap", (gap - 1).coerceIn(0, GardenEngine.MAX_WILT), wilt)
            val dramatic = MoodEngine.moodOf(state, TODAY, last) == Mood.DRAMATIC
            assertEquals("gap $gap", dramatic, wilt >= 2)
        }
    }

    @Test
    fun `a state without any history is never wilted`() {
        assertEquals(0, GardenEngine.wiltOf(TODAY, null))
    }

    @Test
    fun `one record today revives a fully wilted garden`() {
        val silent = domainState(habits = listOf(bed), entries = listOf(entryOn(bed, TODAY - 10)))
        assertEquals(3, GardenEngine.wiltOf(TODAY, StatsEngine.lastActivityDay(silent)))

        val revived = silent.copy(entries = silent.entries + entryOn(bed, TODAY))
        assertEquals(0, GardenEngine.wiltOf(TODAY, StatsEngine.lastActivityDay(revived)))
    }

    @Test
    fun `the garden reads activity exactly like the mood does`() {
        // Solo tareas ayer: para el ánimo hubo actividad, así que el jardín tampoco se mustia.
        val tasksOnly = domainState(habits = listOf(bed), tasks = listOf(task(id = "t1", doneOnDay = TODAY - 1)))
        val last = StatsEngine.lastActivityDay(tasksOnly)
        assertEquals(0, GardenEngine.wiltOf(TODAY, last))
        assertNotEquals(Mood.DRAMATIC, MoodEngine.moodOf(tasksOnly, TODAY, last))

        // Una abstinencia cumplida no deja registros: tras la última recaída, para los dos es silencio.
        val abstinent = RealHabits.noSmoking.createdOn(TODAY - 30)
        val quiet = domainState(habits = listOf(abstinent), entries = listOf(entryOn(abstinent, TODAY - 6)))
        val quietLast = StatsEngine.lastActivityDay(quiet)
        assertEquals(3, GardenEngine.wiltOf(TODAY, quietLast))
        assertEquals(Mood.DRAMATIC, MoodEngine.moodOf(quiet, TODAY, quietLast))
    }

    // -----------------------------------------------------------------------
    // Luz
    // -----------------------------------------------------------------------

    private fun light(
        h: Int,
        m: Int,
    ): GardenLight = GardenEngine.lightAt(LocalTime.of(h, m))

    @Test
    fun `each phase holds steady away from its borders`() {
        assertEquals(GardenLight(LightPhase.NOCHE, LightPhase.NOCHE, LightPhase.NOCHE, 0f), light(2, 0))
        assertEquals(GardenLight(LightPhase.MANANA, LightPhase.MANANA, LightPhase.MANANA, 0f), light(9, 0))
        assertEquals(GardenLight(LightPhase.DIA, LightPhase.DIA, LightPhase.DIA, 0f), light(14, 0))
        assertEquals(GardenLight(LightPhase.TARDE, LightPhase.TARDE, LightPhase.TARDE, 0f), light(19, 0))
        assertEquals(GardenLight(LightPhase.NOCHE, LightPhase.NOCHE, LightPhase.NOCHE, 0f), light(23, 0))
    }

    @Test
    fun `the borders at 6, 12, 17 and 21 fade over half an hour`() {
        assertEquals(light(5, 44).transition, 0f, 0f)
        assertEquals(GardenLight(LightPhase.NOCHE, LightPhase.NOCHE, LightPhase.MANANA, 0f), light(5, 45))
        assertEquals(GardenLight(LightPhase.MANANA, LightPhase.NOCHE, LightPhase.MANANA, 0.5f), light(6, 0))
        assertEquals(29f / 30f, light(6, 14).transition, 1e-4f)
        assertEquals(GardenLight(LightPhase.MANANA, LightPhase.MANANA, LightPhase.MANANA, 0f), light(6, 15))
        assertEquals(GardenLight(LightPhase.DIA, LightPhase.MANANA, LightPhase.DIA, 0.5f), light(12, 0))
        assertEquals(GardenLight(LightPhase.TARDE, LightPhase.DIA, LightPhase.TARDE, 0.5f), light(17, 0))
        assertEquals(GardenLight(LightPhase.NOCHE, LightPhase.TARDE, LightPhase.NOCHE, 0.5f), light(21, 0))
    }

    // -----------------------------------------------------------------------
    // Estación y especiales
    // -----------------------------------------------------------------------

    @Test
    fun `seasons turn on the astronomical dates`() {
        fun season(
            m: Int,
            d: Int,
        ) = GardenEngine.seasonOf(LocalDate.of(2026, m, d))
        assertEquals(GardenSeason.WINTER, season(3, 20))
        assertEquals(GardenSeason.SPRING, season(3, 21))
        assertEquals(GardenSeason.SPRING, season(6, 20))
        assertEquals(GardenSeason.SUMMER, season(6, 21))
        assertEquals(GardenSeason.SUMMER, season(9, 22))
        assertEquals(GardenSeason.AUTUMN, season(9, 23))
        assertEquals(GardenSeason.AUTUMN, season(12, 20))
        assertEquals(GardenSeason.WINTER, season(12, 21))
        assertEquals(GardenSeason.WINTER, season(1, 15))
    }

    @Test
    fun `each special comes from its own badge and the rest plant nothing`() {
        assertEquals(setOf(GardenSpecial.LANTERN), GardenEngine.specialsOf(setOf("streak-7")))
        assertEquals(setOf(GardenSpecial.STONE_PATH), GardenEngine.specialsOf(setOf("first-week")))
        assertEquals(setOf(GardenSpecial.POND), GardenEngine.specialsOf(setOf("perfect-month")))
        assertEquals(setOf(GardenSpecial.MUSHROOMS), GardenEngine.specialsOf(setOf("resurrection")))
        assertEquals(setOf(GardenSpecial.BIG_CHERRY), GardenEngine.specialsOf(setOf("perfect-days-100")))
        assertEquals(setOf(GardenSpecial.GOLDEN_TREE), GardenEngine.specialsOf(setOf("streak-365")))
        assertEquals(emptySet<GardenSpecial>(), GardenEngine.specialsOf(setOf("first-habit", "streak-30", "streak-100")))
    }

    @Test
    fun `gardenOf wires light, season, wilt and specials from its inputs`() {
        val evening = LocalDateTime.of(2026, 10, 20, 19, 0)
        val garden = GardenEngine.gardenOf(domainState(habits = listOf(bed)), TODAY, evening, TODAY - 3, setOf("streak-7"), 0.0)
        assertEquals(LightPhase.TARDE, garden.light.phase)
        assertEquals(GardenSeason.AUTUMN, garden.season)
        assertEquals(2, garden.wilt)
        assertEquals(setOf(GardenSpecial.LANTERN), garden.specials)
    }

    @Test
    fun `placed garden objects reach the garden by key and anything else is ignored`() {
        val garden =
            GardenEngine.gardenOf(
                domainState(),
                TODAY,
                noon,
                null,
                emptySet(),
                waterHighMark = 0.0,
                placedObjectIds = listOf("garden-regadera", "head-boina", "garden-desconocido"),
            )
        assertEquals(setOf("regadera"), garden.objects)
        assertTrue(GardenEngine.gardenOf(domainState(), TODAY, noon, null, emptySet(), waterHighMark = 0.0).objects.isEmpty())
    }
}
