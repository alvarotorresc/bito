package com.alvarotc.bito.ui.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Metric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickActionUseCaseTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    // A non-zero cutoff so a naive wall-clock date would give the wrong logical day.
    private val cutoffMinutes = 240
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z — before the 04:00 cutoff
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, cutoffMinutes, utc)

    private lateinit var db: BitoDatabase
    private lateinit var journalRepo: JournalRepository
    private lateinit var domainStateRepo: DomainStateRepository
    private lateinit var habitsRepo: HabitsRepository
    private lateinit var rewardsRepo: RewardsRepository
    private lateinit var reconciler: PointsReconciler
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var useCase: QuickActionUseCase

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        journalRepo = JournalRepository(db)
        domainStateRepo = DomainStateRepository(db)
        habitsRepo = HabitsRepository(db)
        rewardsRepo = RewardsRepository(db)
        reconciler = PointsReconciler(domainStateRepo, rewardsRepo)
        settingsRepo = SettingsRepository(settingsStore("quick-action-use-case"))
        useCase =
            QuickActionUseCase(
                journalRepo,
                reconciler,
                domainStateRepo,
                habitsRepo,
                settingsRepo,
                now = { fixedNow },
                zone = { utc },
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a quick action logs without opening the app`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(dayCutoffMinutes = cutoffMinutes) }
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.COUNT,
                    direction = Direction.AT_LEAST,
                    target = 8,
                    step = 2,
                    createdOnDay = today,
                ),
            )

            useCase.log("h1", 2)

            val entries = db.entryDao().forHabitInRange("h1", today, today)
            assertEquals(1, entries.size)
            assertEquals(2, entries.first().value)
            assertEquals(today, entries.first().logicalDay)
        }

    @Test
    fun `the notification refreshes honestly after the action`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(dayCutoffMinutes = cutoffMinutes) }
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    target = 1,
                    createdOnDay = today,
                ),
            )
            habitsRepo.create(
                habitEntity(
                    id = "h2",
                    name = "Meditar",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    target = 1,
                    createdOnDay = today,
                ),
            )

            val result = useCase.log("h1", 1)

            assertTrue(result.payload != null)
            assertEquals(listOf("Meditar"), result.payload!!.pendingNames)
        }

    @Test
    fun `completing the last pending habit returns null so the notification dies`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(dayCutoffMinutes = cutoffMinutes) }
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    target = 1,
                    createdOnDay = today,
                ),
            )

            val result = useCase.log("h1", 1)

            assertNull(result.payload)
        }

    @Test
    fun `a quick action that completes the last daily habit reports perfectDayReached`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(dayCutoffMinutes = cutoffMinutes) }
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    target = 1,
                    createdOnDay = today,
                ),
            )
            habitsRepo.create(
                habitEntity(
                    id = "h2",
                    name = "Meditar",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    target = 1,
                    createdOnDay = today,
                ),
            )
            // h2 is already done; h1 is the last habit left, so completing it makes today perfect.
            journalRepo.log(EntryEntity(UUID.randomUUID().toString(), "h2", today, 1, fixedNow))

            val completing = useCase.log("h1", 1)

            assertTrue(completing.perfectDayReached)

            // A further log on the already-fulfilled h2 reconciles nothing new — the grant is already in the ledger.
            val alreadyGranted = useCase.log("h2", 1)

            assertFalse(alreadyGranted.perfectDayReached)
        }
}
