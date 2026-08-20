package com.alvarotc.bito.ui.celebration

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.ui.habi.HabiSounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CelebrationsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")

    private lateinit var db: BitoDatabase
    private lateinit var domainStateRepo: DomainStateRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var rewardsRepo: RewardsRepository
    private lateinit var habiSounds: HabiSounds
    private lateinit var vm: CelebrationsViewModel

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        domainStateRepo = DomainStateRepository(db)
        settingsRepo = SettingsRepository(settingsStore("celebrations-vm"))
        rewardsRepo = RewardsRepository(db)
        habiSounds = HabiSounds(context, settingsRepo, dispatcher = dispatcher)
        vm =
            CelebrationsViewModel(
                domainStateRepo,
                settingsRepo,
                rewardsRepo,
                habiSounds,
                now = { fixedNow },
                zone = { utc },
                defaultDispatcher = dispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun TestScope.state(): CelebrationsUiState {
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()
        return vm.uiState.value
    }

    @Test
    fun `dismissPerfectDay writes today as celebrated`() =
        runTest {
            // A non-zero cutoff so a naive wall-clock date would give the wrong logical day —
            // 00:00 UTC on fixedNow's date sits BEFORE the 04:00 cutoff, so the logical day is
            // yesterday's, proving dismissPerfectDay derives it via todayOf rather than a raw date.
            val cutoffMinutes = 240
            settingsRepo.update { it.copy(dayCutoffMinutes = cutoffMinutes) }
            val expectedToday = LogicalDays.logicalDayOf(fixedNow, cutoffMinutes, utc)
            state()

            vm.dismissPerfectDay()
            advanceUntilIdle()

            assertEquals(expectedToday, settingsRepo.settings.first().perfectDayCelebratedDay)
        }

    @Test
    fun `dismissBadges writes now as seen`() =
        runTest {
            state()

            vm.dismissBadges()
            advanceUntilIdle()

            assertEquals(fixedNow, settingsRepo.settings.first().badgesSeenUntilMillis)
        }

    // R7: CelebrationsViewModel outlives the composition (survives rotation), so BitoNavHost's
    // composition-scoped cue LaunchedEffect re-running with the same keys after recreation must
    // not double-play the sound for the same pending sheet — the idempotency lives in cue()
    // itself via lastCuedSignature. HabiSounds has no seam to assert playback directly, so these
    // assert through the exposed signature instead.
    @Test
    fun `cue plays once for a pending perfect day and is a no-op on a second call`() =
        runTest {
            val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)
            db.pointsLedgerDao().insert(
                pointsLedgerEntity(reason = PointsReason.PERFECT_DAY, refId = "day:$today", logicalDay = today),
            )
            state()

            vm.cue()
            vm.cue()

            assertEquals("perfect-day", vm.lastCued)
        }

    @Test
    fun `cue does nothing once the perfect day is dismissed and no badges are pending`() =
        runTest {
            val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)
            db.pointsLedgerDao().insert(
                pointsLedgerEntity(reason = PointsReason.PERFECT_DAY, refId = "day:$today", logicalDay = today),
            )
            state()
            vm.dismissPerfectDay()
            advanceUntilIdle()
            state()

            vm.cue()

            assertNull(vm.lastCued)
        }
}
