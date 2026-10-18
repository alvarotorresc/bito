package com.alvarotc.bito.ui.tienda

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.AppStartup
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
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
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

/**
 * Capturas de la tienda real (`BitoNavHost`, Room y DataStore de la app de prueba, reloj de la tienda a mano) en los estados
 * de los mocks de `14-app`, para ponerlas al lado de sus capturas. Escribe PNG en `build/capturas/<es|en>/`; no comprueba nada
 * del dibujo. Datos del mock: 140 bitos, mejor racha 31, 16 de octubre de 2026 a mediodía, voz Neutra, 2 congeladores.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class CapturasTiendaBase(private val idioma: String) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<BitoApp>()
    private lateinit var container: AppContainer
    private val sonda = SondaJardin()
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()

    @Volatile private var fijo = instante(2026, 10, 16, 12)
    private val transcurrido = AtomicLong(0)

    @Before
    fun setUp() {
        AppStartup.quiesceForTests()
        AppVisibility.visible = true
        container = app.container
        container.relojTienda = RelojTienda(ahora = { fijo + transcurrido.get() }, zona = { ZoneId.of(ZONA) })
        NotificationChannels.ensure(app)
    }

    @After
    fun tearDown() {
        container.cerrarSesion()
        AppVisibility.visible = false
        NavRequests.consume()
        AppStartup.quiesceForTests()
    }

    /** Siembra, abre la tienda desde Hoy, ejecuta [pasos] y guarda la captura como [nombre].png. */
    protected fun estado(
        nombre: String,
        saldo: Int = 140,
        congeladores: Int = 2,
        racha: Boolean = true,
        hora: Int = 12,
        mes: Int = 10,
        dia: Int = 16,
        voz: Personality = Personality.NEUTRA,
        poseidos: List<String> = emptyList(),
        puesto: List<String> = listOf("neck-bufanda"),
        pestana: String? = null,
        pasos: () -> Unit = {},
    ) {
        fijo = instante(2026, mes, dia, hora)
        runBlocking { container.settings.update { it.copy(onboardingDone = true, personality = voz) } }
        sembrar(saldo, congeladores, racha, poseidos, puesto)
        compose.setContent {
            BitoTheme(sonido = sonido, tacto = tacto) {
                CompositionLocalProvider(LocalSondaJardin provides sonda) { BitoNavHost(container) }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(100)
        hasta { hay("cabecera-saldo") }
        pulsar("cabecera-saldo")
        hasta { hay("saldo-tienda") }
        pulsar("saldo-tienda")
        hasta { hay("tienda") && !hay("tienda-cargando") && hay("tluz") }
        fotogramas(1_500)
        if (pestana != null) {
            pulsar(pestana)
            fotogramas(1_500)
        }
        pasos()
        fotogramas(1_500)
        guardar(nombre)
    }

    protected fun verRanura(id: String) {
        hasta { hay("raiz-$id") }
        pulsar("raiz-$id")
        fotogramas(1_500)
    }

    protected fun tocar(
        itemId: String,
        conBarra: Boolean = true,
    ) {
        hasta { hay("item-$itemId") }
        pulsar("item-$itemId")
        // La barra y la prueba llegan cuando Room contesta por su hilo: se deja correr el bucle hasta que aparece.
        if (conBarra) {
            hasta { hay("barra") && hay("barra-nota") }
        } else {
            // Poner o quitar escribe en Room por otro hilo: se deja correr el bucle para que el estado llegue.
            repeat(100) {
                shadowOf(Looper.getMainLooper()).idle()
                fotogramas(16)
                Thread.sleep(5)
            }
        }
        fotogramas(1_500)
    }

    protected fun tocarTexto(texto: String) {
        clic(compose.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().first())
        fotogramas(1_500)
    }

    protected fun tocarTag(tag: String) {
        pulsar(tag)
        // La compra escribe en Room por otro hilo: se deja correr el bucle un buen rato.
        repeat(400) {
            shadowOf(Looper.getMainLooper()).idle()
            fotogramas(16)
            Thread.sleep(5)
        }
    }

    private fun guardar(nombre: String) {
        val vista = compose.activity.window.decorView
        val lamina = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(lamina))
        val carpeta = File("build/capturas/$idioma").apply { mkdirs() }
        File(carpeta, "$nombre.png").outputStream().use { lamina.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sembrar(
        saldo: Int,
        congeladores: Int,
        racha: Boolean,
        poseidos: List<String>,
        puesto: List<String>,
    ) {
        runBlocking {
            val diaLogico = diaFijo()
            if (racha) {
                container.habits.create(
                    habitEntity(id = "h1", name = "Estirar", metric = Metric.CHECK, target = 1, unit = null, createdOnDay = diaLogico - 40),
                )
                for (i in 0..30) {
                    container.database.entryDao().insert(
                        entryEntity(id = "e$i", habitId = "h1", logicalDay = diaLogico - i, value = 1, createdAtMillis = fijo),
                    )
                }
            }
            val ahora = System.currentTimeMillis()
            container.settings.update { it.copy(badgesSeenUntilMillis = ahora + 1_000L) }
            container.reconciler.reconcile(
                com.alvarotc.bito.domain.LogicalDays.logicalDayOf(ahora, 0, ZoneId.systemDefault()),
                ahora,
            )
            val propios = poseidos.toMutableSet()
            if (racha) propios += setOf("head-flor", "neck-panuelo-brasa")
            propios += puesto
            container.rewards.grantItems(propios, fijo)
            puesto.forEach { container.rewards.equip(it) }
            if (saldo > 0 || congeladores > 0) {
                val actual = container.database.pointsLedgerDao().all().sumOf { it.delta }
                container.rewards.spend(saldo + 100 * congeladores - actual, PointsReason.TASK_DONE, "task:ajuste", diaLogico, fijo)
                repeat(congeladores) { container.rewards.spend(-100, PointsReason.BUY_FREEZER, "f$it", diaLogico, fijo) }
            }
        }
    }

    private fun diaFijo(): Int = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(fijo), ZoneId.of(ZONA)).toEpochDay().toInt()

    private fun fotogramas(ms: Long = 50) {
        compose.mainClock.advanceTimeBy(ms)
        transcurrido.addAndGet(ms)
    }

    private fun hasta(cond: () -> Boolean) {
        repeat(1_500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            fotogramas(16)
            Thread.sleep(1)
        }
        assertTrue("no llegó a tiempo", cond())
    }

    private fun nodos(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun hay(tag: String) = nodos(tag).isNotEmpty()

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
        error("nada pulsable")
    }

    private fun pulsar(tag: String) = clic(nodos(tag).firstOrNull() ?: error("no hay «$tag»"))

    private fun instante(
        anio: Int,
        mes: Int,
        dia: Int,
        hora: Int,
    ): Long = ZonedDateTime.of(anio, mes, dia, hora, 0, 0, 0, ZoneId.of(ZONA)).toInstant().toEpochMilli()

    private companion object {
        const val ZONA = "Europe/Madrid"
    }
}

@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class CapturasTiendaTest : CapturasTiendaBase("es") {
    private val tapados = listOf("costume-calabaza")

    @Test fun `habi armario`() = estado("tienda-habi-armario")

    @Test fun `habi saltar pies`() = estado("tienda-habi-saltar-pies") { verRanura("pies") }

    @Test fun `habi luz tarde`() =
        estado(
            "tienda-habi-luz-tarde",
            poseidos = listOf("body-lavanda"),
            puesto = listOf("body-lavanda", "neck-bufanda"),
        ) { tocarTexto("Tarde") }

    @Test fun `habi luz noche`() =
        estado(
            "tienda-habi-luz-noche",
            poseidos = listOf("body-lavanda"),
            puesto = listOf("body-lavanda", "neck-bufanda"),
        ) { tocarTexto("Noche") }

    @Test fun `habi dia cerrado`() = estado("tienda-habi-dia-cerrado", hora = 23)

    @Test fun `habi noche disfrazado`() =
        estado("tienda-habi-noche-disfrazado", hora = 23, puesto = tapados + "neck-bufanda") { verRanura("disfraces") }

    @Test fun `habi primera vez`() = estado("tienda-habi-primera-vez", saldo = 0, congeladores = 0, racha = false, puesto = emptyList())

    @Test fun `habi prueba gorro`() = estado("tienda-habi-prueba-gorro") { tocar("head-gorro-lana") }

    @Test fun `habi prueba botas`() =
        estado("tienda-habi-prueba-botas") {
            verRanura("pies")
            tocar("feet-botas-agua")
        }

    @Test fun `habi prueba color`() =
        estado("tienda-habi-prueba-color") {
            verRanura("colores")
            tocar("body-lavanda")
        }

    @Test fun `habi sin saldo`() = estado("tienda-habi-sin-saldo", saldo = 50) { tocar("head-copa") }

    @Test fun `habi exclusivo bloqueado`() = estado("tienda-habi-exclusivo-bloqueado") { tocar("head-corona") }

    @Test fun `habi exclusivo ganado`() = estado("tienda-habi-exclusivo-ganado") { tocar("head-flor", conBarra = false) }

    @Test fun `habi temporada en`() =
        estado("tienda-habi-temporada-en", saldo = 210) {
            verRanura("disfraces")
            tocar("costume-calabaza")
        }

    @Test fun `habi temporada fuera`() =
        estado("tienda-habi-temporada-fuera") {
            verRanura("disfraces")
            tocar("costume-reno")
        }

    @Test fun `habi temporada comprado`() =
        estado("tienda-habi-temporada-comprado", mes = 11, dia = 8, poseidos = tapados) {
            verRanura("disfraces")
            tocar("costume-calabaza", conBarra = false)
        }

    @Test fun `habi compra tuyo`() =
        estado(
            "tienda-habi-compra-tuyo",
            saldo = 80,
            poseidos = listOf("head-gorro-lana"),
            puesto = listOf("head-gorro-lana", "neck-bufanda"),
        )

    @Test fun `habi tuyo quitado`() = estado("tienda-habi-tuyo-quitado", saldo = 80, poseidos = listOf("head-gorro-lana"))

    @Test fun `habi puesto`() = estado("tienda-habi-puesto", puesto = listOf("feet-botas-agua", "neck-bufanda")) { verRanura("pies") }

    @Test fun `habi ranura vacia`() =
        estado("tienda-habi-ranura-vacia", puesto = listOf("feet-botas-agua", "neck-bufanda")) {
            verRanura("pies")
            tocar("feet-botas-agua", conBarra = false)
        }

    @Test fun `habi todo comprado`() =
        estado(
            "tienda-habi-todo-comprado",
            saldo = 210,
            mes = 11,
            dia = 8,
            poseidos = TODO_PRECIO,
            puesto = listOf("body-lavanda", "eyes-violeta", "head-copa", "face-gafas-sol", "neck-collar-flores", "feet-zapatillas"),
        ) { verRanura("disfraces") }

    @Test fun `habi disfraz cabeza`() =
        estado(
            "tienda-habi-disfraz-cabeza",
            poseidos = listOf("head-gorro-lana", "face-gafas-redondas"),
            puesto = tapados + listOf("head-gorro-lana", "face-gafas-redondas", "neck-bufanda"),
        )

    @Test fun `habi disfraz puesto`() =
        estado("tienda-habi-disfraz-puesto", puesto = tapados + listOf("neck-bufanda", "feet-botas-agua")) { verRanura("cuello") }

    @Test fun `habi disfraz color`() =
        estado(
            "tienda-habi-disfraz-color",
            puesto = tapados + listOf("body-lavanda", "eyes-avellana", "neck-bufanda"),
        ) { verRanura("colores") }

    @Test fun `congeladores neutra`() = estado("tienda-congeladores-neutra", pestana = "ttab-congeladores")

    @Test fun `congeladores sargento`() = estado("tienda-congeladores-sargento", voz = Personality.SARGENTO, pestana = "ttab-congeladores")

    @Test fun `congeladores cheerleader`() =
        estado("tienda-congeladores-cheerleader", voz = Personality.CHEERLEADER, pestana = "ttab-congeladores")

    @Test fun `congeladores ninguno`() = estado("tienda-congeladores-ninguno", congeladores = 0, pestana = "ttab-congeladores")

    @Test fun `congeladores comprar`() = estado("tienda-congeladores-comprar", pestana = "ttab-congeladores")

    @Test fun `congeladores comprado`() = estado("tienda-congeladores-comprado", pestana = "ttab-congeladores") { tocarTag("tc-buy") }

    @Test fun `congeladores muchos`() = estado("tienda-congeladores-muchos", congeladores = 11, pestana = "ttab-congeladores")

    @Test fun `congeladores muchos 7`() = estado("tienda-congeladores-muchos-7", congeladores = 7, pestana = "ttab-congeladores")

    @Test fun `congeladores muchos 8`() = estado("tienda-congeladores-muchos-8", congeladores = 8, pestana = "ttab-congeladores")

    @Test fun `congeladores sin saldo`() = estado("tienda-congeladores-sin-saldo", saldo = 40, pestana = "ttab-congeladores")

    private companion object {
        val TODO_PRECIO =
            listOf(
                "body-vainilla", "body-melocoton", "body-cielo", "body-lavanda", "body-rosa", "body-terracota", "body-carbon",
                "eyes-avellana", "eyes-verde", "eyes-azul", "eyes-ambar", "eyes-violeta",
                "head-gorro-lana", "head-boina", "head-paja", "head-auriculares", "head-copa",
                "face-gafas-redondas", "face-gafas-sol", "neck-bufanda", "neck-panuelo", "neck-collar-flores",
                "feet-calcetines", "feet-botas-agua", "feet-zapatillas", "costume-calabaza", "costume-reno", "costume-dinosaurio",
            )
    }
}

@Config(sdk = [35], qualifiers = "en-w412dp-h915dp")
class CapturasTiendaEnTest : CapturasTiendaBase("en") {
    @Test fun `habi en`() =
        estado("tienda-habi-en", poseidos = listOf("costume-calabaza"), puesto = listOf("costume-calabaza", "neck-bufanda")) {
            verRanura("disfraces")
        }

    @Test fun `habi en barra`() =
        estado("tienda-habi-en-barra") {
            verRanura("disfraces")
            tocar("costume-dinosaurio")
        }

    @Test fun `habi en sin saldo`() = estado("tienda-habi-en-sin-saldo", saldo = 50) { tocar("head-copa") }

    @Test fun `habi en racha`() = estado("tienda-habi-en-racha") { tocar("head-corona") }

    @Test fun `congeladores en`() = estado("tienda-congeladores-en", pestana = "ttab-congeladores")
}
