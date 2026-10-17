package com.alvarotc.bito.ui.tienda.habi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.ui.habi.SondaSalto
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tienda.AroHornacina
import com.alvarotc.bito.ui.tienda.EscenaArmarioEstado
import com.alvarotc.bito.ui.tienda.EtiquetaHornacina
import com.alvarotc.bito.ui.tienda.HornacinaPieza
import com.alvarotc.bito.ui.tienda.PruebaTienda
import com.alvarotc.bito.ui.tienda.estadoTienda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val CINCO_TAPADAS = listOf("cabeza", "cara", "cuello", "pies", "colores")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DisfrazTapaTest {
    @get:Rule val rule = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private lateinit var vista: View

    private val conDisfraz =
        EquippedSet(
            head = "head-gorro-lana",
            face = "face-gafas-redondas",
            neck = "neck-bufanda",
            feet = "feet-botas-agua",
            costume = "costume-calabaza",
        )

    @Test
    fun `putting on a costume fades the five worn pieces to 50 percent in 200 ms`() {
        val tapada = mutableStateOf(false)
        var hornacina = Color.Unspecified
        rule.setContent {
            ConSistema(sonido, tacto) {
                vista = LocalView.current
                hornacina = BitoTema.colores.hornacina
                Box(Modifier.width(110.dp).height(130.dp)) {
                    HornacinaPieza(
                        imagen = { Box(Modifier.fillMaxSize().background(Color.Black).testTag("arte")) },
                        nombre = "Gorro de lana",
                        etiqueta = EtiquetaHornacina.PUESTO,
                        textoEtiqueta = null,
                        precio = null,
                        aro = AroHornacina.PUESTO,
                        bloqueada = false,
                        tapada = tapada.value,
                        alTocar = {},
                        modifier = Modifier.width(110.dp),
                    )
                }
            }
        }
        rule.mainClock.autoAdvance = false
        rule.waitForIdle()

        fun rojo(): Int {
            val foto = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
            vista.draw(Canvas(foto))
            val b = rule.onNodeWithTag("arte", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            return android.graphics.Color.red(foto.getPixel(b.center.x.toInt(), b.center.y.toInt()))
        }
        assertEquals("opaca antes", 0, rojo())
        rule.runOnIdle {
            tapada.value = true
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(80)
        val medio = rojo()
        assertTrue("a medias a los 100 ms ($medio)", medio in 5..(hornacina.toArgb().let { android.graphics.Color.red(it) } / 2 - 4))
        rule.mainClock.advanceTimeBy(140)
        val fin = rojo()
        val mitad = android.graphics.Color.red(hornacina.toArgb()) / 2
        assertEquals("al 50 % a los 220 ms", mitad.toFloat(), fin.toFloat(), 10f)
    }

    @Test
    fun `the worn pieces and the headers of the five slots read as covered`() {
        val estado = estadoTienda().let { it.copy(puestos = conDisfraz, look = conDisfraz, poseidos = conDisfraz.worn) }
        val tapadas = RanuraHabi.entries.filter { estado.puestos.tapada(it) }.map { it.id }
        assertEquals(CINCO_TAPADAS, tapadas)
        listOf("head-gorro-lana", "face-gafas-redondas", "neck-bufanda", "feet-botas-agua").forEach { id ->
            val d = datosDe(requireNotNull(HabiCatalog.byId(id)), estado)
            assertTrue("$id tapada", d.tapada)
            assertEquals(EtiquetaHornacina.TAPADO, d.etiqueta)
        }
        assertEquals(MetaRanura.Tapada, metaDe(RanuraHabi.CABEZA, estado.puestos))
    }

    @Test
    fun `the eyes stay`() {
        val estado = estadoTienda().let { it.copy(puestos = conDisfraz, look = conDisfraz, poseidos = conDisfraz.worn) }
        assertFalse(estado.puestos.tapada(RanuraHabi.OJOS))
        assertFalse(estado.puestos.tapada(RanuraHabi.DISFRACES))
        assertFalse(datosDe(requireNotNull(HabiCatalog.byId("eyes-avellana")), estado).tapada)
        assertTrue(metaDe(RanuraHabi.OJOS, estado.puestos) !is MetaRanura.Tapada)
    }

    @Test
    fun `all three costumes cover the same five slots`() {
        listOf("costume-calabaza", "costume-reno", "costume-dinosaurio").forEach { id ->
            val puestos = EquippedSet(costume = id)
            assertEquals(id, CINCO_TAPADAS, RanuraHabi.entries.filter { puestos.tapada(it) }.map { it.id })
        }
    }

    @Test
    fun `tapping a covered slot removes the costume and the piece falls`() {
        val calabaza = EquippedSet(costume = "costume-calabaza")
        val estado = mutableStateOf(estadoTienda().copy(puestos = calabaza, look = calabaza, poseidos = calabaza.worn))
        val sonda = SondaSalto()
        lateinit var anim: AnimacionPrueba
        lateinit var armario: EscenaArmarioEstado
        rule.setContent {
            ConSistema(sonido, tacto) {
                armario = remember { EscenaArmarioEstado() }.also { it.sonda = sonda }
                anim = remember { AnimacionPrueba() }
                val e = estado.value
                Column(Modifier.width(412.dp)) {
                    Box(Modifier.height(320.dp)) {
                        ArmarioHabi(e.look, e.prueba, armario, { 0f }, sonido, tacto, animacion = anim)
                    }
                    Box(Modifier.height(600.dp)) {
                        TiraHabi(
                            e,
                            // Como el estado real: el disfraz se quita y la pieza que no es tuya queda a prueba.
                            alTocar = { id ->
                                val sinDisfraz = e.puestos.copy(costume = null)
                                if (id == "head-gorro-lana") {
                                    val probando = sinDisfraz.copy(head = id)
                                    estado.value = e.copy(puestos = sinDisfraz, look = probando, prueba = PruebaTienda.Pieza(id))
                                }
                            },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("item-head-gorro-lana").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("item-head-gorro-lana").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertEquals("el disfraz que sale se ve fundirse", "costume-calabaza", anim.disfrazSaliente)
        assertEquals("y la pieza cae", GestoPrueba.CAE, anim.gesto)
        assertEquals("head-gorro-lana", anim.itemId)
        assertTrue(anim.caidaDp > 300f)
        assertTrue(anim.fundido in 0.2f..1f)
        rule.mainClock.advanceTimeBy(100)
        assertTrue("el disfraz se funde mientras cae", anim.fundido < 0.8f)
        rule.mainClock.advanceTimeBy(300)
        assertNull(anim.disfrazSaliente)
        rule.mainClock.advanceTimeBy(1500)
        assertNull(anim.gesto)
        assertEquals(Snd.POSAR, sonido.snds().first { it == Snd.POSAR })
        assertTrue(Haptica.LOW in tacto.hapticas)
        assertNotNull(anim)
    }
}
