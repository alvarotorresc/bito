package com.alvarotc.bito.ui.today

import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.propuestas.Accion
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h640dp")
class EfectosHoyTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private var banco: BancoHoy? = null

    @After
    fun tearDown() {
        banco?.cerrar()
    }

    private fun conAccion(
        respuesta: Respuesta,
        efecto: Efecto,
    ): FuenteFalsa {
        val fuente =
            FuenteFalsa(
                Propuesta(
                    "p",
                    TextoHabi(R.string.habla_habi_duerme),
                    listOf(Accion(respuesta, TextoHabi(R.string.habla_vale), principal = true)),
                ),
            )
        fuente.efecto = efecto
        val b = BancoHoy(rule, tmp).also { banco = it }
        b.preparar(fuente)
        b.montar()
        return fuente
    }

    @Test
    fun `AbrirCierre opens the day closing`() {
        conAccion(Respuesta.CERRAR_DIA, Efecto.AbrirCierre)
        rule.onNodeWithTag("habi-cerrar_dia").performClick()
        rule.waitForIdle()
        assertEquals(listOf("repaso"), banco!!.llamadas)
    }

    @Test
    fun `AbrirLoGordo opens the big thing sheet`() {
        conAccion(Respuesta.ELEGIR_GORDO, Efecto.AbrirLoGordo)
        rule.onNodeWithTag("habi-elegir_gordo").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hoja-gordo").assertIsDisplayed()
    }

    @Test
    fun `ElegirHora opens the other hour sheet and saving answers with the minute`() {
        val fuente = conAccion(Respuesta.OTRA_HORA, Efecto.ElegirHora(13 * 60, 20 * 60, 14 * 60 + 30))
        rule.onNodeWithTag("habi-otra_hora").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hoja-hora").assertIsDisplayed()
        rule.onNodeWithTag("hoja-hora-reloj").performSemanticsAction(SemanticsActions.SetProgress) { it(16 * 60f) }
        fuente.efecto = Efecto.Ninguno
        rule.onNodeWithTag("hoja-hora-guardar").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Respuesta.OTRA_HORA, Respuesta.OTRA_HORA), fuente.respuestas)
        assertEquals(listOf<Int?>(null, 16 * 60), fuente.horas)
    }

    @Test
    fun `the exact alarm intent points at this app`() {
        val intent: Intent = intentAlarmasExactas("com.alvarotc.bito")
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
        assertEquals("package:com.alvarotc.bito", intent.dataString)
    }

    @Test
    fun `the sink steps 25 ms per row and ends all rows together`() {
        assertEquals(0f, hundidaDeFila(0f, 0), 0f)
        assertEquals(1f, hundidaDeFila(1f, 0), 0f)
        assertEquals(1f, hundidaDeFila(1f, 7), 0f)
        // A la mitad del recorrido la primera fila va por delante de la última.
        assertTrue(hundidaDeFila(0.5f, 0) > hundidaDeFila(0.5f, 7))
        // El escalón es de 25 ms sobre los 220 ms de la fila: a progreso fijo, cada fila va 25/220 por detrás.
        val total = 220f + 25f * 7
        val c = 0.6f
        assertEquals(((c * total) / 220f).coerceIn(0f, 1f), hundidaDeFila(c, 0), 1e-4f)
        assertEquals(((c * total - 25f * 3) / 220f).coerceIn(0f, 1f), hundidaDeFila(c, 3), 1e-4f)
    }

    @Test
    fun `rows past the stagger cap sink with the last stepped one`() {
        assertEquals(hundidaDeFila(0.7f, 7), hundidaDeFila(0.7f, 30), 0f)
    }
}
