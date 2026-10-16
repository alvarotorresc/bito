package com.alvarotc.bito.data.sesion

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Los dos relojes y el contador de arranques del sistema, leídos a la vez. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AhoraAndroidTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `reads both clocks and the boot counter`() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 12)
        val antes = System.currentTimeMillis()
        val ahora = ahoraDelSistema(context)
        assertTrue(ahora.paredMillis >= antes)
        assertTrue(ahora.paredMillis <= System.currentTimeMillis())
        assertEquals(SystemClock.elapsedRealtime(), ahora.elapsedMillis)
        assertEquals(12, ahora.arranques)
    }

    @Test
    fun `without the boot counter it is null`() {
        assertNull(ahoraDelSistema(context).arranques)
    }
}
