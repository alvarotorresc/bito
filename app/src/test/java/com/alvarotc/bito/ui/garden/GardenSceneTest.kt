package com.alvarotc.bito.ui.garden

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenPlot
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiArt
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.premios.RegistroAnclas
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** La escena en Compose: lo que se toca (que una planta se meza lo prueba onSceneTap). Lo que se ve se comprueba en el Pixel (§9.2). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class GardenSceneTest {
    @get:Rule
    val compose = createComposeRule()

    private val spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())
    private val garden = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.MUSHROOMS))

    private fun setContent(
        garden: GardenState = this.garden,
        onHabiTap: () -> Unit = {},
    ) {
        compose.setContent {
            BitoTheme {
                GardenScene(garden = garden, spec = spec, animated = false, onHabiTap = onHabiTap, modifier = Modifier.width(400.dp))
            }
        }
    }

    @Test
    fun `tapping Habi in the garden reaches the callback`() {
        var taps = 0
        setContent(onHabiTap = { taps++ })

        compose.onNodeWithContentDescription("Habi, doing okay").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `tapping a special shows the name of its badge`() {
        setContent()
        val widthPx = with(compose.density) { 400.dp.toPx() }
        val heightPx = widthPx / GardenArt.SCENE_ASPECT
        val mushrooms = sceneItems(garden, widthPx, heightPx).first { it is SceneItem.Special }.rect
        compose.onNodeWithTag("garden-scene").performTouchInput {
            click(Offset(mushrooms.left + mushrooms.width / 2f, mushrooms.top + mushrooms.height / 2f))
        }

        compose.onNodeWithText("Resurrection").assertExists()
        compose.onNodeWithTag("garden-bubble-setas").assertExists()
        // El globito se va solo a los 2,5 s.
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithTag("garden-bubble-setas").assertDoesNotExist()
    }

    @Test
    fun `tapping a plant opens no bubble`() {
        setContent()
        val widthPx = with(compose.density) { 400.dp.toPx() }
        val heightPx = widthPx / GardenArt.SCENE_ASPECT
        val plant = sceneItems(garden, widthPx, heightPx).first { it is SceneItem.Plant }.rect

        compose.onNodeWithTag("garden-scene").performTouchInput {
            click(Offset(plant.left + plant.width / 2f, plant.top + plant.height / 2f))
        }
        compose.waitForIdle()

        compose.onNodeWithTag("garden-bubble-setas").assertDoesNotExist()
    }

    @Test
    fun `tapping the lantern beside Habi opens its bubble instead of petting Habi`() {
        var taps = 0
        val withLantern = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.LANTERN))
        setContent(withLantern) { taps++ }
        val widthPx = with(compose.density) { 400.dp.toPx() }
        val heightPx = widthPx / GardenArt.SCENE_ASPECT
        val lantern = sceneItems(withLantern, widthPx, heightPx).first { it is SceneItem.Special }.rect

        compose.onNodeWithTag("garden-scene").performTouchInput {
            click(Offset(lantern.left + lantern.width / 2f, lantern.top + lantern.height / 2f))
        }

        compose.onNodeWithText("First flame").assertExists()
        assertEquals(0, taps)
    }

    // Las pruebas de la pieza 3: el jardín vivo, con sonido, tacto y anclas grabados.
    private var jardin by mutableStateOf(garden)
    private var salto by mutableIntStateOf(0)
    private var fiesta by mutableIntStateOf(0)
    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val registro = RegistroAnclas()
    private val sonda = SondaJardin()

    private fun montar(
        garden: GardenState = this.garden,
        reducido: Boolean = false,
        alTocarFondo: (() -> Unit)? = null,
        globitoPlanta: Boolean = false,
        llegando: GardenSpecial? = null,
        creciendo: Int? = null,
        destacar: GardenSpecial? = null,
        dormido: Boolean = false,
        onHabiTap: () -> Unit = {},
        respiracion: (() -> Float)? = null,
        animada: Boolean = false,
        saltoAmplitud: Float? = null,
    ) {
        jardin = garden
        compose.setContent {
            ConSistema(sonido, tacto, reducido) {
                CompositionLocalProvider(LocalAnclas provides registro, LocalSondaJardin provides sonda) {
                    GardenScene(
                        garden = jardin,
                        spec = spec,
                        animated = animada,
                        onHabiTap = onHabiTap,
                        modifier = Modifier.width(400.dp),
                        alTocarFondo = alTocarFondo,
                        globitoPlanta = globitoPlanta,
                        llegando = llegando,
                        creciendo = creciendo,
                        destacar = destacar,
                        saltoHabi = salto,
                        fiesta = fiesta,
                        dormido = dormido,
                        respiracion = respiracion,
                        saltoAmplitud = saltoAmplitud,
                    )
                }
            }
        }
    }

    /** Un toque en el centro del cuerpo de Habi, por la capa de delante (como el dedo). */
    private fun tocarHabi() {
        val widthPx = with(compose.density) { 400.dp.toPx() }
        val cuerpo = habiBodyRect(widthPx, widthPx / GardenArt.SCENE_ASPECT)
        tapCenter(cuerpo)
        compose.waitForIdle()
    }

    private fun rectOf(
        garden: GardenState,
        pick: (SceneItem) -> Boolean,
    ): SceneRect {
        val widthPx = with(compose.density) { 400.dp.toPx() }
        return sceneItems(garden, widthPx, widthPx / GardenArt.SCENE_ASPECT).first(pick).rect
    }

    private fun tapCenter(rect: SceneRect) =
        compose.onNodeWithTag("garden-scene").performTouchInput {
            click(Offset(rect.left + rect.width / 2f, rect.top + rect.height / 2f))
        }

    @Test
    fun `tapping the background calls back, a short drag does not`() {
        var fondos = 0
        montar(alTocarFondo = { fondos++ })

        compose.onNodeWithTag("garden-scene").performTouchInput { click(Offset(20f, 20f)) }
        assertEquals(1, fondos)

        compose.onNodeWithTag("garden-scene").performTouchInput { swipeLeft() }
        assertEquals(1, fondos)
    }

    @Test
    fun `tapping a plant sways it with its sound and tick`() {
        montar()
        tapCenter(rectOf(garden) { it is SceneItem.Plant })

        assertTrue(Snd.PLANTA in sonido.snds())
        assertTrue(Haptica.TICK in tacto.hapticas)
    }

    @Test
    fun `tapping a plant in Camino shows its species and stage`() {
        val dos =
            GardenState.INITIAL.copy(
                plots =
                    listOf(
                        GardenPlot(GardenSpecies.ROUND_LEAVES, GrowthStage.SEED),
                        GardenPlot(GardenSpecies.SUNFLOWERS, GrowthStage.SPROUT),
                    ),
            )
        montar(dos, globitoPlanta = true)

        tapCenter(rectOf(dos) { it is SceneItem.Plant && it.plot == 0 })
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Stage 1 of 5.").assertExists()

        tapCenter(rectOf(dos) { it is SceneItem.Plant && it.plot == 1 })
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Sunflowers \u00b7 Stage 2 of 5. Grows now with what you check off.").assertExists()
    }

    @Test
    fun `tapping a special in Camino says which badge it came with`() {
        montar(globitoPlanta = true)
        tapCenter(rectOf(garden) { it is SceneItem.Special })

        compose.onNodeWithText("Mushrooms \u00b7 Came with Resurrection.").assertExists()
    }

    @Test
    fun `the growing plot registers its anchor`() {
        montar()
        compose.waitForIdle()

        assertTrue(registro.posiciones.containsKey(ClavesAncla.PARCELA))
    }

    @Test
    fun `Habi registers its anchor`() {
        montar()
        compose.waitForIdle()

        val scene = compose.onNodeWithTag("garden-scene").fetchSemanticsNode().boundsInRoot
        val habi = registro.posiciones.getValue(ClavesAncla.HABI)
        assertTrue(scene.contains(habi.center))
        assertTrue(habi.width > 0f && habi.height > 0f)
    }

    @Test
    fun `a special arriving plays its sound`() {
        montar(llegando = GardenSpecial.LANTERN)
        compose.mainClock.advanceTimeBy(600)

        assertTrue(Snd.POSAR in sonido.snds())
        assertTrue(Haptica.THUD in tacto.hapticas)
    }

    @Test
    fun `a hop with an amplitude rises that fraction of Habi and lands`() {
        montar(animada = true, saltoAmplitud = 0.11f)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(100)

        compose.runOnUiThread { salto++ }
        compose.mainClock.advanceTimeByFrame()
        repeat(30) { compose.mainClock.advanceTimeBy(30) }

        assertEquals("el pico es la amplitud pedida", 0.11f, sonda.salto.picoFraccion, 0.01f)
        assertEquals("aterriza", 0f, sonda.salto.fraccion, 0.005f)
        assertEquals(1, sonda.saltosHabi())
    }

    @Test
    fun `a hop without amplitude keeps today's reaction and never uses the arc`() {
        montar(animada = true)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(100)

        compose.runOnUiThread { salto++ }
        compose.mainClock.advanceTimeByFrame()
        repeat(30) { compose.mainClock.advanceTimeBy(30) }

        assertEquals(0f, sonda.salto.picoFraccion, 0f)
        assertEquals(1, sonda.saltosHabi())
    }

    @Test
    fun `a growing plot sprouts with its sounds`() {
        montar(creciendo = 0)
        compose.mainClock.advanceTimeBy(600)

        assertTrue(Snd.BROTE in sonido.snds())
        assertTrue(Snd.PLANTA in sonido.snds())
        assertTrue(Haptica.TICKS in tacto.hapticas)
        // Parcela nueva (etapa 1): Habi salta.
        assertEquals(1, sonda.saltosHabi())
    }

    @Test
    fun `when the garden revives the plants sway and Habi jumps`() {
        montar(garden.copy(wilt = 3))
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        assertEquals(3f, sonda.wiltDibujado(), 0f)

        compose.runOnUiThread { jardin = garden.copy(wilt = 0) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(450)
        val mitad = sonda.wiltDibujado()
        assertTrue("a mitad: $mitad", mitad > 0f && mitad < 3f)
        assertTrue(Snd.PLANTA in sonido.snds())
        assertEquals(1, sonda.saltosHabi())

        compose.mainClock.advanceTimeBy(500)
        assertEquals(0f, sonda.wiltDibujado(), 0f)
    }

    @Test
    fun `when the garden wilts nothing moves and nothing sounds`() {
        montar(garden.copy(wilt = 0))
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.runOnUiThread { jardin = garden.copy(wilt = 3) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(50)

        assertEquals(3f, sonda.wiltDibujado(), 0f)
        assertTrue(sonido.snds().isEmpty())
        assertEquals(0, sonda.saltosHabi())
    }

    @Test
    fun `with reduced motion the garden revives in 300 ms without swaying or jumping`() {
        montar(garden.copy(wilt = 3), reducido = true)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.runOnUiThread { jardin = garden.copy(wilt = 0) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(350)

        assertEquals(0f, sonda.wiltDibujado(), 0f)
        assertEquals(0, sonda.saltosHabi())
    }

    @Test
    fun `tapping Habi by day makes Habi jump`() {
        var toques = 0
        montar(onHabiTap = { toques++ })
        compose.waitForIdle()

        tocarHabi()

        assertEquals(1, toques)
        assertEquals(1, sonda.saltosHabi())
        assertEquals(0, sonda.aplastadosHabi())
    }

    @Test
    fun `tapping Habi asleep only squashes Habi, without a jump`() {
        var toques = 0
        montar(dormido = true, onHabiTap = { toques++ })
        compose.waitForIdle()

        tocarHabi()

        assertEquals(1, toques)
        assertEquals(0, sonda.saltosHabi())
        assertEquals(1, sonda.aplastadosHabi())
    }

    @Test
    fun `raising saltoHabi makes Habi jump`() {
        montar()
        compose.waitForIdle()
        assertEquals(0, sonda.saltosHabi())

        compose.runOnUiThread { salto = 1 }
        compose.waitForIdle()

        assertEquals(1, sonda.saltosHabi())
    }

    @Test
    fun `the party of a perfect day sways every plot once and Habi jumps`() {
        val tres =
            garden.copy(
                plots =
                    listOf(
                        GardenPlot(GardenSpecies.ROUND_LEAVES, GrowthStage.SPROUT),
                        GardenPlot(GardenSpecies.SUNFLOWERS, GrowthStage.SPROUT),
                        GardenPlot(GardenSpecies.entries[2], GrowthStage.SPROUT),
                    ),
            )
        montar(tres)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        assertEquals(0, sonido.snds().count { it == Snd.PLANTA })

        compose.runOnUiThread { fiesta = 1 }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1_500)

        assertEquals(3, sonido.snds().count { it == Snd.PLANTA })
        assertEquals(1, sonda.saltosHabi())
    }

    @Test
    fun `the party does not start with the initial value and with reduced motion Habi stays still`() {
        montar(reducido = true)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        assertEquals(0, sonido.snds().count { it == Snd.PLANTA })

        compose.runOnUiThread { fiesta = 1 }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)

        assertEquals(garden.plots.size, sonido.snds().count { it == Snd.PLANTA })
        assertEquals(0, sonda.saltosHabi())
    }

    @Test
    fun `the anchors follow a parent that only changes its graphicsLayer`() {
        var escala by mutableStateOf(1f)
        compose.setContent {
            ConSistema(sonido, tacto) {
                CompositionLocalProvider(LocalAnclas provides registro) {
                    GardenScene(
                        garden = garden,
                        spec = spec,
                        animated = false,
                        modifier =
                            Modifier.width(400.dp).graphicsLayer {
                                scaleX = escala
                                scaleY = escala
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                    )
                }
            }
        }
        compose.waitForIdle()
        val antes = registro.posiciones.getValue(ClavesAncla.HABI)

        compose.runOnUiThread { escala = 0.5f }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        val despues = registro.posiciones.getValue(ClavesAncla.HABI)
        assertEquals(antes.width / 2f, despues.width, 1f)
        assertEquals(antes.left / 2f, despues.left, 1f)
    }

    @Test
    fun `the anchors only follow the parent while the app is resumed`() {
        var escala by mutableStateOf(1f)
        val dueno =
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            }
        compose.setContent {
            ConSistema(sonido, tacto) {
                CompositionLocalProvider(LocalAnclas provides registro, LocalLifecycleOwner provides dueno) {
                    GardenScene(
                        garden = garden,
                        spec = spec,
                        animated = false,
                        modifier =
                            Modifier.width(400.dp).graphicsLayer {
                                scaleX = escala
                                scaleY = escala
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { (dueno.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        val antes = registro.posiciones.getValue(ClavesAncla.HABI)

        compose.runOnUiThread { escala = 0.5f }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)

        assertEquals(antes, registro.posiciones.getValue(ClavesAncla.HABI))
    }

    // --- G40: la noche que pinta el cierre del día (pieza 4, §7.5) ---

    private val conFarolillo = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.LANTERN))

    @Test
    fun `night one lights the lantern`() {
        val sondaNoche = SondaJardin()
        var noche by mutableStateOf(0f)
        compose.setContent {
            BitoTheme {
                CompositionLocalProvider(LocalSondaJardin provides sondaNoche) {
                    GardenScene(conFarolillo, spec, Modifier.width(400.dp), animated = false, noche = { noche })
                }
            }
        }
        compose.waitForIdle()
        assertEquals("de día y sin anochecer, el farolillo está apagado", 0f, sondaNoche.brilloFarolillo(), 0.001f)
        noche = 0.45f
        compose.waitForIdle()
        assertEquals("se enciende tarde: hasta 0,45 no brilla", 0f, sondaNoche.brilloFarolillo(), 0.001f)
        noche = 1f
        compose.waitForIdle()
        assertEquals(1f, sondaNoche.brilloFarolillo(), 0.001f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `night zero draws as today`() {
        val sondas = List(3) { SondaJardin() }
        // El arte ya en la caché: así la prueba no espera a la precarga en otro hilo (como LaminaTest).
        val arte = HabiArt.get(ApplicationProvider.getApplicationContext())
        recursosDelJardin(conFarolillo).forEach { (res, muestra) -> arte.image(res, muestra) }
        lateinit var vista: View
        compose.setContent {
            vista = LocalView.current
            // En píxeles enteros: una escena que empieza a media fila se muestrea distinto aunque se pinte igual.
            val ancho = with(LocalDensity.current) { 300.toDp() }
            val alto = with(LocalDensity.current) { 720.toDp() }
            BitoTheme {
                Box(Modifier.size(ancho, alto)) {
                    listOf<(() -> Float)?>(null, { 0f }, { 1f }).forEachIndexed { i, noche ->
                        val m = Modifier.offset { IntOffset(0, i * 240) }.width(ancho)
                        CompositionLocalProvider(LocalSondaJardin provides sondas[i]) {
                            if (noche == null) {
                                GardenScene(conFarolillo, spec, m, animated = false)
                            } else {
                                GardenScene(conFarolillo, spec, m, animated = false, noche = noche)
                            }
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { sondas.all { it.arteListo() } }
        compose.waitForIdle()
        val captura = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(captura))
        val cajas = compose.onAllNodesWithTag("garden-scene").fetchSemanticsNodes().map { it.boundsInRoot }
        val ancho = cajas.minOf { it.width }.toInt()
        val alto = cajas.minOf { it.height }.toInt()

        fun distintos(
            a: Int,
            b: Int,
        ): Int {
            var n = 0
            val ya = cajas[a].top.toInt()
            val yb = cajas[b].top.toInt()
            for (x in 0 until ancho) {
                for (y in 1 until alto - 1) if (lejos(captura.getPixel(x, ya + y), captura.getPixel(x, yb + y))) n++
            }
            return n
        }
        assertEquals("con noche 0 se pinta igual que hoy (salvo el ruido de muestreo de una unidad)", 0, distintos(0, 1))
        assertTrue("con noche 1 el jardín cambia", distintos(0, 2) > ancho * alto / 4)
    }

    @Test
    fun `the water seeps down while dusk starts from day and then goes away`() {
        val sondaAgua = SondaJardin()
        var noche by mutableStateOf(0f)
        compose.setContent {
            ConSistema(sonido, tacto, reducido = false) {
                CompositionLocalProvider(LocalSondaJardin provides sondaAgua) {
                    GardenScene(conFarolillo, spec, Modifier.width(400.dp), animated = false, noche = { noche })
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        noche = 0.3f
        Snapshot.sendApplyNotifications() // con el reloj quieto, el cambio no llega solo al efecto
        repeat(60) { compose.mainClock.advanceTimeByFrame() } // ~1 s de los 2800
        val aMitad = sondaAgua.filtracion()
        assertTrue("a mitad se está filtrando ($aMitad)", aMitad > 0f && aMitad < 1f)
        repeat(150) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("al acabar no queda franja", 1f, sondaAgua.filtracion(), 0f)
    }

    @Test
    fun `there is no seep with reduced motion`() {
        val sondaAgua = SondaJardin()
        var noche by mutableStateOf(0f)
        compose.setContent {
            ConSistema(sonido, tacto, reducido = true) {
                CompositionLocalProvider(LocalSondaJardin provides sondaAgua) {
                    GardenScene(conFarolillo, spec, Modifier.width(400.dp), animated = false, noche = { noche })
                }
            }
        }
        compose.waitForIdle()
        noche = 0.3f
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("reducido: sin filtración", 0f, sondaAgua.filtracion(), 0f)
    }

    @Test
    fun `opening already at night does not seep`() {
        val sondaAgua = SondaJardin()
        var noche by mutableStateOf(1f)
        compose.setContent {
            ConSistema(sonido, tacto, reducido = false) {
                CompositionLocalProvider(LocalSondaJardin provides sondaAgua) {
                    GardenScene(conFarolillo, spec, Modifier.width(400.dp), animated = false, noche = { noche })
                }
            }
        }
        compose.waitForIdle()
        noche = 0.99f
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(0f, sondaAgua.filtracion(), 0f)
    }

    /** Dos píxeles distintos de verdad: el filtro de color deja ruido de una unidad por canal entre dos pintadas iguales. */
    private fun lejos(
        a: Int,
        b: Int,
    ): Boolean = (0..3).any { abs((a shr (it * 8) and 0xFF) - (b shr (it * 8) and 0xFF)) > 2 }

    @Test
    fun `breathing closes the eyes and stretches from the feet`() {
        var llenado by mutableStateOf(1f)
        montar(respiracion = { llenado })

        assertEquals(1f, sonda.ojosCerradosHabi(), 0f)
        val lleno = sonda.estiramientoHabi()
        assertEquals(1.054f, lleno.scaleY, 0.0001f)
        assertEquals(0.973f, lleno.scaleX, 0.0001f)
        // El origen son los pies de Habi (88 % de la escena), no el borde de abajo de su lienzo.
        val widthPx = with(compose.density) { 400.dp.toPx() }
        val sceneH = widthPx / GardenArt.SCENE_ASPECT
        val caja = habiRect(widthPx, sceneH)
        assertEquals((sceneH * 0.88f - caja.top) / caja.height, lleno.origenY, 0.0001f)

        // Vacío: se encoge lo mismo hacia abajo. El llenado se lee al dibujar, sin recomponer.
        llenado = 0f
        val vacio = sonda.estiramientoHabi()
        assertEquals(0.946f, vacio.scaleY, 0.0001f)
        assertEquals(1.027f, vacio.scaleX, 0.0001f)
    }

    @Test
    fun `breathing in reduced motion stretches half as much`() {
        montar(reducido = true, respiracion = { 1f })

        val lleno = sonda.estiramientoHabi()
        assertEquals(1.027f, lleno.scaleY, 0.0001f)
        assertEquals(0.9865f, lleno.scaleX, 0.0001f)
    }

    @Test
    fun `without breathing nothing changes`() {
        montar()

        assertEquals(0f, sonda.ojosCerradosHabi(), 0f)
        val quieto = sonda.estiramientoHabi()
        assertEquals(1f, quieto.scaleX, 0f)
        assertEquals(1f, quieto.scaleY, 0f)
    }

    @Test
    fun `the drawn Habi grows 5,4 percent in height and shrinks 2,7 in width keeping the feet`() {
        var llenado by mutableStateOf(0.5f)
        montar(respiracion = { llenado })
        compose.waitForIdle()

        val habi = { compose.onNodeWithContentDescription("Habi, doing okay").fetchSemanticsNode().boundsInRoot }
        val reposo = habi()
        // Los pies están al 88 % de la escena (calculado a mano, no con la función de la app).
        val escena = compose.onNodeWithTag("garden-scene").fetchSemanticsNode().boundsInRoot
        val pies = escena.top + escena.height * 0.88f
        val origen = (pies - reposo.top) / reposo.height

        llenado = 1f
        compose.waitForIdle()
        val lleno = habi()
        assertEquals(1.054f, lleno.height / reposo.height, 0.001f)
        assertEquals(0.973f, lleno.width / reposo.width, 0.001f)
        assertEquals(pies, lleno.top + origen * lleno.height, 0.5f)

        llenado = 0f
        compose.waitForIdle()
        val vacio = habi()
        assertEquals(0.946f, vacio.height / reposo.height, 0.001f)
        assertEquals(pies, vacio.top + origen * vacio.height, 0.5f)
    }
}
