package com.alvarotc.bito.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.HabitStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
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

/**
 * Room now backs [SettingsViewModel.archivedHabits], so this suite runs on Robolectric with an
 * in-memory database — same shape as [BackupViewModelTest]: one scheduler backs Dispatchers.Main
 * and both Room's executors and the DataStore's write-actor scope, so advanceUntilIdle() is
 * deterministic end to end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: BitoDatabase
    private lateinit var repository: SettingsRepository
    private lateinit var vm: SettingsViewModel

    private fun store(name: String): DataStore<Preferences> =
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
        repository = SettingsRepository(store("settings-vm"))
        vm = SettingsViewModel(repository, HabitsRepository(db))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `state is null until the repository's first emission lands`() =
        runTest {
            assertNull(vm.state.value)
            advanceUntilIdle()
            assertEquals(0, vm.state.value?.dayCutoffMinutes)
        }

    @Test
    fun `the cutoff clamps to the zero-to-six window in half hours`() =
        runTest {
            advanceUntilIdle()

            vm.setCutoff(7 * 60)
            advanceUntilIdle()
            assertEquals(360, vm.state.value?.dayCutoffMinutes)

            vm.setCutoff(-30)
            advanceUntilIdle()
            assertEquals(0, vm.state.value?.dayCutoffMinutes)

            vm.setCutoff(95)
            advanceUntilIdle()
            assertEquals(90, vm.state.value?.dayCutoffMinutes)
        }

    @Test
    fun `reminders stay sorted and deduplicated`() =
        runTest {
            advanceUntilIdle()

            vm.addReminder(20 * 60)
            advanceUntilIdle()
            vm.addReminder(9 * 60)
            advanceUntilIdle()
            vm.addReminder(9 * 60)
            advanceUntilIdle()

            assertEquals(listOf(9 * 60, 20 * 60), vm.state.value?.globalReminderMinutes)
        }

    @Test
    fun `removing a reminder hour leaves the rest`() =
        runTest {
            advanceUntilIdle()

            vm.addReminder(9 * 60)
            advanceUntilIdle()
            vm.addReminder(20 * 60)
            advanceUntilIdle()
            vm.addReminder(14 * 60)
            advanceUntilIdle()

            vm.removeReminder(9 * 60)
            advanceUntilIdle()

            assertEquals(listOf(14 * 60, 20 * 60), vm.state.value?.globalReminderMinutes)
        }

    @Test
    fun `editing a reminder hour lands as one coherent change`() =
        runTest {
            advanceUntilIdle()

            vm.addReminder(9 * 60)
            advanceUntilIdle()

            // The screen composes an edit as removeReminder(old) + addReminder(new) back-to-back,
            // with no advanceUntilIdle() between the two calls — pins that DataStore.edit's own
            // serialization (not caller-side sequencing) is what keeps this coherent.
            vm.removeReminder(9 * 60)
            vm.addReminder(20 * 60)
            advanceUntilIdle()

            assertEquals(listOf(20 * 60), vm.state.value?.globalReminderMinutes)
        }

    @Test
    fun `the review time is a plain write`() =
        runTest {
            advanceUntilIdle()

            vm.setReviewTime(22 * 60)
            advanceUntilIdle()

            assertEquals(22 * 60, vm.state.value?.reviewTimeMinutes)
        }

    @Test
    fun `setHabiSounds persists the toggle`() =
        runTest {
            advanceUntilIdle()
            assertTrue(vm.state.value?.habiSoundsEnabled == true) // default (T7)

            vm.setHabiSounds(false)
            advanceUntilIdle()
            assertEquals(false, vm.state.value?.habiSoundsEnabled)

            vm.setHabiSounds(true)
            advanceUntilIdle()
            assertEquals(true, vm.state.value?.habiSoundsEnabled)
        }

    @Test
    fun `setPerfectDayCelebration writes`() =
        runTest {
            advanceUntilIdle()
            assertTrue(vm.state.value?.perfectDayCelebration == true) // default

            vm.setPerfectDayCelebration(false)
            advanceUntilIdle()
            assertEquals(false, vm.state.value?.perfectDayCelebration)

            vm.setPerfectDayCelebration(true)
            advanceUntilIdle()
            assertEquals(true, vm.state.value?.perfectDayCelebration)
        }

    @Test
    fun `setLanguage persists the tag`() =
        runTest {
            advanceUntilIdle()
            assertNull(vm.state.value?.languageTag)

            vm.setLanguage("es")
            advanceUntilIdle()

            assertEquals("es", vm.state.value?.languageTag)
        }

    @Test
    fun `setLanguage null clears back to system`() =
        runTest {
            advanceUntilIdle()

            vm.setLanguage("en")
            advanceUntilIdle()
            assertEquals("en", vm.state.value?.languageTag)

            vm.setLanguage(null)
            advanceUntilIdle()

            assertNull(vm.state.value?.languageTag)
        }

    @Test
    fun `settings lets the user rename themselves`() =
        runTest {
            advanceUntilIdle()
            assertEquals("", vm.state.value?.userName)

            vm.setUserName("  Alvaro  ")
            advanceUntilIdle()

            assertEquals("Alvaro", vm.state.value?.userName)
        }

    @Test
    fun `archivedHabits starts empty and is empty until a habit is archived`() =
        runTest {
            val habits = HabitsRepository(db)
            habits.create(habitEntity(id = "h1", name = "Meditar"))
            advanceUntilIdle()

            assertTrue(vm.archivedHabits.value.isEmpty())
        }

    @Test
    fun `archivedHabits reflects only archived habits, not active or paused ones`() =
        runTest {
            val habits = HabitsRepository(db)
            habits.create(habitEntity(id = "active", name = "Agua"))
            habits.create(habitEntity(id = "paused", name = "Yoga", status = HabitStatus.PAUSED))
            habits.create(habitEntity(id = "gone", name = "Fumar", status = HabitStatus.ARCHIVED, archivedOnDay = 20_010))
            advanceUntilIdle()

            assertEquals(listOf("gone"), vm.archivedHabits.value.map { it.id })
            assertEquals("Fumar", vm.archivedHabits.value.single().name)
            assertEquals(20_010, vm.archivedHabits.value.single().archivedOnDay)
        }
}
