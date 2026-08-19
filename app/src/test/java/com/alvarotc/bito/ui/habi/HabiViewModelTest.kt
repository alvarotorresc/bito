package com.alvarotc.bito.ui.habi

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.customizationItemEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

/**
 * Drives [HabiViewModel] over an in-memory Room database, following [DetailViewModelTest]'s
 * harness: a [StandardTestDispatcher] shared by Room's executors and `viewModelScope`, advanced
 * explicitly after every action.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabiViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private lateinit var domainStateRepo: DomainStateRepository
    private lateinit var rewardsRepo: RewardsRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var reconciler: PointsReconciler
    private lateinit var habiSounds: HabiSounds

    private fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "habi-vm.preferences_pb") }

    private fun newViewModel() =
        HabiViewModel(
            domainStateRepo,
            rewardsRepo,
            settingsRepo,
            reconciler,
            habiSounds,
            now = { fixedNow },
            zone = { utc },
            defaultDispatcher = dispatcher,
        )

    /** Grants [points] via a ledger row no engine derivation could ever produce or reconcile away. */
    private suspend fun seedBalance(points: Int) {
        db.pointsLedgerDao().insert(
            pointsLedgerEntity(
                id = "seed-${System.nanoTime()}",
                delta = points,
                reason = PointsReason.HABIT_DONE,
                refId = null,
                logicalDay = today,
            ),
        )
    }

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
        rewardsRepo = RewardsRepository(db)
        settingsRepo = SettingsRepository(settingsStore())
        reconciler = PointsReconciler(domainStateRepo, rewardsRepo)
        habiSounds = HabiSounds(context, settingsRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `purchase succeeds, equips the item and clears the preview`() =
        runTest {
            seedBalance(20) // body-lavanda's exact price
            val vm = newViewModel()
            // uiState is a WhileSubscribed StateFlow: without a live collector the combine above
            // never runs, so `.value` would stay frozen at the seeded HabiUiState() forever —
            // same reason DetailViewModelTest asserts against the DB directly instead. A
            // background collector on the TestScope's own backgroundScope (auto-cancelled when
            // the test ends) is enough to keep it live for `.value` reads below.
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.preview("body-lavanda")
            advanceUntilIdle()
            assertEquals("body-lavanda", vm.uiState.value.previewItemId)

            vm.purchase("body-lavanda")
            advanceUntilIdle()

            assertNull(vm.uiState.value.previewItemId)
            val row = db.customizationItemDao().byId("body-lavanda")
            assertTrue(row != null && row.equipped)
            assertTrue(db.pointsLedgerDao().all().any { it.reason == PointsReason.BUY_ITEM && it.refId == "body-lavanda" })
        }

    @Test
    fun `purchase without enough balance leaves the preview open and writes nothing`() =
        runTest {
            val vm = newViewModel()
            // uiState is a WhileSubscribed StateFlow: without a live collector the combine above
            // never runs, so `.value` would stay frozen at the seeded HabiUiState() forever —
            // same reason DetailViewModelTest asserts against the DB directly instead. A
            // background collector on the TestScope's own backgroundScope (auto-cancelled when
            // the test ends) is enough to keep it live for `.value` reads below.
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.preview("upper-copa") // price 120, balance 0
            advanceUntilIdle()

            vm.purchase("upper-copa")
            advanceUntilIdle()

            assertEquals("upper-copa", vm.uiState.value.previewItemId)
            assertTrue(db.pointsLedgerDao().all().none { it.reason == PointsReason.BUY_ITEM })
            assertNull(db.customizationItemDao().byId("upper-copa"))
        }

    @Test
    fun `buyFreezer moved from Detail spends points and grows the inventory`() =
        runTest {
            seedBalance(100)
            val vm = newViewModel()
            // uiState is a WhileSubscribed StateFlow: without a live collector the combine above
            // never runs, so `.value` would stay frozen at the seeded HabiUiState() forever —
            // same reason DetailViewModelTest asserts against the DB directly instead. A
            // background collector on the TestScope's own backgroundScope (auto-cancelled when
            // the test ends) is enough to keep it live for `.value` reads below.
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.buyFreezer()
            advanceUntilIdle()

            assertEquals(1, vm.uiState.value.freezersOwned)
            assertEquals(0, vm.uiState.value.balance)
        }

    @Test
    fun `buyFreezer refuses without balance`() =
        runTest {
            val vm = newViewModel()
            // uiState is a WhileSubscribed StateFlow: without a live collector the combine above
            // never runs, so `.value` would stay frozen at the seeded HabiUiState() forever —
            // same reason DetailViewModelTest asserts against the DB directly instead. A
            // background collector on the TestScope's own backgroundScope (auto-cancelled when
            // the test ends) is enough to keep it live for `.value` reads below.
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            vm.buyFreezer()
            advanceUntilIdle()

            assertTrue(db.pointsLedgerDao().all().none { it.reason == PointsReason.BUY_FREEZER })
            assertEquals(0, vm.uiState.value.freezersOwned)
        }

    @Test
    fun `unequipDefault clears the category so the default falls back in place of the tapped item`() =
        runTest {
            val vm = newViewModel()
            // uiState is a WhileSubscribed StateFlow: without a live collector the combine above
            // never runs, so `.value` would stay frozen at the seeded HabiUiState() forever —
            // same reason DetailViewModelTest asserts against the DB directly instead. A
            // background collector on the TestScope's own backgroundScope (auto-cancelled when
            // the test ends) is enough to keep it live for `.value` reads below.
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            db.customizationItemDao().upsert(customizationItemEntity("body-vainilla", CustomizationCategory.BODY_COLOR, equipped = true))
            advanceUntilIdle()
            assertEquals("body-vainilla", vm.uiState.value.spec.equipped.bodyColor)

            vm.unequipDefault(HabiCatalog.byId("body-salvia")!!)
            advanceUntilIdle()

            assertEquals("body-salvia", vm.uiState.value.spec.equipped.bodyColor)
            assertEquals(false, db.customizationItemDao().byId("body-vainilla")?.equipped)
        }

    @Test
    fun `unequip takes off a worn optional, same write as unequipDefault`() =
        runTest {
            val vm = newViewModel()
            // uiState is a WhileSubscribed StateFlow: without a live collector the combine above
            // never runs, so `.value` would stay frozen at the seeded HabiUiState() forever —
            // same reason DetailViewModelTest asserts against the DB directly instead. A
            // background collector on the TestScope's own backgroundScope (auto-cancelled when
            // the test ends) is enough to keep it live for `.value` reads below.
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()
            db.customizationItemDao().upsert(customizationItemEntity("upper-lazo", CustomizationCategory.UPPER, equipped = true))
            advanceUntilIdle()
            assertEquals("upper-lazo", vm.uiState.value.spec.equipped.upper)

            vm.unequip(CustomizationCategory.UPPER)
            advanceUntilIdle()

            assertNull(vm.uiState.value.spec.equipped.upper)
        }
}
