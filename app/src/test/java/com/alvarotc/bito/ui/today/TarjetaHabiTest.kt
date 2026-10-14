package com.alvarotc.bito.ui.today

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.R
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.runBlocking
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
class TarjetaHabiTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private var banco: BancoHoy? = null

    @After
    fun tearDown() {
        banco?.cerrar()
    }

    private val todas = setOf(Respuesta.VALE, Respuesta.AHORA, Respuesta.HOY_NO)

    private fun texto(res: Int) = TextoHabi(res)

    private fun montar(
        fuente: FuenteFalsa,
        sonido: SonidoGrabado = SonidoGrabado(),
    ) {
        rule.setContent {
            ConSistema(sonido, TactoGrabado()) {
                val p by fuente.flujo.collectAsState()
                TarjetaHabi(p, { r -> fuente.respuestas += r })
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a greeting without actions shows no buttons`() {
        montar(FuenteFalsa(Propuesta("saludo", texto(R.string.habi_name_fallback), emptySet())))
        rule.onNodeWithTag("tarjeta-habi").assertIsDisplayed()
        rule.onAllNodesWithText("Vale").assertCountEquals(0)
        rule.onAllNodesWithText("Ahora").assertCountEquals(0)
    }

    @Test
    fun `no proposal shows no card`() {
        montar(FuenteFalsa(null))
        rule.onAllNodesWithText("Habi").assertCountEquals(0)
        rule.onAllNodesWithTag("tarjeta-habi").assertCountEquals(0)
    }

    @Test
    fun `the three answers are in a row and each one answers`() {
        val fuente = FuenteFalsa(Propuesta("p", texto(R.string.habi_name_fallback), todas))
        montar(fuente)
        val vale = rule.onNodeWithText("Vale").getBoundsInRoot()
        val hoyNo = rule.onNodeWithText("Hoy no").getBoundsInRoot()
        val ahora = rule.onNodeWithText("Ahora").getBoundsInRoot()
        assertEquals(vale.top.value, hoyNo.top.value, 2f)
        assertEquals(vale.top.value, ahora.top.value, 2f)
        rule.onNodeWithText("Ahora").performClick()
        rule.onNodeWithText("Hoy no").performClick()
        assertEquals(listOf(Respuesta.AHORA, Respuesta.HOY_NO), fuente.respuestas)
    }

    @Test
    fun `the yes label can be custom`() {
        montar(FuenteFalsa(Propuesta("p", texto(R.string.habi_name_fallback), todas, etiquetaVale = texto(R.string.hoy_ahora))))
        rule.onAllNodesWithText("Ahora").assertCountEquals(2)
        rule.onAllNodesWithText("Vale").assertCountEquals(0)
    }

    @Test
    fun `a new proposal arriving sprouts with its sound`() {
        val sonido = SonidoGrabado()
        val fuente = FuenteFalsa(null)
        montar(fuente, sonido)
        assertTrue(Snd.BROTE !in sonido.snds())
        fuente.flujo.value = Propuesta("nueva", texto(R.string.habi_name_fallback), todas)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("tarjeta-habi").assertIsDisplayed()
        assertTrue(Snd.BROTE in sonido.snds())
    }

    @Test
    fun `Vale relays to the new text`() {
        val fuente = FuenteFalsa(Propuesta("uno", texto(R.string.habi_name_fallback), todas))
        montar(fuente)
        rule.onNodeWithText("campeón").assertIsDisplayed()
        rule.onNodeWithText("Vale").performClick()
        fuente.flujo.value = Propuesta("dos", texto(R.string.hoy_tu_dia), emptySet())
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("Tu día").assertIsDisplayed()
        rule.onAllNodesWithText("campeón").assertCountEquals(0)
    }

    @Test
    fun `Hoy no swaps the card for the short line without buttons`() {
        val fuente = FuenteFalsa(Propuesta("uno", texto(R.string.habi_name_fallback), todas))
        montar(fuente)
        rule.onNodeWithText("Hoy no").performClick()
        fuente.flujo.value = Propuesta("corta", texto(R.string.hoy_tu_dia), emptySet())
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText("Tu día").assertIsDisplayed()
        rule.onAllNodesWithText("Hoy no").assertCountEquals(0)
    }

    @Test
    fun `now in the list navigates to the session route`() {
        val fuente = FuenteFalsa(Propuesta("p", texto(R.string.habi_name_fallback), todas, destino = Destino.Tarea("t1")))
        val b = BancoHoy(rule, tmp).also { banco = it }
        b.preparar(fuente)
        runBlocking { TasksRepository(b.db).create(taskEntity(id = "t1", createdOnDay = b.hoy)) }
        b.montar()
        rule.onNodeWithText("Ahora").performClick()
        assertEquals(listOf("ruta:focus?taskId=t1"), b.llamadas)
        assertEquals(listOf(Respuesta.AHORA), fuente.respuestas)
    }
}
