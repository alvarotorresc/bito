package com.alvarotc.bito.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.db.PointsLedgerEntity
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JournalSetDayTotalTest {
    private lateinit var db: BitoDatabase
    private lateinit var journal: JournalRepository
    private lateinit var habits: HabitsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        journal = JournalRepository(db)
        habits = HabitsRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `setDayTotal replaces only that day's entries`() =
        runTest {
            habits.create(habitEntity(id = "agua", metric = Metric.COUNT, target = 8))
            journal.log(entryEntity(id = "e1", habitId = "agua", logicalDay = DAY_ZERO, value = 3))
            journal.log(entryEntity(id = "e2", habitId = "agua", logicalDay = DAY_ZERO, value = 2))
            journal.log(entryEntity(id = "e3", habitId = "agua", logicalDay = DAY_ZERO + 1, value = 4))
            journal.setDayTotal("agua", DAY_ZERO, 6, nowMillis = 99L)
            val dayEntries = db.entryDao().forHabitInRange("agua", DAY_ZERO, DAY_ZERO)
            assertEquals(listOf(6), dayEntries.map { it.value })
            assertEquals(listOf(4), db.entryDao().forHabitInRange("agua", DAY_ZERO + 1, DAY_ZERO + 1).map { it.value })
        }

    @Test
    fun `setDayTotal to zero clears the day`() =
        runTest {
            habits.create(habitEntity(id = "agua", metric = Metric.COUNT, target = 8))
            journal.log(entryEntity(id = "e1", habitId = "agua", logicalDay = DAY_ZERO, value = 3))
            journal.setDayTotal("agua", DAY_ZERO, 0, nowMillis = 99L)
            assertEquals(emptyList<EntryEntity>(), db.entryDao().forHabitInRange("agua", DAY_ZERO, DAY_ZERO))
        }

    @Test
    fun `delete removes the habit and its history but keeps the ledger`() =
        runTest {
            habits.create(habitEntity(id = "junk"))
            journal.log(entryEntity(id = "e1", habitId = "junk"))
            db.pointsLedgerDao().insert(
                PointsLedgerEntity("p1", 1, PointsReason.HABIT_DONE, "junk:0", DAY_ZERO, 1L),
            )
            habits.delete("junk")
            assertNull(db.habitDao().byId("junk"))
            assertEquals(emptyList<EntryEntity>(), db.entryDao().all()) // FK cascade
            assertEquals(1, db.pointsLedgerDao().all().size) // E3: points stay
        }
}
