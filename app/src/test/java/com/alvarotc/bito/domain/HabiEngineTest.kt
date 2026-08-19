package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Entry
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Test

class HabiEngineTest {
    /** [habit]'s weekly target reached on [habit.target] distinct days starting [monday] — one fulfilled week. */
    private fun weekOf(
        habit: Habit,
        monday: LogicalDay,
    ): List<Entry> = entriesOn(habit, (0 until habit.target).map { monday + it })

    @Test
    fun `a 7-day streak earns the sparks pattern and nothing else`() {
        val habit = RealHabits.all.first { it.metric == Metric.CHECK && it.period == Period.DAY && it.direction == Direction.AT_LEAST }
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, (TODAY - 6)..TODAY))
        assertEquals(setOf("pattern-chispas"), HabiEngine.earnedExclusives(state, TODAY))
    }

    @Test
    fun `milestones from different habits union into one earned set`() {
        val sevenDayHabit =
            RealHabits.all.first { it.metric == Metric.CHECK && it.period == Period.DAY && it.direction == Direction.AT_LEAST }
        val thirtyDayHabit = RealHabits.meditate
        assertEquals(
            setOf("pattern-chispas"),
            HabiEngine.earnedExclusives(
                domainState(habits = listOf(sevenDayHabit), entries = entriesOn(sevenDayHabit, (TODAY - 6)..TODAY)),
                TODAY,
            ),
        )
        val state =
            domainState(
                habits = listOf(sevenDayHabit, thirtyDayHabit),
                entries = entriesOn(sevenDayHabit, (TODAY - 6)..TODAY) + entriesOn(thirtyDayHabit, (TODAY - 29)..TODAY),
            )
        assertEquals(setOf("pattern-chispas", "pattern-llamas"), HabiEngine.earnedExclusives(state, TODAY))
    }

    @Test
    fun `weekly habits earn milestones in week units`() {
        val habit = RealHabits.all.first { it.period == Period.WEEK && it.direction == Direction.AT_LEAST }
        val entries = (1..7).flatMap { weekOf(habit, LAST_MONDAY - (it - 1) * 7) }
        val state = domainState(habits = listOf(habit), entries = entries)
        assertEquals(setOf("pattern-chispas"), HabiEngine.earnedExclusives(state, TODAY))
    }

    @Test
    fun `missing exclusives never proposes revoking owned ones`() {
        assertEquals(emptySet<String>(), HabiEngine.missingExclusives(emptySet(), setOf("upper-corona")))
        assertEquals(setOf("pattern-chispas"), HabiEngine.missingExclusives(setOf("pattern-chispas"), emptySet()))
    }

    @Test
    fun `store state ladder - equipped beats owned beats price beats lock`() {
        val lavanda = HabiCatalog.byId("body-lavanda")!!
        val corona = HabiCatalog.byId("upper-corona")!!
        assertEquals(
            StoreItemState.Equipped,
            HabiEngine.storeStateOf(lavanda, setOf("body-lavanda"), EquippedSet(bodyColor = "body-lavanda"), 0),
        )
        assertEquals(StoreItemState.Owned, HabiEngine.storeStateOf(lavanda, setOf("body-lavanda"), EquippedSet(), 0))
        assertEquals(StoreItemState.Affordable, HabiEngine.storeStateOf(lavanda, emptySet(), EquippedSet(), 20))
        assertEquals(StoreItemState.MissingPoints(5), HabiEngine.storeStateOf(lavanda, emptySet(), EquippedSet(), 15))
        assertEquals(StoreItemState.Locked(100), HabiEngine.storeStateOf(corona, emptySet(), EquippedSet(), 999))
    }

    @Test
    fun `defaults are owned without any row`() {
        val salvia = HabiCatalog.byId("body-salvia")!!
        assertEquals(StoreItemState.Equipped, HabiEngine.storeStateOf(salvia, emptySet(), EquippedSet(), 0))
        assertEquals(StoreItemState.Owned, HabiEngine.storeStateOf(salvia, emptySet(), EquippedSet(bodyColor = "body-lavanda"), 0))
    }
}
