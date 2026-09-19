package com.alvarotc.bito.ui.notifications

import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.logicalClockAt
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.today.CardKind
import com.alvarotc.bito.ui.today.HabitCardUi
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
    private val rewards: RewardsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** What [evaluate] decided to do about a fired (kindName, key) reminder alarm. */
    sealed interface Outcome {
        /** The slot no longer exists — self-heal: no notification, no reschedule. */
        data object Stale : Outcome

        /**
         * The GLOBAL slot still has pending habits. [personality] and [userName] travel with it
         * so the receiver renders in Habi's voice without a second settings read. [spec] paints
         * the notification's large icon — the same face the Habi and Today screens show.
         */
        data class Remind(
            val payload: ReminderPayload,
            val slot: Slot,
            val personality: Personality,
            val userName: String,
            val spec: HabiSpec,
        ) : Outcome

        /**
         * The HABIT slot's own card is still open and unfailed. [target] is `null` for kinds
         * with no honest quick action (DURATION, ABSTINENCE — see [quickTargetOf]): the
         * notification still fires under [name], just without an action button.
         */
        data class RemindHabit(val name: String, val target: QuickTarget?, val slot: Slot) : Outcome

        /**
         * The REVIEW slot found something unsealed or still open today. [pendingCount] is how
         * many rows today's review still has to decide (zero = only past days owed a seal);
         * [personality] and [userName] voice the nudge, and [spec] paints the large icon.
         */
        data class Review(
            val slot: Slot,
            val pendingCount: Int,
            val personality: Personality,
            val userName: String,
            val spec: HabiSpec,
        ) : Outcome

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
        val clock = logicalClockAt(now(), prefs.dayCutoffMinutes, zone())
        val owned = rewards.observeOwnedItems().first()
        val state =
            buildTodayUiState(
                domainState.snapshot(),
                entities.associate { it.id to it.sortOrder },
                clock.today,
                prefs.personality,
                owned,
                prefs.userName,
                minutesOfDay = clock.minutesOfDay,
                reviewTimeMinutes = prefs.reviewTimeMinutes,
                eyesPainted = prefs.habiEyesPainted,
            )
        return when (slot.kind) {
            SlotKind.GLOBAL -> {
                val payload = buildReminderPayload(state)
                if (payload == null) {
                    Outcome.Silent(slot)
                } else {
                    Outcome.Remind(payload, slot, prefs.personality, prefs.userName, state.spec)
                }
            }
            SlotKind.HABIT -> {
                val card = state.cards.find { it.id == slot.key }
                if (card == null || card.doneToday || card.failed) {
                    Outcome.Silent(slot)
                } else {
                    Outcome.RemindHabit(card.name, quickTargetOf(card), slot)
                }
            }
            SlotKind.REVIEW ->
                if (reviewIsPending(state)) {
                    Outcome.Review(slot, reviewPendingCount(state), prefs.personality, prefs.userName, state.spec)
                } else {
                    Outcome.Silent(slot)
                }
        }
    }
}

/**
 * The quick action a personal HABIT reminder can honestly offer for [card], or `null` when
 * there isn't one: a DURATION or ABSTINENCE card has no single-tap action that logs the right
 * thing (a "+1" on an abstinence habit would log a relapse — the exact anti-sargento violation
 * [buildReminderPayload] already avoids on the GLOBAL path). Only CHECK/COUNTER get a button;
 * everything else still gets its reminder, just without one.
 */
fun quickTargetOf(card: HabitCardUi): QuickTarget? =
    when (card.kind) {
        CardKind.CHECK -> QuickTarget(card.id, card.name, amount = 1, isCheck = true)
        CardKind.COUNTER -> QuickTarget(card.id, card.name, amount = card.step, isCheck = false)
        CardKind.DURATION, CardKind.ABSTINENCE -> null
    }
