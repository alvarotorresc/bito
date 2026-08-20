package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [HabiVoice] is a pure lookup table — every (personality, mood) pair its mood-bearing functions
 * cover, and every personality its personality-only functions ([HabiVoice.freezerInfoRes],
 * [HabiVoice.reviewRes], [HabiVoice.reviewClearRes], [HabiVoice.sealedRes], [HabiVoice.perfectDayRes],
 * [HabiVoice.perfectDayNotifRes], [HabiVoice.badgeUnlockedRes]) cover, must resolve to its OWN
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
    fun `greetingRes resolves a distinct resource for every personality-mood pair`() {
        assertAllDistinct(Personality.entries.size * Mood.entries.size, HabiVoice::greetingRes)
    }

    @Test
    fun `freezerInfoRes resolves a distinct resource per personality`() {
        val ids = Personality.entries.map { HabiVoice.freezerInfoRes(it) }

        assertEquals(Personality.entries.size, ids.toSet().size)
    }

    @Test
    fun `reviewRes resolves a distinct resource per personality`() {
        assertAllDistinctPersonalityOnly(HabiVoice::reviewRes)
    }

    @Test
    fun `reviewClearRes resolves a distinct resource per personality`() {
        assertAllDistinctPersonalityOnly(HabiVoice::reviewClearRes)
    }

    @Test
    fun `sealedRes resolves a distinct resource per personality`() {
        assertAllDistinctPersonalityOnly(HabiVoice::sealedRes)
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

    /**
     * The full 57-string mapping (12 + 12 + 12 + 3 + 3 + 3 + 3 + 3 + 3 + 3) never collides with
     * itself — bubbleRes, homeRes and greetingRes are three DIFFERENT mood-bearing contexts that
     * intentionally give the same (personality, mood) pair three different resources (e.g. the
     * Stats commentator's `habi_bubble_sargento_normal` is not the Habi screen's
     * `habi_home_sargento_normal`), and freezerInfoRes plus the six review/celebration/badge
     * functions are seven more DIFFERENT personality-only contexts, so the whole 57-id set — not
     * just each function on its own — must have zero duplicate ids.
     */
    @Test
    fun `the full 57-string mapping has no collisions across contexts`() {
        val moodRes = listOf(HabiVoice::bubbleRes, HabiVoice::homeRes, HabiVoice::greetingRes)
        val personalityOnlyRes =
            listOf(
                HabiVoice::freezerInfoRes,
                HabiVoice::reviewRes,
                HabiVoice::reviewClearRes,
                HabiVoice::sealedRes,
                HabiVoice::perfectDayRes,
                HabiVoice::perfectDayNotifRes,
                HabiVoice::badgeUnlockedRes,
            )
        val ids =
            moodRes.flatMap { fn -> Personality.entries.flatMap { p -> Mood.entries.map { m -> fn(m, p) } } } +
                personalityOnlyRes.flatMap { fn -> Personality.entries.map { p -> fn(p) } }

        assertEquals(12 + 12 + 12 + 3 + 3 + 3 + 3 + 3 + 3 + 3, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    /** Same (personality, mood) input always resolves to the same resource — the mapping is a pure function. */
    @Test
    fun `each mapping is stable for the same inputs`() {
        for (personality in Personality.entries) {
            for (mood in Mood.entries) {
                assertEquals(HabiVoice.bubbleRes(mood, personality), HabiVoice.bubbleRes(mood, personality))
                assertEquals(HabiVoice.homeRes(mood, personality), HabiVoice.homeRes(mood, personality))
                assertEquals(HabiVoice.greetingRes(mood, personality), HabiVoice.greetingRes(mood, personality))
            }
        }
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
