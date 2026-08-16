package com.alvarotc.bito.ui.widget

import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.HabitCardUi
import com.alvarotc.bito.ui.today.TodayUiState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetModelTest {
    private fun card(
        id: String,
        kind: CardKind = CardKind.CHECK,
        done: Boolean = false,
    ) = HabitCardUi(
        id = id, name = id, kind = kind, progress = 0, target = 1, unit = null,
        step = 3, direction = com.alvarotc.bito.domain.model.Direction.AT_LEAST,
        period = com.alvarotc.bito.domain.model.Period.DAY,
        doneToday = done, failed = false, streak = 0,
    )

    private fun state(vararg cards: HabitCardUi) =
        TodayUiState(today = 20679, ringDone = 0, ringTotal = cards.size, cards = cards.toList(), loading = false)

    @Test
    fun `completed habits disappear from the widget`() {
        val model = buildWidgetModel(state(card("agua"), card("cama", done = true)), selectedIds = null)
        assertEquals(listOf("agua"), model.items.map { it.habitId })
        assertEquals(1, model.done)
        assertEquals(2, model.total)
    }

    @Test
    fun `an instance only sees its selection and measures progress over it`() {
        val model =
            buildWidgetModel(
                state(card("agua"), card("cama", done = true), card("pasos")),
                selectedIds = setOf("cama", "pasos"),
            )
        assertEquals(listOf("pasos"), model.items.map { it.habitId })
        assertEquals(1, model.done)
        assertEquals(2, model.total)
    }

    @Test
    fun `an empty selection means every habit`() {
        val model = buildWidgetModel(state(card("agua")), selectedIds = emptySet())
        assertEquals(1, model.total)
    }

    @Test
    fun `only check and counter cards log from the widget`() {
        val model =
            buildWidgetModel(
                state(card("agua", CardKind.COUNTER), card("guitarra", CardKind.DURATION), card("fumar", CardKind.ABSTINENCE)),
                selectedIds = null,
            )
        assertTrue(model.items.first { it.habitId == "agua" }.tapLogs)
        assertEquals(3, model.items.first { it.habitId == "agua" }.step)
        assertFalse(model.items.first { it.habitId == "guitarra" }.tapLogs)
        assertFalse(model.items.first { it.habitId == "fumar" }.tapLogs)
    }
}
