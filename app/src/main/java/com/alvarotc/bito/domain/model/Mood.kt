package com.alvarotc.bito.domain.model

/**
 * Habi's mood, a pure function of the user's recent compliance (see MoodEngine):
 * - [RADIANT]: > 80% over the 7-day window.
 * - [NORMAL]: 50–80% (inclusive), or not enough data.
 * - [WILTED]: < 50%.
 * - [DRAMATIC]: prolonged absence — 3+ consecutive logical days without any
 *   activity (no entries, no seals), regardless of the window ratio.
 */
enum class Mood { RADIANT, NORMAL, WILTED, DRAMATIC }

/** Text/personality pack that tints Habi, notifications and the review. */
enum class Personality { SARGENTO, CHEERLEADER, NEUTRA }
