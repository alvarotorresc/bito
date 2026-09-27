package com.alvarotc.bito.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.breathingSessionEntity
import com.alvarotc.bito.domain.model.BreathingMode
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
class BreathingSessionDaoTest {
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
    fun `insert and read back preserves the five fields`() =
        runTest {
            val complete =
                breathingSessionEntity(
                    id = "b1",
                    mode = BreathingMode.SLEEP,
                    startedAtMillis = 5_000L,
                    durationSeconds = 114,
                    completed = true,
                )
            val stopped =
                breathingSessionEntity(
                    id = "b2",
                    mode = BreathingMode.FOCUS,
                    startedAtMillis = 9_000L,
                    durationSeconds = 37,
                    completed = false,
                )
            db.breathingSessionDao().insert(complete)
            db.breathingSessionDao().insert(stopped)

            assertEquals(listOf(complete, stopped), db.breathingSessionDao().all())
        }

    @Test
    fun `observeAll emits what is stored, oldest first`() =
        runTest {
            db.breathingSessionDao().insert(breathingSessionEntity(id = "late", startedAtMillis = 9_000L))
            db.breathingSessionDao().insert(breathingSessionEntity(id = "early", startedAtMillis = 1_000L))

            assertEquals(listOf("early", "late"), db.breathingSessionDao().observeAll().first().map { it.id })
        }

    @Test
    fun `an empty table emits an empty list right away`() =
        runTest {
            assertTrue(db.breathingSessionDao().observeAll().first().isEmpty())
        }

    @Test
    fun `deleteAll empties the table`() =
        runTest {
            db.breathingSessionDao().insert(breathingSessionEntity(id = "b1"))
            db.breathingSessionDao().insert(breathingSessionEntity(id = "b2"))

            db.breathingSessionDao().deleteAll()

            assertTrue(db.breathingSessionDao().all().isEmpty())
        }
}
