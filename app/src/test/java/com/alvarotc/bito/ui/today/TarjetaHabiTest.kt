package com.alvarotc.bito.ui.today

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.propuestas.Accion
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
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
import org.robolectric.RuntimeEnvironment
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

    private val todas =
        listOf(
            Accion(Respuesta.VALE, TextoHabi(R.string.hoy_vale), principal = true),
            Accion(Respuesta.AHORA, TextoHabi(R.string.hoy_ahora)),
            Accion(Respuesta.HOY_NO, TextoHabi(R.string.task_not_today)),
        )

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
        montar(FuenteFalsa(Propuesta("saludo", texto(R.string.habi_name_fallback), emptyList())))
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
    fun `actions are painted in their order with the first one as main`() {
        val ctx = RuntimeEnvironment.getApplication() as Context
        val fuente =
            FuenteFalsa(
                Propuesta(
                    "p",
                    texto(R.string.habi_name_fallback),
                    listOf(
                        Accion(Respuesta.CERRAR_DIA, TextoHabi(R.string.today_close_day), principal = true),
                        Accion(Respuesta.LUEGO, TextoHabi(R.string.task_not_today)),
                    ),
                ),
            )
        montar(fuente)
        val cerrar = rule.onNodeWithText(ctx.getString(R.string.today_close_day)).getBoundsInRoot()
        val luego = rule.onNodeWithText(ctx.getString(R.string.task_not_today)).getBoundsInRoot()
        assertTrue(cerrar.left < luego.left)
        assertEquals(cerrar.top.value, luego.top.value, 2f)
        rule.onNodeWithText(ctx.getString(R.string.today_close_day)).performClick()
        assertEquals(listOf(Respuesta.CERRAR_DIA), fuente.respuestas)
    }

    @Test
    fun `the yes label can be custom`() {
        montar(
            FuenteFalsa(
                Propuesta(
                    "p",
                    texto(R.string.habi_name_fallback),
                    todas.map { if (it.principal) it.copy(etiqueta = texto(R.string.hoy_ahora)) else it },
                ),
            ),
        )
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
        fuente.flujo.value = Propuesta("dos", texto(R.string.hoy_tu_dia), emptyList())
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
        fuente.flujo.value = Propuesta("corta", texto(R.string.hoy_tu_dia), emptyList())
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText("Tu día").assertIsDisplayed()
        rule.onAllNodesWithText("Hoy no").assertCountEquals(0)
    }

    @Test
    fun `now in the list navigates to the session route`() {
        val fuente = FuenteFalsa(Propuesta("p", texto(R.string.habi_name_fallback), todas, destino = Destino.Tarea("t1")))
        fuente.efecto = Efecto.Sesion(Destino.Tarea("t1"))
        val b = BancoHoy(rule, tmp).also { banco = it }
        b.preparar(fuente)
        runBlocking { TasksRepository(b.db).create(taskEntity(id = "t1", createdOnDay = b.hoy)) }
        b.montar()
        rule.onNodeWithText("Ahora").performClick()
        assertEquals(listOf("ruta:focus?taskId=t1"), b.llamadas)
        assertEquals(listOf(Respuesta.AHORA), fuente.respuestas)
    }

    @Test
    fun `a proposal that arrives after answering and an empty gap sprouts again`() {
        val sonido = SonidoGrabado()
        val fuente = FuenteFalsa(Propuesta("uno", texto(R.string.habi_name_fallback), todas))
        montar(fuente, sonido)
        rule.onNodeWithText("Vale").performClick()
        val tras = sonido.snds().count { it == Snd.BROTE }
        fuente.flujo.value = null
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        fuente.flujo.value = Propuesta("dos", texto(R.string.hoy_tu_dia), todas)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(tras + 1, sonido.snds().count { it == Snd.BROTE })
    }

    private fun montarConTacto(
        fuente: FuenteFalsa,
        sonido: SonidoGrabado,
        tacto: TactoGrabado,
    ) {
        rule.setContent {
            ConSistema(sonido, tacto) {
                val p by fuente.flujo.collectAsState()
                TarjetaHabi(p, { r -> fuente.respuestas += r })
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `buttons are at least 44dp tall`() {
        montar(FuenteFalsa(Propuesta("p", texto(R.string.habi_name_fallback), todas)))
        listOf("habi-vale", "habi-ahora", "habi-hoy_no").forEach {
            rule.onNodeWithTag(it).assertHeightIsAtLeast(44.dp)
        }
    }

    @Test
    fun `the longest row of buttons stays inside the card`() {
        val largas =
            listOf(
                Accion(Respuesta.AHORA, TextoHabi(R.string.habla_empezar_25), principal = true),
                Accion(Respuesta.OTRA_HORA, TextoHabi(R.string.habla_otra_hora)),
                Accion(Respuesta.LUEGO, TextoHabi(R.string.habla_todavia_no)),
            )
        montar(FuenteFalsa(Propuesta("p", texto(R.string.habi_name_fallback), largas)))
        val tarjeta = rule.onNodeWithTag("tarjeta-habi").getBoundsInRoot()
        listOf("habi-ahora", "habi-otra_hora", "habi-luego").forEach {
            val caja = rule.onNodeWithTag(it).getBoundsInRoot()
            assertTrue("$it sale por la derecha de la tarjeta", caja.right <= tarjeta.right)
        }
    }

    @Test
    fun `Luego sinks the card with its sound and touch`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        val fuente =
            FuenteFalsa(
                Propuesta("p", texto(R.string.habi_name_fallback), listOf(Accion(Respuesta.LUEGO, TextoHabi(R.string.habla_luego)))),
            )
        montarConTacto(fuente, sonido, tacto)
        rule.onNodeWithTag("habi-luego").performClick()
        assertTrue(Snd.HUNDIR in sonido.snds())
        assertTrue(Haptica.FALL in tacto.hapticas)
        assertEquals(listOf(Respuesta.LUEGO), fuente.respuestas)
    }

    @Test
    fun `a Vale without an hour is the hard day one with tic and confirm`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        val fuente = FuenteFalsa(Propuesta("DIA_DURO:h1:20251 ", texto(R.string.habi_name_fallback), todas, hora = null))
        montarConTacto(fuente, sonido, tacto)
        rule.onNodeWithTag("habi-vale").performClick()
        assertTrue(Snd.TIC in sonido.snds())
        assertTrue(Haptica.CONFIRM in tacto.hapticas)
    }

    @Test
    fun `a Vale with an hour keeps the sprout sound of the relay`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        val fuente = FuenteFalsa(Propuesta("p", texto(R.string.habi_name_fallback), todas, hora = 870))
        montarConTacto(fuente, sonido, tacto)
        rule.onNodeWithTag("habi-vale").performClick()
        assertTrue(Snd.BROTE in sonido.snds())
        assertTrue(Snd.TIC !in sonido.snds())
    }

    @Test
    fun `Hecho shrinks the card without its text`() {
        val fuente =
            FuenteFalsa(
                Propuesta(
                    "p",
                    texto(R.string.habi_name_fallback),
                    listOf(Accion(Respuesta.HECHO, TextoHabi(R.string.habla_hecho), principal = true)),
                ),
            )
        montar(fuente)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("habi-hecho").performClick()
        fuente.flujo.value = null
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(40)
        // La tarjeta sigue encogiéndose, pero ya sin la frase ni el botón.
        rule.onNodeWithTag("tarjeta-habi").assertExists()
        rule.onAllNodesWithText("campeón").assertCountEquals(0)
        rule.onAllNodesWithTag("habi-hecho").assertCountEquals(0)
    }

    @Test
    fun `a Vale without an hour in another situation does not fold the buttons`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        val fuente = FuenteFalsa(Propuesta("HABITO:h1:20251015", texto(R.string.habi_name_fallback), todas, hora = null))
        montarConTacto(fuente, sonido, tacto)
        rule.onNodeWithTag("habi-vale").performClick()
        assertTrue(Snd.TIC !in sonido.snds())
        assertTrue(Haptica.CONFIRM !in tacto.hapticas)
    }
}
