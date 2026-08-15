package com.alvarotc.bito.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // Same scheduler backs Dispatchers.Main and the DataStore's write-actor scope, matching the
    // pattern in BackupViewModelTest/HabitFormViewModelTest so advanceUntilIdle() is deterministic.
    private val dispatcher = StandardTestDispatcher()

    private lateinit var repository: SettingsRepository
    private lateinit var vm: SettingsViewModel

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = SettingsRepository(store("settings-vm"))
        vm = SettingsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
}
