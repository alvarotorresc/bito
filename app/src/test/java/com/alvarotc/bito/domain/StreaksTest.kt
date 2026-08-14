package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.Entry
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Streaks (rule E1) and the anti-sergeant valves: retroactive repair, pauses,
 * freezers and pending periods that bridge instead of breaking.
 */
class StreaksTest {
    private val milestones = setOf(7, 30, 100, 365)

    /** A check on each of the [count] days ending on [lastDay]. */
    private fun run(
        habit: Habit,
        lastDay: LogicalDay,
        count: Int,
    ): List<Entry> = entriesOn(habit, (lastDay - count + 1)..lastDay)

    /** A fulfilled week of strength training: three different days. */
    private fun trainingWeek(monday: LogicalDay): List<Entry> =
        entriesOn(RealHabits.strengthTraining, listOf(monday, monday + 2, monday + 4))

    // -----------------------------------------------------------------------
    // Racha diaria
    // -----------------------------------------------------------------------

    @Test
    fun `an unbroken run of daily checks counts every day up to today`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY, count = 5),
            )

        assertEquals(StreakResult(current = 5, best = 5), Streaks.streaksOf(state, RealHabits.makeBed, TODAY))
    }

    @Test
    fun `a habit with no history at all has no streak`() {
        val state = domainState(habits = listOf(RealHabits.makeBed))

        assertEquals(StreakResult(current = 0, best = 0), Streaks.streaksOf(state, RealHabits.makeBed, TODAY))
    }

    @Test
    fun `today still pending does not break the streak`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY - 1, count = 5),
            )

        assertEquals(StreakResult(current = 5, best = 5), Streaks.streaksOf(state, RealHabits.makeBed, TODAY))
    }

    @Test
    fun `a missed day breaks the streak`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY - 3, count = 3) + run(RealHabits.makeBed, TODAY, count = 2),
            )

        assertEquals(StreakResult(current = 2, best = 3), Streaks.streaksOf(state, RealHabits.makeBed, TODAY))
    }

    @Test
    fun `the best streak keeps the longest run of the whole history`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries =
                    run(RealHabits.makeBed, TODAY - 6, count = 5) +
                        run(RealHabits.makeBed, TODAY - 3, count = 2) +
                        run(RealHabits.makeBed, TODAY, count = 2),
            )

        assertEquals(StreakResult(current = 2, best = 5), Streaks.streaksOf(state, RealHabits.makeBed, TODAY))
    }

    @Test
    fun `logging the forgotten day retroactively repairs the broken streak`() {
        val broken =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY - 3, count = 3) + run(RealHabits.makeBed, TODAY, count = 2),
            )
        val repaired =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = broken.entries + entryOn(RealHabits.makeBed, TODAY - 2),
            )

        assertEquals(StreakResult(current = 2, best = 3), Streaks.streaksOf(broken, RealHabits.makeBed, TODAY))
        assertEquals(StreakResult(current = 6, best = 6), Streaks.streaksOf(repaired, RealHabits.makeBed, TODAY))
    }

    @Test
    fun `the walk back stops at the day the habit was created`() {
        val makeBed = RealHabits.makeBed.createdOn(TODAY - 2)
        val state =
            domainState(
                habits = listOf(makeBed),
                entries = entriesOn(makeBed, (TODAY - 2)..TODAY),
            )

        assertEquals(StreakResult(current = 3, best = 3), Streaks.streaksOf(state, makeBed, TODAY))
    }

    @Test
    fun `breaking a limit today resets the current streak`() {
        val state =
            domainState(
                habits = listOf(RealHabits.socialMedia),
                entries =
                    entriesOn(RealHabits.socialMedia, (TODAY - 3)..(TODAY - 1), value = 10) +
                        entryOn(RealHabits.socialMedia, TODAY, value = 45),
                sealedDays = ((TODAY - 3)..TODAY).toList(),
            )

        assertEquals(StreakResult(current = 0, best = 3), Streaks.streaksOf(state, RealHabits.socialMedia, TODAY))
    }

    // -----------------------------------------------------------------------
    // Pausas: hacen puente sin sumar
    // -----------------------------------------------------------------------

    @Test
    fun `a paused day bridges the streak without adding to it`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY - 3, count = 3) + run(RealHabits.makeBed, TODAY, count = 2),
                pauses = listOf(pauseOn(RealHabits.makeBed, TODAY - 2, TODAY - 2, note = "Gripe")),
            )

        assertEquals(StreakResult(current = 5, best = 5), Streaks.streaksOf(state, RealHabits.makeBed, TODAY))
    }

    @Test
    fun `a holiday pause of several days bridges the streak`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY - 5, count = 2) + run(RealHabits.makeBed, TODAY, count = 2),
                pauses = listOf(pauseOn(RealHabits.makeBed, TODAY - 4, TODAY - 2, note = "Vacaciones")),
            )

        assertEquals(StreakResult(current = 4, best = 4), Streaks.streaksOf(state, RealHabits.makeBed, TODAY))
    }

    // -----------------------------------------------------------------------
    // Congeladores: 1 hábito x 1 día
    // -----------------------------------------------------------------------

    @Test
    fun `a freezer bridges the failed day it protects`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY - 3, count = 2) + run(RealHabits.makeBed, TODAY, count = 2),
                freezerUses = listOf(freezerOn(RealHabits.makeBed, TODAY - 2)),
            )

        assertEquals(StreakResult(current = 4, best = 4), Streaks.streaksOf(state, RealHabits.makeBed, TODAY))
    }

    @Test
    fun `a freezer protects only the habit it was spent on`() {
        val protectedDays = run(RealHabits.makeBed, TODAY - 3, count = 2) + run(RealHabits.makeBed, TODAY, count = 2)
        val otherDays = run(RealHabits.floss, TODAY - 3, count = 2) + run(RealHabits.floss, TODAY, count = 2)
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed, RealHabits.floss),
                entries = protectedDays + otherDays,
                freezerUses = listOf(freezerOn(RealHabits.makeBed, TODAY - 2)),
            )

        assertEquals(StreakResult(current = 4, best = 4), Streaks.streaksOf(state, RealHabits.makeBed, TODAY))
        assertEquals(StreakResult(current = 2, best = 2), Streaks.streaksOf(state, RealHabits.floss, TODAY))
    }

    @Test
    fun `a freezer protects only the day it was spent on`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries =
                    run(RealHabits.makeBed, TODAY - 5, count = 2) +
                        entriesOn(RealHabits.makeBed, listOf(TODAY - 3)) +
                        run(RealHabits.makeBed, TODAY, count = 2),
                freezerUses = listOf(freezerOn(RealHabits.makeBed, TODAY - 2)),
            )

        assertEquals(StreakResult(current = 3, best = 3), Streaks.streaksOf(state, RealHabits.makeBed, TODAY))
    }

    @Test
    fun `a freezer does not rescue a weekly habit`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries = trainingWeek(THREE_WEEKS_AGO_MONDAY) + trainingWeek(LAST_MONDAY),
                freezerUses = listOf(freezerOn(RealHabits.strengthTraining, TWO_WEEKS_AGO_MONDAY)),
            )

        assertEquals(StreakResult(current = 1, best = 1), Streaks.streaksOf(state, RealHabits.strengthTraining, TODAY))
    }

    // -----------------------------------------------------------------------
    // Abstinencias: el silencio ni infla ni rompe
    // -----------------------------------------------------------------------

    @Test
    fun `sealed clean days build the abstinence streak`() {
        val state =
            domainState(
                habits = listOf(RealHabits.noSmoking),
                sealedDays = ((TODAY - 4)..TODAY).toList(),
            )

        assertEquals(StreakResult(current = 5, best = 5), Streaks.streaksOf(state, RealHabits.noSmoking, TODAY))
    }

    @Test
    fun `unsealed days bridge the abstinence streak without inflating it`() {
        val state =
            domainState(
                habits = listOf(RealHabits.noSmoking),
                sealedDays = ((TODAY - 7)..(TODAY - 3)).toList(),
            )

        assertEquals(StreakResult(current = 5, best = 5), Streaks.streaksOf(state, RealHabits.noSmoking, TODAY))
    }

    @Test
    fun `a relapse resets the abstinence streak`() {
        val state =
            domainState(
                habits = listOf(RealHabits.noSmoking),
                entries = listOf(entryOn(RealHabits.noSmoking, TODAY - 2)),
                sealedDays = ((TODAY - 4)..TODAY).toList(),
            )

        assertEquals(StreakResult(current = 2, best = 2), Streaks.streaksOf(state, RealHabits.noSmoking, TODAY))
    }

    // -----------------------------------------------------------------------
    // E1 — rachas semanales medidas en semanas
    // -----------------------------------------------------------------------

    @Test
    fun `a weekly habit streak is counted in consecutive weeks, not days`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries =
                    trainingWeek(THREE_WEEKS_AGO_MONDAY) +
                        trainingWeek(TWO_WEEKS_AGO_MONDAY) +
                        trainingWeek(LAST_MONDAY),
            )

        assertEquals(StreakResult(current = 3, best = 3), Streaks.streaksOf(state, RealHabits.strengthTraining, TODAY))
    }

    @Test
    fun `the running week counts as soon as its goal is reached`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries =
                    trainingWeek(TWO_WEEKS_AGO_MONDAY) +
                        trainingWeek(LAST_MONDAY) +
                        trainingWeek(THIS_MONDAY),
            )

        assertEquals(StreakResult(current = 3, best = 3), Streaks.streaksOf(state, RealHabits.strengthTraining, TODAY))
    }

    @Test
    fun `a week below its goal breaks the weekly streak`() {
        val state =
            domainState(
                habits = listOf(RealHabits.strengthTraining),
                entries =
                    trainingWeek(THREE_WEEKS_AGO_MONDAY - 14) +
                        trainingWeek(THREE_WEEKS_AGO_MONDAY - 7) +
                        trainingWeek(THREE_WEEKS_AGO_MONDAY) +
                        trainingWeek(LAST_MONDAY),
            )

        assertEquals(StreakResult(current = 1, best = 3), Streaks.streaksOf(state, RealHabits.strengthTraining, TODAY))
    }

    // -----------------------------------------------------------------------
    // Hitos de racha — se alcanzan una vez y no se confiscan
    // -----------------------------------------------------------------------

    @Test
    fun `a thirty day run reports both the seven and the thirty milestones`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY, count = 30),
            )

        assertEquals(setOf(7, 30), Streaks.reachedMilestones(state, RealHabits.makeBed, TODAY, milestones))
    }

    @Test
    fun `milestones stay reached after the streak breaks`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY - 5, count = 30) + run(RealHabits.makeBed, TODAY, count = 4),
            )

        assertEquals(StreakResult(current = 4, best = 30), Streaks.streaksOf(state, RealHabits.makeBed, TODAY))
        assertEquals(setOf(7, 30), Streaks.reachedMilestones(state, RealHabits.makeBed, TODAY, milestones))
    }

    @Test
    fun `a run shorter than the first milestone reaches nothing`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY, count = 6),
            )

        assertEquals(emptySet(), Streaks.reachedMilestones(state, RealHabits.makeBed, TODAY, milestones))
    }

    @Test
    fun `exactly seven days reach the seven milestone`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY, count = 7),
            )

        assertEquals(setOf(7), Streaks.reachedMilestones(state, RealHabits.makeBed, TODAY, milestones))
    }

    @Test
    fun `only the requested milestone lengths are reported`() {
        val state =
            domainState(
                habits = listOf(RealHabits.makeBed),
                entries = run(RealHabits.makeBed, TODAY, count = 5),
            )

        assertEquals(setOf(3), Streaks.reachedMilestones(state, RealHabits.makeBed, TODAY, setOf(3, 7)))
    }

    @Test
    fun `weekly milestones are measured in weeks`() {
        val weeks = (1..7).flatMap { trainingWeek(LAST_MONDAY - (it - 1) * 7) }
        val state = domainState(habits = listOf(RealHabits.strengthTraining), entries = weeks)

        assertEquals(7, Streaks.streaksOf(state, RealHabits.strengthTraining, TODAY).current)
        assertEquals(setOf(7), Streaks.reachedMilestones(state, RealHabits.strengthTraining, TODAY, milestones))
    }
}
