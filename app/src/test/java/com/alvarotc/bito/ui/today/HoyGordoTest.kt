package com.alvarotc.bito.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.DueKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h640dp")
class HoyGordoTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val tmp = TemporaryFolder()

    private var banco: BancoHoy? = null

    @After
    fun tearDown() {
        banco?.cerrar()
    }

    private fun preparar(abrirGordo: Boolean): FuenteFalsa {
        val fuente = FuenteFalsa()
        val b = BancoHoy(rule, tmp).also { banco = it }
        b.preparar(fuente)
        runBlocking {
            val tareas = TasksRepository(b.db)
            tareas.create(
                taskEntity(id = "informe", title = "Informe trimestral", dueKind = DueKind.DATE, dueDay = b.hoy, createdOnDay = b.hoy - 3),
            )
            tareas.create(
                taskEntity(
                    id = "dentista",
                    title = "Llamar al dentista",
                    dueKind = DueKind.DATE,
                    dueDay = b.hoy - 2,
                    createdOnDay = b.hoy - 6,
                ),
            )
        }
        b.montar(abrirGordo = abrirGordo)
        return fuente
    }

    private fun esperarHoja() {
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("gordo-informe")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the morning notice opens the sheet with the real candidates and the suggestion marked`() {
        preparar(abrirGordo = true)
        esperarHoja()
        rule.onNodeWithTag("hoja-gordo").assertIsDisplayed()
        rule.onNodeWithTag("gordo-informe").assertIsOff()
        rule.onNodeWithTag("gordo-dentista").assertIsOn()
    }

    @Test
    fun `saving hands the refs to the source and shows the notice`() {
        val fuente = preparar(abrirGordo = true)
        esperarHoja()
        rule.onNodeWithTag("gordo-informe").performClick()
        rule.onNodeWithTag("hoja-gordo-guardar").performClick()
        rule.waitUntil(5_000) { fuente.gordoGuardado.isNotEmpty() }
        assertEquals(
            listOf(listOf(RefGordo(TipoRefGordo.TAREA, "dentista"), RefGordo(TipoRefGordo.TAREA, "informe"))),
            fuente.gordoGuardado,
        )
        rule.waitUntil(5_000) {
            rule.onAllNodes(
                androidx.compose.ui.test.hasText("Lo gordo de hoy: Llamar al dentista, Informe trimestral."),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Lo gordo de hoy: Llamar al dentista, Informe trimestral.").assertIsDisplayed()
    }

    @Test
    fun `reopening with a done big thing and saving keeps it`() {
        val fuente = FuenteFalsa()
        val b = BancoHoy(rule, tmp).also { banco = it }
        b.preparar(fuente)
        runBlocking {
            val tareas = TasksRepository(b.db)
            tareas.create(
                taskEntity(id = "informe", title = "Informe trimestral", dueKind = DueKind.DATE, dueDay = b.hoy, createdOnDay = b.hoy - 3),
            )
            tareas.markDone("informe", b.hoy, b.fixedNow)
            com.alvarotc.bito.data.habla.LoGordoRepository(b.db, now = { b.fixedNow })
                .guardar(b.hoy, listOf(RefGordo(TipoRefGordo.TAREA, "informe")))
        }
        b.montar(abrirGordo = true)
        esperarHoja()
        rule.onNodeWithTag("gordo-informe").assertIsOn()
        rule.onNodeWithTag("hoja-gordo-guardar").performClick()
        rule.waitUntil(5_000) { fuente.gordoGuardado.isNotEmpty() }
        assertEquals(listOf(listOf(RefGordo(TipoRefGordo.TAREA, "informe"))), fuente.gordoGuardado)
    }
}
