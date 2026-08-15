package com.alvarotc.bito.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.customizationItemEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RewardDaosTest {
    private lateinit var db: BitoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `balance is the sum of deltas and zero when empty`() =
        runTest {
            assertEquals(0, db.pointsLedgerDao().observeBalance().first())
            db.pointsLedgerDao().insertAll(
                listOf(
                    pointsLedgerEntity(id = "p1", delta = 5),
                    pointsLedgerEntity(id = "p2", delta = 3),
                    pointsLedgerEntity(id = "p3", delta = -6, reason = PointsReason.BUY_FREEZER, refId = null),
                ),
            )
            assertEquals(2, db.pointsLedgerDao().observeBalance().first())
        }

    @Test
    fun `ledger comes back ordered by day then creation`() =
        runTest {
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "p2", logicalDay = 20_001, createdAtMillis = 5))
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "p1", logicalDay = 20_000, createdAtMillis = 9))
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "p3", logicalDay = 20_001, createdAtMillis = 2))
            assertEquals(listOf("p1", "p3", "p2"), db.pointsLedgerDao().observeAll().first().map { it.id })
        }

    @Test
    fun `unlocking a badge twice keeps the first unlock`() =
        runTest {
            db.badgeDao().insert(BadgeEntity("first-week", unlockedAtMillis = 100))
            db.badgeDao().insert(BadgeEntity("first-week", unlockedAtMillis = 999))
            assertEquals(100L, db.badgeDao().observeAll().first().single().unlockedAtMillis)
        }

    @Test
    fun `unequipCategory clears only that category`() =
        runTest {
            db.customizationItemDao().upsert(customizationItemEntity(itemId = "hat-a", equipped = true))
            db.customizationItemDao().upsert(customizationItemEntity(itemId = "hat-b", equipped = false))
            db.customizationItemDao().upsert(
                customizationItemEntity(itemId = "boots", category = CustomizationCategory.LOWER, equipped = true),
            )
            db.customizationItemDao().unequipCategory(CustomizationCategory.UPPER)
            val equipped = db.customizationItemDao().observeAll().first().filter { it.equipped }.map { it.itemId }
            assertEquals(listOf("boots"), equipped)
        }
}
