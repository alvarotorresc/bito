package com.alvarotc.bito.ui.today

import com.alvarotc.bito.domain.Compliance
import com.alvarotc.bito.domain.ComplianceStatus
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.THIS_MONDAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entriesOn
import com.alvarotc.bito.domain.model.DaySeal
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Entry
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.PauseInterval
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.TargetChange
import com.alvarotc.bito.domain.pauseOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Epoch day 21 is a Thursday; its ISO week runs 18 (Monday) .. 24 (Sunday). */
private const val TODAY = 21

private fun habit(
    id: String,
    metric: Metric = Metric.CHECK,
    period: Period = Period.DAY,
    direction: Direction = Direction.AT_LEAST,
    target: Int = 1,
    unit: String? = null,
    logMode: LogMode = LogMode.COUNTER,
    step: Int = 1,
    status: HabitStatus = HabitStatus.ACTIVE,
    createdOnDay: Int = 0,
) = Habit(id, id, metric, period, direction, target, unit, logMode, step, status, createdOnDay)

private fun stateOf(
    habits: List<Habit>,
    entries: List<Entry> = emptyList(),
    seals: List<DaySeal> = emptyList(),
    pauses: List<PauseInterval> = emptyList(),
) = DomainState(
    habits = habits,
    targetChanges = habits.map { TargetChange(it.id, it.createdOnDay, it.target) },
    entries = entries,
    daySeals = seals,
    pauseIntervals = pauses,
)

private fun entry(
    id: String,
    habitId: String,
    day: Int,
    value: Int = 1,
) = Entry(id, habitId, day, value, 0L)

private fun seal(day: Int) = DaySeal(day, 0L)

private fun pause(
    habitId: String,
    startDay: Int,
    endDay: Int? = null,
) = PauseInterval(habitId, startDay, endDay)

class TodayUiStateTest {
    @Test
    fun `1 - counter short of its target is not done today`() {
        val agua = habit("agua", metric = Metric.COUNT, target = 8)
        val state =
            stateOf(
                habits = listOf(agua),
                entries = listOf(entry("e1", "agua", TODAY, 3), entry("e2", "agua", TODAY, 2)),
            )

        val result = buildTodayUiState(state, emptyMap(), TODAY)
        val card = result.cards.single()

        assertEquals(CardKind.COUNTER, card.kind)
        assertEquals(5, card.progress)
        assertEquals(8, card.target)
        assertFalse(card.doneToday)
        assertEquals(0, result.ringDone)
        assertEquals(1, result.ringTotal)
    }

    @Test
    fun `2 - counter reaching its target is done today and fills the ring`() {
        val agua = habit("agua", metric = Metric.COUNT, target = 8)
        val state =
            stateOf(
                habits = listOf(agua),
                entries = listOf(entry("e1", "agua", TODAY, 5), entry("e2", "agua", TODAY, 3)),
            )

        val result = buildTodayUiState(state, emptyMap(), TODAY)
        val card = result.cards.single()

        assertTrue(card.doneToday)
        assertEquals(1, result.ringDone)
        assertEquals(1, result.ringTotal)
    }

    @Test
    fun `3 - a binary count habit is a single check`() {
        val pasos = habit("pasos", metric = Metric.COUNT, target = 9000, logMode = LogMode.BINARY)
        val state = stateOf(habits = listOf(pasos), entries = listOf(entry("e1", "pasos", TODAY)))

        val card = buildTodayUiState(state, emptyMap(), TODAY).cards.single()

        assertEquals(CardKind.CHECK, card.kind)
        assertEquals(1, card.progress)
        assertEquals(1, card.target)
        assertTrue(card.doneToday)
    }

    @Test
    fun `4 - weekly frequency counts distinct days and today's entry marks doneToday early`() {
        val fuerza = habit("fuerza", metric = Metric.CHECK, period = Period.WEEK, target = 3)
        val stateOneEntry = stateOf(habits = listOf(fuerza), entries = listOf(entry("e1", "fuerza", TODAY - 1)))

        val cardOneEntry = buildTodayUiState(stateOneEntry, emptyMap(), TODAY).cards.single()
        assertEquals(1, cardOneEntry.progress)
        assertFalse(cardOneEntry.doneToday)

        val stateTwoEntries =
            stateOf(
                habits = listOf(fuerza),
                entries = listOf(entry("e1", "fuerza", TODAY - 1), entry("e2", "fuerza", TODAY)),
            )

        val cardTwoEntries = buildTodayUiState(stateTwoEntries, emptyMap(), TODAY).cards.single()
        assertEquals(2, cardTwoEntries.progress)
        assertTrue(cardTwoEntries.doneToday)
        val weekKey = LogicalDays.periodKeyOf(TODAY, Period.WEEK)
        assertEquals(ComplianceStatus.PENDING, Compliance.complianceOf(stateTwoEntries, fuerza, weekKey, TODAY))
    }

    @Test
    fun `5 - duration accumulates minutes toward its target`() {
        val guitarra = habit("guitarra", metric = Metric.DURATION, target = 20)
        val stateBelow =
            stateOf(
                habits = listOf(guitarra),
                entries = listOf(entry("e1", "guitarra", TODAY, 10), entry("e2", "guitarra", TODAY, 5)),
            )

        val cardBelow = buildTodayUiState(stateBelow, emptyMap(), TODAY).cards.single()
        assertEquals(CardKind.DURATION, cardBelow.kind)
        assertEquals(15, cardBelow.progress)
        assertFalse(cardBelow.doneToday)

        val stateReached =
            stateOf(
                habits = listOf(guitarra),
                entries =
                    listOf(
                        entry("e1", "guitarra", TODAY, 10),
                        entry("e2", "guitarra", TODAY, 5),
                        entry("e3", "guitarra", TODAY, 5),
                    ),
            )

        val cardReached = buildTodayUiState(stateReached, emptyMap(), TODAY).cards.single()
        assertTrue(cardReached.doneToday)
    }

    @Test
    fun `6 - a clean abstinence with sealed days is done and streaks the sealed run`() {
        val noFumar = habit("no-fumar", direction = Direction.ZERO, target = 0, createdOnDay = 18)
        val state = stateOf(habits = listOf(noFumar), seals = listOf(seal(18), seal(19), seal(20)))

        val result = buildTodayUiState(state, emptyMap(), TODAY)
        val card = result.cards.single()

        assertEquals(CardKind.ABSTINENCE, card.kind)
        assertTrue(card.doneToday)
        assertFalse(card.failed)
        assertEquals(3, card.streak)
        assertTrue(result.pendingSealDays.isEmpty())
    }

    @Test
    fun `7 - a relapse today fails the abstinence and drops it out of the ring`() {
        val noFumar = habit("no-fumar", direction = Direction.ZERO, target = 0, createdOnDay = 18)
        val state =
            stateOf(
                habits = listOf(noFumar),
                entries = listOf(entry("e1", "no-fumar", TODAY)),
                seals = listOf(seal(18), seal(19), seal(20)),
            )

        val result = buildTodayUiState(state, emptyMap(), TODAY)
        val card = result.cards.single()

        assertTrue(card.failed)
        assertFalse(card.doneToday)
        assertEquals(0, result.ringDone)
        assertEquals(1, result.ringTotal)
    }

    @Test
    fun `8 - a duration limit is done within bounds and failed once exceeded`() {
        val redes = habit("redes", metric = Metric.DURATION, direction = Direction.AT_MOST, target = 30)
        val stateWithin = stateOf(habits = listOf(redes), entries = listOf(entry("e1", "redes", TODAY, 20)))

        val cardWithin = buildTodayUiState(stateWithin, emptyMap(), TODAY).cards.single()
        assertTrue(cardWithin.doneToday)
        assertFalse(cardWithin.failed)

        val stateOver = stateOf(habits = listOf(redes), entries = listOf(entry("e1", "redes", TODAY, 35)))

        val cardOver = buildTodayUiState(stateOver, emptyMap(), TODAY).cards.single()
        assertFalse(cardOver.doneToday)
        assertTrue(cardOver.failed)
    }

    @Test
    fun `9 - pending seal days list every unsealed day before today, never today itself`() {
        val noFumar = habit("no-fumar", direction = Direction.ZERO, target = 0, createdOnDay = 18)
        val state = stateOf(habits = listOf(noFumar))

        val result = buildTodayUiState(state, emptyMap(), TODAY)

        assertEquals(listOf(18, 19, 20), result.pendingSealDays)
    }

    @Test
    fun `10 - a paused or archived habit produces no card`() {
        val pausedHabit = habit("paused-habit")
        val archivedHabit = habit("archived-habit", status = HabitStatus.ARCHIVED)
        val state =
            stateOf(
                habits = listOf(pausedHabit, archivedHabit),
                pauses = listOf(pause("paused-habit", startDay = 20)),
            )

        val result = buildTodayUiState(state, emptyMap(), TODAY)

        assertTrue(result.cards.isEmpty())
        assertEquals(0, result.ringTotal)
    }

    @Test
    fun `11 - a later target change is honored by date (E2)`() {
        val agua = habit("agua", metric = Metric.COUNT, target = 8)
        val base = stateOf(habits = listOf(agua))
        val state = base.copy(targetChanges = base.targetChanges + TargetChange("agua", TODAY, 10))

        val card = buildTodayUiState(state, emptyMap(), TODAY).cards.single()

        assertEquals(10, card.target)
    }

    @Test
    fun `12 - cards are ordered by sortOrder with unknown ids last`() {
        val a = habit("a")
        val b = habit("b")
        val c = habit("c")
        val state = stateOf(habits = listOf(a, b, c))
        val sortOrder = mapOf("c" to 0, "a" to 1)

        val result = buildTodayUiState(state, sortOrder, TODAY)

        assertEquals(listOf("c", "a", "b"), result.cards.map { it.id })
    }

    @Test
    fun `13 - an empty state has no cards and stops loading`() {
        val state = stateOf(habits = emptyList())

        val result = buildTodayUiState(state, emptyMap(), TODAY)

        assertEquals(0, result.ringTotal)
        assertTrue(result.cards.isEmpty())
        assertFalse(result.loading)
    }

    @Test
    fun `14 - an at-most habit clean with partial progress keeps its name and shows its logging chips`() {
        val redes = habit("redes", metric = Metric.DURATION, direction = Direction.AT_MOST, target = 30)
        val state = stateOf(habits = listOf(redes), entries = listOf(entry("e1", "redes", TODAY, 10)))

        val card = buildTodayUiState(state, emptyMap(), TODAY).cards.single()

        assertTrue(card.doneToday)
        assertFalse(card.failed)
        assertFalse(card.nameStruckThrough)
        assertTrue(card.showsLoggingChips)
    }

    @Test
    fun `15 - an at-most habit over its limit still shows its logging chips for honest logging`() {
        val redes = habit("redes", metric = Metric.DURATION, direction = Direction.AT_MOST, target = 30)
        val state = stateOf(habits = listOf(redes), entries = listOf(entry("e1", "redes", TODAY, 35)))

        val card = buildTodayUiState(state, emptyMap(), TODAY).cards.single()

        assertTrue(card.failed)
        assertFalse(card.doneToday)
        assertFalse(card.nameStruckThrough)
        assertTrue(card.showsLoggingChips)
    }

    @Test
    fun `16 - an at-least habit done today strikes its name and hides its logging chips`() {
        val guitarra = habit("guitarra", metric = Metric.DURATION, target = 20)
        val state = stateOf(habits = listOf(guitarra), entries = listOf(entry("e1", "guitarra", TODAY, 20)))

        val card = buildTodayUiState(state, emptyMap(), TODAY).cards.single()

        assertTrue(card.doneToday)
        assertTrue(card.nameStruckThrough)
        assertFalse(card.showsLoggingChips)
    }

    @Test
    fun `17 - a binary-logged at-most habit renders as a CHECK card and keeps its name clean`() {
        // Reachable from the form: toggle "solo cumplido" while on QUANTITY/DURATION, then
        // switch to the QUIT preset before saving — binaryMode survives the preset switch.
        val redes =
            habit(
                "redes",
                metric = Metric.DURATION,
                direction = Direction.AT_MOST,
                target = 30,
                logMode = LogMode.BINARY,
            )
        val state = stateOf(habits = listOf(redes))

        val card = buildTodayUiState(state, emptyMap(), TODAY).cards.single()

        assertEquals(CardKind.CHECK, card.kind)
        assertTrue(card.doneToday)
        assertFalse(card.nameStruckThrough)
    }

    @Test
    fun `19 - paused habits leave the cards and appear in the paused section`() {
        val markedPaused = habit("marked-paused", status = HabitStatus.PAUSED)
        val state =
            stateOf(
                habits = listOf(markedPaused),
                pauses = listOf(pause("marked-paused", startDay = 18)),
            )

        val result = buildTodayUiState(state, emptyMap(), TODAY)

        assertTrue(result.cards.isEmpty())
        assertEquals(listOf(PausedHabitUi("marked-paused", "marked-paused")), result.pausedHabits)
    }

    @Test
    fun `20 - archived habits appear nowhere on today`() {
        val archivedHabit = habit("archived-habit", status = HabitStatus.ARCHIVED)
        val state = stateOf(habits = listOf(archivedHabit))

        val result = buildTodayUiState(state, emptyMap(), TODAY)

        assertTrue(result.cards.isEmpty())
        assertTrue(result.pausedHabits.isEmpty())
    }

    @Test
    fun `18 - entries on paused days do not inflate the weekly card progress`() {
        // forces 3x/week; logged Monday and Tuesday, then a retroactive pause covers Monday-Tuesday
        val habit = RealHabits.strengthTraining
        val state =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, listOf(THIS_MONDAY, THIS_MONDAY + 1)),
                pauses = listOf(pauseOn(habit, THIS_MONDAY, THIS_MONDAY + 1)),
            )
        val card = buildTodayUiState(state, emptyMap(), com.alvarotc.bito.domain.TODAY).cards.single()
        // complianceOf excludes those days; the card must count the same: 0, not 2
        assertEquals(0, card.progress)
    }

    @Test
    fun `21 - paused habits are ordered by sortOrder like actives`() {
        val a = habit("a", status = HabitStatus.PAUSED)
        val b = habit("b", status = HabitStatus.PAUSED)
        val c = habit("c", status = HabitStatus.PAUSED)
        val state =
            stateOf(
                habits = listOf(a, b, c),
                pauses = listOf(pause("a", startDay = 18), pause("b", startDay = 18), pause("c", startDay = 18)),
            )
        val sortOrder = mapOf("c" to 0, "a" to 1, "b" to 2)

        val result = buildTodayUiState(state, sortOrder, TODAY)

        assertEquals(listOf("c", "a", "b"), result.pausedHabits.map { it.id })
    }

    @Test
    fun `22 - the header spec's mood follows the mood engine and carries the given personality`() {
        val habit = RealHabits.makeBed
        val radiantState =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, (com.alvarotc.bito.domain.TODAY - 7)..(com.alvarotc.bito.domain.TODAY - 1)),
            )

        val radiant =
            buildTodayUiState(radiantState, emptyMap(), com.alvarotc.bito.domain.TODAY, Personality.CHEERLEADER)

        assertEquals(Mood.RADIANT, radiant.spec.mood)
        assertEquals(Personality.CHEERLEADER, radiant.spec.personality)

        // Same engine, different branch: 5 silent days trigger DRAMATIC regardless of ratio,
        // proving the builder threads StatsEngine.lastActivityDay into MoodEngine.moodOf, just
        // like buildStatsUiState's own mood test.
        val dramaticState =
            domainState(
                habits = listOf(habit),
                entries = entriesOn(habit, listOf(com.alvarotc.bito.domain.TODAY - 5)),
            )

        val dramatic =
            buildTodayUiState(dramaticState, emptyMap(), com.alvarotc.bito.domain.TODAY, Personality.CHEERLEADER)

        assertEquals(Mood.DRAMATIC, dramatic.spec.mood)
    }

    @Test
    fun `23 - userName passes through to the ui state unchanged`() {
        val state = stateOf(habits = emptyList())

        val result = buildTodayUiState(state, emptyMap(), TODAY, userName = "Alvaro")

        assertEquals("Alvaro", result.userName)
    }
}
