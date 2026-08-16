package com.alvarotc.bito.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.domain.model.HabitStatus
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
class HabitsRepositoryTest {
    private lateinit var db: BitoDatabase
    private lateinit var repository: HabitsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        repository = HabitsRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create records the initial target change on the creation day`() =
        runTest {
            repository.create(habitEntity(id = "h1", target = 8, createdOnDay = DAY_ZERO))
            val changes = db.targetChangeDao().forHabit("h1")
            assertEquals(1, changes.size)
            assertEquals(DAY_ZERO, changes.single().effectiveFromDay)
            assertEquals(8, changes.single().target)
        }

    @Test
    fun `updating the target records a change effective today, keeping history`() =
        runTest {
            repository.create(habitEntity(id = "h1", target = 8, createdOnDay = DAY_ZERO))
            repository.update(habitEntity(id = "h1", target = 10), today = DAY_ZERO + 5)
            val changes = db.targetChangeDao().forHabit("h1")
            assertEquals(listOf(DAY_ZERO to 8, DAY_ZERO + 5 to 10), changes.map { it.effectiveFromDay to it.target })
            assertEquals(10, repository.habit("h1")!!.target)
        }

    @Test
    fun `updating without a target change records no history row`() =
        runTest {
            repository.create(habitEntity(id = "h1", target = 8, createdOnDay = DAY_ZERO))
            repository.update(habitEntity(id = "h1", target = 8, name = "Agua fría"), today = DAY_ZERO + 5)
            assertEquals(1, db.targetChangeDao().forHabit("h1").size)
        }

    @Test
    fun `reorder rewrites sortOrder to match the given id order`() =
        runTest {
            repository.create(habitEntity(id = "a", sortOrder = 0, createdOnDay = DAY_ZERO))
            repository.create(habitEntity(id = "b", sortOrder = 1, createdOnDay = DAY_ZERO))
            repository.create(habitEntity(id = "c", sortOrder = 2, createdOnDay = DAY_ZERO))

            repository.reorder(listOf("c", "a", "b"))

            assertEquals(listOf("c", "a", "b"), db.habitDao().all().map { it.id })
        }

    @Test
    fun `archive stamps status and both archive fields`() =
        runTest {
            repository.create(habitEntity(id = "h1", createdOnDay = DAY_ZERO))
            repository.archive("h1", today = DAY_ZERO + 40, nowMillis = 9_000L)
            val stored = repository.habit("h1")!!
            assertEquals(HabitStatus.ARCHIVED, stored.status)
            assertEquals(DAY_ZERO + 40, stored.archivedOnDay)
            assertEquals(9_000L, stored.archivedAtMillis)
        }

    @Test
    fun `pause opens an interval and sets status, resume closes it`() =
        runTest {
            repository.create(habitEntity(id = "h1", createdOnDay = DAY_ZERO))
            repository.pause("h1", startDay = DAY_ZERO + 3, note = "vacaciones")
            assertEquals(HabitStatus.PAUSED, repository.habit("h1")!!.status)

            repository.resume("h1", endDay = DAY_ZERO + 9)
            assertEquals(HabitStatus.ACTIVE, repository.habit("h1")!!.status)
            val pause = db.pauseIntervalDao().forHabit("h1").single()
            assertEquals(DAY_ZERO + 3, pause.startDay)
            assertEquals(DAY_ZERO + 9, pause.endDay)
        }
}
