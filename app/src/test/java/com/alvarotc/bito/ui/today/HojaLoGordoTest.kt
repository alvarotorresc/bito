package com.alvarotc.bito.ui.today

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.CandidatoGordo
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.SubtituloGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h640dp")
class HojaLoGordoTest {
    @get:Rule val rule = createComposeRule()

    private val ctx = RuntimeEnvironment.getApplication() as Context
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val guardados = mutableListOf<List<RefGordo>>()
    private val avisados = mutableListOf<String>()

    private val informe = RefGordo(TipoRefGordo.TAREA, "informe")
    private val dentista = RefGordo(TipoRefGordo.TAREA, "dentista")
    private val leer = RefGordo(TipoRefGordo.HABITO, "leer")
    private val estirar = RefGordo(TipoRefGordo.HABITO, "estirar")

    private val candidatos =
        listOf(
            CandidatoGordo(informe, "Informe trimestral", SubtituloGordo.TareaHoy),
            CandidatoGordo(dentista, "Llamar al dentista", SubtituloGordo.TareaVencida(2)),
            CandidatoGordo(leer, "Leer 10 min", SubtituloGordo.Habito),
            CandidatoGordo(estirar, "Estirar 5 min", SubtituloGordo.Habito),
        )

    private fun montar(
        sugerencia: RefGordo? = informe,
        elegidos: List<RefGordo> = emptyList(),
        abierta: Boolean = true,
    ) {
        rule.setContent {
            ConSistema(sonido, tacto) {
                HojaLoGordo(
                    abierta = abierta,
                    candidatos = candidatos,
                    sugerencia = sugerencia,
                    elegidos = elegidos,
                    alGuardar = { guardados += it },
                    alCerrar = {},
                    alAvisar = { avisados += it },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun fila(ref: RefGordo) = rule.onNodeWithTag("gordo-${ref.id}")

    private fun apagado() =
        rule.onNodeWithTag("hoja-gordo-guardar").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun nota(res: Int) = ctx.getString(res)

    @Test
    fun `the suggestion is marked when opening`() {
        montar()
        fila(informe).assertIsOn()
        fila(dentista).assertIsOff()
        fila(leer).assertIsOff()
        rule.onNodeWithTag("hoja-gordo-nota").assertTextEquals(ctx.getString(R.string.habla_gordo_nota_n, 1))
    }

    @Test
    fun `the fourth does not enter and the note changes`() {
        montar(sugerencia = informe)
        fila(dentista).performClick()
        fila(leer).performClick()
        rule.onNodeWithTag("hoja-gordo-nota").assertTextEquals(nota(R.string.habla_gordo_nota_3))
        fila(estirar).performClick()
        rule.waitForIdle()
        fila(estirar).assertIsOff()
        fila(informe).assertIsOn()
        fila(dentista).assertIsOn()
        fila(leer).assertIsOn()
        rule.onNodeWithTag("hoja-gordo-nota").assertTextEquals(nota(R.string.habla_gordo_cuarta))
    }

    @Test
    fun `the fourth rejects with a haptic and no sound`() {
        montar(sugerencia = informe)
        fila(dentista).performClick()
        fila(leer).performClick()
        sonido.sonados.clear()
        tacto.hapticas.clear()
        fila(estirar).performClick()
        rule.waitForIdle()
        assertEquals(listOf(Haptica.REJECT), tacto.hapticas)
        assertTrue(sonido.snds().isEmpty())
    }

    @Test
    fun `removing one lets another in and the note goes back to the count`() {
        montar(sugerencia = informe)
        fila(dentista).performClick()
        fila(leer).performClick()
        fila(informe).performClick()
        rule.onNodeWithTag("hoja-gordo-nota").assertTextEquals(ctx.getString(R.string.habla_gordo_nota_n, 2))
        fila(estirar).performClick()
        fila(estirar).assertIsOn()
    }

    @Test
    fun `marking sounds a tic and unmarking empties the water`() {
        montar(sugerencia = null)
        sonido.sonados.clear()
        tacto.hapticas.clear()
        fila(leer).performClick()
        assertEquals(listOf(Snd.TIC), sonido.snds())
        assertEquals(listOf(Haptica.SEG), tacto.hapticas)
        sonido.sonados.clear()
        fila(leer).performClick()
        assertEquals(listOf(Snd.VACIAR), sonido.snds())
        assertTrue(Haptica.LOW in tacto.hapticas)
    }

    @Test
    fun `save is off without a selection`() {
        montar(sugerencia = null)
        assertEquals(ctx.getString(R.string.sistema_apagado), apagado())
        rule.onNodeWithTag("hoja-gordo-guardar").performClick()
        assertTrue(guardados.isEmpty())
        assertTrue(Haptica.LOW in tacto.hapticas)
        fila(leer).performClick()
        assertEquals(null, apagado())
    }

    @Test
    fun `saving reports the chosen refs in order`() {
        montar(sugerencia = informe)
        fila(leer).performClick()
        fila(dentista).performClick()
        rule.onNodeWithTag("hoja-gordo-guardar").performClick()
        rule.waitForIdle()
        assertEquals(listOf(listOf(informe, leer, dentista)), guardados)
    }

    @Test
    fun `saving confirms with a haptic and no sound beyond closing, and tells the list`() {
        montar(sugerencia = informe)
        fila(leer).performClick()
        sonido.sonados.clear()
        tacto.hapticas.clear()
        rule.onNodeWithTag("hoja-gordo-guardar").performClick()
        rule.waitForIdle()
        assertTrue(Haptica.CONFIRM in tacto.hapticas)
        assertEquals(listOf(Snd.HUNDIR), sonido.snds())
        assertEquals(listOf(ctx.getString(R.string.habla_gordo_aviso, "Informe trimestral, Leer 10 min")), avisados)
    }

    @Test
    fun `reopening marks what was chosen, not the suggestion`() {
        montar(sugerencia = informe, elegidos = listOf(leer, estirar))
        fila(leer).assertIsOn()
        fila(estirar).assertIsOn()
        fila(informe).assertIsOff()
        rule.onNodeWithTag("hoja-gordo-nota").assertTextEquals(ctx.getString(R.string.habla_gordo_nota_n, 2))
    }

    @Test
    fun `a sheet closed and opened again forgets an unsaved selection`() {
        var abierta by mutableStateOf(true)
        rule.setContent {
            ConSistema(sonido, tacto) {
                HojaLoGordo(abierta, candidatos, informe, emptyList(), { guardados += it }, { abierta = false })
            }
        }
        rule.waitForIdle()
        fila(leer).performClick()
        abierta = false
        rule.waitForIdle()
        abierta = true
        rule.waitForIdle()
        fila(leer).assertIsOff()
        fila(informe).assertIsOn()
    }

    @Test
    fun `rows show name and subtitle and are at least 44dp tall`() {
        montar()
        rule.onNodeWithTag("gordo-dentista").assertTextEquals("Llamar al dentista", "Tarea, vencida hace 2 días")
        rule.onNodeWithTag("gordo-leer").assertTextEquals("Leer 10 min", "Hábito")
        assertTrue(fila(leer).getBoundsInRoot().height >= 44.dp)
    }

    @Test
    fun `not now sits above and save is wide below`() {
        montar()
        val ahoraNo = rule.onNodeWithTag("hoja-gordo-ahora-no").getBoundsInRoot()
        val guardar = rule.onNodeWithTag("hoja-gordo-guardar").getBoundsInRoot()
        assertTrue(ahoraNo.bottom <= guardar.top)
        assertTrue(ahoraNo.height >= 44.dp)
        assertTrue(guardar.width > 250.dp)
    }

    @Test
    fun `closed it composes nothing`() {
        montar(abierta = false)
        rule.onAllNodesWithTag("hoja-gordo").assertCountEquals(0)
    }

    @Test
    fun `an open sheet seeds again when fresher data arrives until it is touched`() {
        // Hoy viejo o aún cargando: la hoja se abre con lo que hay y llega lo de verdad después.
        var lista by mutableStateOf(emptyList<CandidatoGordo>())
        var sugerida by mutableStateOf<RefGordo?>(null)
        rule.setContent {
            ConSistema(sonido, tacto) {
                HojaLoGordo(true, lista, sugerida, emptyList(), alGuardar = { guardados += it }, alCerrar = {})
            }
        }
        rule.waitForIdle()
        lista = candidatos
        sugerida = dentista
        rule.waitForIdle()
        rule.onNodeWithTag("gordo-dentista").assertIsOn()
        // Una vez tocada, lo que llega ya no pisa la elección.
        rule.onNodeWithTag("gordo-leer").performClick()
        sugerida = informe
        rule.waitForIdle()
        rule.onNodeWithTag("gordo-informe").assertIsOff()
        rule.onNodeWithTag("gordo-dentista").assertIsOn()
        rule.onNodeWithTag("gordo-leer").assertIsOn()
    }
}
