package com.alvarotc.bito.ui.tienda.habi

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.tienda.AroHornacina
import com.alvarotc.bito.ui.tienda.EtiquetaHornacina
import com.alvarotc.bito.ui.tienda.PruebaTienda
import com.alvarotc.bito.ui.tienda.TiendaUiState
import com.alvarotc.bito.ui.tienda.estadoTienda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Locale

private fun item(id: String) = requireNotNull(HabiCatalog.byId(id))

private fun TiendaUiState.con(
    puestos: EquippedSet = this.puestos,
    poseidos: Set<String> = this.poseidos,
    saldo: Int = this.saldo,
    hoy: LocalDate = this.hoy,
    prueba: PruebaTienda? = this.prueba,
) = copy(puestos = puestos, look = puestos, poseidos = poseidos, saldo = saldo, hoy = hoy, prueba = prueba)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EstadosListaTest {
    @get:Rule val rule = createComposeRule()

    private fun tira(
        estado: TiendaUiState,
        vaciada: RanuraHabi? = null,
    ) = rule.setContent {
        ConSistema { Box(Modifier.fillMaxSize()) { TiraHabi(estado, alTocar = {}, ranuraVaciada = vaciada) } }
    }

    @Test
    fun `first time wears nothing and shows the welcome`() {
        val estado = estadoTienda(saldo = 0)
        assertTrue(esPrimeraVez(estado))
        tira(estado)
        rule.onNodeWithText("Aquí gastas los bitos que ganas. Probarte algo no cuesta nada.").assertIsDisplayed()
        assertFalse(esPrimeraVez(estado.con(saldo = 5)))
        assertFalse(esPrimeraVez(estado.con(puestos = EquippedSet(neck = "neck-bufanda"))))
        assertFalse(esPrimeraVez(estado.con(poseidos = setOf("neck-bufanda"))))
    }

    @Test
    fun `no welcome once there is something to spend`() {
        tira(estadoTienda(saldo = 140))
        rule.onNodeWithTag("nota-intro").assertDoesNotExist()
    }

    @Test
    fun `empty slot shows the help line only in that slot`() {
        val estado = estadoTienda(saldo = 140).con(puestos = EquippedSet(neck = "neck-bufanda"))
        val sin = construirTira(false, null, false, estado.puestos)
        assertEquals(0, sin.count { it is EntradaTira.Vacia })
        val pies = construirTira(false, RanuraHabi.PIES, false, estado.puestos)
        assertEquals(listOf(EntradaTira.Vacia(RanuraHabi.PIES)), pies.filterIsInstance<EntradaTira.Vacia>())
        // un hueco que ya no está vacío no la lleva; ni con la bienvenida
        assertEquals(0, construirTira(false, RanuraHabi.CUELLO, false, estado.puestos).count { it is EntradaTira.Vacia })
        assertEquals(0, construirTira(true, RanuraHabi.PIES, false, estado.puestos).count { it is EntradaTira.Vacia })
        // y va justo bajo su cabecera
        val i = pies.indexOf(EntradaTira.Vacia(RanuraHabi.PIES))
        assertEquals(EntradaTira.Cabecera(RanuraHabi.PIES), pies[i - 1])
    }

    @Test
    fun `the help line is drawn under its header`() {
        tira(estadoTienda(saldo = 140), vaciada = RanuraHabi.CABEZA)
        rule.onNodeWithText("Sin nada puesto. Toca una pieza para probártela.").assertIsDisplayed()
    }

    @Test
    fun `costume on covers five slots and not the eyes`() {
        val p = EquippedSet(head = "head-boina", costume = "costume-calabaza")
        val tapadas = RanuraHabi.entries.filter { p.tapada(it) }
        assertEquals(
            listOf(RanuraHabi.CABEZA, RanuraHabi.CARA, RanuraHabi.CUELLO, RanuraHabi.PIES, RanuraHabi.COLORES),
            tapadas,
        )
        tapadas.forEach { assertEquals(MetaRanura.Tapada, metaDe(it, p)) }
        assertEquals(MetaRanura.Puesta("eyes-tinta"), metaDe(RanuraHabi.OJOS, p))
        assertEquals(MetaRanura.Puesta("costume-calabaza"), metaDe(RanuraHabi.DISFRACES, p))
    }

    @Test
    fun `a covered worn piece shows Tapado at 50 percent with its ring`() {
        val estado =
            estadoTienda().con(
                puestos = EquippedSet(head = "head-boina", costume = "costume-calabaza"),
                poseidos = setOf("head-boina", "costume-calabaza"),
            )
        val boina = datosDe(item("head-boina"), estado)
        assertEquals(EtiquetaHornacina.TAPADO, boina.etiqueta)
        assertEquals(AroHornacina.PUESTO, boina.aro)
        assertTrue(boina.tapada)
        // el disfraz mismo no está tapado
        val disfraz = datosDe(item("costume-calabaza"), estado)
        assertEquals(EtiquetaHornacina.PUESTO, disfraz.etiqueta)
        assertFalse(disfraz.tapada)
        // los ojos tampoco
        assertFalse(datosDe(item("eyes-tinta"), estado).tapada)
    }

    @Test
    fun `just after buying the tile reads Worn and Owned after taking it off`() {
        val gorro = item("head-gorro-lana")
        val puesto = estadoTienda().con(puestos = EquippedSet(head = gorro.id), poseidos = setOf(gorro.id))
        assertEquals(EtiquetaHornacina.PUESTO, datosDe(gorro, puesto).etiqueta)
        assertEquals(AroHornacina.PUESTO, datosDe(gorro, puesto).aro)
        val quitado = puesto.con(puestos = EquippedSet())
        assertEquals(EtiquetaHornacina.TUYO, datosDe(gorro, quitado).etiqueta)
        assertEquals(AroHornacina.NINGUNO, datosDe(gorro, quitado).aro)
    }

    @Test
    fun `trying wins over everything and a price shows for what is for sale`() {
        val estado = estadoTienda(saldo = 140).con(prueba = PruebaTienda.Pieza("head-copa"))
        assertEquals(EtiquetaHornacina.PROBANDO, datosDe(item("head-copa"), estado).etiqueta)
        assertEquals(AroHornacina.PROBANDO, datosDe(item("head-copa"), estado).aro)
        val boina = datosDe(item("head-boina"), estado)
        assertEquals(EtiquetaHornacina.PRECIO, boina.etiqueta)
        assertEquals(70, boina.precio)
    }

    @Test
    fun `out of season shows the month`() {
        val marzo = estadoTienda().con(hoy = LocalDate.of(2026, 3, 20))
        assertEquals(R.string.store_label_season_october, datosDe(item("costume-calabaza"), marzo).temporada)
        assertEquals(R.string.store_label_season_december, datosDe(item("costume-reno"), marzo).temporada)
        assertTrue(datosDe(item("costume-reno"), marzo).bloqueada)
        val octubre = estadoTienda().con(hoy = LocalDate.of(2026, 10, 16))
        assertEquals(EtiquetaHornacina.PRECIO, datosDe(item("costume-calabaza"), octubre).etiqueta)
        // comprado fuera de temporada: Tuyo
        val tuyo = marzo.con(poseidos = setOf("costume-reno"))
        assertEquals(EtiquetaHornacina.TUYO, datosDe(item("costume-reno"), tuyo).etiqueta)
    }

    @Test
    fun `out of season month is drawn in the tile`() {
        tira(estadoTienda().con(hoy = LocalDate.of(2026, 3, 20)))
        rule.onNodeWithTag("tira-habi").performScrollToIndex(construirTira(false, null, false, EquippedSet()).size - 1)
        rule.onNodeWithText("En octubre", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("En carnaval", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a locked exclusive shows its streak`() {
        val corona = datosDe(item("head-corona"), estadoTienda().con(saldo = 500))
        assertEquals(EtiquetaHornacina.RACHA, corona.etiqueta)
        assertEquals(100, corona.racha)
        assertTrue(corona.bloqueada)
        tira(estadoTienda())
        rule.onNodeWithText("Racha de 100", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `all bought ends with its line and exclusives do not count`() {
        val comprables = HabiCatalog.all.filter { (it.price ?: 0) > 0 }.map { it.id }.toSet()
        val estado = estadoTienda().con(poseidos = comprables)
        assertTrue(todoComprado(estado))
        assertFalse(todoComprado(estado.con(poseidos = comprables - "head-copa")))
        val entradas = construirTira(false, null, true, estado.puestos)
        assertEquals(EntradaTira.Todo, entradas[entradas.size - 2])
        assertEquals(EntradaTira.Fin, entradas.last())
        tira(estado)
        rule.onNodeWithTag("tira-habi").performScrollToIndex(entradas.size - 1)
        rule.onNodeWithText("Ya tienes todo lo que se vende. Lo demás llega con las rachas.").assertIsDisplayed()
    }

    @Test
    fun `the strip holds the 34 pieces in seven slots in order`() {
        val filas = construirTira(false, null, false, EquippedSet()).filterIsInstance<EntradaTira.Fila>()
        assertEquals(HabiCatalog.all.size, filas.sumOf { it.piezas.size })
        assertEquals(RanuraHabi.entries.map { it.id }, filas.map { it.ranura.id }.distinct())
        assertEquals(listOf("cabeza", "cara", "cuello", "pies", "colores", "ojos", "disfraces"), RanuraHabi.entries.map { it.id })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EstadosListaEnglishTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `English uses Wearing and fits in one line`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val ctx = base.createConfigurationContext(cfg)
        rule.setContent {
            CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides cfg) {
                ConSistema { CabeceraRanura(RanuraHabi.CUELLO, MetaRanura.Puesta("neck-collar-flores"), Modifier) }
            }
        }
        rule.onNodeWithText("Wearing: flower garland").assertIsDisplayed()
        val resultados = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("meta-cuello").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val r = resultados.single()
        assertEquals(1, r.lineCount)
        assertFalse(r.hasVisualOverflow)
        assertNotNull(r)
    }
}
