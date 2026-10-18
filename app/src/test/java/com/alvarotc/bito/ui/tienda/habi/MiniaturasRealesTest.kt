package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Las miniaturas reales, sin la sustitución de las pruebas de geometría: una pieza suelta, una limpia y un nudo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MiniaturasRealesTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `real thumbnails compose`() {
        rule.setContent {
            ConSistema {
                Box(Modifier.size(84.dp, 78.dp).testTag("m")) {
                    MiniaturaPieza(requireNotNull(HabiCatalog.byId("head-gorro-lana")))
                }
                MiniaturaRiel(RanuraHabi.PIES)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("m").assertIsDisplayed()
    }
}
