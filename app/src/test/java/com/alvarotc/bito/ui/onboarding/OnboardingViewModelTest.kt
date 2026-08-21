package com.alvarotc.bito.ui.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habitform.HabitPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private lateinit var habitsRepo: HabitsRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var reconciler: PointsReconciler

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun newViewModel() = OnboardingViewModel(settingsRepo, habitsRepo, reconciler, now = { fixedNow }, zone = { utc })

    /** A second habit's fulfilled entry, logged directly (bypassing the VM), so its HABIT_DONE
     * grant is pending in the ledger. Mirrors DetailViewModelTest's own way of proving a reconcile
     * pass ran: after the call under test, this refId either does or doesn't show up in the ledger.
     */
    private suspend fun seedPendingGrant() {
        habitsRepo.create(
            habitEntity(id = "pending", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
        )
        db.entryDao().insert(entryEntity(id = "pending-entry", habitId = "pending", logicalDay = today, value = 1))
    }

    private suspend fun pendingGrantReconciled(): Boolean = db.pointsLedgerDao().all().any { it.refId == "pending:$today" }

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
        habitsRepo = HabitsRepository(db)
        settingsRepo = SettingsRepository(settingsStore("onboarding-vm"))
        reconciler = PointsReconciler(DomainStateRepository(db), RewardsRepository(db))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `finish persists the name, personality and onboarding flag`() =
        runTest {
            val vm = newViewModel()
            vm.setName("Alvaro")
            vm.setPersonality(Personality.SARGENTO)
            advanceUntilIdle()
            // setPersonality already persists on its own (7f speaks with the chosen voice right
            // away) -- clobber the store back so the assertion below can only pass if finish()
            // itself writes personality, not because setPersonality already did.
            settingsRepo.update { it.copy(personality = Personality.NEUTRA) }

            vm.finish()
            advanceUntilIdle()

            val stored = settingsRepo.settings.first()
            assertEquals("Alvaro", stored.userName)
            assertEquals(Personality.SARGENTO, stored.personality)
            assertTrue(stored.onboardingDone)
        }

    @Test
    fun `finish creates the first habit through the same write path`() =
        runTest {
            seedPendingGrant()
            assertFalse(pendingGrantReconciled())
            val vm = newViewModel()
            vm.setName("Alvaro")
            vm.setHabitName("Meditar")
            vm.setHabitKind(HabitPreset.DAILY_CHECK)

            vm.finish()
            advanceUntilIdle()

            val created = db.habitDao().all().single { it.name == "Meditar" }
            assertEquals(Metric.CHECK, created.metric)
            assertEquals(Direction.AT_LEAST, created.direction)
            assertEquals(today, created.createdOnDay)
            assertEquals(1, created.sortOrder) // "pending" (seeded above) already holds sortOrder 0
            val changes = db.targetChangeDao().forHabit(created.id)
            assertEquals(listOf(today), changes.map { it.effectiveFromDay })
            // Proves finish() actually called reconciler.reconcile, not just habits.create: this
            // grant belongs to an unrelated habit that finish() never touched directly.
            assertTrue(pendingGrantReconciled())
        }

    @Test
    fun `finish with a blank habit name creates nothing but still completes`() =
        runTest {
            seedPendingGrant()
            val vm = newViewModel()
            vm.setName("Alvaro")
            vm.setHabitName("   ")

            vm.finish()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.done)
            assertTrue(settingsRepo.settings.first().onboardingDone)
            assertEquals(listOf("pending"), db.habitDao().all().map { it.id })
            // No habit write happened, so finish() must not have called reconcile either
            // (controller ruling) -- the pending grant from an unrelated habit stays unswept.
            assertFalse(pendingGrantReconciled())
        }

    @Test
    fun `skipStory jumps from any story step to the name step`() =
        runTest {
            listOf(1, 2, 3).forEach { hopsToStory ->
                val vm = newViewModel()
                repeat(hopsToStory) { vm.next() }

                vm.skipStory()

                assertEquals(OnboardingStep.NAME, vm.uiState.value.step)
            }
        }

    @Test
    fun `next walks the steps in order`() =
        runTest {
            val vm = newViewModel()
            val expected = OnboardingStep.entries

            expected.forEach { step ->
                assertEquals(step, vm.uiState.value.step)
                vm.next()
            }

            // Pinned at the last step -- one extra call past the end is a no-op.
            assertEquals(expected.last(), vm.uiState.value.step)
        }

    @Test
    fun `back walks the steps in reverse order and stays pinned at the first`() =
        runTest {
            val vm = newViewModel()
            repeat(OnboardingStep.entries.size) { vm.next() }
            assertEquals(OnboardingStep.entries.last(), vm.uiState.value.step)

            OnboardingStep.entries.reversed().forEach { step ->
                assertEquals(step, vm.uiState.value.step)
                vm.back()
            }

            assertEquals(OnboardingStep.entries.first(), vm.uiState.value.step)
        }

    @Test
    fun `setPersonality persists immediately, before finish`() =
        runTest {
            val vm = newViewModel()

            vm.setPersonality(Personality.CHEERLEADER)
            advanceUntilIdle()

            assertEquals(Personality.CHEERLEADER, settingsRepo.settings.first().personality)
            assertFalse(vm.uiState.value.done)
        }

    @Test
    fun `setLanguage persists the tag and updates the visible state`() =
        runTest {
            val vm = newViewModel()

            vm.setLanguage("en")
            advanceUntilIdle()

            assertEquals("en", vm.uiState.value.languageTag)
            assertEquals("en", settingsRepo.settings.first().languageTag)
        }

    @Test
    fun `a second call to finish while busy does not create a duplicate habit`() =
        runTest {
            val vm = newViewModel()
            vm.setName("Alvaro")
            vm.setHabitName("Meditar")

            vm.finish()
            vm.finish()
            advanceUntilIdle()

            assertEquals(1, db.habitDao().all().count { it.name == "Meditar" })
        }
}
