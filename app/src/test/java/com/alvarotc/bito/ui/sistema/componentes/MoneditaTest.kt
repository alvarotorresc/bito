package com.alvarotc.bito.ui.sistema.componentes

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoneditaTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `both coin images decode, are square and are not empty in the middle`() {
        val res = ApplicationProvider.getApplicationContext<Context>().resources
        listOf(R.drawable.coin to 192, R.drawable.coin_small to 72).forEach { (id, px) ->
            val b = BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })
            assertEquals(px, b.width)
            assertEquals(px, b.height)
            assertTrue(Color.alpha(b.getPixel(px / 2, px / 2)) > 200)
            assertTrue(Color.alpha(b.getPixel(1, 1)) < 30)
        }
    }

    @Test
    fun `the coin is drawn at 18 and 48 dp`() {
        rule.setContent {
            ConSistema {
                Column {
                    Monedita(18.dp, Modifier.testTag("m18"))
                    Monedita(48.dp, Modifier.testTag("m48"))
                }
            }
        }
        rule.onNodeWithTag("m18").assertWidthIsEqualTo(18.dp)
        rule.onNodeWithTag("m48").assertWidthIsEqualTo(48.dp)
    }
}
