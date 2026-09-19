package com.alvarotc.bito.ui.notifications

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [GraphicsMode.Mode.NATIVE] is required for the same reason as [com.alvarotc.bito.ui.habi.HabiDrawingTest]:
 * [HabiNotificationIcon.bitmapOf] calls `renderHabiBitmap`, which builds a Compose `ImageBitmap`
 * that crashes under Robolectric's default LEGACY graphics mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HabiNotificationIconTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the icon is 128 square and caches the same spec`() {
        val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
        val first = HabiNotificationIcon.bitmapOf(context, spec)

        assertEquals(128, first.width)
        assertEquals(128, first.height)
        assertSame(first, HabiNotificationIcon.bitmapOf(context, spec))
    }

    @Test
    fun `a different mood invalidates the cache`() {
        val normal = HabiNotificationIcon.bitmapOf(context, HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()))
        val dramatic = HabiNotificationIcon.bitmapOf(context, HabiSpec(Mood.DRAMATIC, Personality.NEUTRA, EquippedSet()))

        assertNotSame(normal, dramatic)
    }

    /**
     * La caché es de UNA entrada, así que una clave incompleta no sirve un bitmap viejo: sirve el
     * bitmap EQUIVOCADO. Los dos campos que `HabiSpec` puede cambiar sin tocar mood, personalidad,
     * equipo, ojos ni pose tienen que entrar en la clave como el resto.
     */
    @Test
    fun `the body tone override and the closed lids invalidate the cache too`() {
        val base = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())

        val plain = HabiNotificationIcon.bitmapOf(context, base)
        val tinted = HabiNotificationIcon.bitmapOf(context, base.copy(bodyToneOverride = Color.Magenta))
        val closed = HabiNotificationIcon.bitmapOf(context, base.copy(closedEyes = true))

        assertNotSame(plain, tinted)
        assertNotSame(tinted, closed)
    }
}
