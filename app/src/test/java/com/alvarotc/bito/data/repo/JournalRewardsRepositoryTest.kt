package com.alvarotc.bito.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.customizationItemEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.freezerUseEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.domain.model.PointsEvent
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JournalRewardsRepositoryTest {
    private lateinit var db: BitoDatabase
    private lateinit var journal: JournalRepository
    private lateinit var rewards: RewardsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        journal = JournalRepository(db)
        rewards = RewardsRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `log and remove round-trip an entry`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            journal.log(entryEntity(id = "e1", value = 3))
            assertEquals(3, db.entryDao().observeAll().first().single().value)
            journal.remove("e1")
            assertTrue(db.entryDao().observeAll().first().isEmpty())
        }

    @Test
    fun `sealDay is idempotent and keeps the first timestamp`() =
        runTest {
            journal.sealDay(DAY_ZERO, nowMillis = 100)
            journal.sealDay(DAY_ZERO, nowMillis = 999)
            assertEquals(100L, db.daySealDao().byDay(DAY_ZERO)!!.sealedAtMillis)
        }

    @Test
    fun `useFreezer reports whether the day got protected`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            assertTrue(journal.useFreezer(freezerUseEntity(id = "f1")))
            assertFalse(journal.useFreezer(freezerUseEntity(id = "f2")))
        }

    @Test
    fun `append maps engine events into ledger rows with fresh ids`() =
        runTest {
            rewards.append(
                listOf(
                    PointsEvent(PointsReason.HABIT_DONE, refId = "h1:$DAY_ZERO", logicalDay = DAY_ZERO, delta = 1),
                    PointsEvent(PointsReason.PERFECT_DAY, refId = "day:$DAY_ZERO", logicalDay = DAY_ZERO, delta = 3),
                ),
                nowMillis = 500L,
            )
            val rows = db.pointsLedgerDao().observeAll().first()
            assertEquals(2, rows.size)
            assertEquals(setOf("h1:$DAY_ZERO", "day:$DAY_ZERO"), rows.map { it.refId }.toSet())
            assertEquals(2, rows.map { it.id }.toSet().size)
            assertEquals(4, rewards.observeBalance().first())
        }

    @Test
    fun `spend appends a negative movement`() =
        runTest {
            rewards.append(
                listOf(PointsEvent(PointsReason.HABIT_DONE, "h1:$DAY_ZERO", DAY_ZERO, delta = 10)),
                nowMillis = 500L,
            )
            rewards.spend(delta = -6, reason = PointsReason.BUY_FREEZER, refId = null, day = DAY_ZERO, nowMillis = 600L)
            assertEquals(4, rewards.observeBalance().first())
        }

    @Test
    fun `equip swaps the equipped item within its category`() =
        runTest {
            rewards.acquire(customizationItemEntity(itemId = "hat-a", equipped = true))
            rewards.acquire(customizationItemEntity(itemId = "hat-b"))
            rewards.equip("hat-b")
            val equipped = db.customizationItemDao().observeAll().first().filter { it.equipped }.map { it.itemId }
            assertEquals(listOf("hat-b"), equipped)
        }
}
