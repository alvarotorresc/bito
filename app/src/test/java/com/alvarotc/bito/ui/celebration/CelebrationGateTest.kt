package com.alvarotc.bito.ui.celebration

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CelebrationGateTest {
    @Test
    fun `notifies when the grant just landed, the toggle is on and the app is not visible`() {
        assertTrue(CelebrationGate.shouldNotifyPerfectDay(reachedNow = true, celebrationEnabled = true, appVisible = false))
    }

    @Test
    fun `does not notify when the grant did not land just now`() {
        assertFalse(CelebrationGate.shouldNotifyPerfectDay(reachedNow = false, celebrationEnabled = true, appVisible = false))
    }

    @Test
    fun `does not notify when the celebration toggle is off`() {
        assertFalse(CelebrationGate.shouldNotifyPerfectDay(reachedNow = true, celebrationEnabled = false, appVisible = false))
    }

    @Test
    fun `does not notify while the app is visible`() {
        assertFalse(CelebrationGate.shouldNotifyPerfectDay(reachedNow = true, celebrationEnabled = true, appVisible = true))
    }
}
