package com.alvarotc.bito.testutil

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.today.HojaOtraHora
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.ContinuationInterceptor

/**
 * Regresión de ReparaAplicadorSnapshots. `a_envenena` deja, ya desmontada la regla de Compose, una
 * escritura de estado global con el post del aplicador pendiente; Robolectric tira ese post al
 * reiniciar la cola del looper. Sin la reparación, `b_victima` empieza con el aplicador atascado y
 * su primer cambio de estado desde el hilo principal bombea el reloj hasta AppNotIdleException.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h640dp")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EnvenenaSnapshotTest {
    @get:Rule(order = 0)
    val veneno =
        object : TestWatcher() {
            override fun finished(description: Description) {
                if (description.methodName.startsWith("a_")) {
                    mutableStateOf(0).value = 1
                    assertEquals("el veneno no dejó el aplicador pendiente", listOf(true, true, true), banderas())
                }
            }
        }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    @Test
    fun a_envenena() {
        rule.setContent { }
        rule.waitForIdle()
    }

    @Test
    fun b_victima() {
        assertEquals("aplicador atascado al empezar (sent, trampolín, fotograma)", listOf(false, false, false), banderas())
        val guardados = mutableListOf<Int>()
        rule.setContent {
            ConSistema { HojaOtraHora(true, 13 * 60, 20 * 60, 14 * 60 + 30, alGuardar = { guardados += it }, alCerrar = {}) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("hoja-hora-reloj").performSemanticsAction(SemanticsActions.SetProgress) { it(16 * 60f) }
        rule.waitForIdle()
        rule.onNodeWithTag("hoja-hora-guardar").performClick()
        assertEquals(listOf(16 * 60), guardados)
    }

    private fun banderas(): List<Boolean> {
        val gsm = Class.forName("androidx.compose.ui.platform.GlobalSnapshotManager")
        val sent = gsm.getDeclaredField("sent").apply { isAccessible = true }.get(null) as java.util.concurrent.atomic.AtomicBoolean
        val dispatcher = AndroidUiDispatcher.Main[ContinuationInterceptor] as AndroidUiDispatcher

        fun bandera(nombre: String) =
            AndroidUiDispatcher::class.java.getDeclaredField(nombre).apply { isAccessible = true }.getBoolean(dispatcher)
        return listOf(sent.get(), bandera("scheduledTrampolineDispatch"), bandera("scheduledFrameDispatch"))
    }
}
