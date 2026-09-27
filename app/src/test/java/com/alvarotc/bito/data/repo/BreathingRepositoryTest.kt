package com.alvarotc.bito.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.BreathingSessionEntity
import com.alvarotc.bito.domain.model.BreathingMode
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
class BreathingRepositoryTest {
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
    fun `save generates an id and stores the session as given`() =
        runTest {
            val repository = BreathingRepository(db, newId = { "fixed-id" })

            repository.save(BreathingMode.FOCUS, startedAtMillis = 3_000L, durationSeconds = 128, completed = true)

            assertEquals(listOf(BreathingSessionEntity("fixed-id", BreathingMode.FOCUS, 3_000L, 128, true)), db.breathingSessionDao().all())
        }

    @Test
    fun `two saves are two independent sessions`() =
        runTest {
            val repository = BreathingRepository(db)

            repository.save(BreathingMode.CALM, startedAtMillis = 1_000L, durationSeconds = 120, completed = true)
            repository.save(BreathingMode.CALM, startedAtMillis = 2_000L, durationSeconds = 30, completed = false)

            val ids = db.breathingSessionDao().all().map { it.id }
            assertEquals(2, ids.toSet().size)
        }
}
