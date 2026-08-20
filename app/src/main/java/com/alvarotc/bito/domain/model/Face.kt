package com.alvarotc.bito.domain.model

/** Cheek layer style — the personality's signature (functional doc §5.3). */
enum class CheekStyle { NONE, BLUSH, WAR_PAINT }

/**
 * Drawing parameters for Habi's face. mood x personality resolves to parameters,
 * never to per-combination assets (tech doc §7.1). Exact values are art-phase-tunable;
 * the invariants in FaceTest are the contract.
 */
data class FaceParams(
    val cheeks: CheekStyle,
    /** Brow rotation in degrees; negative = frown; null = no brows drawn. */
    val browAngleDeg: Float?,
    /** Mouth curvature: -1 full sad .. +1 full smile. */
    val mouthCurve: Float,
    /** Mouth openness 0..1. */
    val mouthOpen: Float,
    /** Sargento's half-smile: mouth drawn off-center. */
    val smirk: Boolean,
    /** Eye size multiplier (1 = base). */
    val eyeScale: Float,
    /** Number of eye highlights. */
    val sparkles: Int,
)

fun faceParamsOf(
    mood: Mood,
    personality: Personality,
): FaceParams {
    val base =
        when (personality) {
            Personality.NEUTRA ->
                FaceParams(CheekStyle.NONE, null, 0.35f, 0f, smirk = false, eyeScale = 1f, sparkles = 1)
            Personality.SARGENTO ->
                FaceParams(CheekStyle.WAR_PAINT, -18f, 0.25f, 0f, smirk = true, eyeScale = 0.95f, sparkles = 0)
            Personality.CHEERLEADER ->
                FaceParams(CheekStyle.BLUSH, null, 0.7f, 0.4f, smirk = false, eyeScale = 1.15f, sparkles = 2)
        }
    return when (mood) {
        Mood.RADIANT ->
            base.copy(
                mouthCurve = base.mouthCurve + 0.25f,
                mouthOpen = (base.mouthOpen + 0.2f).coerceAtMost(1f),
                eyeScale = base.eyeScale + 0.05f,
                sparkles = if (personality == Personality.SARGENTO) 0 else base.sparkles + 1,
            )
        Mood.NORMAL -> base
        Mood.WILTED ->
            base.copy(
                mouthCurve = base.mouthCurve - 0.55f,
                mouthOpen = 0f,
                eyeScale = base.eyeScale - 0.1f,
                sparkles = if (personality == Personality.CHEERLEADER) 1 else 0,
            )
        Mood.DRAMATIC ->
            when (personality) {
                Personality.SARGENTO -> base.copy(browAngleDeg = -30f, mouthCurve = -0.6f)
                Personality.CHEERLEADER -> base.copy(mouthCurve = -0.8f, mouthOpen = 0.8f)
                Personality.NEUTRA -> base.copy(mouthCurve = -0.25f, eyeScale = 0.9f, sparkles = 0)
            }
    }
}
