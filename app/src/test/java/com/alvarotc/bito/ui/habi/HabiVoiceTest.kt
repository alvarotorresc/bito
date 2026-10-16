package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [HabiVoice] is a pure lookup table — every (personality, mood) pair its mood-bearing functions
 * cover, and every personality its personality-only functions ([HabiVoice.freezerInfoRes],
 * [HabiVoice.perfectDayRes], [HabiVoice.perfectDayNotifRes], [HabiVoice.badgeUnlockedRes]) cover, must resolve to its OWN
 * string resource. A collision here would mean two contexts silently share copy, which the T13
 * brief treats as a bug (a mapping "sin colisiones").
 */
class HabiVoiceTest {
    @Test
    fun `bubbleRes resolves a distinct resource for every personality-mood pair`() {
        assertAllDistinct(Personality.entries.size * Mood.entries.size, HabiVoice::bubbleRes)
    }

    @Test
    fun `homeRes resolves a distinct resource for every personality-mood pair`() {
        assertAllDistinct(Personality.entries.size * Mood.entries.size, HabiVoice::homeRes)
    }

    @Test
    fun `freezerInfoRes resolves a distinct resource per personality`() {
        val ids = Personality.entries.map { HabiVoice.freezerInfoRes(it) }

        assertEquals(Personality.entries.size, ids.toSet().size)
    }

    @Test
    fun `onboardingPreviewRes resolves a distinct resource per personality`() {
        val ids = Personality.entries.map { HabiVoice.onboardingPreviewRes(it) }

        assertEquals(Personality.entries.size, ids.toSet().size)
    }

    @Test
    fun `perfectDayRes resolves a distinct resource per personality`() {
        assertAllDistinctPersonalityOnly(HabiVoice::perfectDayRes)
    }

    @Test
    fun `perfectDayNotifRes resolves a distinct resource per personality`() {
        assertAllDistinctPersonalityOnly(HabiVoice::perfectDayNotifRes)
    }

    @Test
    fun `badgeUnlockedRes resolves a distinct resource per personality`() {
        assertAllDistinctPersonalityOnly(HabiVoice::badgeUnlockedRes)
    }

    @Test
    fun `taskDoneRes resolves a distinct resource per personality`() {
        assertAllDistinctPersonalityOnly(HabiVoice::taskDoneRes)
    }

    @Test
    fun `labelRes maps each personality to its label`() {
        assertEquals(R.string.personality_sargento, HabiVoice.labelRes(Personality.SARGENTO))
        assertEquals(R.string.personality_cheerleader, HabiVoice.labelRes(Personality.CHEERLEADER))
        assertEquals(R.string.personality_neutra, HabiVoice.labelRes(Personality.NEUTRA))
    }

    @Test
    fun `formPromptRes maps each personality to its prompt`() {
        assertEquals(R.string.habi_form_prompt_sargento, HabiVoice.formPromptRes(Personality.SARGENTO))
        assertEquals(R.string.habi_form_prompt_cheerleader, HabiVoice.formPromptRes(Personality.CHEERLEADER))
        assertEquals(R.string.habi_form_prompt_neutra, HabiVoice.formPromptRes(Personality.NEUTRA))
    }

    /**
     * Every one of the eight voiced contexts — the two mood-bearing ones per mood, plus the
     * six personality-only ones — must give each of the three personalities its OWN resource.
     * This is the copy-paste guard: a context that accidentally reused another personality's id
     * would pass unnoticed otherwise.
     */
    @Test
    fun `every voiced context resolves a distinct resource per personality`() {
        val moodRes = listOf(HabiVoice::bubbleRes, HabiVoice::homeRes)
        for (resolve in moodRes) {
            for (mood in Mood.entries) {
                val ids = Personality.entries.map { resolve(mood, it) }
                assertEquals(Personality.entries.size, ids.toSet().size)
            }
        }

        val personalityOnlyRes =
            listOf(
                HabiVoice::freezerInfoRes,
                HabiVoice::perfectDayRes,
                HabiVoice::perfectDayNotifRes,
                HabiVoice::badgeUnlockedRes,
                HabiVoice::taskDoneRes,
            )
        for (resolve in personalityOnlyRes) {
            assertAllDistinctPersonalityOnly(resolve)
        }
    }

    /**
     * The full 39-string mapping (12 + 12 + 3 * 5) never collides with itself — bubbleRes and
     * homeRes are two DIFFERENT mood-bearing contexts that intentionally give the same
     * (personality, mood) pair two different resources (e.g. the Stats commentator's
     * `habi_bubble_sargento_normal` is not the Habi screen's `habi_home_sargento_normal`), and
     * freezerInfoRes plus the four celebration/badge/task functions are five DIFFERENT
     * personality-only contexts, so the whole 39-id set — not just each function on its own —
     * must have zero duplicate ids.
     */
    @Test
    fun `the full 39-string mapping has no collisions across contexts`() {
        val moodRes = listOf(HabiVoice::bubbleRes, HabiVoice::homeRes)
        val personalityOnlyRes =
            listOf(
                HabiVoice::freezerInfoRes,
                HabiVoice::perfectDayRes,
                HabiVoice::perfectDayNotifRes,
                HabiVoice::badgeUnlockedRes,
                HabiVoice::taskDoneRes,
            )
        val ids =
            moodRes.flatMap { fn -> Personality.entries.flatMap { p -> Mood.entries.map { m -> fn(m, p) } } } +
                personalityOnlyRes.flatMap { fn -> Personality.entries.map { p -> fn(p) } }

        assertEquals(12 + 12 + 3 * 5, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    /** Same (personality, mood) input always resolves to the same resource — the mapping is a pure function. */
    @Test
    fun `each mapping is stable for the same inputs`() {
        for (personality in Personality.entries) {
            for (mood in Mood.entries) {
                assertEquals(HabiVoice.bubbleRes(mood, personality), HabiVoice.bubbleRes(mood, personality))
                assertEquals(HabiVoice.homeRes(mood, personality), HabiVoice.homeRes(mood, personality))
            }
        }
    }

    @Test
    fun `sinNombre maps the neutral perfect day notice to its own twin`() {
        assertEquals(
            R.string.habi_perfect_notif_neutra_sin_nombre,
            HabiVoice.sinNombre(HabiVoice.perfectDayNotifRes(Personality.NEUTRA)),
        )
    }

    private fun assertAllDistinct(
        expectedCount: Int,
        resolve: (Mood, Personality) -> Int,
    ) {
        val ids = Personality.entries.flatMap { p -> Mood.entries.map { m -> resolve(m, p) } }

        assertEquals(expectedCount, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    private fun assertAllDistinctPersonalityOnly(resolve: (Personality) -> Int) {
        val ids = Personality.entries.map { resolve(it) }

        assertEquals(Personality.entries.size, ids.toSet().size)
    }
}
