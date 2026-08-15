package com.alvarotc.bito.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.daySealEntity
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.freezerUseEntity
import com.alvarotc.bito.data.habitEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JournalDaosTest {
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
    fun `entries filter by habit and day range, ordered`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            db.habitDao().upsert(habitEntity(id = "h2"))
            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", logicalDay = DAY_ZERO + 1))
            db.entryDao().insert(entryEntity(id = "e2", habitId = "h1", logicalDay = DAY_ZERO + 3))
            db.entryDao().insert(entryEntity(id = "e3", habitId = "h1", logicalDay = DAY_ZERO + 9))
            db.entryDao().insert(entryEntity(id = "e4", habitId = "h2", logicalDay = DAY_ZERO + 1))
            val hits = db.entryDao().forHabitInRange("h1", DAY_ZERO, DAY_ZERO + 3)
            assertEquals(listOf("e1", "e2"), hits.map { it.id })
        }

    @Test
    fun `deleting an entry removes only that entry`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            db.entryDao().insert(entryEntity(id = "e1"))
            db.entryDao().insert(entryEntity(id = "e2"))
            db.entryDao().delete("e1")
            assertEquals(listOf("e2"), db.entryDao().observeAll().first().map { it.id })
        }

    @Test
    fun `deleting a habit cascades to its entries and freezers`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            db.entryDao().insert(entryEntity(id = "e1"))
            db.freezerUseDao().insert(freezerUseEntity(id = "f1"))
            db.habitDao().delete("h1")
            assertTrue(db.entryDao().observeAll().first().isEmpty())
            assertTrue(db.freezerUseDao().observeAll().first().isEmpty())
        }

    @Test
    fun `sealing a day twice keeps the first seal`() =
        runTest {
            db.daySealDao().insert(daySealEntity(sealedAtMillis = 100))
            db.daySealDao().insert(daySealEntity(sealedAtMillis = 999))
            assertEquals(100L, db.daySealDao().byDay(DAY_ZERO)!!.sealedAtMillis)
        }

    @Test
    fun `a second freezer on the same habit and day is ignored`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            val first = db.freezerUseDao().insert(freezerUseEntity(id = "f1"))
            val second = db.freezerUseDao().insert(freezerUseEntity(id = "f2"))
            assertTrue(first != -1L)
            assertEquals(-1L, second)
            assertEquals(listOf("f1"), db.freezerUseDao().observeAll().first().map { it.id })
        }
}
