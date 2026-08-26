package com.alvarotc.bito.ui.onboarding

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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

/**
 * [OnboardingReconciler] is [com.alvarotc.bito.ui.BitoNavHost]'s final-review-flagged reconcile
 * hook: a v1/v2 backup restored onto a fresh install writes habits into Room but
 * [com.alvarotc.bito.data.backup.BackupRepository.import] replaces `Settings` wholesale with
 * whatever the backup carries — and every pre-M9.5 backup never had `onboardingDone` at all, so the
 * restored value is always the DataStore default, `false`. This exercises [OnboardingReconciler.reconcile]
 * directly against a plain `SettingsRepository`/`HabitsRepository` pair (the suspend function
 * [com.alvarotc.bito.ui.BitoNavHost] itself awaits, inline, before deciding its start destination —
 * see [com.alvarotc.bito.ui.BitoNavHostTest]'s own restorer test for that wiring), same recipe
 * [OnboardingViewModelTest] uses for its own Room + DataStore pair.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingReconcilerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: BitoDatabase
    private lateinit var habits: HabitsRepository
    private lateinit var settings: SettingsRepository

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
        habits = HabitsRepository(db)
        settings = SettingsRepository(settingsStore("onboarding-reconciler"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `a restored database with habits and onboarding not done gets it seeded true`() =
        runTest {
            db.habitDao().upsert(habitEntity(id = "h1"))

            OnboardingReconciler.reconcile(settings, habits)

            assertTrue(settings.settings.first().onboardingDone)
        }

    @Test
    fun `a genuinely fresh install with no habits leaves onboardingDone untouched`() =
        runTest {
            OnboardingReconciler.reconcile(settings, habits)

            assertFalse(settings.settings.first().onboardingDone)
        }

    @Test
    fun `onboardingDone already true is left alone even with habits present`() =
        runTest {
            settings.update { it.copy(onboardingDone = true) }
            db.habitDao().upsert(habitEntity(id = "h1"))

            OnboardingReconciler.reconcile(settings, habits)

            assertTrue(settings.settings.first().onboardingDone)
        }
}
