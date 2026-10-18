package com.alvarotc.bito.ui.habi

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.customizationItemEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.ui.sistema.sonido.MotorSonidoMudo
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
            habiSounds,
            now = { fixedNow },
            zone = { utc },
            defaultDispatcher = dispatcher,
        )

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
        habiSounds = HabiSounds(MotorSonidoMudo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
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
            db.customizationItemDao().upsert(customizationItemEntity("neck-collar-flores", CustomizationCategory.NECK, equipped = true))
            advanceUntilIdle()
            assertEquals("neck-collar-flores", vm.uiState.value.spec.equipped.neck)

            vm.unequip(CustomizationCategory.NECK)
            advanceUntilIdle()

            assertNull(vm.uiState.value.spec.equipped.neck)
        }

    @Test
    fun `the garden shows the unlocked specials and the stored high mark`() =
        runTest {
            rewardsRepo.unlockBadges(setOf("resurrection"), 1L)
            settingsRepo.update { it.copy(gardenWaterHighMark = 26.0) }
            val vm = newViewModel()
            backgroundScope.launch { vm.uiState.collect {} }
            advanceUntilIdle()

            val garden = vm.uiState.value.garden
            assertEquals(2, garden.plots.size)
            assertEquals(setOf(GardenSpecial.MUSHROOMS), garden.specials)
        }
}
