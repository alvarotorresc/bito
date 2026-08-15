package com.alvarotc.bito.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.daySealEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.freezerUseEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pauseIntervalEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.targetChangeEntity
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
class DomainStateRepositoryTest {
    private lateinit var db: BitoDatabase
    private lateinit var repository: DomainStateRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        repository = DomainStateRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `snapshot of an empty database is the empty domain state`() =
        runTest {
            val state = repository.snapshot()
            assertTrue(state.habits.isEmpty())
            assertTrue(state.entries.isEmpty())
            assertTrue(state.pointsLedger.isEmpty())
        }

    @Test
    fun `snapshot assembles every stored table into domain types`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1", target = 8))
            db.targetChangeDao().upsert(targetChangeEntity(habitId = "h1", target = 8))
            db.entryDao().insert(entryEntity(id = "e1", habitId = "h1", value = 3))
            db.daySealDao().insert(daySealEntity(logicalDay = DAY_ZERO))
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "p1", delta = 1))
            db.pauseIntervalDao().upsert(pauseIntervalEntity(habitId = "h1", startDay = DAY_ZERO + 1, note = "trip"))
            db.freezerUseDao().insert(freezerUseEntity(id = "f1", habitId = "h1", protectedDay = DAY_ZERO + 2))

            val state = repository.snapshot()
            assertEquals("h1", state.habits.single().id)
            assertEquals(8, state.targetChanges.single().target)
            assertEquals(3, state.entries.single().value)
            assertEquals(DAY_ZERO, state.daySeals.single().logicalDay)
            assertEquals(1, state.pointsLedger.single().delta)
            assertEquals(DAY_ZERO + 1, state.pauseIntervals.single().startDay)
            assertEquals("trip", state.pauseIntervals.single().note)
            assertEquals(DAY_ZERO + 2, state.freezerUses.single().protectedDay)
            assertEquals("f1", state.freezerUses.single().id)
        }

    @Test
    fun `observe emits a new state when data changes`() =
        runTest {
            assertTrue(repository.observe().first().habits.isEmpty())
            db.habitDao().upsert(habitEntity(id = "h1"))
            assertEquals(1, repository.observe().first().habits.size)
        }
}
