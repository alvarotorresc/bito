package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.alvarotc.bito.domain.HabiEngine
import com.alvarotc.bito.domain.StoreItemState
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExclusivoEnTiendaTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private val t by lazy { TiendaReal(rule, tmp.root) }

    @After
    fun cerrar() = t.cerrar()

    private fun estadoDe(id: String): StoreItemState {
        val e = t.vm.uiState.value
        return HabiEngine.storeStateOf(HabiCatalog.byId(id)!!, e.poseidos, EquippedSet(), e.saldo, e.hoy)
    }

    @Test
    fun `a streak reward earned inside the store turns the tile to Owned without a sheet`() {
        t.abrir(saldo = 10)
        assertEquals(StoreItemState.Locked(7), estadoDe("head-flor"))
        t.ejecutar { t.rewards.grantItems(setOf("head-flor"), 1L) }
        t.avanzarHasta { "head-flor" in t.vm.uiState.value.poseidos }
        t.avanzar(400)
        assertEquals(StoreItemState.Owned, estadoDe("head-flor"))
        assertEquals("en silencio: sin sonido ni vibración", emptyList<Any>(), t.sonido.snds() + t.tacto.hapticas)
        rule.onNodeWithTag("hoja-logro", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("aviso", useUnmergedTree = true).assertDoesNotExist()
    }
}
