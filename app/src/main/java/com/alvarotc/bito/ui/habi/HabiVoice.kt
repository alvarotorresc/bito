package com.alvarotc.bito.ui.habi

import androidx.annotation.StringRes
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality

/**
 * Resolves which string resource Habi speaks in each of its ten voiced contexts, by
 * [Personality] and (for three of the ten) [Mood]. Every resource here is PROVISIONAL copy —
 * `res/values{,-es}/strings_habi.xml` holds the 57 mapped strings plus [R.string.habi_name_fallback];
 * the definitive wording lands in the M9 economy/copy session.
 *
 * - [bubbleRes] — the Stats commentator's card AND the Habi screen's own bubble (T11) both used
 *   to share this mapping; T13 splits the Habi screen off into [homeRes] instead, since the
 *   mockup gives it its own playful, name-addressed voice ("¿Me has traído algo, %1$s?").
 *   [bubbleRes] now backs Stats alone.
 * - [homeRes] — the Habi screen bubble, playful home-context lines, `%1$s` = the user's name.
 * - [greetingRes] — the Hoy corner (consumed by T14), short, `%1$s` = the user's name.
 * - [freezerInfoRes] — the freezers ⓘ sheet body, personality-only (no mood), `%1$s` = the
 *   user's name. Deliberately never bakes the freezer price in — that lives in [com.alvarotc.bito.domain.model.EconomyConfig].
 * - [reviewRes] — the E1 review mini-bubble, personality-only, `%1$s` = the user's name.
 * - [reviewClearRes] — the E1 celebratory empty-review state, personality-only, `%1$s` = the
 *   user's name.
 * - [sealedRes] — the E2 normal day-sealed sheet, personality-only, `%1$s` = the user's name.
 * - [perfectDayRes] — the E2 perfect-day in-app sheet, personality-only, `%1$s` = the user's name.
 * - [perfectDayNotifRes] — the perfect-day notification body, personality-only, `%1$s` = the
 *   user's name.
 * - [badgeUnlockedRes] — the badge-unlocked sheet, personality-only, `%1$s` = the user's name and
 *   `%2$s` = the badge name.
 */
object HabiVoice {
    @StringRes
    fun bubbleRes(
        mood: Mood,
        personality: Personality,
    ): Int =
        when (personality) {
            Personality.SARGENTO ->
                when (mood) {
                    Mood.RADIANT -> R.string.habi_bubble_sargento_radiant
                    Mood.NORMAL -> R.string.habi_bubble_sargento_normal
                    Mood.WILTED -> R.string.habi_bubble_sargento_wilted
                    Mood.DRAMATIC -> R.string.habi_bubble_sargento_dramatic
                }
            Personality.CHEERLEADER ->
                when (mood) {
                    Mood.RADIANT -> R.string.habi_bubble_cheerleader_radiant
                    Mood.NORMAL -> R.string.habi_bubble_cheerleader_normal
                    Mood.WILTED -> R.string.habi_bubble_cheerleader_wilted
                    Mood.DRAMATIC -> R.string.habi_bubble_cheerleader_dramatic
                }
            Personality.NEUTRA ->
                when (mood) {
                    Mood.RADIANT -> R.string.habi_bubble_neutra_radiant
                    Mood.NORMAL -> R.string.habi_bubble_neutra_normal
                    Mood.WILTED -> R.string.habi_bubble_neutra_wilted
                    Mood.DRAMATIC -> R.string.habi_bubble_neutra_dramatic
                }
        }

    @StringRes
    fun homeRes(
        mood: Mood,
        personality: Personality,
    ): Int =
        when (personality) {
            Personality.SARGENTO ->
                when (mood) {
                    Mood.RADIANT -> R.string.habi_home_sargento_radiant
                    Mood.NORMAL -> R.string.habi_home_sargento_normal
                    Mood.WILTED -> R.string.habi_home_sargento_wilted
                    Mood.DRAMATIC -> R.string.habi_home_sargento_dramatic
                }
            Personality.CHEERLEADER ->
                when (mood) {
                    Mood.RADIANT -> R.string.habi_home_cheerleader_radiant
                    Mood.NORMAL -> R.string.habi_home_cheerleader_normal
                    Mood.WILTED -> R.string.habi_home_cheerleader_wilted
                    Mood.DRAMATIC -> R.string.habi_home_cheerleader_dramatic
                }
            Personality.NEUTRA ->
                when (mood) {
                    Mood.RADIANT -> R.string.habi_home_neutra_radiant
                    Mood.NORMAL -> R.string.habi_home_neutra_normal
                    Mood.WILTED -> R.string.habi_home_neutra_wilted
                    Mood.DRAMATIC -> R.string.habi_home_neutra_dramatic
                }
        }

    @StringRes
    fun greetingRes(
        mood: Mood,
        personality: Personality,
    ): Int =
        when (personality) {
            Personality.SARGENTO ->
                when (mood) {
                    Mood.RADIANT -> R.string.habi_greeting_sargento_radiant
                    Mood.NORMAL -> R.string.habi_greeting_sargento_normal
                    Mood.WILTED -> R.string.habi_greeting_sargento_wilted
                    Mood.DRAMATIC -> R.string.habi_greeting_sargento_dramatic
                }
            Personality.CHEERLEADER ->
                when (mood) {
                    Mood.RADIANT -> R.string.habi_greeting_cheerleader_radiant
                    Mood.NORMAL -> R.string.habi_greeting_cheerleader_normal
                    Mood.WILTED -> R.string.habi_greeting_cheerleader_wilted
                    Mood.DRAMATIC -> R.string.habi_greeting_cheerleader_dramatic
                }
            Personality.NEUTRA ->
                when (mood) {
                    Mood.RADIANT -> R.string.habi_greeting_neutra_radiant
                    Mood.NORMAL -> R.string.habi_greeting_neutra_normal
                    Mood.WILTED -> R.string.habi_greeting_neutra_wilted
                    Mood.DRAMATIC -> R.string.habi_greeting_neutra_dramatic
                }
        }

    @StringRes
    fun freezerInfoRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.freezer_info_sargento
            Personality.CHEERLEADER -> R.string.freezer_info_cheerleader
            Personality.NEUTRA -> R.string.freezer_info_neutra
        }

    @StringRes
    fun reviewRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.habi_review_sargento
            Personality.CHEERLEADER -> R.string.habi_review_cheerleader
            Personality.NEUTRA -> R.string.habi_review_neutra
        }

    @StringRes
    fun reviewClearRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.habi_review_clear_sargento
            Personality.CHEERLEADER -> R.string.habi_review_clear_cheerleader
            Personality.NEUTRA -> R.string.habi_review_clear_neutra
        }

    @StringRes
    fun sealedRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.habi_sealed_sargento
            Personality.CHEERLEADER -> R.string.habi_sealed_cheerleader
            Personality.NEUTRA -> R.string.habi_sealed_neutra
        }

    @StringRes
    fun perfectDayRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.habi_perfect_sargento
            Personality.CHEERLEADER -> R.string.habi_perfect_cheerleader
            Personality.NEUTRA -> R.string.habi_perfect_neutra
        }

    @StringRes
    fun perfectDayNotifRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.habi_perfect_notif_sargento
            Personality.CHEERLEADER -> R.string.habi_perfect_notif_cheerleader
            Personality.NEUTRA -> R.string.habi_perfect_notif_neutra
        }

    @StringRes
    fun badgeUnlockedRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.habi_badge_sargento
            Personality.CHEERLEADER -> R.string.habi_badge_cheerleader
            Personality.NEUTRA -> R.string.habi_badge_neutra
        }
}
