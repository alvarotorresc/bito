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
}
