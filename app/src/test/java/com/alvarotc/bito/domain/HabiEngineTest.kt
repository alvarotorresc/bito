package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Entry
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HabiEngineTest {
    private val august = LocalDate.of(2026, 8, 14)

    /** [habit]'s weekly target reached on [habit.target] distinct days starting [monday] — one fulfilled week. */
    private fun weekOf(
        habit: Habit,
        monday: LogicalDay,
    ): List<Entry> = entriesOn(habit, (0 until habit.target).map { monday + it })

    @Test
    fun `a 7-day streak earns the flower and nothing else`() {
        val habit = RealHabits.all.first { it.metric == Metric.CHECK && it.period == Period.DAY && it.direction == Direction.AT_LEAST }
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, (TODAY - 6)..TODAY))
        assertEquals(setOf("head-flor"), HabiEngine.earnedExclusives(state, TODAY))
    }

    @Test
    fun `milestones from different habits union into one earned set`() {
        val sevenDayHabit =
            RealHabits.all.first { it.metric == Metric.CHECK && it.period == Period.DAY && it.direction == Direction.AT_LEAST }
        val thirtyDayHabit = RealHabits.meditate
        assertEquals(
            setOf("head-flor"),
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
        assertEquals(setOf("head-flor", "neck-panuelo-brasa"), HabiEngine.earnedExclusives(state, TODAY))
    }

    @Test
    fun `weekly habits earn milestones in week units`() {
        val habit = RealHabits.all.first { it.period == Period.WEEK && it.direction == Direction.AT_LEAST }
        val entries = (1..7).flatMap { weekOf(habit, LAST_MONDAY - (it - 1) * 7) }
        val state = domainState(habits = listOf(habit), entries = entries)
        assertEquals(setOf("head-flor"), HabiEngine.earnedExclusives(state, TODAY))
    }

    @Test
    fun `missing exclusives never proposes revoking owned ones`() {
        assertEquals(emptySet<String>(), HabiEngine.missingExclusives(emptySet(), setOf("head-corona")))
        assertEquals(setOf("head-flor"), HabiEngine.missingExclusives(setOf("head-flor"), emptySet()))
    }

    @Test
    fun `store state ladder - equipped beats owned beats price beats lock`() {
        val lavanda = HabiCatalog.byId("body-lavanda")!!
        val corona = HabiCatalog.byId("head-corona")!!
        assertEquals(
            StoreItemState.Equipped,
            HabiEngine.storeStateOf(lavanda, setOf("body-lavanda"), EquippedSet(bodyColor = "body-lavanda"), 0, august),
        )
        assertEquals(StoreItemState.Owned, HabiEngine.storeStateOf(lavanda, setOf("body-lavanda"), EquippedSet(), 0, august))
        assertEquals(StoreItemState.Affordable, HabiEngine.storeStateOf(lavanda, emptySet(), EquippedSet(), 20, august))
        assertEquals(StoreItemState.MissingPoints(5), HabiEngine.storeStateOf(lavanda, emptySet(), EquippedSet(), 15, august))
        assertEquals(StoreItemState.Locked(100), HabiEngine.storeStateOf(corona, emptySet(), EquippedSet(), 999, august))
    }

    @Test
    fun `defaults are owned without any row`() {
        val salvia = HabiCatalog.byId("body-salvia")!!
        assertEquals(StoreItemState.Equipped, HabiEngine.storeStateOf(salvia, emptySet(), EquippedSet(), 0, august))
        assertEquals(StoreItemState.Owned, HabiEngine.storeStateOf(salvia, emptySet(), EquippedSet(bodyColor = "body-lavanda"), 0, august))
    }

    @Test
    fun `a costume out of season reads OutOfSeason unless it is already owned or worn`() {
        val pumpkin = HabiCatalog.byId("costume-calabaza")!!
        val october = LocalDate.of(2026, 10, 20)
        assertEquals(StoreItemState.OutOfSeason, HabiEngine.storeStateOf(pumpkin, emptySet(), EquippedSet(), 999, august))
        assertEquals(StoreItemState.Affordable, HabiEngine.storeStateOf(pumpkin, emptySet(), EquippedSet(), 150, october))
        assertEquals(StoreItemState.MissingPoints(50), HabiEngine.storeStateOf(pumpkin, emptySet(), EquippedSet(), 100, october))
        assertEquals(StoreItemState.Owned, HabiEngine.storeStateOf(pumpkin, setOf("costume-calabaza"), EquippedSet(), 0, august))
        assertEquals(
            StoreItemState.Equipped,
            HabiEngine.storeStateOf(pumpkin, setOf("costume-calabaza"), EquippedSet(costume = "costume-calabaza"), 0, august),
        )
    }

    @Test
    fun `an accessory hidden under a costume still reads Equipped`() {
        val beret = HabiCatalog.byId("head-boina")!!
        val dressed = EquippedSet(head = "head-boina", costume = "costume-reno")
        assertEquals(StoreItemState.Equipped, HabiEngine.storeStateOf(beret, setOf("head-boina", "costume-reno"), dressed, 0, august))
    }

    @Test
    fun `an owned garden object is OwnedInGarden and never Equipped`() {
        val banco = HabiCatalog.byId("garden-banco")!!
        // Comprado = colocado, y aun así nunca se lee como «puesto» de Habi.
        assertEquals(StoreItemState.OwnedInGarden, HabiEngine.storeStateOf(banco, setOf("garden-banco"), EquippedSet(), 0, august))
        assertEquals(
            StoreItemState.OwnedInGarden,
            HabiEngine.storeStateOf(banco, setOf("garden-banco"), EquippedSet(head = "garden-banco"), 0, august),
        )
    }

    @Test
    fun `an unowned garden object is Affordable or MissingPoints with the exact missing amount`() {
        val regadera = HabiCatalog.byId("garden-regadera")!!
        assertEquals(StoreItemState.Affordable, HabiEngine.storeStateOf(regadera, emptySet(), EquippedSet(), 25, august))
        assertEquals(StoreItemState.MissingPoints(1), HabiEngine.storeStateOf(regadera, emptySet(), EquippedSet(), 24, august))
        assertEquals(StoreItemState.MissingPoints(25), HabiEngine.storeStateOf(regadera, emptySet(), EquippedSet(), 0, august))
    }

    @Test
    fun `a special is FromBadge`() {
        assertEquals(StoreItemState.FromBadge("streak-7"), HabiEngine.storeStateOfEspecial(GardenSpecial.LANTERN))
        assertEquals(StoreItemState.FromBadge("resurrection"), HabiEngine.storeStateOfEspecial(GardenSpecial.MUSHROOMS))
    }

    @Test
    fun `the costume window with a fixed date`() {
        val calabaza = HabiCatalog.byId("costume-calabaza")!!
        val reno = HabiCatalog.byId("costume-reno")!!
        val dinosaurio = HabiCatalog.byId("costume-dinosaurio")!!

        fun estado(
            item: com.alvarotc.bito.domain.model.CatalogItem,
            fecha: LocalDate,
        ) = HabiEngine.storeStateOf(item, emptySet(), EquippedSet(), 999, fecha)

        assertEquals(StoreItemState.Affordable, estado(calabaza, LocalDate.of(2026, 10, 16)))
        assertEquals(StoreItemState.OutOfSeason, estado(calabaza, LocalDate.of(2026, 11, 3)))
        assertEquals(StoreItemState.Affordable, estado(reno, LocalDate.of(2026, 12, 1)))
        // Pascua de 2026: 5 de abril. Carnaval = 14 días antes del Miércoles de Ceniza (18 de febrero).
        assertEquals(StoreItemState.OutOfSeason, estado(dinosaurio, LocalDate.of(2026, 2, 3)))
        assertEquals(StoreItemState.Affordable, estado(dinosaurio, LocalDate.of(2026, 2, 4)))
        assertEquals(StoreItemState.Affordable, estado(dinosaurio, LocalDate.of(2026, 2, 17)))
        assertEquals(StoreItemState.OutOfSeason, estado(dinosaurio, LocalDate.of(2026, 2, 18)))
    }
}
