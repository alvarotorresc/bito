package com.alvarotc.bito.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.data.taskEventEntity
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
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
class TaskDaosTest {
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
            val task =
                taskEntity(
                    id = "t1",
                    title = "Llamar al banco",
                    firstStep = "Buscar el numero",
                    dueKind = DueKind.DATE,
                    dueDay = DAY_ZERO + 4,
                    status = TaskStatus.DONE,
                    createdAtMillis = 1_234L,
                    createdOnDay = DAY_ZERO,
                    doneAtMillis = 5_678L,
                    doneOnDay = DAY_ZERO + 2,
                )
            db.taskDao().upsert(task)
            assertEquals(task, db.taskDao().byId("t1"))
        }

    @Test
    fun `a loose task keeps its nulls`() =
        runTest {
            val task = taskEntity(id = "t2", firstStep = null, dueKind = DueKind.NONE, dueDay = null)
            db.taskDao().upsert(task)
            val read = db.taskDao().byId("t2")!!
            assertNull(read.firstStep)
            assertNull(read.dueDay)
            assertNull(read.doneOnDay)
        }

    @Test
    fun `byId returns null for an unknown id`() =
        runTest {
            assertNull(db.taskDao().byId("nope"))
        }

    @Test
    fun `observeAll and all see every stored task`() =
        runTest {
            db.taskDao().upsert(taskEntity(id = "a", createdAtMillis = 200L))
            db.taskDao().upsert(taskEntity(id = "b", createdAtMillis = 100L))

            assertEquals(setOf("a", "b"), db.taskDao().observeAll().first().map { it.id }.toSet())
            assertEquals(setOf("a", "b"), db.taskDao().all().map { it.id }.toSet())
        }

    @Test
    fun `events round-trip and keep their kind`() =
        runTest {
            db.taskDao().upsert(taskEntity(id = "t1"))
            val event = taskEventEntity(id = "e1", taskId = "t1", kind = TaskEventKind.POSTPONED, logicalDay = DAY_ZERO)
            db.taskEventDao().insert(event)

            assertEquals(listOf(event), db.taskEventDao().all())
        }

    @Test
    fun `deleting a task takes its history with it`() =
        runTest {
            db.taskDao().upsert(taskEntity(id = "t1"))
            db.taskDao().upsert(taskEntity(id = "t2"))
            db.taskEventDao().insert(taskEventEntity(id = "e1", taskId = "t1", kind = TaskEventKind.BROUGHT))
            db.taskEventDao().insert(taskEventEntity(id = "e2", taskId = "t2", kind = TaskEventKind.BROUGHT))

            db.taskDao().delete("t1")

            assertNull(db.taskDao().byId("t1"))
            assertEquals(listOf("e2"), db.taskEventDao().all().map { it.id })
        }

    @Test
    fun `deleteAll clears both tables`() =
        runTest {
            db.taskDao().upsert(taskEntity(id = "t1"))
            db.taskEventDao().insert(taskEventEntity(id = "e1", taskId = "t1", kind = TaskEventKind.ATTEMPT))

            db.taskEventDao().deleteAll()
            db.taskDao().deleteAll()

            assertTrue(db.taskDao().all().isEmpty())
            assertTrue(db.taskEventDao().all().isEmpty())
        }
}
