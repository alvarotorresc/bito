package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class HojasAjustesTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `adding a reminder goes to the clock step and back`() {
        var anadida: Int? = null
        rule.setContent { ConSistema { HojaRecordatorios(true, listOf(12 * 60), { anadida = it }, { _, _ -> }, {}, {}) } }
        rule.onNodeWithText("Añadir una hora").performClick()
        rule.onNodeWithText("Guardar").performClick()
        rule.waitForIdle()
        assertNotNull(anadida)
        rule.onNodeWithText("Añadir una hora").assertIsDisplayed()
    }

    @Test
    fun `editing a reminder reports the old and the new time`() {
        var par: Pair<Int, Int>? = null
        rule.setContent { ConSistema { HojaRecordatorios(true, listOf(12 * 60), {}, { a, d -> par = a to d }, {}, {}) } }
        rule.onNodeWithText("Editar").performClick()
        rule.onNodeWithText("Guardar").performClick()
        rule.waitForIdle()
        assertEquals(12 * 60, par?.first)
    }

    @Test
    fun `removing a reminder folds the row and reports it`() {
        var quitada: Int? = null
        val sonido = SonidoGrabado()
        rule.setContent { ConSistema(sonido) { HojaRecordatorios(true, listOf(12 * 60), {}, { _, _ -> }, { quitada = it }, {}) } }
        rule.onNodeWithText("Quitar").performClick()
        rule.waitForIdle()
        assertEquals(12 * 60, quitada)
        assertTrue(Snd.HUNDIR in sonido.snds())
    }

    @Test
    fun `an empty list shows its text`() {
        rule.setContent { ConSistema { HojaRecordatorios(true, emptyList(), {}, { _, _ -> }, {}, {}) } }
        rule.onNodeWithText("Sin recordatorios. Habi no te escribirá a ninguna hora fija.").assertIsDisplayed()
    }

    @Test
    fun `Habi nudges show morning and night`() {
        rule.setContent { ConSistema { HojaAvisosHabi(true, 8 * 60, 21 * 60 + 30, {}, {}, {}) } }
        rule.onNodeWithText("Mañana").assertIsDisplayed()
        rule.onNodeWithText("Noche").assertIsDisplayed()
        rule.onNodeWithText("Sustituye al repaso del día", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the night clock is bounded to 19,00 to 23,30`() {
        var guardada: Int? = null
        rule.setContent { ConSistema { HojaAvisosHabi(true, 8 * 60, 21 * 60 + 30, {}, { guardada = it }, {}) } }
        rule.onNodeWithText("Noche").performClick()
        rule.onNodeWithContentDescription("Hora del aviso de la noche")
            .assertRangeInfoEquals(ProgressBarRangeInfo(1290f, 1140f..1410f))
        rule.onNodeWithText("Guardar").performClick()
        rule.waitForIdle()
        assertEquals(1290, guardada)
    }

    @Test
    fun `the morning clock is bounded to 6,00 to 11,00`() {
        rule.setContent { ConSistema { HojaAvisosHabi(true, 8 * 60, 21 * 60 + 30, {}, {}, {}) } }
        rule.onNodeWithText("Mañana").performClick()
        rule.onNodeWithContentDescription("Hora del aviso de la mañana")
            .assertRangeInfoEquals(ProgressBarRangeInfo(480f, 360f..660f))
    }

    @Test
    fun `the day end saves and closes`() {
        var guardada: Int? = null
        var cierres = 0
        rule.setContent { ConSistema { HojaDiaTermina(true, 120, { guardada = it }, { cierres++ }) } }
        rule.onNodeWithText("Aplica desde ahora; los días sellados no se tocan.").assertIsDisplayed()
        rule.onNodeWithText("Guardar").performClick()
        rule.waitForIdle()
        assertEquals(120, guardada)
        assertEquals(1, cierres)
    }

    @Test
    fun `do not disturb hours are locked while it is off`() {
        rule.setContent { ConSistema { HojaNoMolestar(true, false, 23 * 60, 7 * 60 + 30, false, {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Desde").assertIsNotEnabled()
    }

    @Test
    fun `a denied permission shows the box and asks for it`() {
        var pedido = false
        rule.setContent { ConSistema { HojaNoMolestar(true, true, 23 * 60, 7 * 60 + 30, true, {}, {}, {}, { pedido = true }, {}) } }
        rule.onNodeWithText("Permitir").performClick()
        assertTrue(pedido)
    }

    @Test
    fun `pomodoro steppers keep their ranges`() {
        var p by mutableStateOf(Pomodoro(90, 5, 15, 4, true))
        rule.setContent { ConSistema { HojaPomodoro(true, p, { p = it }, {}) } }
        rule.onNodeWithContentDescription("Foco").onChildren().filterToOne(hasContentDescription("Más")).performClick()
        assertEquals(90, p.foco)
        rule.onNodeWithContentDescription("Descanso corto").onChildren().filterToOne(hasContentDescription("Más")).performClick()
        assertEquals(6, p.corto)
    }

    @Test
    fun `choosing a background previews it briefly and stops`() {
        val sonido = SonidoGrabado()
        var elegido by mutableStateOf(SessionBackground.RAIN)
        rule.mainClock.autoAdvance = false
        rule.setContent { ConSistema(sonido) { HojaFondo(true, elegido, { elegido = it }, {}) } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Cafetería").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(SessionBackground.CAFE, elegido)
        assertTrue(sonido.bucles.getValue(Snd.FONDO_CAFE).sonando)
        rule.mainClock.advanceTimeBy(2500)
        assertFalse(sonido.bucles.getValue(Snd.FONDO_CAFE).sonando)
    }

    @Test
    fun `choosing another background stops the previous preview`() {
        val sonido = SonidoGrabado()
        var elegido by mutableStateOf(SessionBackground.NONE)
        rule.mainClock.autoAdvance = false
        rule.setContent { ConSistema(sonido) { HojaFondo(true, elegido, { elegido = it }, {}) } }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Lluvia").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Cafetería").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertFalse(sonido.bucles.getValue(Snd.FONDO_LLUVIA).sonando)
        assertTrue(sonido.bucles.getValue(Snd.FONDO_CAFE).sonando)
    }
}
