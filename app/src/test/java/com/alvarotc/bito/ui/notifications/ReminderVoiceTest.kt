package com.alvarotc.bito.ui.notifications

import com.alvarotc.bito.domain.model.Personality
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ReminderVoice] is a pure lookup table in the `HabiVoice` mold: the flavor thresholds must
 * split the day exactly as documented (evening wraps midnight), and every (personality, flavor)
 * pair must resolve to its OWN resource — a collision would mean two contexts silently share
 * copy, the exact bug the T13 brief called "sin colisiones".
 */
class ReminderVoiceTest {
    @Test
    fun `morning runs from 5,00 to 11,59`() {
        assertEquals(ReminderFlavor.MORNING, ReminderVoice.flavorOf(5 * 60))
        assertEquals(ReminderFlavor.MORNING, ReminderVoice.flavorOf(9 * 60))
        assertEquals(ReminderFlavor.MORNING, ReminderVoice.flavorOf(11 * 60 + 59))
    }

    @Test
    fun `afternoon runs from 12,00 to 17,59`() {
        assertEquals(ReminderFlavor.AFTERNOON, ReminderVoice.flavorOf(12 * 60))
        assertEquals(ReminderFlavor.AFTERNOON, ReminderVoice.flavorOf(15 * 60))
        assertEquals(ReminderFlavor.AFTERNOON, ReminderVoice.flavorOf(17 * 60 + 59))
    }

    @Test
    fun `evening runs from 18,00 and wraps midnight until 4,59`() {
        assertEquals(ReminderFlavor.EVENING, ReminderVoice.flavorOf(18 * 60))
        assertEquals(ReminderFlavor.EVENING, ReminderVoice.flavorOf(21 * 60))
        assertEquals(ReminderFlavor.EVENING, ReminderVoice.flavorOf(23 * 60 + 59))
        assertEquals(ReminderFlavor.EVENING, ReminderVoice.flavorOf(0))
        assertEquals(ReminderFlavor.EVENING, ReminderVoice.flavorOf(4 * 60 + 59))
    }

    @Test
    fun `titleRes resolves a distinct resource for every personality-flavor pair`() {
        val ids =
            Personality.entries.flatMap { personality ->
                ReminderFlavor.entries.map { flavor -> ReminderVoice.titleRes(personality, flavor) }
            }

        assertEquals(Personality.entries.size * ReminderFlavor.entries.size, ids.toSet().size)
    }

    @Test
    fun `bodyRes resolves a distinct resource for every personality-flavor pair`() {
        val ids =
            Personality.entries.flatMap { personality ->
                ReminderFlavor.entries.map { flavor -> ReminderVoice.bodyRes(personality, flavor) }
            }

        assertEquals(Personality.entries.size * ReminderFlavor.entries.size, ids.toSet().size)
    }

    @Test
    fun `review title, body, and seal-only each resolve a distinct resource per personality`() {
        val ids =
            Personality.entries.flatMap {
                listOf(ReminderVoice.reviewTitleRes(it), ReminderVoice.reviewBodyRes(it), ReminderVoice.reviewSealOnlyRes(it))
            }

        assertEquals(Personality.entries.size * 3, ids.toSet().size)
    }
}
