package com.alvarotc.bito.ui.camino

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.Progreso
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
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HojaLogroTest {
    @get:Rule
    val rule = createComposeRule()

    private val contexto: Context = ApplicationProvider.getApplicationContext()

    private fun cadena(
        id: Int,
        vararg args: Any,
    ) = contexto.getString(id, *args)

    private val dia8Julio = LocalDate.of(2026, 7, 8).toEpochDay().toInt()

    private fun logro(
        id: String,
        conseguido: Boolean,
        progreso: Progreso? = null,
    ) = LogroUi(
        BadgeCatalog.byId(id)!!,
        if (conseguido) dia8Julio else null,
        progreso,
        GardenSpecial.entries.firstOrNull { it.badgeId == id },
    )

    private fun montar(
        modo: ModoLogro,
        logro: LogroUi,
        sonido: SonidoGrabado = SonidoGrabado(),
        tacto: TactoGrabado = TactoGrabado(),
        alVerEnJardin: () -> Unit = {},
        hoy: LocalDate? = null,
        mesImposible: Boolean = false,
    ) {
        rule.setContent {
            ConSistema(sonido, tacto) {
                HojaLogro(true, logro, modo, alVerEnJardin, alCerrar = {}, hoy = hoy, mesImposible = mesImposible)
            }
        }
    }

    @Test
    fun `new mode lights up with its sounds and Seguir confirms`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        montar(ModoLogro.NUEVO, logro("streak-7", true, Progreso(7, 7)), sonido, tacto)
        rule.mainClock.advanceTimeBy(700)
        assertTrue(Snd.LOGRO in sonido.snds())
        assertTrue(Haptica.TICK in tacto.hapticas)
        assertTrue(Haptica.CLICK in tacto.hapticas)
        rule.onNodeWithText(cadena(R.string.camino_seguir)).performClick()
        assertTrue(Haptica.CONFIRM in tacto.hapticas)
    }

    @Test
    fun `new mode says what it won in the prize row`() {
        montar(ModoLogro.NUEVO, logro("first-week", true, Progreso(7, 7)))
        rule.onNodeWithText(cadena(R.string.store_reward_special, cadena(R.string.camino_especial_caminito))).assertExists()
        rule.onNodeWithText(cadena(R.string.store_reward_special_under)).assertExists()
    }

    @Test
    fun `consult mode shows the unlock date`() {
        montar(ModoLogro.CONSULTA, logro("streak-7", true, Progreso(7, 7)))
        rule.onNodeWithText(cadena(R.string.camino_lo_conseguiste, "July 8")).assertExists()
        rule.onNodeWithText(cadena(R.string.camino_planto, cadena(R.string.camino_especial_farolillo))).assertExists()
    }

    @Test
    fun `consult mode shows what is left for a pending one`() {
        montar(ModoLogro.CONSULTA, logro("streak-100", false, Progreso(13, 100)))
        rule.onNodeWithText(cadena(R.string.camino_falta_dias, 13, 100)).assertExists()
        rule.onNodeWithText(cadena(R.string.camino_plantara, cadena(R.string.camino_especial_cerezo_grande))).assertDoesNotExist()
    }

    @Test
    fun `a pending one that plants something says it will`() {
        montar(ModoLogro.CONSULTA, logro("perfect-days-100", false, Progreso(13, 100)))
        rule.onNodeWithText(cadena(R.string.camino_plantara, cadena(R.string.camino_especial_cerezo_grande))).assertExists()
    }

    @Test
    fun `this month can no longer be perfect and the next starts on the first`() {
        montar(ModoLogro.CONSULTA, logro("perfect-month", false), hoy = LocalDate.of(2026, 10, 13), mesImposible = true)
        rule.onNodeWithText(cadena(R.string.camino_otro_mes, "November 1")).assertExists()
    }

    @Test
    fun `a new sheet has only Seguir and no see it in the garden`() {
        montar(ModoLogro.NUEVO, logro("streak-7", true, Progreso(7, 7)))
        rule.onNodeWithText(cadena(R.string.camino_seguir)).assertExists()
        rule.onNodeWithText(cadena(R.string.camino_verlo)).assertDoesNotExist()
    }

    @Test
    fun `consult buttons follow D12 and see it in the garden calls up`() {
        var subido = 0
        montar(ModoLogro.CONSULTA, logro("streak-7", true, Progreso(7, 7)), alVerEnJardin = { subido++ })

        fun arriba(texto: String) = rule.onNodeWithText(texto).fetchSemanticsNode().boundsInRoot.top
        assertTrue(arriba(cadena(R.string.camino_verlo)) > arriba(cadena(R.string.sistema_cerrar)))
        rule.onNodeWithText(cadena(R.string.camino_verlo)).performClick()
        rule.mainClock.advanceTimeBy(600)
        assertEquals(1, subido)
    }
}
