package com.alvarotc.bito.ui

import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [NavRequests] is a process-wide singleton: reset it after every test so other test classes
 * that touch it (e.g. [BitoNavHostTest]) never inherit a leftover pending route. */
class NavRequestsTest {
    @After
    fun tearDown() {
        NavRequests.consume()
    }

    @Test
    fun `open sets the pending route`() {
        NavRequests.open("review")

        assertEquals("review", NavRequests.pending.value)
    }

    @Test
    fun `consume clears the pending route`() {
        NavRequests.open("review")

        NavRequests.consume()

        assertNull(NavRequests.pending.value)
    }

    @Test
    fun `open sets tasks as the pending route`() {
        NavRequests.open("tasks")

        assertEquals("tasks", NavRequests.pending.value)
    }

    @Test
    fun `open ignores a route outside the allowlist`() {
        // MainActivity is exported (LAUNCHER): any app can send an arbitrary openRoute extra.
        // A non-allowlisted route must never reach the NavHost, which would otherwise crash on
        // NavController.navigate(String) for an unknown destination.
        NavRequests.open("garbage")

        assertNull(NavRequests.pending.value)
    }

    @Test
    fun `open sets focus as the pending route`() {
        NavRequests.open("focus")

        assertEquals("focus", NavRequests.pending.value)
    }

    @Test
    fun `open ignores focus with a query string, exact strings only`() {
        // The allowlist is a set of exact strings, never a pattern — a route carrying an id
        // (as the notification's Intent extra never does, but a hostile one could try) must be
        // rejected exactly like any other non-allowlisted string.
        NavRequests.open("focus?taskId=abc")

        assertNull(NavRequests.pending.value)
    }

    @Test
    fun `open sets breathing as the pending route`() {
        NavRequests.open("breathing")

        assertEquals("breathing", NavRequests.pending.value)
    }

    @Test
    fun `open ignores breathing with a suffix, exact strings only`() {
        NavRequests.open("breathing?mode=SLEEP")

        assertNull(NavRequests.pending.value)
    }
}
