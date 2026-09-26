package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.PointsEvent
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Points. Grants are derived from history and reconciled append-only: the
 * engine may discover new grants, never take one back.
 *
 * Weekly and monthly refIds are built with [LogicalDays.periodKeyOf] instead of
 * literals: the contract is "the habit id and its period key", and the key
 * numbering itself is pinned by LogicalDaysTest.
 */
class PointsEngineTest {
    private val config = EconomyConfig()

    private fun earned(
        state: DomainState,
        today: LogicalDay = TODAY,
    ): List<PointsEvent> = PointsEngine.earnedEvents(state, today, config)

    private fun List<PointsEvent>.withReason(reason: PointsReason): Set<PointsEvent> = filter { it.reason == reason }.toSet()

    private fun List<PointsEvent>.perfect(prefix: String): Set<PointsEvent> =
        filter { it.reason == PointsReason.PERFECT_DAY && it.refId.startsWith(prefix) }.toSet()

    // -----------------------------------------------------------------------
    // HABIT_DONE — momento de la concesión
    // -----------------------------------------------------------------------

    @Test
    fun `an empty history grants nothing`() {
        assertEquals(emptyList(), earned(domainState()))
    }

    @Test
    fun `breathing sessions grant nothing and change no grant`() {
        val base =
            domainState(
                habits = listOf(RealHabits.water),
                entries = listOf(entryOn(RealHabits.water, TODAY - 1, value = 8)),
            )
        val withBreathing = base.copy(breathingSessions = listOf(breathingSession(TODAY - 1), breathingSession(TODAY)))

        assertEquals(earned(base), earned(withBreathing))
        assertEquals(emptyList(), earned(domainState(breathingSessions = listOf(breathingSession(TODAY)))))
    }

    @Test
    fun `a fulfilled daily goal grants a point on that day`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = listOf(entryOn(RealHabits.water, TODAY - 1, value = 8)),
            )

        assertEquals(
            setOf(PointsEvent(PointsReason.HABIT_DONE, "water:${TODAY - 1}", TODAY - 1, 1)),
            earned(state).withReason(PointsReason.HABIT_DONE),
        )
    }

    @Test
    fun `the weekly goal grants on the day the third session is logged`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries = entriesOn(RealHabits.strengthTraining, listOf(LAST_MONDAY, LAST_WEDNESDAY, LAST_FRIDAY)),
            )

        assertEquals(
            setOf(
                PointsEvent(
                    reason = PointsReason.HABIT_DONE,
                    refId = "strength:${weekKey(LAST_MONDAY)}",
                    logicalDay = LAST_FRIDAY,
                    delta = 1,
                ),
            ),
            earned(state).withReason(PointsReason.HABIT_DONE),
        )
    }

    @Test
    fun `an abstinence grants on the day it is sealed`() {
        val state =
            domainState(
                habits = listOf(RealHabits.noSmoking),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(
            setOf(PointsEvent(PointsReason.HABIT_DONE, "no-smoking:${TODAY - 1}", TODAY - 1, 1)),
            earned(state).withReason(PointsReason.HABIT_DONE),
        )
    }

    @Test
    fun `a daily limit grants on the day it is sealed`() {
        val state =
            domainState(
                habits = listOf(RealHabits.socialMedia),
                entries = listOf(entryOn(RealHabits.socialMedia, TODAY - 1, value = 20)),
                sealedDays = listOf(TODAY - 1),
            )

        assertEquals(
            setOf(PointsEvent(PointsReason.HABIT_DONE, "social-media:${TODAY - 1}", TODAY - 1, 1)),
            earned(state).withReason(PointsReason.HABIT_DONE),
        )
    }

    @Test
    fun `a weekly limit grants on the last day of its week`() {
        val state =
            domainState(
                habits = listOf(RealHabits.foodDelivery),
                entries = listOf(entryOn(RealHabits.foodDelivery, LAST_WEDNESDAY)),
                sealedDays = (LAST_MONDAY..LAST_SUNDAY).toList(),
            )

        assertEquals(
            setOf(
                PointsEvent(
                    reason = PointsReason.HABIT_DONE,
                    refId = "food-delivery:${weekKey(LAST_MONDAY)}",
                    logicalDay = LAST_SUNDAY,
                    delta = 1,
                ),
            ),
            earned(state).withReason(PointsReason.HABIT_DONE),
        )
    }

    @Test
    fun `a pending period grants nothing`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = listOf(entryOn(RealHabits.water, TODAY, value = 5)),
            )

        assertEquals(emptySet(), earned(state).withReason(PointsReason.HABIT_DONE))
    }

    @Test
    fun `a failed period grants nothing`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = listOf(entryOn(RealHabits.water, TODAY - 1, value = 5)),
            )

        assertEquals(emptySet(), earned(state).withReason(PointsReason.HABIT_DONE))
    }

    @Test
    fun `an unsealed clean abstinence grants nothing`() {
        val state = domainState(habits = listOf(RealHabits.noSmoking))

        assertEquals(emptySet(), earned(state).withReason(PointsReason.HABIT_DONE))
    }

    @Test
    fun `each fulfilled period is granted exactly once`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = entriesOn(RealHabits.water, (TODAY - 2)..TODAY, value = 8),
            )
        val grants = earned(state).withReason(PointsReason.HABIT_DONE)

        assertEquals(3, grants.size)
        assertEquals(3, grants.map { it.refId }.toSet().size)
        assertTrue(grants.all { it.delta == config.habitDonePoints })
    }

    // -----------------------------------------------------------------------
    // PERFECT_DAY / semana / mes
    // -----------------------------------------------------------------------

    @Test
    fun `every perfect day grants its bonus on that day`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = entriesOn(RealHabits.makeBed, listOf(TODAY - 2, TODAY - 1)),
            )

        assertEquals(
            setOf(
                PointsEvent(PointsReason.PERFECT_DAY, "day:${TODAY - 2}", TODAY - 2, config.perfectDayPoints),
                PointsEvent(PointsReason.PERFECT_DAY, "day:${TODAY - 1}", TODAY - 1, config.perfectDayPoints),
            ),
            earned(state).perfect("day:"),
        )
    }

    @Test
    fun `a fully perfect week grants its bonus on the sunday`() {
        val makeBed = RealHabits.makeBed.createdOn(LAST_MONDAY)
        val state =
            domainState(
                habits = listOf(makeBed),
                entries = entriesOn(makeBed, LAST_MONDAY..LAST_SUNDAY),
            )

        assertEquals(
            setOf(
                PointsEvent(
                    reason = PointsReason.PERFECT_DAY,
                    refId = "week:${weekKey(LAST_MONDAY)}",
                    logicalDay = LAST_SUNDAY,
                    delta = config.perfectWeekPoints,
                ),
            ),
            earned(state).perfect("week:"),
        )
    }

    @Test
    fun `a single spoiled day cancels the perfect week`() {
        val makeBed = RealHabits.makeBed.createdOn(LAST_MONDAY)
        val state =
            domainState(
                habits = listOf(makeBed),
                entries = entriesOn(makeBed, (LAST_MONDAY..LAST_SUNDAY).filter { it != LAST_WEDNESDAY }),
            )

        assertEquals(emptySet(), earned(state).perfect("week:"))
    }

    @Test
    fun `a fully perfect month grants its bonus on the last day of the month`() {
        val makeBed = RealHabits.makeBed.createdOn(JULY_FIRST)
        val state =
            domainState(
                habits = listOf(makeBed),
                entries = entriesOn(makeBed, JULY_FIRST..JULY_LAST),
            )

        assertEquals(
            setOf(
                PointsEvent(
                    reason = PointsReason.PERFECT_DAY,
                    refId = "month:${monthKey(JULY_FIRST)}",
                    logicalDay = JULY_LAST,
                    delta = config.perfectMonthPoints,
                ),
            ),
            earned(state).perfect("month:"),
        )
    }

    @Test
    fun `a single spoiled day cancels the perfect month`() {
        val makeBed = RealHabits.makeBed.createdOn(JULY_FIRST)
        val state =
            domainState(
                habits = listOf(makeBed),
                entries = entriesOn(makeBed, (JULY_FIRST..JULY_LAST).filter { it != JULY_FIRST + 10 }),
            )

        assertEquals(emptySet(), earned(state).perfect("month:"))
    }

    // -----------------------------------------------------------------------
    // STREAK_MILESTONE
    // -----------------------------------------------------------------------

    @Test
    fun `a thirty day streak grants the seven and thirty milestones`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = entriesOn(RealHabits.makeBed, (TODAY - 29)..TODAY),
            )
        val grants = earned(state).withReason(PointsReason.STREAK_MILESTONE)

        assertEquals(
            setOf("make-bed:7" to 5, "make-bed:30" to 20),
            grants.map { it.refId to it.delta }.toSet(),
        )
    }

    @Test
    fun `a milestone is granted only once even after the streak breaks and grows again`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries =
                    entriesOn(RealHabits.makeBed, (TODAY - 39)..(TODAY - 10)) +
                        entriesOn(RealHabits.makeBed, (TODAY - 8)..TODAY),
            )
        val grants = earned(state).withReason(PointsReason.STREAK_MILESTONE)

        assertEquals(setOf("make-bed:7", "make-bed:30"), grants.map { it.refId }.toSet())
        assertEquals(2, grants.size)
    }

    @Test
    fun `a streak below the first milestone grants nothing`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = entriesOn(RealHabits.makeBed, (TODAY - 5)..TODAY),
            )

        assertEquals(emptySet(), earned(state).withReason(PointsReason.STREAK_MILESTONE))
    }

    // -----------------------------------------------------------------------
    // TASK_DONE
    // -----------------------------------------------------------------------

    @Test
    fun `a loose task done is worth three`() {
        val state = domainState(tasks = listOf(task(id = "t1", status = TaskStatus.DONE, doneOnDay = TODAY)))

        val event = PointsEngine.earnedEvents(state, TODAY, EconomyConfig()).single()

        assertEquals(PointsReason.TASK_DONE, event.reason)
        assertEquals("task:t1", event.refId)
        assertEquals(TODAY, event.logicalDay)
        assertEquals(3, event.delta)
    }

    @Test
    fun `a dated task done on time is worth five, and late three`() {
        val onTime =
            domainState(
                tasks =
                    listOf(
                        task(id = "t1", dueKind = DueKind.DATE, dueDay = TODAY, status = TaskStatus.DONE, doneOnDay = TODAY),
                    ),
            )
        val late =
            domainState(
                tasks =
                    listOf(
                        task(id = "t1", dueKind = DueKind.DATE, dueDay = TODAY - 1, status = TaskStatus.DONE, doneOnDay = TODAY),
                    ),
            )

        assertEquals(5, PointsEngine.earnedEvents(onTime, TODAY, EconomyConfig()).single().delta)
        assertEquals(3, PointsEngine.earnedEvents(late, TODAY, EconomyConfig()).single().delta)
    }

    @Test
    fun `a week task done inside its week is worth five and the monday after three`() {
        val sunday = Tasks.weekDueOf(THIS_MONDAY)
        val inside =
            domainState(
                tasks =
                    listOf(
                        task(
                            id = "t1",
                            dueKind = DueKind.WEEK,
                            dueDay = sunday,
                            status = TaskStatus.DONE,
                            createdOnDay = THIS_MONDAY,
                            doneOnDay = sunday,
                        ),
                    ),
            )
        val after =
            domainState(
                tasks =
                    listOf(
                        task(
                            id = "t1",
                            dueKind = DueKind.WEEK,
                            dueDay = sunday,
                            status = TaskStatus.DONE,
                            createdOnDay = THIS_MONDAY,
                            doneOnDay = sunday + 1,
                        ),
                    ),
            )

        assertEquals(5, PointsEngine.earnedEvents(inside, sunday, EconomyConfig()).single().delta)
        assertEquals(3, PointsEngine.earnedEvents(after, sunday + 1, EconomyConfig()).single().delta)
    }

    @Test
    fun `an open task pays nothing, and one done in the future waits`() {
        val open = domainState(tasks = listOf(task(id = "t1")))
        val future = domainState(tasks = listOf(task(id = "t2", status = TaskStatus.DONE, doneOnDay = TODAY + 1)))

        assertTrue(PointsEngine.earnedEvents(open, TODAY, EconomyConfig()).isEmpty())
        assertTrue(PointsEngine.earnedEvents(future, TODAY, EconomyConfig()).isEmpty())
    }

    @Test
    fun `granting the same task twice is a no-op`() {
        val state = domainState(tasks = listOf(task(id = "t1", status = TaskStatus.DONE, doneOnDay = TODAY)))
        val earned = PointsEngine.earnedEvents(state, TODAY, EconomyConfig())
        val ledger = earned.map { it.asLedgerEntry() }

        assertTrue(PointsEngine.missingEvents(earned, ledger).isEmpty())
    }

    @Test
    fun `marking done, undoing and marking again does not grant twice`() {
        val done = domainState(tasks = listOf(task(id = "t1", status = TaskStatus.DONE, doneOnDay = TODAY)))
        val ledger = PointsEngine.earnedEvents(done, TODAY, EconomyConfig()).map { it.asLedgerEntry() }
        // Deshacer devuelve la tarea a OPEN pero el apunte se queda (los puntos no se confiscan).
        val reopened = domainState(tasks = listOf(task(id = "t1")), ledger = ledger)
        assertTrue(PointsEngine.earnedEvents(reopened, TODAY, EconomyConfig()).isEmpty())

        val redone = domainState(tasks = listOf(task(id = "t1", status = TaskStatus.DONE, doneOnDay = TODAY + 1)), ledger = ledger)
        assertTrue(PointsEngine.missingEvents(PointsEngine.earnedEvents(redone, TODAY + 1, EconomyConfig()), ledger).isEmpty())
    }

    @Test
    fun `tasks do not add or remove any habit, perfect day or streak event`() {
        val habits = listOf(RealHabits.makeBed.createdOn(TODAY - 2))
        val entries = listOf(entryOn(RealHabits.makeBed, TODAY - 1))
        val without = domainState(habits = habits, entries = entries)
        val with =
            domainState(
                habits = habits,
                entries = entries,
                tasks = listOf(task(id = "t1", status = TaskStatus.DONE, doneOnDay = TODAY)),
            )

        val nonTask = PointsEngine.earnedEvents(with, TODAY, EconomyConfig()).filter { it.reason != PointsReason.TASK_DONE }

        assertEquals(PointsEngine.earnedEvents(without, TODAY, EconomyConfig()), nonTask)
    }

    // -----------------------------------------------------------------------
    // Reconciliación append-only — los puntos no se confiscan
    // -----------------------------------------------------------------------

    @Test
    fun `every derived grant is missing from an empty ledger`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = entriesOn(RealHabits.water, (TODAY - 2)..TODAY, value = 8),
            )
        val events = earned(state)

        assertEquals(events.toSet(), PointsEngine.missingEvents(events, emptyList()).toSet())
    }

    @Test
    fun `grants already in the ledger are not proposed again`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = entriesOn(RealHabits.water, (TODAY - 2)..TODAY, value = 8),
            )
        val events = earned(state)
        val ledger = events.take(1).map { it.asLedgerEntry() }

        assertEquals(events.drop(1).toSet(), PointsEngine.missingEvents(events, ledger).toSet())
    }

    @Test
    fun `nothing is missing once the ledger holds every grant`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = entriesOn(RealHabits.water, (TODAY - 2)..TODAY, value = 8),
            )
        val events = earned(state)
        val ledger = events.map { it.asLedgerEntry() }

        assertEquals(emptyList(), PointsEngine.missingEvents(events, ledger))
    }

    @Test
    fun `deleting entries never produces a negative event`() {
        val logged =
            domainState(
                habits = listOf(RealHabits.water),
                entries = entriesOn(RealHabits.water, (TODAY - 2)..TODAY, value = 8),
            )
        val ledger = earned(logged).map { it.asLedgerEntry() }
        val erased = domainState(habits = listOf(RealHabits.water), ledger = ledger)

        val stillEarned = earned(erased)

        assertTrue(stillEarned.none { it.delta < 0 })
        assertEquals(emptyList(), PointsEngine.missingEvents(stillEarned, ledger))
        assertEquals(PointsEngine.balance(ledger), PointsEngine.balance(erased.pointsLedger))
    }

    @Test
    fun `a stale grant in the ledger is never proposed for removal`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = entriesOn(RealHabits.water, (TODAY - 2)..TODAY, value = 8),
            )
        val events = earned(state)
        val ledger =
            listOf(
                ledgerEntry(1, PointsReason.HABIT_DONE, refId = "water:${TODAY - 200}", day = TODAY - 200),
            )

        val missing = PointsEngine.missingEvents(events, ledger)

        assertEquals(events.toSet(), missing.toSet())
        assertTrue(missing.none { it.delta < 0 })
    }

    @Test
    fun `a ledger row without a refId never matches a derived grant`() {
        val state =
            domainState(
                habits = listOf(RealHabits.water),
                entries = entriesOn(RealHabits.water, (TODAY - 2)..TODAY, value = 8),
            )
        val events = earned(state)
        val ledger =
            listOf(
                ledgerEntry(-15, PointsReason.BUY_FREEZER),
                ledgerEntry(-5, PointsReason.BUY_ITEM),
            )

        assertEquals(events.toSet(), PointsEngine.missingEvents(events, ledger).toSet())
    }

    @Test
    fun `the same refId under another reason does not count as granted`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = listOf(entryOn(RealHabits.makeBed, TODAY - 1)),
            )
        val events = earned(state)
        val ledger = events.map { ledgerEntry(it.delta, PointsReason.BUY_ITEM, it.refId, it.logicalDay) }

        assertEquals(events.toSet(), PointsEngine.missingEvents(events, ledger).toSet())
    }

    // -----------------------------------------------------------------------
    // Saldo, gasto e inventario
    // -----------------------------------------------------------------------

    @Test
    fun `the balance of an empty ledger is zero`() {
        assertEquals(0, PointsEngine.balance(emptyList()))
    }

    @Test
    fun `the balance is the sum of every delta, purchases included`() {
        val ledger =
            listOf(
                ledgerEntry(1, PointsReason.HABIT_DONE, "water:${TODAY - 1}", TODAY - 1),
                ledgerEntry(3, PointsReason.PERFECT_DAY, "day:${TODAY - 1}", TODAY - 1),
                ledgerEntry(10, PointsReason.PERFECT_DAY, "week:1", TODAY - 1),
                ledgerEntry(-15, PointsReason.BUY_FREEZER),
                ledgerEntry(-4, PointsReason.BUY_ITEM, "hat"),
            )

        assertEquals(-5, PointsEngine.balance(ledger))
    }

    @Test
    fun `spending is allowed up to the exact balance`() {
        val ledger =
            listOf(
                ledgerEntry(20, PointsReason.PERFECT_DAY, "week:1"),
                ledgerEntry(-5, PointsReason.BUY_ITEM, "hat"),
            )

        assertTrue(PointsEngine.canSpend(ledger, 1))
        assertTrue(PointsEngine.canSpend(ledger, 15))
        assertFalse(PointsEngine.canSpend(ledger, 16))
    }

    @Test
    fun `an empty ledger cannot buy anything`() {
        assertFalse(PointsEngine.canSpend(emptyList(), 1))
    }

    @Test
    fun `freezers owned are purchases minus uses`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                freezerUses = listOf(freezerOn(RealHabits.makeBed, TODAY - 4)),
                ledger =
                    listOf(
                        ledgerEntry(-15, PointsReason.BUY_FREEZER),
                        ledgerEntry(-15, PointsReason.BUY_FREEZER),
                        ledgerEntry(-15, PointsReason.BUY_FREEZER),
                    ),
            )

        assertEquals(2, PointsEngine.freezersOwned(state))
    }

    @Test
    fun `a user who never bought a freezer owns none`() {
        val state = domainState(habits = listOf(RealHabits.makeBed))

        assertEquals(0, PointsEngine.freezersOwned(state))
    }

    @Test
    fun `buying without spending leaves the whole stock available`() {
        val state =
            domainState(
                ledger =
                    listOf(
                        ledgerEntry(-15, PointsReason.BUY_FREEZER),
                        ledgerEntry(-15, PointsReason.BUY_FREEZER),
                    ),
            )

        assertEquals(2, PointsEngine.freezersOwned(state))
    }

    @Test
    fun `other purchases do not add freezers to the inventory`() {
        val state =
            domainState(
                ledger =
                    listOf(
                        ledgerEntry(-15, PointsReason.BUY_FREEZER),
                        ledgerEntry(-4, PointsReason.BUY_ITEM, "hat"),
                        ledgerEntry(3, PointsReason.PERFECT_DAY, "day:$TODAY"),
                    ),
            )

        assertEquals(1, PointsEngine.freezersOwned(state))
    }

    @Test
    fun `freezers spent on different habits all leave the inventory`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.floss),
                freezerUses =
                    listOf(
                        freezerOn(RealHabits.makeBed, TODAY - 4),
                        freezerOn(RealHabits.floss, TODAY - 2),
                    ),
                ledger =
                    listOf(
                        ledgerEntry(-15, PointsReason.BUY_FREEZER),
                        ledgerEntry(-15, PointsReason.BUY_FREEZER),
                    ),
            )

        assertEquals(0, PointsEngine.freezersOwned(state))
    }

    @Test
    fun `pointsEarnedOn sums only positive deltas dated that day`() {
        val ledger =
            listOf(
                ledgerEntry(1, PointsReason.HABIT_DONE, "a:1", day = TODAY),
                ledgerEntry(3, PointsReason.PERFECT_DAY, "day:$TODAY", day = TODAY),
                ledgerEntry(-100, PointsReason.BUY_FREEZER, null, day = TODAY),
                ledgerEntry(5, PointsReason.STREAK_MILESTONE, "a:7", day = TODAY - 1),
            )
        assertEquals(4, PointsEngine.pointsEarnedOn(ledger, TODAY))
        assertTrue(PointsEngine.perfectDayGranted(ledger, TODAY))
        assertFalse(PointsEngine.perfectDayGranted(ledger, TODAY - 1))
    }

    @Test
    fun `economy numbers are the ones settled in the economy session`() {
        val economy = EconomyConfig()
        assertEquals(1, economy.habitDonePoints)
        assertEquals(3, economy.perfectDayPoints)
        assertEquals(10, economy.perfectWeekPoints)
        assertEquals(50, economy.perfectMonthPoints)
        assertEquals(mapOf(7 to 5, 30 to 20, 100 to 75, 365 to 300), economy.streakMilestonePoints)
        assertEquals(100, economy.freezerPrice)
    }

    @Test
    fun `earnedEvents with a precomputed perfect-day list matches the self-computed result`() {
        val makeBed = RealHabits.makeBed.createdOn(JULY_FIRST)
        val state =
            domainState(
                habits = listOf(makeBed),
                entries = entriesOn(makeBed, JULY_FIRST..TODAY),
            )

        val defaultResult = earned(state)
        val perfectDays = PerfectDays.perfectDaysUpTo(state, TODAY)
        val withPrecomputed = PointsEngine.earnedEvents(state, TODAY, config, perfectDays)

        // Verify non-vacuity: the fixture has perfect days and perfect-week grants
        assertTrue(earned(state).perfect("week:").isNotEmpty())

        // Verify that the precomputed list is actually used
        assertEquals(defaultResult, withPrecomputed)
        assertNotEquals(
            defaultResult,
            PointsEngine.earnedEvents(state, TODAY, config, emptySet()),
            "Passing an empty perfect-day set should produce different results",
        )
    }
}
