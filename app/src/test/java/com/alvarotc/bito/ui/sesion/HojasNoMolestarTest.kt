package com.alvarotc.bito.ui.sesion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.SondaSalto
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class HojasNoMolestarTest {
    @get:Rule
    val compose = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val permiso = BancoPermiso()
    private val avisos = mutableListOf<String>()
    private var activado by mutableStateOf(false)
    private var ahoraNo = 0
    private val sondaSalto = SondaSalto()
    private lateinit var flujo: FlujoNoMolestar

    private fun montar(personalidad: Personality = Personality.NEUTRA) {
        compose.setContent {
            ConSistema(sonido, tacto) {
                permiso.Con {
                    flujo = rememberFlujoNoMolestar()
                    Column(Modifier.fillMaxSize()) {
                        HojasNoMolestar(
                            flujo = flujo,
                            noMolestar = permiso.noMolestar,
                            spec = habiCon(personalidad),
                            alActivar = { activado = true },
                            alAhoraNo = { ahoraNo++ },
                            aviso = { avisos += it },
                            sondaSalto = sondaSalto,
                        )
                    }
                }
            }
        }
        compose.runOnIdle { flujo.pedir() }
        compose.waitForIdle()
    }

    @Test
    fun `the permission sheet has its exact texts and the soft exit above the principal`() {
        montar()
        compose.onNodeWithText("No molestar durante la sesión").assertIsDisplayed()
        compose.onNodeWithText(
            "Android pide tu permiso para silenciar avisos. Solo se activa mientras dura la sesión; al terminar, todo vuelve como estaba.",
        ).assertIsDisplayed()
        val ahora = compose.onNodeWithText("Ahora no").getBoundsInRoot()
        val dar = compose.onNodeWithText("Dar permiso").getBoundsInRoot()
        assertTrue(ahora.bottom <= dar.top)
        assertTrue(dar.height >= 48.dp)
    }

    @Test
    fun `now not closes the sheet and tells the caller`() {
        montar()
        compose.onNodeWithText("Ahora no").performClick()
        compose.waitForIdle()
        assertEquals(1, ahoraNo)
        assertFalse(activado)
        assertEquals(PasoPermiso.NINGUNO, flujo.paso)
        compose.onNodeWithText("No molestar durante la sesión").assertDoesNotExist()
    }

    @Test
    fun `giving the permission opens the system screen, turns it on and says it`() {
        permiso.concederAlLanzar = true
        montar()
        compose.onNodeWithText("Dar permiso").performClick()
        compose.waitForIdle()
        assertEquals(1, permiso.lanzados.size)
        assertEquals("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS", permiso.lanzados.single().action)
        assertTrue(activado)
        assertEquals(PasoPermiso.NINGUNO, flujo.paso)
        assertTrue(Snd.INTERRUPTOR_ON in sonido.snds())
        assertTrue(Haptica.CONFIRM in tacto.hapticas)
        assertEquals(listOf("Listo: el móvil se silencia al empezar."), avisos)
    }

    private fun denegado(
        personalidad: Personality,
        frase: String,
    ) {
        montar(personalidad)
        compose.onNodeWithText("Dar permiso").performClick()
        compose.waitForIdle()
        assertFalse(activado)
        assertEquals(PasoPermiso.DENEGADO, flujo.paso)
        compose.onNodeWithText("No molestar sigue apagado").assertIsDisplayed()
        compose.onNodeWithText(frase).assertIsDisplayed()
        compose.onNodeWithText("Es opcional. Puedes darlo cuando quieras desde Ajustes.").assertIsDisplayed()
        val sin = compose.onNodeWithText("Seguir sin silenciar").getBoundsInRoot()
        val dar = compose.onNodeWithText("Dar permiso").getBoundsInRoot()
        assertTrue(sin.bottom <= dar.top)
        // El hueco de Habi: 140 dp; Habi, el 86 % de su alto.
        compose.onNodeWithTag("dnd-hueco").assertHeightIsEqualTo(140.dp)
        compose.onNodeWithTag("dnd-habi").assertHeightIsEqualTo(120.4.dp)
    }

    @Test
    fun `denied shows the neutral voice phrase`() =
        denegado(Personality.NEUTRA, "Sin ese permiso no puedo silenciar el móvil durante la sesión. Lo demás funciona igual.")

    @Test
    fun `denied shows the cheerleader voice phrase`() =
        denegado(Personality.CHEERLEADER, "Sin ese permiso no puedo silenciar el móvil mientras trabajas. ¡Pero la sesión sigue igual!")

    @Test
    fun `denied shows the sergeant voice phrase`() =
        denegado(Personality.SARGENTO, "Sin ese permiso el móvil puede sonar durante la sesión. La sesión sigue.")

    @Test
    fun `from denied, giving the permission goes to Listo with arrival and confirm`() {
        montar()
        compose.onNodeWithText("Dar permiso").performClick()
        compose.waitForIdle()
        permiso.concederAlLanzar = true
        compose.onNodeWithText("Dar permiso").performClick()
        compose.waitForIdle()
        assertTrue(activado)
        assertEquals(PasoPermiso.LISTO, flujo.paso)
        compose.onNodeWithText("Silenciaré el móvil mientras dure cada sesión y lo dejaré como estaba al terminar.").assertIsDisplayed()
        assertTrue(Snd.LLEGADA in sonido.snds())
        assertTrue(Haptica.CONFIRM in tacto.hapticas)
        compose.onNodeWithText("Vale").performClick()
        compose.waitForIdle()
        assertEquals(PasoPermiso.NINGUNO, flujo.paso)
    }

    @Test
    fun `Listo makes Habi jump 0,24`() {
        montar()
        compose.onNodeWithText("Dar permiso").performClick()
        compose.waitForIdle()
        permiso.concederAlLanzar = true
        compose.onNodeWithText("Dar permiso").performClick()
        compose.waitUntil(5_000) { sondaSalto.picoFraccion > 0f }
        compose.waitForIdle()

        assertEquals(0.24f, sondaSalto.picoFraccion, 0.01f)
    }

    @Test
    fun `keep going without silencing is a low tick and a notice`() {
        montar()
        compose.onNodeWithText("Dar permiso").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Seguir sin silenciar").performClick()
        compose.waitForIdle()
        assertTrue(Haptica.LOW in tacto.hapticas)
        assertEquals(listOf("Vale: la sesión empieza sin silenciar el móvil."), avisos)
        assertFalse(activado)
        assertEquals(PasoPermiso.NINGUNO, flujo.paso)
    }
}
