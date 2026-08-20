package com.alvarotc.bito.ui.stats

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Drives the real [BadgesScreen] over an in-memory Room database, following
 * [StatsScreenTest]'s harness.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class BadgesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var db: BitoDatabase

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

    private fun setContent(onBack: () -> Unit = {}) {
        val rewards = RewardsRepository(db)
        val vm = BadgesViewModel(rewards, defaultDispatcher = dispatcher)
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
}
