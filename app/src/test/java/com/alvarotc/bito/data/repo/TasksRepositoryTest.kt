package com.alvarotc.bito.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TasksRepositoryTest {
    private lateinit var db: BitoDatabase
    private lateinit var repo: TasksRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        repo = TasksRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create trims the title and turns a blank first step into null`() =
        runTest {
            repo.create(taskEntity(id = "t1", title = "  Llamar al banco  ", firstStep = "   "))

            val stored = repo.task("t1")!!
            assertEquals("Llamar al banco", stored.title)
            assertNull(stored.firstStep)
        }

    @Test
    fun `marking done sets both halves of the invariant, and reopening clears both`() =
        runTest {
            repo.create(taskEntity(id = "t1"))

            repo.markDone("t1", DAY_ZERO + 2, nowMillis = 5_000L)
            val done = repo.task("t1")!!
            assertEquals(TaskStatus.DONE, done.status)
            assertEquals(DAY_ZERO + 2, done.doneOnDay)
            assertEquals(5_000L, done.doneAtMillis)

            repo.reopen("t1")
            val open = repo.task("t1")!!
            assertEquals(TaskStatus.OPEN, open.status)
            assertNull(open.doneOnDay)
            assertNull(open.doneAtMillis)
        }

    @Test
    fun `editing touches only the task row, never its events`() =
        runTest {
            repo.create(taskEntity(id = "t1", title = "Viejo"))
            repo.postpone("t1", DAY_ZERO, nowMillis = 1_000L)

            repo.update("t1", title = "Nuevo", firstStep = "Primer paso", dueKind = DueKind.DATE, dueDay = DAY_ZERO + 5)

            val stored = repo.task("t1")!!
            assertEquals("Nuevo", stored.title)
            assertEquals(DueKind.DATE, stored.dueKind)
            assertEquals(DAY_ZERO + 5, stored.dueDay)
            assertEquals(1, db.taskEventDao().all().size)
        }

    @Test
    fun `editing an unknown id is a no-op, not a crash`() =
        runTest {
            repo.update("nope", title = "X", firstStep = null, dueKind = DueKind.NONE, dueDay = null)

            assertTrue(db.taskDao().all().isEmpty())
        }

    @Test
    fun `the three event kinds are written with their day`() =
        runTest {
            repo.create(taskEntity(id = "t1"))

            repo.postpone("t1", DAY_ZERO, nowMillis = 1_000L)
            repo.bringToToday("t1", DAY_ZERO + 1, nowMillis = 2_000L)
            repo.recordAttempt("t1", DAY_ZERO + 2, nowMillis = 3_000L)

            assertEquals(
                listOf(
                    TaskEventKind.POSTPONED to DAY_ZERO,
                    TaskEventKind.BROUGHT to (DAY_ZERO + 1),
                    TaskEventKind.ATTEMPT to (DAY_ZERO + 2),
                ),
                db.taskEventDao().all().map { it.kind to it.logicalDay },
            )
        }

    @Test
    fun `posponing the same task twice on the same day writes two events`() =
        runTest {
            // La historia es historia: el tope de una por dia vive en MoodEngine, no aqui.
            repo.create(taskEntity(id = "t1"))
            repo.postpone("t1", DAY_ZERO, nowMillis = 1_000L)
            repo.postpone("t1", DAY_ZERO, nowMillis = 2_000L)

            assertEquals(2, db.taskEventDao().all().size)
        }

    @Test
    fun `deleting takes the history with it`() =
        runTest {
            repo.create(taskEntity(id = "t1"))
            repo.postpone("t1", DAY_ZERO, nowMillis = 1_000L)

            repo.delete("t1")

            assertNull(repo.task("t1"))
            assertTrue(db.taskEventDao().all().isEmpty())
        }
}
