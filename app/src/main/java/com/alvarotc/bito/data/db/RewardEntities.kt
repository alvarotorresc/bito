package com.alvarotc.bito.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.alvarotc.bito.domain.model.PointsReason

/** One ledger movement. The balance is always derived (SUM of deltas). */
@Entity(tableName = "points_ledger")
data class PointsLedgerEntity(
    @PrimaryKey val id: String,
    val delta: Int,
    val reason: PointsReason,
    val refId: String?,
    val logicalDay: Int,
    val createdAtMillis: Long,
)

/** An unlocked badge; the catalog lives in code (M7). */
@Entity(tableName = "badges")
data class BadgeEntity(
    @PrimaryKey val badgeId: String,
    val unlockedAtMillis: Long,
)

/** Customization axis of an item (tech doc §3); catalog in code (M6). */
enum class CustomizationCategory { BODY_COLOR, PATTERN, EYE_COLOR, UPPER, LOWER }

/** An acquired customization item; equipped is per-category exclusive. */
@Entity(tableName = "customization_items")
data class CustomizationItemEntity(
    @PrimaryKey val itemId: String,
    val category: CustomizationCategory,
    val acquiredAtMillis: Long,
    val equipped: Boolean,
)
