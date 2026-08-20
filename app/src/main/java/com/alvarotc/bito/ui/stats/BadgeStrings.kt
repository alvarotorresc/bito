package com.alvarotc.bito.ui.stats

import androidx.annotation.StringRes
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.BadgeFamily

/**
 * Resolves the string resources for the 14 catalog badges ([com.alvarotc.bito.domain.model.BadgeCatalog])
 * and the three [BadgeFamily] labels, so the domain stays free of Android resource ids. Every
 * resource here is PROVISIONAL copy — `res/values{,-es}/strings_badges.xml` — the definitive
 * wording lands in the M9 economy/copy session.
 *
 * - [badgeNameRes] — the badge's own name, personality never changes it (docs/06-badges.md §2).
 * - [badgeHowRes] — the unlock condition, plugged into `R.string.badge_how_prefix`.
 * - [badgeFamilyRes] — the [BadgeFamily] label the Badges list groups by.
 */
object BadgeStrings {
    @StringRes
    fun badgeNameRes(id: String): Int =
        when (id) {
            "streak-7" -> R.string.badge_name_streak_7
            "streak-30" -> R.string.badge_name_streak_30
            "streak-100" -> R.string.badge_name_streak_100
            "streak-365" -> R.string.badge_name_streak_365
            "perfect-day-1" -> R.string.badge_name_perfect_day_1
            "perfect-days-10" -> R.string.badge_name_perfect_days_10
            "perfect-days-50" -> R.string.badge_name_perfect_days_50
            "perfect-days-100" -> R.string.badge_name_perfect_days_100
            "perfect-week" -> R.string.badge_name_perfect_week
            "perfect-month" -> R.string.badge_name_perfect_month
            "first-habit" -> R.string.badge_name_first_habit
            "first-week" -> R.string.badge_name_first_week
            "resurrection" -> R.string.badge_name_resurrection
            "first-freezer" -> R.string.badge_name_first_freezer
            else -> throw IllegalArgumentException("Unknown badge id: $id")
        }

    @StringRes
    fun badgeHowRes(id: String): Int =
        when (id) {
            "streak-7" -> R.string.badge_how_streak_7
            "streak-30" -> R.string.badge_how_streak_30
            "streak-100" -> R.string.badge_how_streak_100
            "streak-365" -> R.string.badge_how_streak_365
            "perfect-day-1" -> R.string.badge_how_perfect_day_1
            "perfect-days-10" -> R.string.badge_how_perfect_days_10
            "perfect-days-50" -> R.string.badge_how_perfect_days_50
            "perfect-days-100" -> R.string.badge_how_perfect_days_100
            "perfect-week" -> R.string.badge_how_perfect_week
            "perfect-month" -> R.string.badge_how_perfect_month
            "first-habit" -> R.string.badge_how_first_habit
            "first-week" -> R.string.badge_how_first_week
            "resurrection" -> R.string.badge_how_resurrection
            "first-freezer" -> R.string.badge_how_first_freezer
            else -> throw IllegalArgumentException("Unknown badge id: $id")
        }

    @StringRes
    fun badgeFamilyRes(family: BadgeFamily): Int =
        when (family) {
            BadgeFamily.STREAK -> R.string.badge_family_streak
            BadgeFamily.CONSTANCY -> R.string.badge_family_constancy
            BadgeFamily.MOMENT -> R.string.badge_family_moment
        }
}
