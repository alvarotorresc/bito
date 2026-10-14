package com.alvarotc.bito.ui.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AvisoHabiReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the receiver is declared in the manifest`() {
        val receivers = context.packageManager.queryBroadcastReceivers(Intent(context, AvisoHabiReceiver::class.java), 0)

        assertFalse(receivers.isEmpty())
    }

    @Test
    fun `an unknown action neither throws nor posts a notification`() {
        AvisoHabiReceiver().onReceive(context, Intent("com.alvarotc.bito.accion.desconocida"))

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(0, shadowOf(manager).size())
    }

    @Test
    fun `the action and extra names are the ones the notifications will carry`() {
        assertEquals(
            listOf("vale", "hoy_no", "mas_tarde"),
            listOf(AvisoHabiReceiver.ACTION_VALE, AvisoHabiReceiver.ACTION_HOY_NO, AvisoHabiReceiver.ACTION_MAS_TARDE)
                .map { it.substringAfterLast('.') },
        )
    }
}
