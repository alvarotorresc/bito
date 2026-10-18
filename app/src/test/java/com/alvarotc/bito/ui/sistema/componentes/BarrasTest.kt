package com.alvarotc.bito.ui.sistema.componentes

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `masBarras` suma la barra de estado arriba y la de navegación abajo al relleno de una lista. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class BarrasTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun ponerBarras(
        estado: Int,
        navegacion: Int,
    ) {
        rule.runOnUiThread {
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, estado, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navegacion))
                    .build()
            ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, insets)
        }
        rule.waitForIdle()
    }

    private fun medir(
        arriba: Boolean = true,
        abajo: Boolean = true,
    ): List<Float> {
        WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false)
        var valores = emptyList<Float>()
        rule.setContent {
            val dir = LocalLayoutDirection.current
            val p = PaddingValues(horizontal = 16.dp, vertical = 8.dp).masBarras(arriba, abajo)
            val d = LocalDensity.current
            valores =
                with(d) {
                    listOf(p.calculateTopPadding().toPx(), p.calculateBottomPadding().toPx(), p.calculateLeftPadding(dir).toPx())
                }
        }
        rule.waitForIdle()
        ponerBarras(66, 48)
        return valores
    }

    @Test
    fun `adds the status bar on top and the navigation bar at the bottom`() {
        val densidad = rule.activity.resources.displayMetrics.density
        val v = medir()
        assertEquals(8f * densidad + 66f, v[0], 1f)
        assertEquals(8f * densidad + 48f, v[1], 1f)
        assertEquals(16f * densidad, v[2], 0.5f)
    }

    @Test
    fun `each bar can be left out`() {
        val densidad = rule.activity.resources.displayMetrics.density
        val v = medir(arriba = false)
        assertEquals(8f * densidad, v[0], 1f)
        assertEquals(8f * densidad + 48f, v[1], 1f)
    }
}
