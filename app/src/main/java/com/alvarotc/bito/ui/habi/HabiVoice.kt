package com.alvarotc.bito.ui.habi

import androidx.annotation.StringRes
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.HabiDayPhase
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality

/**
 * Resolves which string resource Habi speaks in each of its twelve voiced contexts, by
 * [Personality] and (for three of the twelve) [Mood]. `res/values{,-es}/strings_habi.xml` holds
 * the 69 mapped strings plus [R.string.habi_name_fallback] — final copy, validated against
 * `docs/07-textos-personalidades.md`.
 *
 * - [bubbleRes] — the Stats commentator's card AND the Habi screen's own bubble (T11) both used
 *   to share this mapping; T13 splits the Habi screen off into [homeRes] instead, since the
 *   mockup gives it its own playful, name-addressed voice ("¿Me has traído algo, %1$s?").
 *   [bubbleRes] now backs Stats alone.
 * - [homeRes] — the Habi screen bubble, playful home-context lines, `%1$s` = the user's name.
 * - [greetingRes] — the Hoy corner (consumed by T14), short, `%1$s` = the user's name. Kept in
 *   full for this milestone (spec §12.1): the Hoy corner is not going body-only.
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
 *   `%2$s` = the badge name. Backs every badge EXCEPT `streak-7`, where `BadgeUnlockSheet`
 *   substitutes [eyeRitualRes] instead (one voice, not two — see that function's kdoc).
 * - [labelRes] — the speaker label ("SARGENTO"/"CHEERLEADER"/"NEUTRA") every `SpeechBubble` shows
 *   next to "HABI · ". Single source, replacing the seven identical private copies each screen
 *   used to keep.
 * - [formPromptRes] — the habit-form bubble's body, `habi_form_prompt_*`, no placeholders.
 * - [dayPhaseRes] — the Habi screen's own day line (biblia §7.2), `habi_day_waiting_*` /
 *   `habi_day_asleep_*`, no placeholders, a dropper: two lines and only two, and only on the Habi
 *   screen. Returns `null` for [HabiDayPhase.AWAKE], on purpose — see that function's kdoc.
 * - [eyeRitualRes] — the eye ritual's surface text (biblia §4), `habi_eye_first_*` /
 *   `habi_eye_second_*`, `%1$s` = the user's name. Never sits over the gesture itself (silent,
 *   wordless) — this is what the SCREEN shows after the paint completes: 7g's own line for the
 *   first eye, the `streak-7` badge sheet's for the second.
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

    /** 7f's per-personality preview line — the first thing each voice says to the user (docs/07 §4.4). */
    @StringRes
    fun onboardingPreviewRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.onb_personality_preview_sargento
            Personality.CHEERLEADER -> R.string.onb_personality_preview_cheerleader
            Personality.NEUTRA -> R.string.onb_personality_preview_neutra
        }

    @StringRes
    fun formPromptRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.habi_form_prompt_sargento
            Personality.CHEERLEADER -> R.string.habi_form_prompt_cheerleader
            Personality.NEUTRA -> R.string.habi_form_prompt_neutra
        }

    /**
     * Returns `null` for [HabiDayPhase.AWAKE], on purpose (biblia §7.2): her day speaks with a
     * dropper, two lines and only two, and only from the Habi screen. `HabiUiState.dayLineRes`
     * only ever passes [HabiDayPhase.WAITING] or [HabiDayPhase.ASLEEP] in practice, but the null
     * branch is real, not a `when` formality — a day that narrates itself while she's still awake
     * stops being a mirror and turns into a commentator.
     */
    @StringRes
    fun dayPhaseRes(
        phase: HabiDayPhase,
        personality: Personality,
    ): Int? =
        when (phase) {
            HabiDayPhase.AWAKE -> null
            HabiDayPhase.WAITING ->
                when (personality) {
                    Personality.SARGENTO -> R.string.habi_day_waiting_sargento
                    Personality.CHEERLEADER -> R.string.habi_day_waiting_cheerleader
                    Personality.NEUTRA -> R.string.habi_day_waiting_neutra
                }
            HabiDayPhase.ASLEEP ->
                when (personality) {
                    Personality.SARGENTO -> R.string.habi_day_asleep_sargento
                    Personality.CHEERLEADER -> R.string.habi_day_asleep_cheerleader
                    Personality.NEUTRA -> R.string.habi_day_asleep_neutra
                }
        }

    /**
     * [level] is [com.alvarotc.bito.domain.EyeTransition.to] — the number of eyes painted AFTER
     * the transition this line accompanies (1 = the first, 2 = the second/`streak-7`). Only ever
     * called with 1 or 2: a transition TO 0 doesn't exist ([com.alvarotc.bito.domain.EyeRitual]
     * only ever heals upward). Never a bubble over the gesture itself — the paint always happens
     * in silence, wordless — this is the line the surface that follows it speaks: onboarding 7g
     * for the first eye, `BadgeUnlockSheet` for the second, where it REPLACES [badgeUnlockedRes]
     * for the `streak-7` badge instead of stacking a second bubble next to it (one voice, not two).
     */
    @StringRes
    fun eyeRitualRes(
        level: Int,
        personality: Personality,
    ): Int =
        when (level) {
            1 ->
                when (personality) {
                    Personality.SARGENTO -> R.string.habi_eye_first_sargento
                    Personality.CHEERLEADER -> R.string.habi_eye_first_cheerleader
                    Personality.NEUTRA -> R.string.habi_eye_first_neutra
                }
            2 ->
                when (personality) {
                    Personality.SARGENTO -> R.string.habi_eye_second_sargento
                    Personality.CHEERLEADER -> R.string.habi_eye_second_cheerleader
                    Personality.NEUTRA -> R.string.habi_eye_second_neutra
                }
            else -> error("eyeRitualRes only maps a transition TO 1 or 2, got $level")
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
