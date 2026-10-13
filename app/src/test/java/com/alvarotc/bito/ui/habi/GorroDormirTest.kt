package com.alvarotc.bito.ui.habi

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GorroDormirTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val size = 400

    @Test
    fun `the sleep cap paints above the head where the bare head has nothing`() {
        val cap = HabiAtlas.pieces.getValue(SLEEP_CAP_KEY)
        assertTrue("el gorro tiene que asomar por encima del cuerpo", cap.top < 0f)
        val bare = renderHabiBitmap(context, HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()), size)
        val capped = renderHabiBitmap(context, HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet(), sleepCap = true), size)
        val fit = rigFit(size.toFloat(), size.toFloat())
        val x0 = (fit.originX + cap.left * fit.scale).toInt().coerceAtLeast(0)
        val x1 = (fit.originX + (cap.left + cap.width) * fit.scale).toInt().coerceAtMost(size)
        val y0 = (fit.originY + cap.top * fit.scale).toInt().coerceAtLeast(0)
        val y1 = fit.originY.toInt().coerceAtMost(size)
        var onlyCap = 0
        for (x in x0 until x1) {
            for (y in y0 until y1) {
                if (Color.alpha(capped.getPixel(x, y)) > 128 && Color.alpha(bare.getPixel(x, y)) < 10) onlyCap++
            }
        }
        assertTrue("el gorro tiene que pintar donde la cabeza sola no pinta ($onlyCap píxeles)", onlyCap > 200)
    }
}
