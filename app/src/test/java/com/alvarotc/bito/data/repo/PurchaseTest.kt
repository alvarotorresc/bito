package com.alvarotc.bito.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.toDomain
import com.alvarotc.bito.data.freezerUseEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

/** Compras de la tienda con Room en memoria y el reloj y los DAO reales (pieza 6, tarea 1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PurchaseTest {
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

    private suspend fun saldo(bitos: Int) =
        db.pointsLedgerDao().insert(
            pointsLedgerEntity(id = "seed-$bitos", delta = bitos, refId = "seed-$bitos"),
        )

    private suspend fun balance() = db.pointsLedgerDao().observeBalance().first()

    private suspend fun congeladores(): Int {
        val ledger = db.pointsLedgerDao().all().map { it.toDomain() }
        val usos = db.freezerUseDao().all().map { it.toDomain() }
        return PointsEngine.freezersOwned(DomainState(pointsLedger = ledger, freezerUses = usos))
    }

    @Test
    fun `buying the bench places it`() =
        runTest {
            saldo(100)
            assertTrue(rewards.purchase(HabiCatalog.byId("garden-banco")!!, DAY_ZERO, 10L))
            assertEquals(20, balance())
            val fila = db.customizationItemDao().byId("garden-banco")
            assertEquals(CustomizationCategory.GARDEN_OBJECT, fila?.category)
            assertTrue(fila?.equipped == true)
        }

    @Test
    fun `buying the watering can keeps the bench placed`() =
        runTest {
            saldo(200)
            assertTrue(rewards.purchase(HabiCatalog.byId("garden-banco")!!, DAY_ZERO, 10L))
            assertTrue(rewards.purchase(HabiCatalog.byId("garden-regadera")!!, DAY_ZERO, 20L))
            assertTrue(db.customizationItemDao().byId("garden-banco")?.equipped == true)
            assertTrue(db.customizationItemDao().byId("garden-regadera")?.equipped == true)
        }

    @Test
    fun `equip and unequip never take a garden object off`() =
        runTest {
            saldo(200)
            rewards.purchase(HabiCatalog.byId("garden-banco")!!, DAY_ZERO, 10L)
            rewards.purchase(HabiCatalog.byId("garden-regadera")!!, DAY_ZERO, 20L)
            rewards.equip("garden-regadera")
            rewards.unequip(CustomizationCategory.GARDEN_OBJECT)
            assertTrue(db.customizationItemDao().byId("garden-banco")?.equipped == true)
            assertTrue(db.customizationItemDao().byId("garden-regadera")?.equipped == true)
        }

    @Test
    fun `buying a hat only unequips the head slot`() =
        runTest {
            saldo(400)
            rewards.purchase(HabiCatalog.byId("garden-banco")!!, DAY_ZERO, 10L)
            rewards.purchase(HabiCatalog.byId("body-lavanda")!!, DAY_ZERO, 20L)
            rewards.purchase(HabiCatalog.byId("head-boina")!!, DAY_ZERO, 30L)
            rewards.purchase(HabiCatalog.byId("head-paja")!!, DAY_ZERO, 40L)
            assertFalse(db.customizationItemDao().byId("head-boina")?.equipped == true)
            assertTrue(db.customizationItemDao().byId("head-paja")?.equipped == true)
            assertTrue(db.customizationItemDao().byId("body-lavanda")?.equipped == true)
            assertTrue(db.customizationItemDao().byId("garden-banco")?.equipped == true)
        }

    @Test
    fun `a garden object is not purchasable twice`() =
        runTest {
            saldo(200)
            val banco = HabiCatalog.byId("garden-banco")!!
            assertTrue(rewards.purchase(banco, DAY_ZERO, 10L))
            assertFalse(rewards.purchase(banco, DAY_ZERO, 20L))
            assertEquals(120, balance())
        }

    @Test
    fun `a double purchase of the same item pays once`() =
        runTest {
            saldo(200)
            val banco = HabiCatalog.byId("garden-banco")!!
            val resultados =
                coroutineScope {
                    listOf(
                        async { rewards.purchase(banco, DAY_ZERO, 10L) },
                        async { rewards.purchase(banco, DAY_ZERO, 11L) },
                    ).awaitAll()
                }
            assertEquals(1, resultados.count { it })
            assertEquals(120, balance())
            assertEquals(1, db.pointsLedgerDao().all().count { it.reason == PointsReason.BUY_ITEM })
        }

    @Test
    fun `without balance nothing is written`() =
        runTest {
            saldo(79)
            assertFalse(rewards.purchase(HabiCatalog.byId("garden-banco")!!, DAY_ZERO, 10L))
            assertEquals(79, balance())
            assertTrue(db.customizationItemDao().all().isEmpty())
        }

    @Test
    fun `an exclusive and a badge special return false`() =
        runTest {
            saldo(1_000)
            assertFalse(rewards.purchase(HabiCatalog.byId("head-corona")!!, DAY_ZERO, 10L))
            val especial = CatalogItem("garden-farolillo", CustomizationCategory.GARDEN_OBJECT)
            assertFalse(rewards.purchase(especial, DAY_ZERO, 20L))
            assertEquals(1_000, balance())
            assertTrue(db.customizationItemDao().all().isEmpty())
        }

    @Test
    fun `with exactly 100 bitos the freezer purchase works and leaves 0`() =
        runTest {
            saldo(100)
            rewards.spend(-100, PointsReason.BUY_FREEZER, "compra-1", DAY_ZERO, 10L)
            assertEquals(0, balance())
            assertEquals(1, congeladores())
        }

    @Test
    fun `undo returns the coins once`() =
        runTest {
            saldo(100)
            rewards.spend(-100, PointsReason.BUY_FREEZER, "compra-1", DAY_ZERO, 10L)
            assertTrue(rewards.undoFreezerPurchase("compra-1"))
            assertEquals(100, balance())
            assertEquals(0, congeladores())
            assertFalse(rewards.undoFreezerPurchase("compra-1"))
            assertEquals(100, balance())
        }

    @Test
    fun `undo after using the freezer is refused`() =
        runTest {
            saldo(100)
            rewards.spend(-100, PointsReason.BUY_FREEZER, "compra-1", DAY_ZERO, 10L)
            db.habitDao().upsert(habitEntity(id = "h1"))
            db.freezerUseDao().insert(freezerUseEntity(id = "uso-1"))
            assertFalse(rewards.undoFreezerPurchase("compra-1"))
            assertEquals(0, balance())
            assertEquals(0, congeladores())
        }

    @Test
    fun `undo with two freezers and one used undoes the unused one`() =
        runTest {
            saldo(200)
            rewards.spend(-100, PointsReason.BUY_FREEZER, "compra-1", DAY_ZERO, 10L)
            rewards.spend(-100, PointsReason.BUY_FREEZER, "compra-2", DAY_ZERO, 20L)
            db.habitDao().upsert(habitEntity(id = "h1"))
            db.freezerUseDao().insert(freezerUseEntity(id = "uso-1"))
            assertTrue(rewards.undoFreezerPurchase("compra-2"))
            assertEquals(100, balance())
            assertEquals(0, congeladores())
            assertFalse(rewards.undoFreezerPurchase("compra-1"))
        }

    @Test
    fun `undo of an unknown refId writes nothing`() =
        runTest {
            saldo(100)
            rewards.spend(-100, PointsReason.BUY_FREEZER, "compra-1", DAY_ZERO, 10L)
            assertFalse(rewards.undoFreezerPurchase("otra"))
            assertEquals(0, balance())
            assertEquals(1, db.pointsLedgerDao().all().count { it.reason == PointsReason.BUY_FREEZER })
        }

    @Test
    fun `undo never touches an item purchase with the same refId`() =
        runTest {
            saldo(200)
            rewards.purchase(HabiCatalog.byId("garden-banco")!!, DAY_ZERO, 10L)
            assertFalse(rewards.undoFreezerPurchase("garden-banco"))
            assertEquals(120, balance())
        }
}
