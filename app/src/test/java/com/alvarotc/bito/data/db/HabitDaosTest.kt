package com.alvarotc.bito.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pauseIntervalEntity
import com.alvarotc.bito.data.targetChangeEntity
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Metric
import kotlinx.coroutines.flow.first
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
class HabitDaosTest {
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
    fun `upsert and read back preserves every field`() =
        runTest {
            val habit =
                habitEntity(
                    id = "smoke",
                    name = "No fumar",
                    metric = Metric.CHECK,
                    direction = Direction.ZERO,
                    target = 0,
                    unit = null,
                    timeBucket = TimeBucket.EVENING,
                    reminderMinutes = 21 * 60,
                )
            db.habitDao().upsert(habit)
            assertEquals(habit, db.habitDao().byId("smoke"))
        }

    @Test
    fun `byId returns null for unknown id`() =
        runTest {
            assertNull(db.habitDao().byId("ghost"))
        }

    @Test
    fun `upsert replaces the existing row`() =
        runTest {
            db.habitDao().upsert(habitEntity(target = 8))
            db.habitDao().upsert(habitEntity(target = 10, status = HabitStatus.PAUSED))
            val stored = db.habitDao().byId("h1")!!
            assertEquals(10, stored.target)
            assertEquals(HabitStatus.PAUSED, stored.status)
        }

    @Test
    fun `observeAll orders by sortOrder then creation`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "b", sortOrder = 1, createdAtMillis = 1))
            db.habitDao().upsert(habitEntity(id = "c", sortOrder = 2, createdAtMillis = 2))
            db.habitDao().upsert(habitEntity(id = "a", sortOrder = 1, createdAtMillis = 0))
            assertEquals(listOf("a", "b", "c"), db.habitDao().observeAll().first().map { it.id })
        }

    @Test
    fun `deleting a habit cascades to target changes and pauses`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            db.targetChangeDao().upsert(targetChangeEntity(habitId = "h1"))
            db.pauseIntervalDao().upsert(pauseIntervalEntity(habitId = "h1"))
            db.habitDao().delete("h1")
            assertEquals(emptyList<TargetChangeEntity>(), db.targetChangeDao().forHabit("h1"))
            assertEquals(emptyList<PauseIntervalEntity>(), db.pauseIntervalDao().forHabit("h1"))
        }

    @Test
    fun `target changes come back ordered by effective day`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            db.targetChangeDao().upsert(targetChangeEntity(effectiveFromDay = DAY_ZERO + 5, target = 10))
            db.targetChangeDao().upsert(targetChangeEntity(effectiveFromDay = DAY_ZERO, target = 8))
            assertEquals(listOf(8, 10), db.targetChangeDao().forHabit("h1").map { it.target })
        }

    @Test
    fun `re-editing the target the same day replaces that change`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            db.targetChangeDao().upsert(targetChangeEntity(effectiveFromDay = DAY_ZERO, target = 8))
            db.targetChangeDao().upsert(targetChangeEntity(effectiveFromDay = DAY_ZERO, target = 12))
            assertEquals(listOf(12), db.targetChangeDao().forHabit("h1").map { it.target })
        }

    @Test
    fun `closeOpen closes only the open pause`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            db.pauseIntervalDao().upsert(pauseIntervalEntity(startDay = DAY_ZERO, endDay = DAY_ZERO + 2))
            db.pauseIntervalDao().upsert(pauseIntervalEntity(startDay = DAY_ZERO + 10, endDay = null))
            db.pauseIntervalDao().closeOpen("h1", endDay = DAY_ZERO + 12)
            val pauses = db.pauseIntervalDao().forHabit("h1")
            assertEquals(listOf(DAY_ZERO + 2, DAY_ZERO + 12), pauses.map { it.endDay })
        }
}
