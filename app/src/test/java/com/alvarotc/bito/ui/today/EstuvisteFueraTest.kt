package com.alvarotc.bito.ui.today

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.domain.RealHabits
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h640dp")
class EstuvisteFueraTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var banco: BancoHoy

    @Before
    fun setUp() {
        banco = BancoHoy(rule, tmp)
        banco.preparar()
        banco.sembrar(RealHabits.noSmoking, creadosEn = banco.hoy - 3)
    }

    @After
    fun tearDown() = banco.cerrar()

    private fun diasSinSellar() = runBlocking { banco.db.daySealDao().all().size }

    @Test
    fun `away days open the sheet and sealing seals them all`() {
        banco.montar()
        rule.onNodeWithText("Sellar 3 días").assertIsDisplayed().performClick()
        banco.esperar(1500)
        assertEquals(3, diasSinSellar())
        rule.onNodeWithText("3 días sellados.").assertIsDisplayed()
    }

    @Test
    fun `closing without sealing is remembered for the session`() {
        banco.montar()
        rule.onNodeWithText("Ahora no").performClick()
        banco.esperar()
        rule.onAllNodesWithText("Sellar 3 días").assertCountEquals(0)
        assertEquals(0, diasSinSellar())
    }
}
