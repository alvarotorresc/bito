package com.alvarotc.bito.ui.premios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnclasTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `an anchored node registers its bounds and forgets them when it leaves`() {
        val registro = RegistroAnclas()
        var visible by mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalAnclas provides registro) {
                if (visible) Box(Modifier.size(40.dp).anclaParticula(ClavesAncla.fila("h1")))
            }
        }
        rule.waitForIdle()
        assertTrue(registro.posiciones.containsKey("fila:h1"))
        visible = false
        rule.waitForIdle()
        assertFalse(registro.posiciones.containsKey("fila:h1"))
    }

    @Test
    fun `without a registry the modifier does nothing`() {
        rule.setContent { Box(Modifier.size(40.dp).anclaParticula("x").testTag("caja")) }
        rule.onNodeWithTag("caja").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
    }

    @Test
    fun `drops, put-outs and grains travel on the same flow in order`() =
        runTest {
            val registro = RegistroAnclas()
            val eventos = mutableListOf<EventoGota>()
            val recoge = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { registro.gotas.toList(eventos) }
            registro.gota(ClavesAncla.fila("h1"))
            registro.apagarGota(ClavesAncla.fila("h1"))
            registro.granos(ClavesAncla.tarea("t1"))
            assertEquals(
                listOf(EventoGota.Sale("fila:h1"), EventoGota.Apaga("fila:h1"), EventoGota.Granos("tarea:t1")),
                eventos,
            )
            recoge.cancel()
        }
}
