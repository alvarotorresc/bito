package com.alvarotc.bito.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FocusStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun kotlinx.coroutines.test.TestScope.store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    @Test
    fun `a fresh store has no session`() =
        runTest {
            val focus = FocusStore(store("fresh"))
            assertNull(focus.session.first())
        }

    @Test
    fun `a started session round-trips through the store`() =
        runTest {
            val focus = FocusStore(store("roundtrip"))
            val session =
                FocusSession(
                    taskId = "t1",
                    startedAtMillis = 5_000_000L,
                    endsAtMillis = 5_600_000L,
                    endsAtElapsed = 4_600_000L,
                    bootMillis = 400_000L,
                )
            focus.start(session)
            assertEquals(session, focus.session.first())
        }

    @Test
    fun `extendBy moves both clocks by the same amount`() =
        runTest {
            val focus = FocusStore(store("extend"))
            val session =
                FocusSession(
                    taskId = "t1",
                    startedAtMillis = 5_000_000L,
                    endsAtMillis = 5_600_000L,
                    endsAtElapsed = 4_600_000L,
                    bootMillis = 400_000L,
                )
            focus.start(session)
            focus.extendBy(5)
            val extended = focus.session.first()!!
            assertEquals(session.endsAtMillis + 5 * 60_000L, extended.endsAtMillis)
            assertEquals(session.endsAtElapsed + 5 * 60_000L, extended.endsAtElapsed)
        }

    @Test
    fun `clear leaves it null`() =
        runTest {
            val focus = FocusStore(store("clear"))
            val session =
                FocusSession(
                    taskId = "t1",
                    startedAtMillis = 5_000_000L,
                    endsAtMillis = 5_600_000L,
                    endsAtElapsed = 4_600_000L,
                    bootMillis = 400_000L,
                )
            focus.start(session)
            focus.clear()
            assertNull(focus.session.first())
        }

    @Test
    fun `the same boot signature makes the elapsed clock win`() {
        // Session written before a restart: same boot signature.
        val session =
            FocusSession(
                taskId = "t1",
                startedAtMillis = 5_000_000L,
                endsAtMillis = 5_600_000L,
                endsAtElapsed = 4_600_000L,
                bootMillis = 400_000L,
            )
        // Same boot signature: elapsed clock wins (and is already elapsed).
        // Boot signature = nowMillis - elapsedMillis = 5_300_000 - 4_900_000 = 400_000 (matches bootMillis).
        val remaining = FocusClock.remainingMillis(session, nowMillis = 5_300_000L, elapsedMillis = 4_900_000L)

        assertEquals(0L, remaining)
    }

    @Test
    fun `a different boot signature makes the wall clock win`() {
        // Session written before a restart: boot signature 1_000_000, end in 10 minutes.
        val session =
            FocusSession(
                taskId = "t1",
                startedAtMillis = 5_000_000L,
                endsAtMillis = 5_600_000L,
                endsAtElapsed = 4_600_000L,
                bootMillis = 400_000L,
            )
        // After restart, elapsedRealtime starts from zero: boot signature no longer matches.
        val remaining = FocusClock.remainingMillis(session, nowMillis = 5_300_000L, elapsedMillis = 30_000L)

        assertEquals(300_000L, remaining)
    }

    @Test
    fun `an end already past reports zero, never a negative`() {
        val session =
            FocusSession(
                taskId = "t1",
                startedAtMillis = 5_000_000L,
                endsAtMillis = 5_600_000L,
                endsAtElapsed = 4_600_000L,
                bootMillis = 400_000L,
            )
        // Current time is past the end.
        val remaining = FocusClock.remainingMillis(session, nowMillis = 5_700_000L, elapsedMillis = 5_300_000L)

        assertEquals(0L, remaining)
    }
}
