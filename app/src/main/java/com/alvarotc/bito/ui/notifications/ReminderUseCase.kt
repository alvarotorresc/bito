package com.alvarotc.bito.ui.notifications

import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.buildTodayUiState
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * Resolves a fired reminder alarm against the live state before ever notifying: a slot that no
 * longer exists (a deleted hour, an archived habit) self-heals silently, and a slot that still
 * exists only notifies when there is genuinely something pending — anti-spam by construction.
 */
class ReminderUseCase(
    private val domainState: DomainStateRepository,
    private val habits: HabitsRepository,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** What [evaluate] decided to do about a fired (kindName, key) reminder alarm. */
    sealed interface Outcome {
        /** The slot no longer exists — self-heal: no notification, no reschedule. */
        data object Stale : Outcome

        /** The GLOBAL slot still has pending habits. */
        data class Remind(val payload: ReminderPayload, val slot: Slot) : Outcome

        /** The HABIT slot's own card is still open and unfailed. */
        data class RemindHabit(val target: QuickTarget, val slot: Slot) : Outcome

        /** The REVIEW slot found something unsealed or still open today. */
        data class Review(val slot: Slot) : Outcome

        /** Nothing to say, but the slot is still live — reprogram it without notifying. */
        data class Silent(val slot: Slot) : Outcome
    }

    suspend fun evaluate(
        kindName: String,
        key: String,
    ): Outcome {
        val prefs = settings.settings.first()
        val entities = habits.observeHabits().first()
        val slot =
            ReminderScheduler.slotsOf(prefs, entities).find { it.kind.name == kindName && it.key == key }
                ?: return Outcome.Stale
        val today = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())
        val state = buildTodayUiState(domainState.snapshot(), entities.associate { it.id to it.sortOrder }, today)
        return when (slot.kind) {
            SlotKind.GLOBAL -> {
                val payload = buildReminderPayload(state)
                if (payload == null) Outcome.Silent(slot) else Outcome.Remind(payload, slot)
            }
            SlotKind.HABIT -> {
                val card = state.cards.find { it.id == slot.key }
                if (card == null || card.doneToday || card.failed) {
                    Outcome.Silent(slot)
                } else {
                    val amount = if (card.kind == CardKind.COUNTER) card.step else 1
                    Outcome.RemindHabit(QuickTarget(card.id, card.name, amount), slot)
                }
            }
            SlotKind.REVIEW -> if (reviewIsPending(state)) Outcome.Review(slot) else Outcome.Silent(slot)
        }
    }
}
