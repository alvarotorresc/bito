package com.alvarotc.bito.ui.camino

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.BadgeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogrosArteTest {
    @Test
    fun `every catalog badge has its art`() {
        assertEquals(BadgeCatalog.all.map { it.id }.toSet(), LogrosArte.porId.keys)
        val context = ApplicationProvider.getApplicationContext<Context>()
        BadgeCatalog.all.forEach { def ->
            val res = LogrosArte.de(def.id)
            assertNotNull(def.id, res)
            val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.resources.openRawResource(res!!).use { BitmapFactory.decodeStream(it, null, opciones) }
            assertEquals(def.id, 496, opciones.outWidth)
        }
    }
}
