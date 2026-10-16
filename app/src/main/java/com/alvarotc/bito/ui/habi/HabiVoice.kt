package com.alvarotc.bito.ui.habi

import androidx.annotation.StringRes
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.TextoHabi

/**
 * Resolves which string resource Habi speaks in each of its thirteen voiced contexts, by
 * [Personality] and (for three of the thirteen) [Mood]. `res/values{,-es}/strings_habi.xml` holds
 * the 66 mapped strings — M9 draft copy, pending architect
 * validation (docs/07-textos-personalidades.md).
 *
 * - [bubbleRes] — the Stats commentator's card AND the Habi screen's own bubble (T11) both used
 *   to share this mapping; T13 splits the Habi screen off into [homeRes] instead, since the
 *   mockup gives it its own playful, name-addressed voice ("¿Me has traído algo, %1$s?").
 *   [bubbleRes] now backs Stats alone.
 * - [homeRes] — the Habi screen bubble, playful home-context lines, `%1$s` = the user's name.
 * - [freezerInfoRes] — the freezers ⓘ sheet body, personality-only (no mood), `%1$s` = the
 *   user's name. Deliberately never bakes the freezer price in — that lives in [com.alvarotc.bito.domain.model.EconomyConfig].
 * - [perfectDayRes] — the E2 perfect-day in-app sheet, personality-only, `%1$s` = the user's name.
 * - [perfectDayNotifRes] — the perfect-day notification body, personality-only, `%1$s` = the
 *   user's name.
 * - [badgeUnlockedRes] — the badge-unlocked sheet, personality-only, `%1$s` = the user's name and
 *   `%2$s` = the badge name.
 * - [taskDoneRes] — the Today task row's undo-snackbar text (T13), personality-only, `%1$s` =
 *   the user's name — except `habi_task_done_sargento`, which deliberately carries no name.
 * - [breathingDoneRes] — the closing line after breathing (M11), personality-only, `%1$s` = the
 *   user's name, the same line for a completed session as for a stopped one.
 * - [taskFocusRes] — [com.alvarotc.bito.ui.tasks.FocusScreen]'s entrance line, spoken once a
 *   session starts (T19), personality-only, `%1$s` = the user's name and `%2$d` = the chosen
 *   minutes — only `habi_task_focus_sargento` uses the second placeholder; the other two ignore it.
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
    fun freezerInfoRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.freezer_info_sargento
            Personality.CHEERLEADER -> R.string.freezer_info_cheerleader
            Personality.NEUTRA -> R.string.freezer_info_neutra
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
    fun taskDoneRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.habi_task_done_sargento
            Personality.CHEERLEADER -> R.string.habi_task_done_cheerleader
            Personality.NEUTRA -> R.string.habi_task_done_neutra
        }

    /** M11: la frase de cierre tras respirar, igual para una sesion completa que para una parada. */
    @StringRes
    fun breathingDoneRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.habi_breathing_done_sargento
            Personality.CHEERLEADER -> R.string.habi_breathing_done_cheerleader
            Personality.NEUTRA -> R.string.habi_breathing_done_neutra
        }

    @StringRes
    fun taskFocusRes(personality: Personality): Int =
        when (personality) {
            Personality.SARGENTO -> R.string.habi_task_focus_sargento
            Personality.CHEERLEADER -> R.string.habi_task_focus_cheerleader
            Personality.NEUTRA -> R.string.habi_task_focus_neutra
        }

    /**
     * Decision 9: la variante de un texto con `%1$s` para quien no ha dado su nombre. Tabla
     * explicita; `null` si el texto no tiene gemelo (los contextos que no llevan nombre, o los
     * que aun no lo necesitan). La variante dice lo mismo sin llamar a nadie por nada y conserva
     * la numeracion de los demas huecos (`%2$s`, `%2$d`).
     */
    @StringRes
    fun sinNombre(
        @StringRes res: Int,
    ): Int? = SIN_NOMBRE[res]

    /**
     * El texto de Habi en [res] con [nombre], o su gemelo [sinNombre] si no hay nombre (vacio o en
     * blanco). [extra] son los huecos que siguen al nombre (`%2$s`...); sin nombre se conserva su
     * posicion con un primer argumento vacio que el gemelo no usa.
     */
    fun conNombre(
        @StringRes res: Int,
        nombre: String,
        vararg extra: Any,
    ): TextoHabi =
        if (nombre.isBlank()) {
            val gemelo = requireNotNull(sinNombre(res)) { "El texto $res no tiene variante sin nombre" }
            TextoHabi.Recurso(gemelo, if (extra.isEmpty()) emptyList() else listOf<Any>("") + extra)
        } else {
            TextoHabi.Recurso(res, listOf<Any>(nombre) + extra)
        }

    private val SIN_NOMBRE: Map<Int, Int> =
        mapOf(
            R.string.habi_home_sargento_radiant to R.string.habi_home_sargento_radiant_sin_nombre,
            R.string.habi_home_sargento_normal to R.string.habi_home_sargento_normal_sin_nombre,
            R.string.habi_home_sargento_wilted to R.string.habi_home_sargento_wilted_sin_nombre,
            R.string.habi_home_sargento_dramatic to R.string.habi_home_sargento_dramatic_sin_nombre,
            R.string.habi_home_cheerleader_radiant to R.string.habi_home_cheerleader_radiant_sin_nombre,
            R.string.habi_home_cheerleader_normal to R.string.habi_home_cheerleader_normal_sin_nombre,
            R.string.habi_home_cheerleader_wilted to R.string.habi_home_cheerleader_wilted_sin_nombre,
            R.string.habi_home_cheerleader_dramatic to R.string.habi_home_cheerleader_dramatic_sin_nombre,
            R.string.habi_home_neutra_radiant to R.string.habi_home_neutra_radiant_sin_nombre,
            R.string.habi_home_neutra_normal to R.string.habi_home_neutra_normal_sin_nombre,
            R.string.habi_home_neutra_wilted to R.string.habi_home_neutra_wilted_sin_nombre,
            R.string.habi_home_neutra_dramatic to R.string.habi_home_neutra_dramatic_sin_nombre,
            R.string.habi_badge_sargento to R.string.habi_badge_sargento_sin_nombre,
            R.string.habi_task_focus_sargento to R.string.habi_task_focus_sargento_sin_nombre,
            R.string.habi_breathing_done_sargento to R.string.habi_breathing_done_sargento_sin_nombre,
            R.string.freezer_info_sargento to R.string.freezer_info_sargento_sin_nombre,
            R.string.habi_task_done_sargento to R.string.habi_task_done_sargento_sin_nombre,
            R.string.habi_perfect_notif_sargento to R.string.habi_perfect_notif_sargento_sin_nombre,
            R.string.habi_badge_cheerleader to R.string.habi_badge_cheerleader_sin_nombre,
            R.string.habi_task_focus_cheerleader to R.string.habi_task_focus_cheerleader_sin_nombre,
            R.string.habi_breathing_done_cheerleader to R.string.habi_breathing_done_cheerleader_sin_nombre,
            R.string.freezer_info_cheerleader to R.string.freezer_info_cheerleader_sin_nombre,
            R.string.habi_task_done_cheerleader to R.string.habi_task_done_cheerleader_sin_nombre,
            R.string.habi_perfect_notif_cheerleader to R.string.habi_perfect_notif_cheerleader_sin_nombre,
            R.string.habi_badge_neutra to R.string.habi_badge_neutra_sin_nombre,
            R.string.habi_task_focus_neutra to R.string.habi_task_focus_neutra_sin_nombre,
            R.string.habi_breathing_done_neutra to R.string.habi_breathing_done_neutra_sin_nombre,
            R.string.freezer_info_neutra to R.string.freezer_info_neutra_sin_nombre,
            R.string.habi_task_done_neutra to R.string.habi_task_done_neutra_sin_nombre,
            R.string.habi_perfect_notif_neutra to R.string.habi_perfect_notif_neutra_sin_nombre,
            R.string.habi_perfect_sargento to R.string.habi_perfect_sargento_sin_nombre,
            R.string.habi_perfect_cheerleader to R.string.habi_perfect_cheerleader_sin_nombre,
            R.string.habi_perfect_neutra to R.string.habi_perfect_neutra_sin_nombre,
            R.string.onb_personality_preview_sargento to R.string.onb_personality_preview_sargento_sin_nombre,
            R.string.onb_personality_preview_cheerleader to R.string.onb_personality_preview_cheerleader_sin_nombre,
            R.string.onb_personality_preview_neutra to R.string.onb_personality_preview_neutra_sin_nombre,
        )

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
