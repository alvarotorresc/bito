package com.alvarotc.bito.ui.review

import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.ui.today.HabitCardUi

/**
 * What the nightly review asks about: AT_LEAST cards not yet done, and clean ZERO/AT_MOST cards
 * while today is unsealed (their seal IS the answer). Failed cards (relapse, blown limit) never
 * appear — nothing can be fixed, nothing is pointed at (same rule as reminders, anti-sargento).
 */
fun reviewRowsOf(
    cards: List<HabitCardUi>,
    todaySealed: Boolean,
): List<HabitCardUi> =
    cards.filter { card ->
        !card.failed &&
            when (card.direction) {
                Direction.AT_LEAST -> !card.doneToday
                Direction.AT_MOST, Direction.ZERO -> !todaySealed
            }
    }
