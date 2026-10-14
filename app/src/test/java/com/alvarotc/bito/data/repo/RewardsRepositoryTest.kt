package com.alvarotc.bito.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.equippedSetOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RewardsRepositoryTest {
    private lateinit var db: BitoDatabase
    private lateinit var rewards: RewardsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        rewards = RewardsRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `recent movements come newest first, tie broken by id, capped`() =
        runTest {
            db.pointsLedgerDao().insertAll(
                listOf(
                    pointsLedgerEntity(id = "a", refId = "a", createdAtMillis = 10L),
                    pointsLedgerEntity(id = "c", refId = "c", createdAtMillis = 30L),
                    pointsLedgerEntity(id = "b", refId = "b", createdAtMillis = 30L),
                    pointsLedgerEntity(id = "d", refId = "d", createdAtMillis = 20L),
                ),
            )
            val recientes = rewards.observeRecentMovements(3).first()
            assertEquals(listOf("c", "b", "d"), recientes.map { it.id })
        }

    @Test
    fun `purchase spends, acquires and equips in one shot`() =
        runTest {
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "seed", delta = 25, refId = "seed"))
            val item = HabiCatalog.byId("body-lavanda")!!

            val result = rewards.purchase(item, DAY_ZERO, 10L)

            assertTrue(result)
            assertEquals(5, db.pointsLedgerDao().observeBalance().first())
            val row = db.customizationItemDao().byId("body-lavanda")
            assertTrue(row?.equipped == true)
            val entry = db.pointsLedgerDao().all().single { it.reason == PointsReason.BUY_ITEM }
            assertEquals("body-lavanda", entry.refId)
            assertEquals(-20, entry.delta)

            // Buying a second BODY_COLOR item unequips the previously equipped one.
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "seed2", delta = 15, refId = "seed2"))
            val second = HabiCatalog.byId("body-vainilla")!!
            assertTrue(rewards.purchase(second, DAY_ZERO, 20L))
            assertFalse(db.customizationItemDao().byId("body-lavanda")?.equipped == true)
            assertTrue(db.customizationItemDao().byId("body-vainilla")?.equipped == true)
        }

    @Test
    fun `purchase refuses without balance and writes nothing`() =
        runTest {
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "seed", delta = 10, refId = "seed"))
            val item = HabiCatalog.byId("body-lavanda")!!

            val result = rewards.purchase(item, DAY_ZERO, 10L)

            assertFalse(result)
            assertTrue(db.pointsLedgerDao().all().none { it.reason == PointsReason.BUY_ITEM })
            assertNull(db.customizationItemDao().byId("body-lavanda"))
        }

    @Test
    fun `purchase refuses an already owned item`() =
        runTest {
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "seed", delta = 40, refId = "seed"))
            val item = HabiCatalog.byId("body-lavanda")!!

            assertTrue(rewards.purchase(item, DAY_ZERO, 10L))
            assertFalse(rewards.purchase(item, DAY_ZERO, 20L))

            assertEquals(1, db.pointsLedgerDao().all().count { it.reason == PointsReason.BUY_ITEM })
        }

    @Test
    fun `purchase refuses exclusives and defaults`() =
        runTest {
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "seed", delta = 1_000, refId = "seed"))

            assertFalse(rewards.purchase(HabiCatalog.byId("head-corona")!!, DAY_ZERO, 10L))
            assertFalse(rewards.purchase(HabiCatalog.byId("body-salvia")!!, DAY_ZERO, 10L))
        }

    @Test
    fun `unlockBadges is insert-ignore and observable`() =
        runTest {
            rewards.unlockBadges(setOf("first-habit", "streak-7"), 5L)
            rewards.unlockBadges(setOf("first-habit"), 99L)
            assertEquals(setOf("first-habit", "streak-7"), rewards.unlockedBadgeIds())
            assertEquals(5L, rewards.observeBadges().first().single { it.badgeId == "first-habit" }.unlockedAtMillis)
        }

    @Test
    fun `unequip clears the category`() =
        runTest {
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "seed", delta = 25, refId = "seed"))
            val item = HabiCatalog.byId("body-lavanda")!!
            rewards.purchase(item, DAY_ZERO, 10L)

            rewards.unequip(CustomizationCategory.BODY_COLOR)

            val equippedInCategory =
                rewards.observeOwnedItems().first()
                    .filter { it.category == CustomizationCategory.BODY_COLOR && it.equipped }
            assertTrue(equippedInCategory.isEmpty())
        }

    @Test
    fun `purchase refuses a costume out of season even with enough balance`() =
        runTest {
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "seed", delta = 1_000, refId = "seed"))
            val pumpkin = HabiCatalog.byId("costume-calabaza")!!
            val august = LocalDate.of(2026, 8, 14).toEpochDay().toInt()
            val october = LocalDate.of(2026, 10, 20).toEpochDay().toInt()

            assertFalse(rewards.purchase(pumpkin, august, 10L))
            assertEquals(1_000, db.pointsLedgerDao().observeBalance().first())
            assertNull(db.customizationItemDao().byId("costume-calabaza"))

            assertTrue(rewards.purchase(pumpkin, october, 20L))
            assertEquals(850, db.pointsLedgerDao().observeBalance().first())
        }

    @Test
    fun `an owned costume equips out of season and keeps the other axes equipped underneath`() =
        runTest {
            rewards.acquire(CustomizationItemEntity("costume-calabaza", CustomizationCategory.COSTUME, 1L, equipped = false))
            rewards.acquire(CustomizationItemEntity("head-boina", CustomizationCategory.HEAD, 1L, equipped = true))

            rewards.equip("costume-calabaza")
            val dressed = equippedSetOf(db.customizationItemDao().all().filter { it.equipped }.map { it.itemId })
            assertEquals("costume-calabaza", dressed.costume)
            assertEquals("head-boina", dressed.head)

            rewards.unequip(CustomizationCategory.COSTUME)
            val undressed = equippedSetOf(db.customizationItemDao().all().filter { it.equipped }.map { it.itemId })
            assertNull(undressed.costume)
            assertEquals("head-boina", undressed.head)
        }
}
