package com.alvarotc.bito.ui.sesion

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Instante
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.PlanSesion
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.sesion.AvisoSesion
import com.alvarotc.bito.sesion.ResultadoAccion
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.premios.RegistroAnclas
import com.alvarotc.bito.ui.sistema.ConSistema
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

private const val MINUTO = 60_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class FocoContenidoTest {
    @get:Rule
    val compose = createComposeRule()

    private val motor = MotorSesionFalso()
    private val anclas = RegistroAnclas()
    private val retenido = BloqueRetenido()
    private val cara = CaraHabi()
    private val sonda = SondaFoco()
    private val volvio = mutableListOf<String>()
    private val avisos = mutableListOf<String>()
    private val saltos = mutableListOf<Float>()
    private var masOtro = 0
    private var instante by mutableStateOf<Instante?>(null)
    private val directo = planSesion(entrada = Entrada.DIRECTO)
    private val habi = Rect(150f, 100f, 250f, 300f)

    private val texto = ApplicationProvider.getApplicationContext<Context>().resources

    /** 18:42 de 25: el mock. */
    private fun instanteMock(plan: PlanSesion = directo) = motor.viva(plan, 25 * MINUTO - (18 * MINUTO + 42_000L))

    private fun montar(
        plan: PlanSesion = directo,
        instanteInicial: Instante = instanteMock(plan),
        datos: DatosFoco = DatosFoco("Informe trimestral", "Abrir el documento"),
        fondo: SessionBackground? = null,
        noMolestar: Boolean = false,
        reducido: Boolean = false,
        frases: FrasesFoco = FrasesFoco("Aquí estoy, Ana. Tú a lo tuyo.", "Hecha, Ana."),
        habiConocida: Boolean = true,
        avanceInicial: Long = 100,
    ) {
        instante = instanteInicial
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ConSistema(reducido = reducido) {
                CompositionLocalProvider(LocalAnclas provides anclas) {
                    Column(Modifier.width(380.dp)) {
                        FocoContenido(
                            instante = instante!!,
                            plan = plan,
                            datos = datos,
                            fondoSonando = fondo,
                            noMolestar = noMolestar,
                            entorno =
                                EntornoFoco(
                                    motor = motor,
                                    salida = SalidaSesion({ volvio += "hoy" }, { avisos += it }),
                                    retenido = retenido,
                                    cara = cara,
                                    saltarHabi = { saltos += it },
                                    alMasOtro = { masOtro++ },
                                    anclaHabi = { if (habiConocida) habi else null },
                                    anclaMonedas = (plan.objetivo as? Objetivo.Tarea)?.let { ClavesAncla.tarea(it.id) },
                                    textoAviso = { it.texto(texto) },
                                ),
                            frases = frases,
                            sonda = sonda,
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(avanceInicial)
    }

    private fun avanzar(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    /** Las escrituras de estado llegan a la composición dentro de una instantánea (como en `SesionContenidoTest`). */
    private fun cambiar(bloque: () -> Unit) = compose.runOnUiThread { Snapshot.withMutableSnapshot(bloque) }

    private fun existe(tag: String) = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the mock texts and tags are exact`() {
        montar()

        compose.onNodeWithTag("f-nom").assertTextEquals("Informe trimestral")
        compose.onNodeWithTag("f-paso").assertTextEquals("Primer paso: abrir el documento")
        compose.onNodeWithTag("f-t").assertTextEquals("18:42")
        compose.onNodeWithTag("f-de").assertTextEquals("de 25 min")
        compose.onNodeWithTag("f-blq").assertExists()
        compose.onNodeWithTag("f-et").assertExists()
        compose.onNodeWithTag("f-pausa").assertExists()
        compose.onNodeWithTag("f-mas-5").assertTextEquals("+5")
        compose.onNodeWithTag("f-mas-15").assertTextEquals("+15")
        compose.onNodeWithTag("f-mas-30").assertTextEquals("+30")
        compose.onNodeWithTag("f-otro").assertTextEquals("+ Otro")
        compose.onNodeWithTag("f-hecho").assertTextEquals("He terminado")
        compose.onNodeWithTag("f-dejar").assertTextEquals("Ha habido un inconveniente, lo dejo")
    }

    @Test
    fun `without a first step it says to start with what you have and free focus is just focus`() {
        montar(datos = DatosFoco("Informe trimestral", null))
        compose.onNodeWithTag("f-paso").assertTextEquals("Sin primer paso: empieza por lo que tengas a mano")
    }

    @Test
    fun `free focus says solo foco and has no subtitle`() {
        montar(plan = planSesion(entrada = Entrada.DIRECTO, objetivo = Objetivo.Libre), datos = DatosFoco(null, null))

        compose.onNodeWithTag("f-nom").assertTextEquals("Solo foco")
        assertFalse(existe("f-paso"))
    }

    @Test
    fun `the figure is read as minutes and seconds of total`() {
        montar()

        val lectura = compose.onNodeWithTag("f-t").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertEquals("18 minutos 42 segundos de 25", lectura)
    }

    @Test
    fun `the blocks read as block n of m and one block has no dots`() {
        montar()
        val d = compose.onNodeWithTag("f-blq").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertEquals("Bloque 1 de 4", d)
        assertEquals(1, compose.onNodeWithTag("f-blq").fetchSemanticsNode().config[PuntitosEncendidos])
    }

    @Test
    fun `one block has no dots`() {
        montar(plan = planSesion(bloques = 1, entrada = Entrada.DIRECTO))

        assertFalse(existe("f-blq"))
    }

    @Test
    fun `tags only show what is really on`() {
        montar(fondo = null, noMolestar = false)
        assertTrue("el contenedor existe con su alto mínimo", existe("f-et"))
        assertFalse(existe("f-et-fondo"))
        assertFalse(existe("f-et-dnd"))
    }

    @Test
    fun `the background and do not disturb tags say what is on`() {
        montar(fondo = SessionBackground.RAIN, noMolestar = true)

        compose.onNodeWithTag("f-et-fondo").assertTextEquals("Lluvia")
        compose.onNodeWithTag("f-et-dnd").assertTextEquals("No molestar")
    }

    @Test
    fun `sizes of the controls follow the mock`() {
        montar()

        compose.onNodeWithTag("f-pausa").assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        compose.onNodeWithTag("f-mas-5").assertHeightIsEqualTo(44.dp)
        compose.onNodeWithTag("f-hecho").assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun `pause dims to 0 point 45 and says en pausa`() {
        montar()
        assertEquals(1f, sonda.alfaCifra, 0f)

        cambiar { instante = instante!!.copy(pausado = true) }
        avanzar(300)

        assertEquals(0.45f, sonda.alfaCifra, 0.01f)
        assertEquals(0.45f, sonda.alfaDe, 0.01f)
        compose.onNodeWithTag("f-de").assertTextEquals("en pausa")
        assertEquals(
            "Seguir",
            compose.onNodeWithTag("f-pausa").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single(),
        )

        cambiar { instante = instante!!.copy(pausado = false) }
        avanzar(300)
        assertEquals("tras seguir: ${sonda.alfaCifra} ${sonda.alfaDe}", 1f, sonda.alfaCifra, 0.01f)
        compose.onNodeWithTag("f-de").assertTextEquals("de 25 min")
    }

    @Test
    fun `the pause button asks the motor`() {
        montar()
        compose.onNodeWithTag("f-pausa").performClick()
        avanzar(100)
        assertTrue("pausar" in motor.llamadas)

        cambiar { instante = instante!!.copy(pausado = true) }
        avanzar(100)
        compose.onNodeWithTag("f-pausa").performClick()
        avanzar(100)
        assertTrue("seguir" in motor.llamadas)
    }

    @Test
    fun `the clock ticking never rolls the figure`() {
        montar()

        cambiar { instante = instante!!.copy(restaMillis = instante!!.restaMillis - 1_000) }
        avanzar(16)

        compose.onNodeWithTag("f-t").assertTextEquals("18:41")
        assertEquals("sin rodillo cada segundo", 1f, sonda.rodilloCifra, 0f)
    }

    @Test
    fun `plus five rolls and adds`() {
        montar()
        compose.onNodeWithTag("f-mas-5").performClick()
        avanzar(100)
        assertTrue("sumar:5" in motor.llamadas)
        val tres = with(compose.density) { 3.dp.toPx() }
        assertEquals("el botón sube 3 dp", -tres, sonda.saltoMas.getValue(5), 0.6f)

        val actual = instante!!
        val mas = 5 * MINUTO
        cambiar { instante = actual.copy(duracionTramoMillis = actual.duracionTramoMillis + mas, restaMillis = actual.restaMillis + mas) }
        avanzar(16)
        assertEquals("la cifra arranca el rodillo", 0f, sonda.rodilloCifra, 0.1f)
        avanzar(120)
        val mitad = sonda.rodilloCifra
        assertTrue("a mitad de rodar: $mitad", mitad > 0.2f && mitad < 0.99f)
        avanzar(200)
        assertEquals(1f, sonda.rodilloCifra, 0f)
        compose.onNodeWithTag("f-de").assertTextEquals("de 30 min")
        compose.onNodeWithTag("f-t").assertTextEquals("23:42")
        avanzar(100)
        assertEquals("el botón vuelve", 0f, sonda.saltoMas.getValue(5), 0.01f)
    }

    @Test
    fun `reduced motion does not move the plus button`() {
        montar(reducido = true)
        compose.onNodeWithTag("f-mas-15").performClick()
        avanzar(100)

        assertTrue("sumar:15" in motor.llamadas)
        assertEquals(0f, sonda.saltoMas.getValue(15), 0f)
    }

    @Test
    fun `plus other fires its action and no sheet of its own`() {
        montar()

        compose.onNodeWithTag("f-otro").performClick()

        assertEquals(1, masOtro)
        assertFalse("sumar" in motor.llamadas.joinToString())
    }

    @Test
    fun `the first block shows the bubble at 620 ms for 2 point 6 s`() {
        montar(instanteInicial = motor.viva(directo, 0L))
        avanzar(450)
        assertFalse("a los 550 ms aún no", existe("globito"))

        avanzar(100)
        avanzar(100)
        assertTrue("a los 750 ms sí", existe("globito"))

        avanzar(2_000)
        assertTrue("a los 2,7 s sigue", existe("globito"))

        avanzar(1_100)
        assertFalse("a los 3,8 s se fue", existe("globito"))
    }

    @Test
    fun `reduced motion brings the bubble at 100 ms`() {
        montar(reducido = true, instanteInicial = motor.viva(directo, 0L), avanceInicial = 50)
        assertFalse(existe("globito"))

        avanzar(150)
        assertTrue(existe("globito"))
    }

    @Test
    fun `the second block has no bubble`() {
        montar(instanteInicial = motor.viva(directo, 31 * MINUTO))

        avanzar(1_000)

        assertFalse(existe("globito"))
    }

    @Test
    fun `habi hops 0 point 11 first at 22 s and not while paused`() {
        montar()
        avanzar(21_800)
        assertEquals(emptyList<Float>(), saltos)

        avanzar(400)
        assertEquals(listOf(0.11f), saltos)

        cambiar { instante = instante!!.copy(pausado = true) }
        avanzar(100)
        val antes = saltos.size
        avanzar(60_000)
        assertEquals("quieto en pausa", antes, saltos.size)
    }

    @Test
    fun `he terminado shows coins then returns at 2200 ms with its notice`() {
        motor.respuesta = ResultadoAccion(bitos = 3, celebraciones = false, aviso = AvisoSesion.Hecho("Informe trimestral"))
        montar()
        assertNotNull("las monedas salen del botón", anclas.posicionO(ClavesAncla.tarea("t1")))

        compose.onNodeWithTag("f-hecho").performClick()
        avanzar(100)

        assertTrue("heTerminado" in motor.llamadas)
        assertNull("el motor ya cerró como el real", motor.instante.value)
        assertEquals(BloqueSesion.FOCO, retenido.bloque)
        assertEquals("la cámara pasa a Terminada como el mock", Encuadre.TERMINADA, retenido.encuadre)
        assertTrue("Habi radiante como en Terminada", cara.radiante)
        compose.onNodeWithTag("t-preg").assertTextEquals("Hecha, Ana.")
        compose.onNodeWithTag("t-min").assertTextEquals("6 min")
        assertFalse("sin botones", existe("f-hecho"))
        assertFalse(existe("f-dejar"))
        assertNotNull("el ancla se recuerda para las monedas", anclas.posicionO(ClavesAncla.tarea("t1")))

        avanzar(2_000)
        assertEquals("aún no a los 2,1 s", emptyList<String>(), volvio)

        avanzar(200)
        assertEquals(listOf("hoy"), volvio)
        assertEquals(listOf("Informe trimestral, hecho."), avisos)
        assertNull(retenido.bloque)
        assertNull("suelta la cámara", retenido.encuadre)
    }

    @Test
    fun `lo dejo returns with the minutes notice`() {
        motor.respuesta = ResultadoAccion(bitos = 0, celebraciones = false, aviso = AvisoSesion.LoDejamos(6))
        montar()

        compose.onNodeWithTag("f-dejar").performClick()
        avanzar(100)

        assertTrue("loDejo" in motor.llamadas)
        assertEquals(listOf("hoy"), volvio)
        assertEquals(listOf("Lo dejamos aquí. Tus 6 min cuentan."), avisos)
    }

    @Test
    fun `lo dejo with no minutes says otra vez sera`() {
        motor.respuesta = ResultadoAccion(bitos = 0, celebraciones = false, aviso = AvisoSesion.LoDejamos(0))
        montar()

        compose.onNodeWithTag("f-dejar").performClick()
        avanzar(100)

        assertEquals(listOf("Lo dejamos aquí. Otra vez será."), avisos)
    }
}
