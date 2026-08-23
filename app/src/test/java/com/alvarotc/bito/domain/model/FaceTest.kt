package com.alvarotc.bito.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceTest {
    private fun all(p: Personality) = Mood.entries.map { faceParamsOf(it, p) }

    @Test
    fun `neutra is always clean - no cheeks, no brows`() =
        all(Personality.NEUTRA).forEach {
            assertEquals(CheekStyle.NONE, it.cheeks)
            assertNull(it.browAngleDeg)
        }

    @Test
    fun `sargento always wears war paint, frowning brows and no sparkles`() =
        all(Personality.SARGENTO).forEach {
            assertEquals(CheekStyle.WAR_PAINT, it.cheeks)
            assertTrue(it.browAngleDeg != null && it.browAngleDeg!! < 0f)
            assertEquals(0, it.sparkles)
        }

    @Test
    fun `cheerleader has blush, big eyes and sparkles`() =
        all(Personality.CHEERLEADER).forEach {
            assertEquals(CheekStyle.BLUSH, it.cheeks)
            assertTrue(it.eyeScale > 1f)
            assertTrue(it.sparkles >= 1)
        }

    @Test
    fun `mood orders the mouth curve within every personality`() =
        Personality.entries.forEach { p ->
            val radiant = faceParamsOf(Mood.RADIANT, p).mouthCurve
            val normal = faceParamsOf(Mood.NORMAL, p).mouthCurve
            val wilted = faceParamsOf(Mood.WILTED, p).mouthCurve
            assertTrue("$p", radiant > normal && normal > wilted)
        }

    @Test
    fun `dramatic is negative everywhere but tinted per personality`() {
        assertTrue(faceParamsOf(Mood.DRAMATIC, Personality.SARGENTO).mouthCurve < 0f)
        assertTrue(faceParamsOf(Mood.DRAMATIC, Personality.CHEERLEADER).mouthOpen > 0.5f)
        assertTrue(faceParamsOf(Mood.DRAMATIC, Personality.NEUTRA).mouthCurve < 0f)
    }

    @Test
    fun `delighted face smiles hugely for every personality`() {
        Personality.entries.forEach { personality ->
            val face = delightedParamsOf(personality)
            assertTrue(face.mouthCurve > 0.9f)
            assertTrue(face.mouthOpen > 0.5f)
            assertFalse(face.smirk)
        }
    }
}
