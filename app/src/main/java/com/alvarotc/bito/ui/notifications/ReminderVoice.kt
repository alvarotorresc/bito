package com.alvarotc.bito.ui.notifications

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Personality

/**
 * Which part of the day a reminder fires in — the same hour list reads differently at 09:00
 * (lay the day out) than at 15:00 (progress so far) or at 21:00 (what's left to close).
 */
enum class ReminderFlavor { MORNING, AFTERNOON, EVENING }

/**
 * Resolves what a reminder or review notification says, by [Personality] and — for the GLOBAL
 * reminder — [ReminderFlavor]. A pure lookup table in the [com.alvarotc.bito.ui.habi.HabiVoice]
 * mold: this decides WHICH string speaks; [Notifier] renders it. Copy lives in
 * `res/values{,-es}/strings_notifications.xml`, written in each personality's docs/07 register.
 *
 * Placeholder contracts (every sibling key keeps the same one so the render call is uniform):
 * - [titleRes] / [reviewTitleRes] — `%1$s` = the user's name.
 * - [bodyRes] — plurals keyed by the pending count: `%1$d` = pending count, `%2$s` = up to
 *   [MAX_NAMED_PENDING] pending names joined, `%3$d` = done today, `%4$d` = total today.
 * - [reviewBodyRes] — plurals keyed by the still-to-decide count: `%1$d` = that count.
 * - [reviewSealOnlyRes] — no placeholders: nothing to decide today, only past days unsealed.
 */
object ReminderVoice {
    /** The GLOBAL body names at most this many pending habits — the count still says the rest. */
    const val MAX_NAMED_PENDING = 3

    private const val MORNING_START = 5 * 60
    private const val AFTERNOON_START = 12 * 60
    private const val EVENING_START = 18 * 60

    /**
     * [minutesOfDay] is wall-clock minutes since midnight (a GLOBAL slot's configured hour, or
     * "now" for a tray refresh). Evening wraps midnight: an 00:30 reminder is closing yesterday's
     * business, not planning a day that hasn't dawned.
     */
    fun flavorOf(minutesOfDay: Int): ReminderFlavor =
        when (minutesOfDay) {
            in MORNING_START until AFTERNOON_START -> ReminderFlavor.MORNING
            in AFTERNOON_START until EVENING_START -> ReminderFlavor.AFTERNOON
            else -> ReminderFlavor.EVENING
        }

    @StringRes
    fun titleRes(
        personality: Personality,
        flavor: ReminderFlavor,
    ): Int =
        when (personality) {
            Personality.SARGENTO ->
                when (flavor) {
                    ReminderFlavor.MORNING -> R.string.notif_reminder_title_sargento_morning
                    ReminderFlavor.AFTERNOON -> R.string.notif_reminder_title_sargento_afternoon
                    ReminderFlavor.EVENING -> R.string.notif_reminder_title_sargento_evening
                }
            Personality.CHEERLEADER ->
                when (flavor) {
                    ReminderFlavor.MORNING -> R.string.notif_reminder_title_cheerleader_morning
                    ReminderFlavor.AFTERNOON -> R.string.notif_reminder_title_cheerleader_afternoon
                    ReminderFlavor.EVENING -> R.string.notif_reminder_title_cheerleader_evening
                }
            Personality.NEUTRA ->
                when (flavor) {
                    ReminderFlavor.MORNING -> R.string.notif_reminder_title_neutra_morning
                    ReminderFlavor.AFTERNOON -> R.string.notif_reminder_title_neutra_afternoon
                    ReminderFlavor.EVENING -> R.string.notif_reminder_title_neutra_evening
                }
        }

    @PluralsRes
    fun bodyRes(
        personality: Personality,
        flavor: ReminderFlavor,
    ): Int =
        when (personality) {
            Personality.SARGENTO ->
                when (flavor) {
                    ReminderFlavor.MORNING -> R.plurals.notif_reminder_body_sargento_morning
                    ReminderFlavor.AFTERNOON -> R.plurals.notif_reminder_body_sargento_afternoon
                    ReminderFlavor.EVENING -> R.plurals.notif_reminder_body_sargento_evening
                }
            Personality.CHEERLEADER ->
                when (flavor) {
                    ReminderFlavor.MORNING -> R.plurals.notif_reminder_body_cheerleader_morning
                    ReminderFlavor.AFTERNOON -> R.plurals.notif_reminder_body_cheerleader_afternoon
                    ReminderFlavor.EVENING -> R.plurals.notif_reminder_body_cheerleader_evening
                }
            Personality.NEUTRA ->
                when (flavor) {
                    ReminderFlavor.MORNING -> R.plurals.notif_reminder_body_neutra_morning
                    ReminderFlavor.AFTERNOON -> R.plurals.notif_reminder_body_neutra_afternoon
                    ReminderFlavor.EVENING -> R.plurals.notif_reminder_body_neutra_evening
                }
        }

    @StringRes
    fun reviewTitleRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.notif_review_title_sargento
            Personality.CHEERLEADER -> R.string.notif_review_title_cheerleader
            Personality.NEUTRA -> R.string.notif_review_title_neutra
        }

    @PluralsRes
    fun reviewBodyRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.plurals.notif_review_body_sargento
            Personality.CHEERLEADER -> R.plurals.notif_review_body_cheerleader
            Personality.NEUTRA -> R.plurals.notif_review_body_neutra
        }

    @StringRes
    fun reviewSealOnlyRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.notif_review_seal_sargento
            Personality.CHEERLEADER -> R.string.notif_review_seal_cheerleader
            Personality.NEUTRA -> R.string.notif_review_seal_neutra
        }
}
