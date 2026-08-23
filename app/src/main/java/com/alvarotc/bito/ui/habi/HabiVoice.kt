package com.alvarotc.bito.ui.habi

import androidx.annotation.StringRes
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality

/**
 * Resolves which string resource Habi speaks in each of its ten voiced contexts, by
 * [Personality] and (for three of the ten) [Mood]. `res/values{,-es}/strings_habi.xml` holds the
 * 57 mapped strings plus [R.string.habi_name_fallback] — M9 draft copy, pending architect
 * validation (docs/07-textos-personalidades.md).
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
 * - [labelRes] — the speaker label ("SARGENTO"/"CHEERLEADER"/"NEUTRA") every `SpeechBubble` shows
 *   next to "HABI · ". Single source, replacing the seven identical private copies each screen
 *   used to keep.
 * - [formPromptRes] — the habit-form bubble's body, `habi_form_prompt_*`, no placeholders.
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

    @StringRes
    fun labelRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.personality_sargento
            Personality.CHEERLEADER -> R.string.personality_cheerleader
            Personality.NEUTRA -> R.string.personality_neutra
        }

    @StringRes
    fun formPromptRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.habi_form_prompt_sargento
            Personality.CHEERLEADER -> R.string.habi_form_prompt_cheerleader
            Personality.NEUTRA -> R.string.habi_form_prompt_neutra
        }

    /**
     * The short, honest mood adjective [HabiAvatar] folds into its `contentDescription`
     * ("Habi, feeling great") — deliberately mood-only, never personality-flavored: inventing
     * humorous per-personality copy here is a copy call for the architect (docs/07), not something
     * this a11y pass should freelance. `mood_label_*`, unlike every other `HabiVoice` mapping
     * above, carries no `%1$s` name placeholder — a screen reader announces it standalone.
     */
    @StringRes
    fun moodLabelRes(mood: Mood): Int =
        when (mood) {
            Mood.RADIANT -> R.string.mood_label_radiant
            Mood.NORMAL -> R.string.mood_label_normal
            Mood.WILTED -> R.string.mood_label_wilted
            Mood.DRAMATIC -> R.string.mood_label_dramatic
        }
}
