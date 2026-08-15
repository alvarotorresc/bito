package com.alvarotc.bito.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.PointsReason
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
class PointsReconcilerTest {
    private lateinit var db: BitoDatabase
    private lateinit var habits: HabitsRepository
    private lateinit var journal: JournalRepository
    private lateinit var reconciler: PointsReconciler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        habits = HabitsRepository(db)
        journal = JournalRepository(db)
        reconciler = PointsReconciler(DomainStateRepository(db), RewardsRepository(db))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `reconcile appends the grants history justifies`() =
        runTest {
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            journal.log(entryEntity(id = "e1", habitId = "cama", logicalDay = DAY_ZERO, value = 1))
            reconciler.reconcile(today = DAY_ZERO, nowMillis = 10L)
            val ledger = db.pointsLedgerDao().all()
            assertTrue(ledger.any { it.reason == PointsReason.HABIT_DONE && it.refId == "cama:$DAY_ZERO" })
        }

    @Test
    fun `reconcile twice grants nothing twice`() =
        runTest {
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            journal.log(entryEntity(id = "e1", habitId = "cama", logicalDay = DAY_ZERO, value = 1))
            reconciler.reconcile(DAY_ZERO, 10L)
            val count = db.pointsLedgerDao().all().size
            reconciler.reconcile(DAY_ZERO, 20L)
            assertEquals(count, db.pointsLedgerDao().all().size)
        }

    @Test
    fun `an undone entry never claws back a granted point`() =
        runTest {
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            journal.log(entryEntity(id = "e1", habitId = "cama", logicalDay = DAY_ZERO, value = 1))
            reconciler.reconcile(DAY_ZERO, 10L)
            journal.remove("e1")
            reconciler.reconcile(DAY_ZERO, 20L)
            assertTrue(db.pointsLedgerDao().all().any { it.refId == "cama:$DAY_ZERO" })
        }
}
