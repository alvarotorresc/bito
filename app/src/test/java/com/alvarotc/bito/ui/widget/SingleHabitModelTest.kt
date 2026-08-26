package com.alvarotc.bito.ui.widget

import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.HabitCardUi
import com.alvarotc.bito.ui.today.PausedHabitUi
import com.alvarotc.bito.ui.today.TodayUiState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SingleHabitModelTest {
    private fun card(
        id: String,
        kind: CardKind = CardKind.CHECK,
        done: Boolean = false,
        failed: Boolean = false,
        progress: Int = 0,
        target: Int = 1,
        step: Int = 3,
    ) = HabitCardUi(
        id = id, name = "Habit $id", kind = kind, progress = progress, target = target, unit = null,
        step = step, direction = Direction.AT_LEAST, period = Period.DAY,
        doneToday = done, failed = failed, streak = 0,
    )

    private fun state(
        vararg cards: HabitCardUi,
        paused: List<PausedHabitUi> = emptyList(),
    ) = TodayUiState(today = 20679, cards = cards.toList(), pausedHabits = paused, loading = false)

    @Test
    fun `a pending counter maps its fraction, step and a logging tap`() {
        val model = buildSingleHabitModel(state(card("agua", CardKind.COUNTER, progress = 3, target = 8)), "agua")

        val active = assertIs<SingleHabitModel.Active>(model)
        assertEquals("Habit agua", active.name)
        assertEquals(3, active.progress)
        assertEquals(8, active.target)
        assertEquals(3, active.step)
        assertTrue(active.tapLogs)
    }

    @Test
    fun `a check always logs one no matter its stored step`() {
        val model = buildSingleHabitModel(state(card("cama", CardKind.CHECK, step = 5)), "cama")

        assertEquals(1, assertIs<SingleHabitModel.Active>(model).step)
    }

    @Test
    fun `a done habit stops logging from the tap`() {
        val model = buildSingleHabitModel(state(card("agua", CardKind.COUNTER, done = true)), "agua")

        val active = assertIs<SingleHabitModel.Active>(model)
        assertTrue(active.doneToday)
        assertFalse(active.tapLogs)
    }

    @Test
    fun `duration and abstinence habits never log from the tap`() {
        val duration = buildSingleHabitModel(state(card("guitarra", CardKind.DURATION)), "guitarra")
        val abstinence = buildSingleHabitModel(state(card("fumar", CardKind.ABSTINENCE)), "fumar")

        assertFalse(assertIs<SingleHabitModel.Active>(duration).tapLogs)
        assertFalse(assertIs<SingleHabitModel.Active>(abstinence).tapLogs)
    }

    @Test
    fun `a failed abstinence day carries the relapse flag`() {
        val model = buildSingleHabitModel(state(card("fumar", CardKind.ABSTINENCE, failed = true)), "fumar")

        assertTrue(assertIs<SingleHabitModel.Active>(model).failed)
    }

    @Test
    fun `an archived or deleted habit falls back to the missing state`() {
        assertIs<SingleHabitModel.Missing>(buildSingleHabitModel(state(card("agua")), "borrado"))
    }

    @Test
    fun `a never-configured widget falls back to the missing state`() {
        assertIs<SingleHabitModel.Missing>(buildSingleHabitModel(state(card("agua")), null))
    }

    @Test
    fun `a paused habit shows as paused, not missing`() {
        val model = buildSingleHabitModel(state(paused = listOf(PausedHabitUi("yoga", "Yoga"))), "yoga")

        assertEquals("Yoga", assertIs<SingleHabitModel.Paused>(model).name)
    }
}
