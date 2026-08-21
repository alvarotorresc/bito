package com.alvarotc.bito.ui.review

import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.HabitCardUi
import kotlin.test.Test
import kotlin.test.assertEquals
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

class ReviewRowsTest {
    @Test
    fun `undone positives are rows, done ones are not`() {
        val rows = reviewRowsOf(listOf(card("a"), card("b", done = true)), todaySealed = false)
        assertEquals(listOf("a"), rows.map { it.id })
    }

    @Test
    fun `clean abstinences and limits are rows until today is sealed`() {
        val clean = card("nofap", kind = CardKind.ABSTINENCE, done = true).copy(direction = Direction.ZERO)
        val limit = card("redes", kind = CardKind.DURATION, done = true).copy(direction = Direction.AT_MOST)
        assertEquals(listOf("nofap", "redes"), reviewRowsOf(listOf(clean, limit), todaySealed = false).map { it.id })
        assertTrue(reviewRowsOf(listOf(clean, limit), todaySealed = true).isEmpty())
    }

    @Test
    fun `failed cards never appear`() {
        val relapsed = card("nofap", kind = CardKind.ABSTINENCE).copy(direction = Direction.ZERO, failed = true)
        val blown = card("redes", kind = CardKind.DURATION).copy(direction = Direction.AT_MOST, failed = true)
        assertTrue(reviewRowsOf(listOf(relapsed, blown), todaySealed = false).isEmpty())
    }
}
