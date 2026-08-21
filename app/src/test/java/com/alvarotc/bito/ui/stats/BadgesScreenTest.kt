package com.alvarotc.bito.ui.stats

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertTrue

/**
 * Drives the real [BadgesScreen] over an in-memory Room database, following
 * [StatsScreenTest]'s harness — [BadgesViewModel] now also reads [SettingsRepository] for the
 * day cutoff (task-14 review finding 2), so this harness gains the same `settingsStore()` helper.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class BadgesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val utc = ZoneId.of("UTC")
    private var storeIndex = 0

    private lateinit var db: BitoDatabase

    private fun settingsStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher + Job()),
        ) { File(tmp.root, "badges-screen-${storeIndex++}.preferences_pb") }

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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun setContent(
        onBack: () -> Unit = {},
        dayCutoffMinutes: Int = 0,
    ) {
        val rewards = RewardsRepository(db)
        val settings = SettingsRepository(settingsStore())
        if (dayCutoffMinutes != 0) {
            runBlocking { settings.update { it.copy(dayCutoffMinutes = dayCutoffMinutes) } }
        }
        val vm = BadgesViewModel(rewards, settings, zone = { utc }, defaultDispatcher = dispatcher)
        compose.setContent {
            BitoTheme {
                BadgesScreen(viewModel = vm, onBack = onBack)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `every badge renders with its name`() {
        runBlocking {
            db.badgeDao().insert(BadgeEntity("streak-7", unlockedAtMillis = 1_000L))
        }
        setContent()

        // The full list is composed inside a verticalScroll Column, so rows below the fold
        // are placed but not necessarily on-screen — assertExists, not assertIsDisplayed.
        BadgeCatalog.all.forEach { def ->
            compose.onNodeWithTag("badge-${def.id}", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `back navigates out`() {
        var back = false
        setContent(onBack = { back = true })

        compose.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()

        assertTrue(back)
    }

    @Test
    fun `the unlock caption honors the day cutoff`() {
        // 2026-01-02T01:00 UTC with a 240min (4h) cutoff resolves to the PREVIOUS calendar day —
        // proves BadgesViewModel actually threads Settings.dayCutoffMinutes into
        // buildBadgesUiState, not just that the pure builder does the right arithmetic
        // (BadgesUiStateTest covers that in isolation).
        val unlockMillis = LocalDateTime.of(2026, 1, 2, 1, 0).atZone(utc).toInstant().toEpochMilli()
        runBlocking {
            db.badgeDao().insert(BadgeEntity("streak-7", unlockedAtMillis = unlockMillis))
        }

        setContent(dayCutoffMinutes = 240)

        compose.onNodeWithText("1 Jan 2026", substring = true, useUnmergedTree = true).assertExists()
    }
}
