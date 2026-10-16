package com.alvarotc.bito.ui.notifications

import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.HabitCardUi
import com.alvarotc.bito.ui.today.TodayUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun card(
    id: String,
    kind: CardKind = CardKind.CHECK,
    done: Boolean = false,
) = HabitCardUi(
    id = id,
    name = id,
    kind = kind,
    progress = 0,
    target = 1,
    unit = null,
    step = 1,
    direction = Direction.AT_LEAST,
    period = Period.DAY,
    doneToday = done,
    failed = false,
    streak = 0,
)

private fun counterCard(
    id: String,
    step: Int,
) = card(id, kind = CardKind.COUNTER, done = false).copy(step = step)

private fun limitCard(clean: Boolean) =
    card(id = "limit", kind = CardKind.COUNTER, done = clean).copy(
        direction = Direction.AT_MOST,
        failed = !clean,
    )

private fun state(vararg cards: HabitCardUi) = TodayUiState(cards = cards.toList(), loading = false)

class ReminderContentTest {
    @Test
    fun `nothing pending means no notification at all`() {
        assertNull(buildReminderPayload(state(card("cama", done = true))))
    }

    @Test
    fun `a clean limit habit is not pending — fulfilled never makes noise`() {
        assertNull(buildReminderPayload(state(limitCard(clean = true))))
    }

    @Test
    fun `quick targets cap at three and carry the counter step`() {
        val p = buildReminderPayload(state(card("a"), card("b"), counterCard("c", step = 5), card("d")))!!
        assertEquals(3, p.targets.size)
        assertEquals(5, p.targets.first { it.habitId == "c" }.amount)
        assertFalse(p.targets.first { it.habitId == "c" }.isCheck)
        assertTrue(p.targets.first { it.habitId == "a" }.isCheck)
        assertEquals(4, p.pendingNames.size)
    }

    @Test
    fun `duration and abstinence habits are listed but not quick-actionable`() {
        val abstinence = card("nofap", kind = CardKind.ABSTINENCE).copy(direction = Direction.ZERO)
        val p = buildReminderPayload(state(card("d", kind = CardKind.DURATION), abstinence))!!
        assertEquals(2, p.pendingNames.size)
        assertTrue(p.targets.isEmpty())
    }

    @Test
    fun `an exceeded limit habit is neither listed nor quick-actionable`() {
        assertNull(buildReminderPayload(state(limitCard(clean = false))))

        val p = buildReminderPayload(state(limitCard(clean = false), card("other")))!!
        assertEquals(listOf("other"), p.pendingNames)
        assertTrue(p.targets.none { it.habitId == "limit" })
    }

    @Test
    fun `a relapsed abstinence today makes no reminder noise`() {
        val relapsed = card("nofap", kind = CardKind.ABSTINENCE).copy(direction = Direction.ZERO, failed = true)
        assertNull(buildReminderPayload(state(relapsed)))
    }

    @Test
    fun `a check card ignores its step and a duration card ahead of it does not steal a target slot`() {
        val p = buildReminderPayload(state(card("d", kind = CardKind.DURATION), card("chk").copy(step = 5)))!!
        assertEquals(1, p.targets.size)
        assertEquals(1, p.targets.single().amount)
        assertTrue(p.targets.single().isCheck)
    }

    @Test
    fun `the payload counts progress, and a failed card leaves both sides of the fraction`() {
        val p = buildReminderPayload(state(card("hecha", done = true), card("abierta"), limitCard(clean = false)))!!
        assertEquals(1, p.doneCount)
        assertEquals(2, p.totalCount)
        assertEquals(listOf("abierta"), p.pendingNames)
    }

    @Test
    fun `an all-pending morning counts zero done over the full board`() {
        val p = buildReminderPayload(state(card("a"), card("b"), card("c")))!!
        assertEquals(0, p.doneCount)
        assertEquals(3, p.totalCount)
    }
}
