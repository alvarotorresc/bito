package com.alvarotc.bito.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.customizationItemEntity
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.freezerUseEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.pauseIntervalEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.targetChangeEntity
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
class SnapshotDaosTest {
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
    fun `all returns every row and deleteAll leaves the table empty`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "a"))
            db.habitDao().upsert(habitEntity(id = "b", sortOrder = 1))
            db.entryDao().insert(entryEntity(id = "e1", habitId = "a"))
            db.daySealDao().insert(DaySealEntity(DAY_ZERO, 1L))
            assertEquals(2, db.habitDao().all().size)
            assertEquals(1, db.entryDao().all().size)
            assertEquals(1, db.daySealDao().all().size)
            db.entryDao().deleteAll()
            db.daySealDao().deleteAll()
            db.habitDao().deleteAll()
            assertEquals(emptyList<HabitEntity>(), db.habitDao().all())
            assertEquals(emptyList<EntryEntity>(), db.entryDao().all())
        }

    @Test
    fun `all returns every row and deleteAll leaves the table empty for the remaining DAOs`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))
            db.targetChangeDao().upsert(targetChangeEntity(habitId = "h1"))
            db.pauseIntervalDao().upsert(pauseIntervalEntity(habitId = "h1"))
            db.freezerUseDao().insert(freezerUseEntity(id = "f1", habitId = "h1"))
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "p1"))
            db.badgeDao().insert(BadgeEntity("first-week", 1L))
            db.customizationItemDao().upsert(customizationItemEntity(itemId = "hat-basic"))

            assertEquals(1, db.targetChangeDao().all().size)
            assertEquals(1, db.pauseIntervalDao().all().size)
            assertEquals(1, db.freezerUseDao().all().size)
            assertEquals(1, db.pointsLedgerDao().all().size)
            assertEquals(1, db.badgeDao().all().size)
            assertEquals(1, db.customizationItemDao().all().size)

            db.targetChangeDao().deleteAll()
            db.pauseIntervalDao().deleteAll()
            db.freezerUseDao().deleteAll()
            db.pointsLedgerDao().deleteAll()
            db.badgeDao().deleteAll()
            db.customizationItemDao().deleteAll()

            assertEquals(emptyList<TargetChangeEntity>(), db.targetChangeDao().all())
            assertEquals(emptyList<PauseIntervalEntity>(), db.pauseIntervalDao().all())
            assertEquals(emptyList<FreezerUseEntity>(), db.freezerUseDao().all())
            assertEquals(emptyList<PointsLedgerEntity>(), db.pointsLedgerDao().all())
            assertEquals(emptyList<BadgeEntity>(), db.badgeDao().all())
            assertEquals(emptyList<CustomizationItemEntity>(), db.customizationItemDao().all())
        }
}
