package com.alvarotc.bito.ui.tienda

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.AppStartup
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.sesion.SonidoGrabado
import com.alvarotc.bito.sesion.TactoGrabado
import com.alvarotc.bito.ui.AppVisibility
import com.alvarotc.bito.ui.BitoNavHost
import com.alvarotc.bito.ui.NavRequests
import com.alvarotc.bito.ui.garden.LocalSondaJardin
import com.alvarotc.bito.ui.garden.SondaJardin
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

private const val ESPERA_MAXIMA = 1_500
private const val ZONA = "Europe/Madrid"
private const val MARGEN_LOGROS = 1_000L
private const val ASENTAR_MS = 1_500L

/**
 * El recorrido de la tienda de punta a punta (plan de la pieza 6, Tarea 18): `BitoNavHost` con el `AppContainer` real,
 * Room y DataStore reales de la app de prueba, `RelojTienda` fijado a mano y los grabadores de sonido y tacto. Lo esperado
 * sale de la spec (§3, §7, §9, D2, D7, J13, J35) y de los mocks, no de lo que hace hoy el código.
 *
 * Cómo se monta, y lo que esto no es:
 * - El reloj de pared de Robolectric no se puede mover: la tienda lee `RelojTienda`, que aquí es la fecha fijada por el test
 *   más lo que han avanzado los fotogramas (así el rebote de dedo del congelador y la ventana de venta ven pasar el tiempo).
 * - Reloj manual de Compose (`autoAdvance = false`): las escenas animadas no dejan nunca la prueba quieta. Se espera con
 *   `hasta`, que avanza fotogramas mientras Room y DataStore responden por sus hilos.
 * - Girar la actividad conserva los `ViewModel`; para imitar que Android mata el proceso se vacía su almacén antes de recrear.
 * - Lo que solo se ve pintado (opacidad del botón, temblor, negrita de la línea) no lo lee el árbol semántico: de eso se
 *   comprueba lo que sí se observa (texto, descripción, sonido, tacto, escrituras).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class TiendaE2ETest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<BitoApp>()
    private lateinit var container: AppContainer
    private val sonda = SondaJardin()
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()

    /** El instante fijado a mano (16 de octubre de 2026 a mediodía) y lo que han avanzado los fotogramas desde entonces. */
    private var ultimaNota = ""

    @Volatile private var fijo = instante(2026, 10, 16)
    private val transcurrido = AtomicLong(0)

    @Before
    fun setUp() {
        AppStartup.quiesceForTests()
        AppVisibility.visible = true
        container = app.container
        container.relojTienda = RelojTienda(ahora = { fijo + transcurrido.get() }, zona = { ZoneId.of(ZONA) })
        NotificationChannels.ensure(app)
        runBlocking { container.settings.update { it.copy(onboardingDone = true, personality = Personality.NEUTRA) } }
    }

    @After
    fun tearDown() {
        container.cerrarSesion()
        AppVisibility.visible = false
        NavRequests.consume()
        AppStartup.quiesceForTests()
    }

    // ───────────────────────── 1 ─────────────────────────

    @Test
    fun `from Today the balance opens the sheet and Go to the store opens the store on Habi`() {
        sembrar(140)
        montar()
        hasta { hay("cabecera-saldo") }
        pulsar("cabecera-saldo")
        hasta { hay("saldo-tienda") }
        assertTrue("la hoja se llama Tus bitos; en pantalla: ${etiquetas()}", hayTexto("Tus bitos"))
        assertTrue("la hoja lleva Ir a la tienda", hayTexto("Ir a la tienda"))
        pulsar("saldo-tienda")
        esperarTienda()

        assertTrue("la pestaña Habi está elegida", seleccionada("ttab-habi"))
        assertFalse(seleccionada("ttab-jardin"))
        assertFalse(seleccionada("ttab-congeladores"))
        assertEquals("a mediodía la luz de ahora es el día", "Luz: Día", luzActual())
        assertFalse("sin prueba no hay barra", hay("barra"))
        assertEquals(140, saldoDe())
    }

    // ───────────────────────── 2 ─────────────────────────

    @Test
    fun `try a hat, buy it, take it off and put it back`() {
        sembrar(140)
        abrirTienda()

        verItem("head-gorro-lana")
        pulsar("item-head-gorro-lana")
        hasta { hay("barra") }
        assertEquals("60 bitos. Te quedarían 80.", texto("barra-nota"))
        assertTrue("Comprar por 60", texto("barra-comprar").contains("Comprar por 60"))
        assertTrue("la hornacina dice Probando", desc("item-head-gorro-lana").contains("Probando"))
        assertEquals("probar no cuesta", 140, saldoDe())

        val marca = marca()
        pulsar("barra-comprar")
        hasta { saldoDe() == 80 }
        hasta { Snd.HABI in sonidosDesde(marca) }
        val sonaron = sonidosDesde(marca)
        assertTrue("suena GASTAR y luego HABI: $sonaron", sonaron.indexOf(Snd.GASTAR) in 0 until sonaron.indexOf(Snd.HABI))
        assertTrue("el tacto confirma: ${hapticasDesde(marca)}", Haptica.CONFIRM in hapticasDesde(marca))
        hasta { desc("item-head-gorro-lana").contains("Puesto") }
        hasta { !hay("barra") }

        // Tocarla la quita: pasa a Tuyo. Tocarla otra vez la pone, sin barra.
        pulsar("item-head-gorro-lana")
        hasta { desc("item-head-gorro-lana").contains("Tuyo") }
        assertFalse("lo tuyo no abre barra", hay("barra"))
        pulsar("item-head-gorro-lana")
        hasta { desc("item-head-gorro-lana").contains("Puesto") }
        assertFalse(hay("barra"))
        assertEquals("quitar y poner no cuesta", 80, saldoDe())
    }

    // ───────────────────────── 3 ─────────────────────────

    @Test
    fun `no balance says how many are missing`() {
        sembrar(50)
        abrirTienda()

        verItem("head-copa")
        pulsar("item-head-copa")
        hasta { hay("barra") }
        assertEquals(
            "Cuesta 120 bitos y tienes 50: te faltan 70. Cada hábito hecho da 1 y un día completo, 3 más.",
            texto("barra-nota"),
        )
        assertFalse("sin saldo no hay botón de comprar", hay("barra-comprar"))
        assertTrue("solo Quitárselo", texto("barra-quitar").contains("Quitárselo"))
        assertFalse("tampoco Ahora no", hay("barra-ahora-no"))
    }

    // ───────────────────────── 4 ─────────────────────────

    @Test
    fun `a costume is bought in season and shown out of season`() {
        sembrar(210)
        abrirTienda()

        verItem("costume-calabaza")
        pulsar("item-costume-calabaza")
        hasta { hay("barra") }
        assertEquals(
            "150 bitos. Te quedarían 60. A la venta hasta el 2 de noviembre; luego es tuyo todo el año.",
            texto("barra-nota"),
        )
        pulsar("barra-comprar")
        hasta { saldoDe() == 60 }
        hasta { desc("item-costume-calabaza").contains("Puesto") }
        hasta { !hay("barra") }

        // Se quita y, ya fuera de temporada, se pone sin barra: es tuyo todo el año.
        pulsar("item-costume-calabaza")
        hasta { desc("item-costume-calabaza").contains("Tuyo") }
        fijo = instante(2026, 11, 3)
        pulsar("item-costume-calabaza")
        hasta { desc("item-costume-calabaza").contains("Puesto") }
        assertFalse("ponerlo fuera de temporada no abre barra", hay("barra"))
        assertEquals(60, saldoDe())

        // Lo que no es tuyo y no está en temporada: solo se prueba.
        verItem("costume-reno")
        pulsar("item-costume-reno")
        hasta { hay("barra") }
        val nota = texto("barra-nota")
        assertTrue("dice cuándo se vende: $nota", nota.startsWith("A la venta del 1 de diciembre al 6 de enero"))
        assertFalse("fuera de temporada no se compra", hay("barra-comprar"))
        assertTrue(texto("barra-quitar").contains("Quitárselo"))
    }

    // ───────────────────────── 5 ─────────────────────────

    @Test
    fun `a costume covers five slots and tapping a covered slot swaps`() {
        sembrar(140)
        runBlocking {
            container.rewards.acquire(CustomizationItemEntity("costume-calabaza", CustomizationCategory.COSTUME, fijo, equipped = true))
        }
        abrirTienda()

        // La tira solo compone las cabeceras a la vista: se baja a cada ranura con la raíz.
        listOf("cabeza", "cara", "cuello", "pies", "colores").forEach { ranura ->
            hasta { hay("raiz-$ranura") }
            if (!hay("meta-$ranura")) pulsar("raiz-$ranura")
            hasta { hay("meta-$ranura") }
            assertEquals("$ranura va tapada por el disfraz", "Tapado por el disfraz", texto("meta-$ranura"))
        }
        pulsar("raiz-cabeza")
        hasta { hay("meta-cabeza") }

        verItem("head-flor")
        pulsar("item-head-flor")
        hasta { propios().firstOrNull { it.itemId == "head-flor" }?.equipped == true }
        assertFalse("tocar una pieza tapada quita el disfraz", propios().first { it.itemId == "costume-calabaza" }.equipped)
        assertFalse("sin barra: la flor ya era tuya", hay("barra"))
        hasta { texto("meta-cabeza") != "Tapado por el disfraz" }
    }

    // ───────────────────────── 6 ─────────────────────────

    @Test
    fun `garden - buy the bench and the watering can and both stay in Today`() {
        sembrar(140)
        abrirTienda()

        pulsar("ttab-jardin")
        hasta { hay("item-garden-banco") }
        pulsar("item-garden-banco")
        hasta { hay("barra") }
        assertEquals("80 bitos. Te quedarían 60. Se queda donde lo ves.", texto("barra-nota"))
        pulsar("barra-comprar")
        hasta { saldoDe() == 60 }
        hasta { desc("item-garden-banco").contains("En tu jardín") }

        pulsar("item-garden-regadera")
        hasta { hay("barra") && texto("barra-nota").startsWith("25 bitos") }
        assertEquals("25 bitos. Te quedarían 35. Se queda donde lo ves.", texto("barra-nota"))
        pulsar("barra-comprar")
        hasta { saldoDe() == 35 }
        hasta { desc("item-garden-regadera").contains("En tu jardín") }
        assertTrue(desc("item-garden-banco").contains("En tu jardín"))

        // Atrás a Hoy: los dos objetos siguen en el jardín, y tocar el banco abre su globito.
        pulsar("tienda-atras")
        hasta { hay("garden-scene") && !hay("tienda") }
        hasta { sonda.arteListo() }
        val escena = compose.onNodeWithTag("garden-scene", useUnmergedTree = true)
        escena.performTouchInput { click(Offset(width * BANCO_X, height * BANCO_Y)) }
        fotogramas()
        hasta { hay("garden-bubble-banco") }
        assertTrue("el globito dice cómo llegó", hayTextoQueContiene("Banco"))
        assertTrue("el globito dice cómo llegó", hayTextoQueContiene("Lo compraste con tus bitos."))

        escena.performTouchInput { click(Offset(width * REGADERA_X, height * REGADERA_Y)) }
        fotogramas()
        hasta { hay("garden-bubble-regadera") }
        assertTrue("la regadera se ve en el jardín de Hoy", hayTextoQueContiene("Regadera"))
    }

    // ───────────────────────── 7 ─────────────────────────

    @Test
    fun `garden - without balance the bench cannot be bought but the watering can can`() {
        sembrar(52)
        abrirTienda()

        pulsar("ttab-jardin")
        hasta { hay("item-garden-banco") }
        pulsar("item-garden-banco")
        hasta { hay("barra") }
        assertEquals(
            "Cuesta 80 bitos y tienes 52: te faltan 28. Cada hábito hecho da 1 y un día completo, 3 más.",
            texto("barra-nota"),
        )
        assertFalse(hay("barra-comprar"))
        assertTrue("un objeto no se lleva puesto: Quitarlo", texto("barra-quitar").contains("Quitarlo"))
        assertFalse("no es Quitárselo", texto("barra-quitar").contains("Quitárselo"))

        pulsar("item-garden-regadera")
        hasta { hay("barra-comprar") }
        assertTrue("la regadera sí: ${texto("barra-comprar")}", texto("barra-comprar").contains("Comprar por 25"))
    }

    // ───────────────────────── 8 ─────────────────────────

    @Test
    fun `a badge special is tried and cannot be bought`() {
        sembrar(140)
        abrirTienda()

        pulsar("ttab-jardin")
        hasta { hay("item-setas") }
        pulsar("item-setas")
        hasta { hay("barra") }
        assertEquals(
            "Llega con el logro Resurrección: volver a cumplir tras romper una racha de 30 o más. No se compra.",
            texto("barra-nota"),
        )
        assertFalse(hay("barra-comprar"))
        assertTrue(texto("barra-quitar").contains("Quitarlo"))
        assertEquals("probar un especial no cuesta", 140, saldoDe())
    }

    // ───────────────────────── 9 ─────────────────────────

    @Test
    fun `freezer - buy, undo, and let it become final`() {
        sembrar(140)
        abrirTienda()

        pulsar("ttab-congeladores")
        hasta { hay("tc-buy") }
        assertTrue("Tienes 2", hayDescripcion("Tienes 2"))
        assertEquals("100 bitos. Te quedarían 40.", texto("tc-linea"))

        var marca = marca()
        pulsar("tc-buy")
        hasta { saldoDe() == 40 }
        hasta { hayDescripcion("Tienes 3") }
        hasta { hayTexto("Congelador comprado") }
        assertTrue("el aviso ofrece Deshacer", hayTexto("Deshacer"))
        hasta { Snd.CAMPANILLA in sonidosDesde(marca) }
        val sonaron = sonidosDesde(marca)
        assertTrue("suena GASTAR y luego CAMPANILLA: $sonaron", sonaron.indexOf(Snd.GASTAR) in 0 until sonaron.indexOf(Snd.CAMPANILLA))
        assertTrue("el tacto confirma: ${hapticasDesde(marca)}", Haptica.CONFIRM in hapticasDesde(marca))
        assertFalse("Habi no salta con un congelador: $sonaron", Snd.HABI in sonaron)

        // Deshacer devuelve los bitos y el contador.
        pulsarTexto("Deshacer")
        hasta { saldoDe() == 140 }
        hasta { hayDescripcion("Tienes 2") }
        assertEquals("el asiento se borró del libro", 2, asientos(PointsReason.BUY_FREEZER))

        // Comprar otra vez y dejar pasar los 4 s del aviso: la compra queda firme.
        fotogramas(1_000)
        marca = marca()
        pulsar("tc-buy")
        hasta { saldoDe() == 40 }
        hasta { hayTexto("Congelador comprado") }
        hasta(maximo = 2_000) { !hayTexto("Congelador comprado") }
        assertEquals("la compra queda firme", 40, saldoDe())
        assertTrue(hayDescripcion("Tienes 3"))
        assertEquals(3, asientos(PointsReason.BUY_FREEZER))
    }

    // ───────────────────────── 10 ─────────────────────────

    @Test
    fun `freezer - without balance the button shakes and nothing is written`() {
        sembrar(40)
        abrirTienda()

        pulsar("ttab-congeladores")
        hasta { hay("tc-buy") }
        val faltan = "Te faltan 60 bitos. Cada hábito hecho da 1 y un día completo, 3 más."
        assertEquals(faltan, texto("tc-linea"))
        assertTrue("el botón lo dice también por accesibilidad: ${desc("tc-buy")}", desc("tc-buy").contains(faltan))

        val libro = asientos()
        val marca = marca()
        pulsar("tc-buy")
        hasta { Haptica.REJECT in hapticasDesde(marca) }
        fotogramas(600)
        assertFalse("sin saldo no suena la compra: ${sonidosDesde(marca)}", Snd.GASTAR in sonidosDesde(marca))
        assertEquals("ningún asiento nuevo", libro, asientos())
        assertEquals(40, saldoDe())
        assertFalse("sin aviso de compra", hayTexto("Congelador comprado"))

        // Con 100 exactos compra y deja 0.
        runBlocking { container.rewards.spend(60, PointsReason.TASK_DONE, "task:extra", diaFijo(), fijo) }
        hasta { saldoDe() == 100 }
        hasta { texto("tc-linea") == "100 bitos. Te quedarían 0." }
        pulsar("tc-buy")
        hasta { saldoDe() == 0 }
        hasta { hayDescripcion("Tienes 3") }
    }

    // ───────────────────────── 11 ─────────────────────────

    @Test
    fun `back removes the trial first and then leaves`() {
        sembrar(140)
        abrirTienda()

        verItem("head-gorro-lana")
        pulsar("item-head-gorro-lana")
        hasta { hay("barra") }

        atrasDelSistema()
        hasta { !hay("barra") }
        assertTrue("el primer atrás solo quita la prueba y la tienda sigue", hay("tienda"))
        assertTrue(hay("item-head-gorro-lana"))

        atrasDelSistema()
        hasta { hay("cabecera-saldo") && !hay("tienda") }
        assertFalse("el segundo atrás sale", hay("tienda"))
    }

    @Test
    fun `changing tab drops the trial`() {
        sembrar(140)
        abrirTienda()

        verItem("head-gorro-lana")
        pulsar("item-head-gorro-lana")
        hasta { hay("barra") }

        pulsar("ttab-jardin")
        hasta { seleccionada("ttab-jardin") && !hay("barra") }
        pulsar("ttab-habi")
        hasta { seleccionada("ttab-habi") && hay("tira-habi") }
        assertFalse("la prueba no vuelve al regresar", hay("barra"))
        verItem("head-gorro-lana")
        assertFalse(desc("item-head-gorro-lana").contains("Probando"))
        assertEquals(140, saldoDe())
    }

    // ───────────────────────── 12 ─────────────────────────

    @Test
    fun `after recreating the activity the tab and the trial come back but not the light`() {
        sembrar(140)
        // Lo mismo que `StateRestorationTester`, pero con el reloj a mano: guardar, apagar el contenido, encenderlo con lo guardado.
        var registro by mutableStateOf(SaveableStateRegistry(null) { true })
        var encendido by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registro) { if (encendido) contenido() }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(100)
        entrarEnLaTienda()

        pulsar("ttab-jardin")
        hasta { hay("item-garden-banco") }
        pulsar("item-garden-banco")
        hasta { hay("barra") }
        pulsarTexto("Noche")
        hasta { luzActual() == "Luz: Noche" }

        // Android mata el proceso: se vacía el almacén de ViewModel y solo queda el estado guardado de la pantalla.
        val guardado = registro.performSave()
        encendido = false
        Snapshot.sendApplyNotifications()
        fotogramas(300)
        assertFalse("el contenido se apagó", hay("tienda"))
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        registro = SaveableStateRegistry(guardado) { true }
        encendido = true
        Snapshot.sendApplyNotifications()
        fotogramas(100)

        esperarTienda()
        hasta { seleccionada("ttab-jardin") }
        hasta { hay("barra") }
        assertEquals(
            "la prueba vuelve",
            "80 bitos. Te quedarían 60. Se queda donde lo ves.",
            texto("barra-nota"),
        )
        assertEquals("la luz no vuelve: se recalcula (mediodía, día abierto)", "Luz: Día", luzActual())
    }

    // ───────────────────────── 13 ─────────────────────────

    @Test
    fun `a streak reward earned inside the store turns Racha de 100 into Tuyo without a sheet`() {
        sembrar(140)
        abrirTienda()

        verItem("head-corona")
        hasta { desc("item-head-corona").contains("Racha de 100") }

        // Lo que escribe el cierre cuando la racha llega a 100: la pieza y el logro.
        val ahora = System.currentTimeMillis() + 10 * MARGEN_LOGROS
        runBlocking {
            container.rewards.grantItems(setOf("head-corona"), ahora)
            container.rewards.unlockBadges(setOf("streak-100"), ahora)
        }
        hasta { desc("item-head-corona").contains("Tuyo") }
        fotogramas(1_000)
        assertFalse("dentro de la tienda no sale la hoja de logro: ${etiquetas()}", hayTexto("Logro desbloqueado"))
        assertFalse(hayTexto("Tienes la corona."))
        assertFalse("sin barra", hay("barra"))

        // Fuera de la tienda, la hoja de logro lleva la línea de premio.
        pulsar("tienda-atras")
        hasta(maximo = 2_000) { hayTexto("Tienes la corona.") }
        assertTrue("La encuentras en la tienda, en Cabeza.", hayTexto("La encuentras en la tienda, en Cabeza."))
    }

    // ───────────────────────── 14 ─────────────────────────

    @Test
    @Config(sdk = [35], qualifiers = "en-w411dp-h891dp")
    fun `English - bar titles fit on one line, the description on two and the buttons do not split`() {
        sembrar(50)
        abrirTienda()

        // Sin saldo (la nota más larga), una racha, una temporada y un especial: la barra sin botón principal.
        verItem("head-copa")
        pulsar("item-head-copa")
        medirBarra("copa sin saldo")
        verItem("head-corona")
        pulsar("item-head-corona")
        medirBarra("corona por racha")
        verItem("costume-reno")
        pulsar("item-costume-reno")
        medirBarra("reno fuera de temporada")
        pulsar("ttab-jardin")
        hasta { hay("item-garden-banco") }
        pulsar("item-garden-banco")
        medirBarra("banco sin saldo")
        pulsar("item-setas")
        medirBarra("setas por logro")

        // Con saldo: la barra con dos botones y la nota de temporada.
        runBlocking { container.rewards.spend(400, PointsReason.TASK_DONE, "task:extra", diaFijo(), fijo) }
        hasta { saldoDe() == 450 }
        pulsar("item-garden-banco")
        medirBarra("banco con saldo")
        pulsar("ttab-habi")
        hasta { hay("tira-habi") }
        verItem("costume-calabaza")
        pulsar("item-costume-calabaza")
        medirBarra("calabaza en temporada")
        verItem("head-gorro-lana")
        pulsar("item-head-gorro-lana")
        medirBarra("gorro")
    }

    // ───────────────────────── ayudantes ─────────────────────────

    private fun instante(
        anio: Int,
        mes: Int,
        dia: Int,
    ): Long = ZonedDateTime.of(anio, mes, dia, 12, 0, 0, 0, ZoneId.of(ZONA)).toInstant().toEpochMilli()

    private fun diaFijo(): Int = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(fijo), ZoneId.of(ZONA)).toEpochDay().toInt()

    /**
     * Saldo [saldo] con dos congeladores comprados, un hábito con 31 días seguidos (mejor racha de 31), y la flor y el
     * pañuelo brasa ya ganados. El libro lleva un único asiento positivo para que el saldo sea exacto.
     */
    private fun sembrar(saldo: Int) {
        runBlocking {
            val dia = diaFijo()
            container.habits.create(
                habitEntity(id = "h1", name = "Estirar", metric = Metric.CHECK, target = 1, unit = null, createdOnDay = dia - 40),
            )
            for (i in 0..30) {
                container.database.entryDao().insert(
                    entryEntity(id = "e$i", habitId = "h1", logicalDay = dia - i, value = 1, createdAtMillis = fijo),
                )
            }
            // Hoy abre el Inicio y reconcilia con el reloj de verdad: se hace aquí antes, para que luego no llegue nada nuevo
            // (puntos, piezas de racha ni logros por celebrar) y el saldo sea el que se pide.
            val ahora = System.currentTimeMillis()
            container.settings.update { it.copy(badgesSeenUntilMillis = ahora + MARGEN_LOGROS) }
            container.reconciler.reconcile(LogicalDays.logicalDayOf(ahora, 0, ZoneId.systemDefault()), ahora)
            container.rewards.grantItems(setOf("head-flor", "neck-panuelo-brasa"), fijo)
            val actual = container.database.pointsLedgerDao().all().sumOf { it.delta }
            container.rewards.spend(saldo + 200 - actual, PointsReason.TASK_DONE, "task:ajuste", dia, fijo)
            container.rewards.spend(-100, PointsReason.BUY_FREEZER, "f1", dia, fijo)
            container.rewards.spend(-100, PointsReason.BUY_FREEZER, "f2", dia, fijo)
        }
    }

    @androidx.compose.runtime.Composable
    private fun contenido() {
        BitoTheme(sonido = sonido, tacto = tacto) {
            CompositionLocalProvider(LocalSondaJardin provides sonda) { BitoNavHost(container) }
        }
    }

    private fun montar() {
        compose.setContent { contenido() }
        // Las escenas piden un fotograma tras otro: con el reloj solo, la prueba no llegaría nunca a estar quieta.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(100)
    }

    private fun abrirTienda() {
        montar()
        entrarEnLaTienda()
    }

    private fun entrarEnLaTienda() {
        hasta { hay("cabecera-saldo") }
        pulsar("cabecera-saldo")
        hasta { hay("saldo-tienda") }
        pulsar("saldo-tienda")
        esperarTienda()
        // La tienda brota con un muelle: hasta que asienta no es la pantalla de arriba y atrás no la cierra.
        fotogramas(ASENTAR_MS)
    }

    private fun esperarTienda() {
        hasta { hay("tienda") && !hay("tienda-cargando") && hay("tluz") }
    }

    private fun fotogramas(ms: Long = 50) {
        compose.mainClock.advanceTimeBy(ms)
        transcurrido.addAndGet(ms)
    }

    /** Espera a que se cumpla [cond] dejando correr fotogramas: Room y DataStore van por hilos reales. */
    private fun hasta(
        maximo: Int = ESPERA_MAXIMA,
        cond: () -> Boolean,
    ) {
        repeat(maximo) {
            // Lo que lanzan los ViewModel en el principal solo corre cuando el bucle se vacía; las condiciones que no miran el árbol no lo hacen.
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            fotogramas(16)
            Thread.sleep(1)
        }
        assertTrue("no llegó a tiempo; en pantalla: ${etiquetas()}", cond())
    }

    private fun etiquetas(): List<String> =
        compose.onAllNodes(SemanticsMatcher("con etiqueta") { SemanticsProperties.TestTag in it.config }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }

    private fun nodos(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun hay(tag: String) = nodos(tag).isNotEmpty()

    private fun nodo(tag: String): SemanticsNode = nodos(tag).firstOrNull() ?: error("no hay «$tag»; en pantalla: ${etiquetas()}")

    private fun hayTexto(texto: String) = compose.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hayTextoQueContiene(trozo: String) =
        compose.onAllNodes(
            SemanticsMatcher(
                "texto con «$trozo»",
            ) { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(trozo) } == true },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    private fun hayDescripcion(texto: String) =
        compose.onAllNodes(
            SemanticsMatcher(
                "descripción «$texto»",
            ) { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == texto } == true },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    /** Todo el texto de un nodo y de sus descendientes. */
    private fun textos(n: SemanticsNode): String =
        buildString {
            n.config.getOrNull(SemanticsProperties.Text)?.let { lista -> append(lista.joinToString("") { it.text }) }
            n.children.forEach { append(textos(it)) }
        }

    private fun texto(tag: String): String = textos(nodo(tag))

    private fun desc(tag: String): String =
        nodos(tag).firstOrNull()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ").orEmpty()

    private fun seleccionada(tag: String): Boolean = nodos(tag).firstOrNull()?.config?.getOrNull(SemanticsProperties.Selected) == true

    private fun luzActual(): String? =
        compose.onAllNodes(
            SemanticsMatcher("astro de la luz") { n ->
                n.config.getOrNull(SemanticsProperties.ContentDescription)?.any {
                    it.startsWith("Luz: ")
                } == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().firstOrNull()?.config?.get(SemanticsProperties.ContentDescription)?.first()

    private fun saldoDe(): Int =
        nodos("saldo").mapNotNull { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
            .firstNotNullOfOrNull { Regex("""(\d+) bito""").find(it)?.groupValues?.get(1)?.toInt() }
            ?: -1

    /** Un clic de verdad: sube por el árbol hasta el primer nodo con acción de clic. */
    private fun clic(inicio: SemanticsNode) {
        var n: SemanticsNode? = inicio
        while (n != null) {
            val accion = n.config.getOrNull(SemanticsActions.OnClick)?.action
            if (accion != null) {
                accion()
                fotogramas()
                return
            }
            n = n.parent
        }
        error("nada pulsable en ${textos(inicio)}")
    }

    private fun pulsar(tag: String) = clic(nodo(tag))

    private fun pulsarTexto(texto: String) =
        clic(
            compose.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
                ?: error("no hay el texto «$texto»; en pantalla: ${etiquetas()}"),
        )

    /** Trae la pieza a la tira de Habi: salta a su ranura con la raíz y espera a que esté compuesta. */
    private fun verItem(id: String) {
        val ranura =
            when (id.substringBefore('-')) {
                "head" -> "cabeza"
                "face" -> "cara"
                "neck" -> "cuello"
                "feet" -> "pies"
                "body" -> "colores"
                "eyes" -> "ojos"
                "costume" -> "disfraces"
                else -> error("ranura desconocida para $id")
            }
        hasta { hay("raiz-$ranura") || hay("item-$id") }
        if (!hay("item-$id")) pulsar("raiz-$ranura")
        hasta { hay("item-$id") }
    }

    private fun atrasDelSistema() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        fotogramas()
    }

    private fun marca() = sonido.sonidos.size to tacto.hapticas.size

    private fun sonidosDesde(m: Pair<Int, Int>): List<Snd> = sonido.sonidos.toList().drop(m.first)

    private fun hapticasDesde(m: Pair<Int, Int>): List<Haptica> = tacto.hapticas.toList().drop(m.second)

    private fun propios() = runBlocking { container.database.customizationItemDao().all() }

    private fun asientos(motivo: PointsReason? = null): Int =
        runBlocking { container.database.pointsLedgerDao().all() }.count { motivo == null || it.reason == motivo }

    /** La barra de prueba a la vista: título en una línea, descripción en dos como mucho y botones sin partir. */
    private fun medirBarra(cual: String) {
        // La barra se rehace en cuanto Room contesta: se espera a que traiga una nota distinta de la anterior.
        hasta { hay("barra") && hay("barra-nota") && texto("barra-nota") != ultimaNota }
        ultimaNota = texto("barra-nota")
        fotogramas(600)
        val barra = nodo("barra")
        val conTexto = ArrayList<SemanticsNode>()

        fun recoger(n: SemanticsNode) {
            if (n.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null) conTexto += n
            n.children.forEach(::recoger)
        }
        recoger(barra)
        assertTrue("$cual: la barra lleva título, nota y botón", conTexto.size >= 3)
        conTexto.forEachIndexed { i, n ->
            val capas = mutableListOf<TextLayoutResult>()
            n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(capas)
            val capa = capas.first()
            val ultima = capa.lineCount - 1
            val limite =
                when (i) {
                    0 -> 1
                    1 -> 2
                    else -> 1
                }
            val rol =
                if (i == 0) {
                    "el título"
                } else if (i == 1) {
                    "la descripción"
                } else {
                    "un botón"
                }
            assertTrue(
                "$cual: $rol («${capa.layoutInput.text.text}») cabe en $limite línea(s), usa ${capa.lineCount}",
                capa.lineCount <= limite,
            )
            assertFalse(
                "$cual: $rol («${capa.layoutInput.text.text}») no queda cortado con puntos suspensivos",
                capa.isLineEllipsized(ultima),
            )
        }
    }

    private companion object {
        // Pie de cada objeto en el jardín (spec §3.3.3): banco (69, 72,5) y regadera (44, 99), un poco por encima del pie.
        const val BANCO_X = 0.69f
        const val BANCO_Y = 0.69f
        const val REGADERA_X = 0.44f
        const val REGADERA_Y = 0.96f
    }
}
