package com.alvarotc.bito.ui.cierre

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.alvarotc.bito.R
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.ui.inicio.BancoInicio
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class CierreContenidoTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoInicio
    private val cerrados = mutableIntStateOf(0)
    private val noches = mutableIntStateOf(0)

    @Before
    fun setUp() {
        banco = BancoInicio(tmp)
    }

    @After
    fun tearDown() = banco.cerrar()

    private fun hay(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(tag: String) = rule.waitUntil(5_000) { hay(tag) }

    @Composable
    private fun Cierre(estado: EstadoCierre) {
        CierreContenido(estado, banco.cierre, alCerrar = { cerrados.intValue++ }, alBuenasNoches = { noches.intValue++ })
    }

    private fun montarAbierto(reducido: Boolean = false) {
        rule.setContent {
            ConSistema(banco.sonido, banco.tacto, reducido) { Cierre(remember { EstadoCierre(Animatable(1f), Animatable(0f)) }) }
        }
    }

    @Test
    fun `opening from the card, from the button and with the route shows the close`() {
        // Desde la tarjeta: la cámara sube con muelle y el cierre llega con su sonido.
        banco.preparar(habitos = 1)
        rule.setContent {
            ConSistema(banco.sonido, banco.tacto) {
                val estado = remember { EstadoCierre(Animatable(0f), Animatable(0f)) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) { scope.launch { estado.c.animateTo(1f) } }
                Cierre(estado)
            }
        }
        esperar("cierre")
        rule.waitUntil(5_000) { Snd.DESCENSO in banco.sonido.snds() }
        assertTrue(Haptica.TICK in banco.tacto.hapticas)
    }

    @Test
    fun `opening with the button of Today or with the route mounts the same close`() {
        banco.preparar(habitos = 1)
        rule.setContent { ConSistema(banco.sonido, banco.tacto) { banco.Inicio() } }
        rule.onNodeWithTag("cerrar-dia").performClick()
        esperar("cierre-buenas-noches")
        esperar("cierre-fila-h0")
    }

    @Test
    fun `the coins and veins of the rewards are drawn over the close`() {
        // Lo que se compone después en el Inicio se pinta encima: los premios van tras el cierre.
        banco.preparar(habitos = 1)
        rule.setContent { ConSistema(banco.sonido, banco.tacto) { banco.Inicio() } }
        rule.onNodeWithTag("cerrar-dia").performClick()
        esperar("cierre")
        val orden = mutableListOf<String>()

        fun recorrer(n: androidx.compose.ui.semantics.SemanticsNode) {
            n.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.let { orden += it }
            n.children.forEach(::recorrer)
        }
        recorrer(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(orden.toString(), orden.indexOf("premios") > orden.indexOf("cierre"))
        // Y los premios no se quedan los toques del cierre.
        rule.onNodeWithTag("cierre-cerrar").performClick()
        rule.waitUntil(5_000) { !hay("cierre") }
    }

    @Test
    fun `until tomorrow squashes Habi before sleeping`() {
        banco.preparar(habitos = 1)
        rule.setContent { ConSistema(banco.sonido, banco.tacto) { banco.Inicio() } }
        rule.onNodeWithTag("cerrar-dia").performClick()
        esperar("cierre-buenas-noches")
        rule.onNodeWithTag("cierre-buenas-noches").performClick()
        rule.mainClock.advanceTimeBy(2_600)
        rule.waitUntil(10_000) { hay("cierre-hasta-manana") }
        assertEquals(0, banco.sonda.aplastadosHabi())
        rule.onNodeWithTag("cierre-hasta-manana").performClick()
        rule.waitUntil(5_000) { banco.sonda.aplastadosHabi() == 1 }
    }

    @Test
    fun `opening with the route does not play the descent twice`() {
        banco.preparar(habitos = 1)
        montarAbierto()
        esperar("cierre-buenas-noches")
        rule.waitForIdle()
        assertEquals(emptyList<Snd>(), banco.sonido.snds().filter { it == Snd.DESCENSO })
    }

    @Test
    fun `deciding without doing dims and can be undone`() {
        banco.preparar(habitos = 1)
        montarAbierto()
        esperar("cierre-no-h0")
        rule.onNodeWithTag("cierre-no-h0").performClick()
        esperar("cierre-atenuada-h0")
        assertTrue(Snd.HUNDIR in banco.sonido.snds())
        rule.onNodeWithTag("cierre-deshacer-h0").performClick()
        rule.waitUntil(5_000) { !hay("cierre-atenuada-h0") }
        assertTrue(Snd.BROTE in banco.sonido.snds())
        assertEquals(emptySet<String>(), banco.cierre.uiState.value.decididas)
    }

    @Test
    fun `the tasks line lists two names and the rest`() {
        banco.preparar(habitos = 1)
        runBlocking {
            val tareas = TasksRepository(banco.db)
            listOf("Informe", "Dentista", "Impuestos").forEachIndexed { i, titulo ->
                tareas.create(
                    taskEntity(
                        id = "t$i",
                        title = titulo,
                        dueKind = DueKind.DATE,
                        dueDay = banco.hoyLogico,
                        createdOnDay = banco.hoyLogico,
                    ),
                )
            }
        }
        montarAbierto()
        esperar("cierre-tareas")
        rule.waitUntil(5_000) { hay("cierre-tareas") }
        val texto =
            rule.onNodeWithTag("cierre-tareas").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        assertTrue(texto, "Informe" in texto && "Dentista" in texto && "1" in texto && "Impuestos" !in texto)
    }

    @Test
    fun `without tasks there is no tasks line`() {
        banco.preparar(habitos = 1)
        montarAbierto()
        esperar("cierre-buenas-noches")
        rule.waitForIdle()
        assertTrue(!hay("cierre-tareas"))
    }

    @Test
    fun `mood pills are a radio group and answer`() {
        banco.preparar(habitos = 1)
        montarAbierto()
        esperar("cierre-animo-1")
        (0..2).forEach {
            val config = rule.onNodeWithTag("cierre-animo-$it").fetchSemanticsNode().config
            assertEquals(Role.RadioButton, config[SemanticsProperties.Role])
        }
        rule.onNodeWithTag("cierre-animo-1").performClick()
        rule.onNodeWithTag("cierre-animo-1").assertIsSelected()
        esperar("cierre-animo-respuesta")
        assertTrue(Snd.TIC in banco.sonido.snds())
        assertTrue(Haptica.SEG in banco.tacto.hapticas)
        rule.waitUntil(5_000) { banco.cierre.uiState.value.animo == 1 }
        // Se puede cambiar de idea hasta las buenas noches.
        rule.onNodeWithTag("cierre-animo-2").performClick()
        rule.waitUntil(5_000) { banco.cierre.uiState.value.animo == 2 }
    }

    @Test
    fun `good night is always enabled and seals the day`() {
        banco.preparar(habitos = 1)
        montarAbierto()
        esperar("cierre-buenas-noches")
        rule.onNodeWithTag("cierre-buenas-noches").assertIsEnabled()
        rule.onNodeWithTag("cierre-buenas-noches").performClick()
        rule.waitUntil(5_000) { runBlocking { banco.cierre.uiState.first { !it.loading }.sellado } }
        // Sellar no vuelve a Hoy: brota el sello y, al acabar de anochecer, «Hasta mañana».
        assertEquals(0, noches.intValue)
        esperar("cierre-hasta-manana")
        rule.onNodeWithTag("cierre-hasta-manana").performClick()
        rule.waitUntil(5_000) { noches.intValue == 1 }
    }

    @Test
    fun `close and back return to Today`() {
        banco.preparar(habitos = 1)
        montarAbierto()
        esperar("cierre-cerrar")
        // El ✕ dice adónde lleva (decisión de Álvaro).
        rule.onNodeWithContentDescription("Back to Today").assertExists()
        rule.onNodeWithTag("cierre-cerrar").performClick()
        assertEquals(1, cerrados.intValue)
        assertTrue(Snd.ASCENSO in banco.sonido.snds())
        assertTrue(Haptica.LOW in banco.tacto.hapticas)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { cerrados.intValue == 2 }
    }

    @Test
    fun `with reduced motion everything still works`() {
        banco.preparar(habitos = 1)
        montarAbierto(reducido = true)
        esperar("cierre-hecho-h0")
        rule.onNodeWithTag("cierre-animo-0").performClick()
        rule.onNodeWithTag("cierre-hecho-h0").performClick()
        rule.waitUntil(5_000) { !hay("cierre-hecho-h0") }
        rule.onNodeWithTag("cierre-buenas-noches").performClick()
        esperar("cierre-hasta-manana")
        rule.onNodeWithTag("cierre-hasta-manana").performClick()
        rule.waitUntil(5_000) { noches.intValue == 1 }
        assertTrue(Snd.TIC in banco.sonido.snds())
    }

    @Test
    fun `every tappable is at least 44dp`() {
        banco.preparar(habitos = 3)
        montarAbierto()
        esperar("cierre-buenas-noches")
        esperar("cierre-animo-2")
        rule.waitForIdle()
        val tocables = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(tocables.size >= 6)
        rule.onAllNodes(hasClickAction()).let { todos ->
            tocables.indices.forEach { i ->
                val b = todos[i].getUnclippedBoundsInRoot()
                assertTrue(
                    "tocable $i mide ${b.width} x ${b.height}",
                    b.width.value >= 43.9f && b.height.value >= 43.9f,
                )
            }
        }
    }

    @Test
    fun `an empty close says there is nothing pending but can still be sealed`() {
        banco.preparar(habitos = 0)
        montarAbierto()
        esperar("cierre-buenas-noches")
        rule.onNodeWithTag("cierre-buenas-noches").assertIsEnabled()
        rule.onNodeWithTag("cierre-vacio").assertExists()
    }

    @Test
    fun `the header counts what is done today`() {
        banco.preparar(habitos = 2)
        montarAbierto()
        val texto = rule.activity.getString(R.string.cierre_hoy_n_de_m, 0, 2)
        rule.waitUntil(5_000) { rule.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(texto).assertExists()
    }
}
