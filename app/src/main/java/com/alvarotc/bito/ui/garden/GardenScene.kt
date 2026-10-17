package com.alvarotc.bito.ui.garden

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.GardenSeason
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.ui.habi.ColorTint
import com.alvarotc.bito.ui.habi.HabiArt
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiBreath
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.SondaSalto
import com.alvarotc.bito.ui.habi.rememberArte
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.componentes.Globito
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.stats.BadgeStrings
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.tan

private const val BUBBLE_MS = 2500L

// El vaivén de un toque (G20): el muelle `planta` con este impulso inicial, en grados por segundo.
private const val IMPULSO_PLANTA = 90f

// Reducido: en vez de mecerse o aplastarse, la pieza brilla 300 ms.
private const val BRILLO_MS = 300
private const val BRILLO_ALFA = 0.45f

// Un especial tocado o destacado se aplasta hasta este alto (muelle `nodulo`) y se ensancha la mitad de lo que se aplasta.
private const val APLASTE_MIN = 0.9f
private const val APLASTE_ENSANCHA = 0.5f

// G15: lo que cae rebota aplastándose hasta aquí (muelle `caida`) y suelta cinco motas de polvo.
private const val REBOTE_MIN = 0.88f
private const val POLVO_MOTAS = 5
private const val POLVO_ABRE = 0.2f
private const val POLVO_RADIO = 0.07f
private const val POLVO_ALFA = 0.6f
private const val SALPICA_ALFA = 0.8f

// G16: la etapa nueva sube desde su base (hasta la mitad de su alto, desde el 80 % de su tamaño) y la vieja se funde en 260 ms.
private const val CRECE_SUBE = 0.55f
private const val CRECE_ESCALA_MIN = 0.8f
private const val VIEJA_MS = 260

// El jardín que revive (§5.6): el mustio baja en 900 ms (300 reducido) y las plantas se mecen en ola, 70 ms entre parcelas.
private const val REVIVE_MS = 900
private const val OLA_MS = 70L

// La luz de la fiesta del día completo sube y baja en 1,4 s en total, con este brillo como tope.
private const val FIESTA_SUBE_MS = 450
private const val FIESTA_BAJA_MS = 950
private const val FIESTA_LUZ_MAX = 0.22f

// El mustio que se anima se cuantiza en octavos: la caché de filtros de HabiArt es por lista de tintes y no debe crecer por fotograma.
private const val PASOS_MUSTIO = 8f

// El brillo del farolillo de noche, como el del prototipo: una elipse del 22 % del ancho y el 30 % del
// alto, centrada 1,5 puntos a la derecha y 15 por encima del pie del farolillo, en modo pantalla. Es
// luz, no arte.
private const val GLOW_RADIUS_X = 0.22f
private const val GLOW_RADIUS_Y = 0.30f
private const val GLOW_SHIFT_X_PCT = 1.5f
private const val GLOW_RISE_PCT = 15f
private const val GLOW_MID_STOP = 0.45f
private val GlowCore = Color(0xBFFFBE6E)

// G40: el tinte de noche que el anochecer multiplica sobre el jardín (el de la fase NOCHE de la tabla de luz). El
// farolillo «se enciende tarde»: su brillo empieza a subir con la noche a 0,45 y llega a 1 con la noche entera; de la
// mitad de la noche en adelante se pinta encima de la luz.
internal val NOCHE_ARRIBA = Color(GardenLookTable.light.getValue("NOCHE").first)
internal val NOCHE_ABAJO = Color(GardenLookTable.light.getValue("NOCHE").second)
private const val FAROL_DESDE = 0.45f
private const val FAROL_ENCIMA = 0.5f

// G40: mientras anochece, una franja de agua de 160 dp se filtra hacia abajo por el jardín en 2800 ms `io`: un
// degradado fijo que se traslada, sin desenfoque (los valores del prototipo `cierre.html`; el agua no tiene token).
private val FRANJA_FILTRA = 160.dp
private val AGUA_FILTRA = Color(0xFF7DB6C9)
private const val AGUA_FILTRA_ALFA = 0.42f
private const val AGUA_FILTRA_PICO = 0.55f
private const val AGUA_FILTRA_LLENA = 0.2f

/** El brillo del farolillo: el de la noche del reloj ([night]) o el del anochecer del cierre ([noche]), el mayor. */
internal fun brilloFarolillo(
    night: Float,
    noche: Float,
): Float = maxOf(night.coerceIn(0f, 1f), ((noche - FAROL_DESDE) / (1f - FAROL_DESDE)).coerceIn(0f, 1f))

private val GlowMid = Color(0x38FFA050)

// «Hasta mañana» (spec §7.5, §9): Habi se aplasta un 4 % en 200 ms. Sin token propio, con nombre aquí.
private const val APLASTADO_DORMIR = 0.04f
private const val APLASTAR_DORMIR_MS = 200

/** Lo que las pruebas miran del jardín sin cambiar su firma: el mustio que se dibuja y cuántos saltos ha dado Habi. */
internal class SondaJardin {
    var wiltDibujado: () -> Float = { 0f }
    var saltosHabi: () -> Int = { 0 }

    /** El salto de Habi pedido con amplitud, como se dibuja. */
    val salto = SondaSalto()

    /** Cuántas veces se ha aplastado Habi para dormirse («Hasta mañana»). */
    var aplastadosHabi: () -> Int = { 0 }

    /** La noche que se pinta (G40): 0 de día, 1 en Hoy de noche. */
    var noche: () -> Float = { 0f }

    /** El brillo del farolillo que se pinta, de 0 a 1 (G40). */
    var brilloFarolillo: () -> Float = { 0f }

    /** Por dónde va la franja de agua que se filtra al anochecer (0 o 1 cuando no se pinta). */
    var filtracion: () -> Float = { 0f }

    /** Si el arte del jardín ya está cargado y se dibuja. */
    var arteListo: () -> Boolean = { false }

    /** Las claves de los especiales que se han destacado, en orden. */
    val destacados = mutableListOf<String>()

    /** El estiramiento de Habi que respira, leído como al dibujar (1, 1 sin respiración). */
    var estiramientoHabi: () -> EstiramientoHabi = { EstiramientoHabi(1f, 1f, 1f) }

    /** El cierre de los ojos que se le pide a Habi: 1 mientras respira, 0 si no. */
    var ojosCerradosHabi: () -> Float = { 0f }

    /** El ánimo con el que se pinta a Habi (radiante en Terminada, el del día fuera de ella). */
    var moodHabi: () -> Mood = { Mood.NORMAL }

    /** Cuántas veces se ha recompuesto el jardín compartido: el Inicio no ha de recomponerse con cada tic de la sesión. */
    var recomposiciones: Int = 0
}

/** La escala de Habi que respira, con su origen vertical (fracción de su lienzo) en los pies. */
internal data class EstiramientoHabi(val scaleX: Float, val scaleY: Float, val origenY: Float)

// Respirar (spec §3.3.3): Habi se estira 0,054 en alto y la mitad en ancho; reducido, a media amplitud.
private const val ESTIRA_RESPIRA = 0.054f
private const val ESTIRA_RESPIRA_REDUCIDO = 0.027f

// Los ojos cerrados y quieto de la respiración: sin la respiración propia de Habi, que la lleva la escena.
private val RESPIRA_OJOS_CERRADOS = HabiBreath(fill = 0f, amplitude = 0f, eyeClosure = 1f)

/** `respirarHabi()` del mock: con el llenado [fill] (0..1), `scaleY = 1 + a·(2f − 1)` y `scaleX = 1 − a/2·(2f − 1)`. */
internal fun estiramientoRespiracion(
    fill: Float,
    reducido: Boolean,
    origenY: Float,
): EstiramientoHabi {
    val a = if (reducido) ESTIRA_RESPIRA_REDUCIDO else ESTIRA_RESPIRA
    val k = 2f * fill.coerceIn(0f, 1f) - 1f
    return EstiramientoHabi(scaleX = 1f - a * 0.5f * k, scaleY = 1f + a * k, origenY = origenY)
}

internal val LocalSondaJardin = staticCompositionLocalOf<SondaJardin?> { null }

/** Cómo llega cada especial al jardín (G15). */
private enum class Llegada { CAE, BROTA, PIEDRAS, ASIENTA }

private fun GardenSpecial.llegada(): Llegada =
    when (this) {
        GardenSpecial.LANTERN, GardenSpecial.MUSHROOMS -> Llegada.CAE
        GardenSpecial.BIG_CHERRY, GardenSpecial.GOLDEN_TREE -> Llegada.BROTA
        GardenSpecial.STONE_PATH -> Llegada.PIEDRAS
        GardenSpecial.POND -> Llegada.ASIENTA
    }

/** El especial que llega: [p] es el avance de su llegada, [rebote] el aplastón de lo que cae y [polvo] el polvo que levanta. */
private class AnimLlegada(val especial: GardenSpecial) {
    val p = Animatable(0f)
    val rebote = Animatable(1f)
    val polvo = Animatable(0f)
}

/** La parcela que crece (G16): [p] sube la etapa nueva y [fundido] borra la vieja. */
private class AnimCrece(inicio: Float) {
    val p = Animatable(inicio)
    val fundido = Animatable(inicio)
}

/** El globito de una planta tocada en Camino; [tick] reinicia sus 2,4 s en cada toque. */
private class GlobitoDePlanta(val plot: Int, val tick: Int)

private class Efectos(
    val balanceo: Map<Int, Animatable<Float, AnimationVector1D>>,
    val brillo: Map<String, Animatable<Float, AnimationVector1D>>,
    val aplaste: Map<String, Animatable<Float, AnimationVector1D>>,
    val llegada: AnimLlegada?,
    val crece: AnimCrece?,
    val creciendo: Int?,
    val vieja: GardenPiece?,
    val reducido: Boolean,
    val polvo: Color,
    val salpicadura: Color,
)

/** Cómo se pinta una pieza este fotograma: desplazamiento, escala, opacidad, recorte horizontal y brillo. */
private class Pose {
    var dy = 0f
    var escalaX = 1f
    var escalaY = 1f
    var alpha = 1f
    var recorteArriba = 0f
    var recorteAbajo = Float.MAX_VALUE
    var brillo = 0f
}

/**
 * El jardín de Habi (pieza 1 §6.3), de atrás adelante: fondo, parcelas, plantas y especiales por
 * profundidad, Habi (con su sombra, que es capa suya), lo que queda delante de Habi, la luz en modo
 * multiplicar y, de noche, el farolillo encima de la luz ([itemsOverLight]) con su brillo en modo
 * pantalla. Mustio y estación son tintes y transformaciones sobre el mismo arte pintado
 * ([GardenLook]). Todos los toques los decide [onSceneTap]: el cuerpo de Habi hace lo de siempre
 * ([onHabiTap], con la reacción de [HabiAvatar] hacia el dedo); una planta se mece (G20) y, con
 * [globitoPlanta] (Camino), dice su especie y etapa; un especial se aplasta y abre un globito con su
 * logro; el fondo llama a [alTocarFondo] (un arrastre de más de [UMBRAL_ARRASTRE_DP] no es un toque).
 *
 * [llegando] es el especial que llega (G15), [creciendo] la parcela que sube de etapa (G16) y
 * [destacar] el especial que se aplasta una vez. Cada incremento de [saltoHabi] es un salto de Habi.
 * Cuando el mustio de [garden] baja (el jardín revive al sellar) se anima en 900 ms, las plantas se
 * mecen en ola y Habi salta. Nada de esto recompone por fotograma: los avances son `Animatable`
 * leídos al dibujar. La escena registra en [LocalAnclas] la base de la parcela que crece
 * ([ClavesAncla.PARCELA]) y el cuerpo de Habi ([ClavesAncla.HABI]). [forma] recorta la escena: por
 * defecto esquinas de 24 dp; el Inicio a sangre pasa un rectángulo.
 */
@Composable
fun GardenScene(
    garden: GardenState,
    spec: HabiSpec,
    modifier: Modifier = Modifier,
    delighted: Boolean = false,
    onHabiTap: (() -> Unit)? = null,
    animated: Boolean = true,
    // Toque en el fondo de la escena (ni Habi, ni planta, ni especial).
    alTocarFondo: (() -> Unit)? = null,
    // Globito sobre la planta y sobre el especial tocados (Camino).
    globitoPlanta: Boolean = false,
    // El especial que llega al jardín.
    llegando: GardenSpecial? = null,
    // La parcela que está creciendo.
    creciendo: Int? = null,
    // El especial que se destaca.
    destacar: GardenSpecial? = null,
    // Cada incremento, un salto de Habi de .24; lo pide el Inicio al volver de crear, spec §5.7.
    saltoHabi: Int = 0,
    // El recorte de la escena. Hoy a sangre pasa `RectangleShape`: su borde de abajo es recto contra la costra.
    forma: Shape = RoundedCornerShape(24.dp),
    // Cada incremento, la fiesta del día completo: las plantas en ola, Habi salta y la luz sube 1,4 s (spec §6.3).
    fiesta: Int = 0,
    // 0 de día, 1 de noche (G40). Lambda para leerla al dibujar sin recomponer en cada fotograma.
    noche: () -> Float = { 0f },
    // Cada incremento, Habi se aplasta un 4 % desde los pies y vuelve: «Hasta mañana», justo antes de dormirse (§7.5).
    aplastarHabi: Int = 0,
    // Habi duerme (Hoy de noche, día sellado): tocarlo lo aplasta un 4 % en vez de hacerlo saltar (G17, §5.10).
    dormido: Boolean = false,
    // La sesión: el llenado de la respiración (0..1), leído al dibujar. No nulo = Habi con los ojos cerrados, sin
    // parpadeo ni gestos propios, estirándose desde los pies con cada fotograma (spec §3.3.3, §8.2 y §13).
    respiracion: (() -> Float)? = null,
    // La amplitud (fracción del alto de Habi) del salto de cada incremento de [saltoHabi]; null = el salto de siempre.
    saltoAmplitud: Float? = null,
    // Objetos de jardín que se pintan además de los de [garden] (el que se prueba en la tienda).
    objetos: List<ObjetoJardin> = emptyList(),
) {
    val context = LocalContext.current
    val art = remember(context) { HabiArt.get(context) }
    val reducido = BitoTema.reducido
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    val scope = rememberCoroutineScope()
    // Lo que se pinta: el jardín y, mientras llega, el especial que llega aunque aún no esté en él.
    val pintado =
        remember(garden, llegando) {
            if (llegando != null && llegando !in garden.specials) garden.copy(specials = garden.specials + llegando) else garden
        }
    val vieja = remember(garden, creciendo) { etapaVieja(garden, creciendo) }
    val listo = rememberArte(art, remember(pintado, creciendo, objetos) { recursosDelJardin(pintado, creciendo, objetos) })

    val balanceo = remember { mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>() }
    val brillo = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    val aplaste = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() }
    var bubble by remember { mutableStateOf<SceneItem.Special?>(null) }
    // Cada toque en un especial vuelve a contar los 2,5 s, aunque sea el mismo.
    var bubbleTick by remember { mutableIntStateOf(0) }
    var globo by remember { mutableStateOf<GlobitoDePlanta?>(null) }
    var globoTick by remember { mutableIntStateOf(0) }
    var habiNudge by remember { mutableIntStateOf(0) }
    var habiAmplitud by remember { mutableStateOf<Float?>(null) }
    var habiTouch by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    LaunchedEffect(bubble, bubbleTick) {
        if (bubble != null) {
            delay(BUBBLE_MS)
            bubble = null
        }
    }

    // G20: la planta se mece desde la base con el muelle `planta`; reducido, brilla 300 ms.
    fun mecer(plot: Int) {
        if (reducido) {
            val b = brillo.getOrPut("p$plot") { Animatable(0f) }
            scope.launch {
                b.snapTo(1f)
                b.animateTo(0f, tween(BRILLO_MS))
            }
        } else {
            val a = balanceo.getOrPut(plot) { Animatable(0f) }
            scope.launch { a.animateTo(0f, Muelles.planta<Float>(), initialVelocity = IMPULSO_PLANTA) }
        }
    }

    // El especial se aplasta con el muelle `nodulo`; reducido, brilla.
    fun aplastar(key: String) {
        if (reducido) {
            val b = brillo.getOrPut(key) { Animatable(0f) }
            scope.launch {
                b.snapTo(1f)
                b.animateTo(0f, tween(BRILLO_MS))
            }
        } else {
            val a = aplaste.getOrPut(key) { Animatable(1f) }
            scope.launch {
                a.snapTo(APLASTE_MIN)
                a.animateTo(1f, Muelles.nodulo<Float>())
            }
        }
    }

    // El jardín que revive: el mustio que se dibuja baja animado; si sube (el jardín se mustia) cambia sin animar.
    val wiltAnim = remember { Animatable(garden.wilt.toFloat()) }
    var ultimoWilt by remember { mutableIntStateOf(garden.wilt) }
    LaunchedEffect(garden.wilt) {
        val destino = garden.wilt
        val baja = destino < ultimoWilt
        ultimoWilt = destino
        if (!baja) {
            wiltAnim.snapTo(destino.toFloat())
        } else {
            gesto(Snd.PLANTA, Haptica.TICK)
            if (!reducido) {
                habiAmplitud = null
                habiNudge++
                garden.plots.indices.forEach { i ->
                    launch {
                        delay(OLA_MS * i)
                        mecer(i)
                    }
                }
            }
            wiltAnim.animateTo(destino.toFloat(), tween(if (reducido) BRILLO_MS else REVIVE_MS, easing = Curvas.io))
        }
    }

    // Habi salta por el mismo camino que el toque; solo una subida cuenta (el valor inicial ya está contestado).
    var ultimoSalto by remember { mutableIntStateOf(saltoHabi) }
    LaunchedEffect(saltoHabi) {
        if (saltoHabi > ultimoSalto) {
            habiAmplitud = saltoAmplitud
            habiNudge++
        }
        ultimoSalto = saltoHabi
    }

    // La fiesta del día completo: ola de plantas (70 ms entre parcelas), salto de Habi y la luz que sube y baja en 1,4 s.
    val luzFiesta = remember { Animatable(0f) }
    var ultimaFiesta by remember { mutableIntStateOf(fiesta) }
    LaunchedEffect(fiesta) {
        val sube = fiesta > ultimaFiesta
        ultimaFiesta = fiesta
        if (!sube) return@LaunchedEffect
        if (!reducido) {
            run {
                habiAmplitud = null
                habiNudge++
            }
        }
        garden.plots.indices.forEach { i ->
            launch {
                delay(OLA_MS * i)
                gesto(Snd.PLANTA, Haptica.TICK)
                mecer(i)
            }
        }
        luzFiesta.snapTo(0f)
        luzFiesta.animateTo(1f, tween(if (reducido) BRILLO_MS else FIESTA_SUBE_MS, easing = Curvas.io))
        luzFiesta.animateTo(0f, tween(if (reducido) BRILLO_MS else FIESTA_BAJA_MS, easing = Curvas.io))
    }

    // G15: un especial que llega. El avance parte de 0 en la misma composición en que cambia `llegando`.
    val llegada = remember(llegando) { llegando?.let { AnimLlegada(it) } }
    LaunchedEffect(llegando) {
        val l = llegada ?: return@LaunchedEffect
        val breve: AnimationSpec<Float> = tween(Duraciones.REDUCIDO)
        when (l.especial.llegada()) {
            Llegada.CAE -> {
                if (reducido) {
                    gesto(Snd.POSAR, Haptica.THUD)
                    l.p.animateTo(1f, breve)
                } else {
                    l.p.animateTo(1f, tween(Duraciones.CAER, easing = Curvas.cae))
                    gesto(Snd.POSAR, Haptica.THUD)
                    launch { l.polvo.animateTo(1f, tween(Duraciones.POLVO, easing = Curvas.lin)) }
                    l.rebote.snapTo(REBOTE_MIN)
                    l.rebote.animateTo(1f, Muelles.caida<Float>())
                }
            }
            Llegada.BROTA -> {
                gesto(Snd.BROTE, Haptica.TICK)
                l.p.animateTo(1f, if (reducido) breve else Muelles.hoja<Float>())
            }
            Llegada.PIEDRAS -> {
                gesto(Snd.POSAR, Haptica.TICK)
                l.p.animateTo(1f, if (reducido) breve else tween(PIEDRAS_MS, easing = Curvas.drawer))
            }
            Llegada.ASIENTA -> {
                gesto(Snd.LLEGADA, Haptica.CLICK)
                l.p.animateTo(1f, if (reducido) breve else tween(ASIENTA_MS, easing = Curvas.out))
            }
        }
    }

    // G16: la parcela que crece. La etapa vieja es la anterior de la planta; en una parcela nueva (etapa 1) no hay vieja.
    val crece = remember(creciendo) { AnimCrece(if (creciendo == null) 1f else 0f) }
    val jardinActual by rememberUpdatedState(garden)
    LaunchedEffect(creciendo) {
        val idx = creciendo ?: return@LaunchedEffect
        val plot = jardinActual.plots.getOrNull(idx) ?: return@LaunchedEffect
        gesto(Snd.BROTE, Haptica.TICKS)
        if (plot.stage == GrowthStage.SEED) {
            run {
                habiAmplitud = null
                habiNudge++
            }
        }
        launch { crece.fundido.animateTo(1f, tween(if (reducido) Duraciones.REDUCIDO else VIEJA_MS)) }
        launch { crece.p.animateTo(1f, if (reducido) tween(Duraciones.REDUCIDO) else Muelles.hoja<Float>()) }
        delay(VIEJA_MS.toLong())
        gesto(Snd.PLANTA, Haptica.CLICK)
        mecer(idx)
    }

    val sonda = LocalSondaJardin.current

    // Destacar: el especial se aplasta una vez al cambiar.
    LaunchedEffect(destacar) {
        destacar?.let {
            sonda?.destacados?.add(it.key)
            aplastar(it.key)
        }
    }

    // G40: la noche se lee al dibujar; solo recompone cuando el farolillo pasa a pintarse encima de la luz.
    val nocheAhora by rememberUpdatedState(noche)
    val farolEncima by remember { derivedStateOf { nocheAhora() > FAROL_ENCIMA } }
    // La filtración sale solo cuando el anochecer arranca desde el día; abrir de noche (Hoy de noche) no la pinta.
    val filtracion = remember { Animatable(0f) }
    LaunchedEffect(reducido) {
        var antes = nocheAhora()
        snapshotFlow { nocheAhora() }.collect { n ->
            if (!reducido && antes <= 0f && n > 0f && n < 1f && !filtracion.isRunning) {
                launch {
                    filtracion.snapTo(0f)
                    filtracion.animateTo(1f, tween(Duraciones.FILTRACION, easing = Curvas.io))
                }
            }
            antes = n
        }
    }
    val listoAhora by rememberUpdatedState(listo)

    // «Hasta mañana» y el toque a Habi dormido: un aplastado corto desde los pies. Reducido, sin escala.
    val aplastado = remember { Animatable(1f) }
    var ultimoAplastado by remember { mutableIntStateOf(aplastarHabi) }
    var aplastados by remember { mutableIntStateOf(0) }

    fun aplastarHabiAhora() {
        aplastados++
        if (reducido) return
        scope.launch {
            aplastado.animateTo(1f - APLASTADO_DORMIR, tween(APLASTAR_DORMIR_MS, easing = Curvas.out))
            aplastado.animateTo(1f, Muelles.marcar())
        }
    }
    LaunchedEffect(aplastarHabi) {
        val sube = aplastarHabi > ultimoAplastado
        ultimoAplastado = aplastarHabi
        if (sube) aplastarHabiAhora()
    }
    val dormidoAhora by rememberUpdatedState(dormido)

    SideEffect {
        sonda?.wiltDibujado = { wiltAnim.value }
        sonda?.saltosHabi = { habiNudge }
        sonda?.moodHabi = { spec.mood }
        sonda?.aplastadosHabi = { aplastados }
        sonda?.noche = { nocheAhora() }
        sonda?.arteListo = { listoAhora }
        sonda?.filtracion = { filtracion.value }
        sonda?.brilloFarolillo = {
            if (GardenSpecial.LANTERN in pintado.specials) brilloFarolillo(GardenLook.nightness(garden.light), nocheAhora()) else 0f
        }
    }

    val registro = LocalAnclas.current
    DisposableEffect(registro) {
        onDispose {
            registro?.quitar(ClavesAncla.PARCELA)
            registro?.quitar(ClavesAncla.HABI)
        }
    }
    val coordenadas = remember { arrayOfNulls<LayoutCoordinates>(1) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(GardenArt.SCENE_ASPECT)
            .clip(forma)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .testTag("garden-scene"),
    ) {
        val sceneW = constraints.maxWidth.toFloat()
        val sceneH = constraints.maxHeight.toFloat()
        val items = remember(pintado, sceneW, sceneH, objetos) { sceneItems(pintado, sceneW, sceneH, objetos) }
        val habiDepth = GardenArt.habiSpot.yPct
        // De noche (la del reloj o la del cierre), el farolillo encendido se pinta encima de la luz y no se oscurece.
        val overLight =
            itemsOverLight(items, garden.light).ifEmpty {
                if (farolEncima) items.filter { it is SceneItem.Special && it.special == GardenSpecial.LANTERN } else emptyList()
            }
        val habi = habiRect(sceneW, sceneH)
        val habiBody = habiBodyRect(sceneW, sceneH)
        val currentOnHabiTap by rememberUpdatedState(onHabiTap)
        val currentAlTocarFondo by rememberUpdatedState(alTocarFondo)
        val efectos =
            Efectos(balanceo, brillo, aplaste, llegada, crece, creciendo, vieja, reducido, colores.tAlta, colores.hielo)

        // Las anclas, en coordenadas de la raíz y aunque la escena vaya escalada por su padre.
        val registrar: (LayoutCoordinates) -> Unit = { c ->
            if (registro != null && c.isAttached) {
                fun enRaiz(r: SceneRect) =
                    Rect(c.localToRoot(Offset(r.left, r.top)), c.localToRoot(Offset(r.left + r.width, r.top + r.height)))

                // Solo se escribe si el rectángulo cambia: el bucle de fotogramas de abajo no mueve el registro en balde.
                fun poner(
                    clave: String,
                    rect: Rect,
                ) {
                    if (registro.posiciones[clave] != rect) registro.poner(clave, rect)
                }
                val parcela = parcelaQueCrece(garden)
                if (parcela in garden.plots.indices) poner(ClavesAncla.PARCELA, enRaiz(baseDeParcela(parcela, sceneW, sceneH)))
                poner(ClavesAncla.HABI, enRaiz(habiBody))
            }
        }
        val registrarActual by rememberUpdatedState(registrar)
        // El padre puede mover la escena solo con graphicsLayer (encoger con el scroll, crecer a Camino), lo que no
        // dispara onGloballyPositioned: mientras esté compuesta, se recalcula cada ~16 ms (localToRoot cuenta la capa). Un bucle de `withFrameNanos` dejaría la escena siempre ocupada para las pruebas de Compose.
        // Solo con la app en primer plano: en segundo plano la composición sigue viva y no hay nadie a quien apuntar.
        val ciclo = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(registro, ciclo) {
            if (registro == null) return@LaunchedEffect
            ciclo.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(ANCLAS_REFRESCO_MS)
                    coordenadas[0]?.let(registrarActual)
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    coordenadas[0] = it
                    registrarActual(it)
                },
        )

        Canvas(Modifier.fillMaxSize()) {
            if (!listo) return@Canvas
            val wilt = wiltAnim.value
            val tintesCesped = tintes(wilt, garden.season, planta = false)
            val tintesPlanta = tintes(wilt, garden.season, planta = true)
            art.image(GardenArt.background)?.let { drawFill(it, if (tintesCesped.isEmpty()) null else art.filter(tintesCesped)) }
            items.filter { it.depth <= habiDepth && it !in overLight }.forEach { drawItem(art, it, wilt, tintesPlanta, efectos) }
        }

        val density = LocalDensity.current
        // Los pies de Habi dentro de su lienzo: el origen del estiramiento de la respiración.
        val piesHabi = (sceneH * habiDepth / 100f - habi.top) / habi.height
        val respira = respiracion
        // Lo que se le pasa a HabiAvatar: la sonda lee este mismo valor, no una copia.
        val respiraHabi = if (respira != null) RESPIRA_OJOS_CERRADOS else null
        SideEffect {
            sonda?.estiramientoHabi = {
                respira?.let { estiramientoRespiracion(it(), reducido, piesHabi) } ?: EstiramientoHabi(1f, 1f, piesHabi)
            }
            sonda?.ojosCerradosHabi = { respiraHabi?.eyeClosure ?: 0f }
        }
        HabiAvatar(
            spec = spec,
            breath = respiraHabi,
            modifier =
                Modifier
                    .offset { IntOffset(habi.left.roundToInt(), habi.top.roundToInt()) }
                    .size(with(density) { habi.width.toDp() }, with(density) { habi.height.toDp() })
                    .then(
                        if (respira == null) {
                            Modifier
                        } else {
                            // El llenado se lee aquí, en la fase de dibujo: respirar no recompone (riesgo 8).
                            Modifier.graphicsLayer {
                                val e = estiramientoRespiracion(respira(), reducido, piesHabi)
                                transformOrigin = TransformOrigin(0.5f, e.origenY)
                                scaleX = e.scaleX
                                scaleY = e.scaleY
                            }
                        },
                    ).graphicsLayer {
                        val a = aplastado.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = a
                        scaleX = 2f - a
                    },
            animated = animated,
            // Los toques de verdad los recoge la capa de delante; esto deja la reacción preparada
            // (y el toque de accesibilidad, que no pasa por la capa).
            onTap = onHabiTap,
            delighted = delighted,
            nudge = habiNudge,
            nudgeTouch = habiTouch,
            nudgeSalto = habiAmplitud,
            sondaSalto = sonda?.salto,
        )

        // La capa de delante recoge todos los toques: la caja del lienzo de Habi es mucho mayor que su
        // cuerpo y, si los recogiera Habi, taparía el farolillo. Un toque es bajar y subir sin pasar de
        // UMBRAL_ARRASTRE_DP (ni que el padre, el scroll, se quede el gesto); un arrastre corto no abre nada.
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(items, habiBody, globitoPlanta, reducido) {
                    val umbral = UMBRAL_ARRASTRE_DP.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var toque = true
                        while (true) {
                            val cambio = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (cambio == null || cambio.isConsumed || (cambio.position - down.position).getDistance() > umbral) {
                                toque = false
                                break
                            }
                            if (cambio.changedToUp()) {
                                cambio.consume()
                                break
                            }
                        }
                        if (!toque) return@awaitEachGesture
                        when (val action = onSceneTap(items, down.position.x, down.position.y, habiBody)) {
                            is SceneTap.Habi ->
                                currentOnHabiTap?.let { alTocarHabi ->
                                    habiTouch =
                                        Offset(
                                            ((action.x - habi.left) / habi.width).coerceIn(0f, 1f),
                                            ((action.y - habi.top) / habi.height).coerceIn(0f, 1f),
                                        )
                                    // Dormido (G17) solo se aplasta: el tick y el aviso los da quien recibe el toque.
                                    if (dormidoAhora) {
                                        aplastarHabiAhora()
                                    } else {
                                        run {
                                            habiAmplitud = null
                                            habiNudge++
                                        }
                                    }
                                    alTocarHabi()
                                }
                            is SceneTap.Sway -> {
                                gesto(Snd.PLANTA, Haptica.TICK)
                                mecer(action.plot)
                                if (globitoPlanta) globo = GlobitoDePlanta(action.plot, ++globoTick)
                            }
                            is SceneTap.Bubble -> {
                                gesto(Snd.PLANTA, Haptica.TICK)
                                aplastar(action.item.special.key)
                                bubble = action.item
                                bubbleTick++
                            }
                            SceneTap.Fondo -> currentAlTocarFondo?.invoke()
                        }
                    }
                },
        ) {
            if (!listo) return@Canvas
            val wilt = wiltAnim.value
            val tintesPlanta = tintes(wilt, garden.season, planta = true)
            items.filter { it.depth > habiDepth && it !in overLight }.forEach { drawItem(art, it, wilt, tintesPlanta, efectos) }
            val (top, bottom) = GardenLook.lightGradient(garden.light)
            drawRect(Brush.verticalGradient(listOf(Color(top), Color(bottom))), blendMode = BlendMode.Multiply)
            if (luzFiesta.value > 0f) drawRect(Color.White.copy(alpha = FIESTA_LUZ_MAX * luzFiesta.value), blendMode = BlendMode.Plus)
            // G40: el tinte de la noche, multiplicado encima de la luz del reloj. Con 0 no se pinta nada.
            val n = nocheAhora().coerceIn(0f, 1f)
            if (n > 0f) drawRect(Brush.verticalGradient(listOf(NOCHE_ARRIBA, NOCHE_ABAJO)), alpha = n, blendMode = BlendMode.Multiply)
            drawFiltracion(filtracion.value)
            overLight.forEach { drawItem(art, it, wilt, tintesPlanta, efectos) }
            val brillo = brilloFarolillo(GardenLook.nightness(garden.light), n)
            val lantern = GardenArt.specialSpots[GardenSpecial.LANTERN.key]
            if (GardenSpecial.LANTERN in garden.specials && lantern != null && brillo > 0f) drawLanternGlow(lantern, brillo)
        }

        bubble?.let { SpecialBubble(it, sceneW, conOrigen = globitoPlanta) }
        globo?.let { g ->
            val plant = items.firstOrNull { it is SceneItem.Plant && it.plot == g.plot }
            if (plant != null) {
                key(g.tick) {
                    // El globito se posa sobre su padre: una caja con el rectángulo de la planta.
                    Box(
                        Modifier
                            .offset { IntOffset(plant.rect.left.roundToInt(), plant.rect.top.roundToInt()) }
                            .size(with(density) { plant.rect.width.toDp() }, with(density) { plant.rect.height.toDp() }),
                    ) {
                        Globito(
                            visible = true,
                            texto = textoPlanta(garden, g.plot),
                            alIrse = { if (globo?.tick == g.tick) globo = null },
                        )
                    }
                }
            }
        }
    }
}

// Cada cuánto se recalculan las anclas de la escena (un fotograma a 60 Hz).
private const val ANCLAS_REFRESCO_MS = 16L
private const val PIEDRAS_MS = 720
private const val ASIENTA_MS = 420

/** La base de la parcela [plot] (su tierra, sin hundir) en píxeles de la escena: adonde llega la gota de lo que se marca. */
internal fun baseDeParcela(
    plot: Int,
    sceneW: Float,
    sceneH: Float,
): SceneRect {
    val spot = GardenArt.plotSpots[plot]
    val soil = GardenArt.plots[plot]
    return spotRect(spot, spot.heightPct * soil.heightRatio, soil.aspect, sceneW, sceneH)
}

/** La pieza de la etapa anterior de la parcela [creciendo], para fundirla mientras sube la nueva. Null en una parcela nueva. */
internal fun etapaVieja(
    garden: GardenState,
    creciendo: Int?,
): GardenPiece? {
    val plot = creciendo?.let { garden.plots.getOrNull(it) } ?: return null
    val anterior = plot.stage.ordinal - 1
    return if (anterior < 0) null else GardenArt.plants[plot.species.ordinal][anterior]
}

/** Lo que [GardenScene] pinta para [garden]: el fondo y la pieza de cada parcela y de cada especial, y la etapa vieja de [creciendo], a tamaño completo. */
internal fun recursosDelJardin(
    garden: GardenState,
    creciendo: Int? = null,
    objetos: List<ObjetoJardin> = emptyList(),
    arte: FuenteObjetosJardin = ArteObjetosJardin,
): List<Pair<Int, Int>> =
    (
        listOf(GardenArt.background) +
            sceneItems(garden, 1f, 1f, objetos, arte).map {
                it.res
            } + listOfNotNull(etapaVieja(garden, creciendo)?.res)
    )
        .distinct()
        .map { it to 1 }

@Composable
private fun textoPlanta(
    garden: GardenState,
    plot: Int,
): String {
    val parcela = garden.plots.getOrNull(plot) ?: return ""
    val etapa = parcela.stage.ordinal + 1
    return if (plot == parcelaQueCrece(garden)) {
        stringResource(R.string.jardin_crece_ahora, stringResource(nombreDeEspecie(parcela.species)), etapa)
    } else {
        stringResource(R.string.jardin_etapa, etapa)
    }
}

private fun nombreDeEspecie(species: GardenSpecies): Int =
    when (species) {
        GardenSpecies.ROUND_LEAVES -> R.string.base_especie_hojas_redondas
        GardenSpecies.SUNFLOWERS -> R.string.base_especie_girasoles
        GardenSpecies.LAVENDER -> R.string.base_especie_lavanda
        GardenSpecies.APPLE_TREE -> R.string.base_especie_manzano
        GardenSpecies.FERNS -> R.string.base_especie_helechos
        GardenSpecies.CHERRY_TREE -> R.string.base_especie_cerezo
    }

private fun nombreDeEspecial(special: GardenSpecial): Int =
    when (special) {
        GardenSpecial.LANTERN -> R.string.jardin_especial_farolillo
        GardenSpecial.STONE_PATH -> R.string.jardin_especial_caminito
        GardenSpecial.POND -> R.string.jardin_especial_estanque
        GardenSpecial.MUSHROOMS -> R.string.jardin_especial_setas
        GardenSpecial.BIG_CHERRY -> R.string.jardin_especial_cerezo_grande
        GardenSpecial.GOLDEN_TREE -> R.string.jardin_especial_arbol_dorado
    }

/**
 * Los tintes del mustio [wilt] (que al animarse es fraccionario) y la estación: lo mismo que
 * [GardenLook.plantTints] y [GardenLook.grassTints] con los valores de [GardenLookTable]. El mustio se
 * cuantiza en octavos para que la caché de filtros no crezca fotograma a fotograma.
 */
private fun tintes(
    wilt: Float,
    season: GardenSeason,
    planta: Boolean,
): List<ColorTint> {
    val w = (wilt * PASOS_MUSTIO).roundToInt() / PASOS_MUSTIO
    val mustio =
        if (w == 0f) {
            null
        } else if (planta) {
            ColorTint(
                saturation = 1f - GardenLookTable.PLANT_SATURATION_LOSS * w,
                brightness = 1f - GardenLookTable.PLANT_DIM * w,
                sepia = GardenLookTable.PLANT_SEPIA * w,
            )
        } else {
            ColorTint(
                saturation = 1f - GardenLookTable.GRASS_SATURATION_LOSS * w,
                brightness = 1f - GardenLookTable.GRASS_DIM * w,
                sepia = GardenLookTable.GRASS_SEPIA * w,
            )
        }
    return listOfNotNull(mustio, GardenLookTable.seasons[season.name])
}

/** La franja de agua que baja mientras anochece (G40): aparece en el primer 20 % y se apaga al llegar abajo. */
private fun DrawScope.drawFiltracion(p: Float) {
    if (p <= 0f || p >= 1f) return
    val franja = FRANJA_FILTRA.toPx()
    val y = -franja + (size.height + franja) * p
    val alfa = if (p < AGUA_FILTRA_LLENA) p / AGUA_FILTRA_LLENA else (1f - p) / (1f - AGUA_FILTRA_LLENA)
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            AGUA_FILTRA_PICO to AGUA_FILTRA.copy(alpha = AGUA_FILTRA_ALFA),
            1f to Color.Transparent,
            startY = y,
            endY = y + franja,
        ),
        topLeft = Offset(0f, y),
        size = Size(size.width, franja),
        alpha = alfa.coerceIn(0f, 1f),
    )
}

private fun DrawScope.drawLanternGlow(
    lantern: GardenSpot,
    night: Float,
) {
    val center =
        Offset(
            size.width * (lantern.xPct + GLOW_SHIFT_X_PCT) / 100f,
            size.height * (lantern.yPct - GLOW_RISE_PCT) / 100f,
        )
    val radiusX = size.width * GLOW_RADIUS_X
    val radiusY = size.height * GLOW_RADIUS_Y
    // Un degradado radial es circular: se pinta con el radio horizontal y se estira en vertical.
    withTransform({ scale(1f, radiusY / radiusX, center) }) {
        drawCircle(
            brush =
                Brush.radialGradient(
                    0f to GlowCore,
                    GLOW_MID_STOP to GlowMid,
                    1f to Color.Transparent,
                    center = center,
                    radius = radiusX,
                ),
            radius = radiusX,
            center = center,
            alpha = night.coerceIn(0f, 1f),
            blendMode = BlendMode.Screen,
        )
    }
}

private fun DrawScope.drawFill(
    image: ImageBitmap,
    filter: ColorFilter?,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        colorFilter = filter,
        filterQuality = FilterQuality.Medium,
    )
}

/**
 * Una pieza en su sitio con su pose de este fotograma: las plantas llevan el mustio del prototipo,
 * `scaleY(..) skewX(..)` con el origen en el pie (la copa se ladea a la derecha), y encima el vaivén
 * del toque. Los especiales llevan su llegada (G15) y su aplastón; la parcela que crece, la etapa que sube (G16).
 */
private fun DrawScope.drawItem(
    art: HabiArt,
    item: SceneItem,
    wilt: Float,
    plantTints: List<ColorTint>,
    fx: Efectos,
) {
    val image = art.image(item.res) ?: return
    val r = item.rect
    val base = r.top + r.height
    val pivot = Offset(r.left + r.width / 2f, base)
    val pose = Pose()
    val plant = item as? SceneItem.Plant
    val special = item as? SceneItem.Special
    val filtro = if (plant != null && plantTints.isNotEmpty()) art.filter(plantTints) else null
    var giro = 0f
    if (plant != null) {
        giro = fx.balanceo[plant.plot]?.value ?: 0f
        pose.brillo = fx.brillo["p${plant.plot}"]?.value ?: 0f
        val crece = fx.crece
        if (crece != null && plant.plot == fx.creciendo) {
            val p = crece.p.value
            if (fx.reducido) {
                pose.alpha = p.coerceIn(0f, 1f)
            } else {
                pose.dy = (1f - p) * r.height * CRECE_SUBE
                val e = CRECE_ESCALA_MIN + (1f - CRECE_ESCALA_MIN) * p
                pose.escalaX = e
                pose.escalaY = e
                pose.recorteAbajo = base
            }
            val vieja = fx.vieja
            val imagenVieja = vieja?.let { art.image(it.res) }
            if (vieja != null && imagenVieja != null && crece.fundido.value < 1f) {
                val spot = GardenArt.plotSpots[plant.plot]
                val rv = spotRect(spot, spot.heightPct * vieja.heightRatio, vieja.aspect, size.width, size.height)
                val viejaPose = Pose().apply { alpha = 1f - crece.fundido.value }
                drawPieza(imagenVieja, rv, true, wilt, filtro, giro, viejaPose)
            }
        }
    }
    if (special != null) {
        val key = special.special.key
        pose.brillo = fx.brillo[key]?.value ?: 0f
        fx.aplaste[key]?.value?.let { a ->
            pose.escalaY *= a
            pose.escalaX *= 1f + (1f - a) * APLASTE_ENSANCHA
        }
        val llegada = fx.llegada?.takeIf { it.especial == special.special }
        if (llegada != null) posarLlegada(llegada, r, base, fx.reducido, pose)
    }
    if (item is SceneItem.Objeto) drawSombraObjeto(r)
    drawPieza(image, r, plant != null, wilt, filtro, giro, pose)
    val llegada = fx.llegada
    if (special != null && llegada != null && llegada.especial == special.special && !fx.reducido) {
        when (special.special.llegada()) {
            Llegada.CAE -> drawPolvo(llegada.polvo.value, pivot, r, fx.polvo)
            Llegada.ASIENTA -> drawSalpicadura(llegada.p.value, r, fx.salpicadura)
            else -> Unit
        }
    }
}

// La sombra de un objeto de jardín (sin token), como `--sombra-objeto` del mock: una elipse del 110 % del ancho
// y el 18 % del ancho de alto, centrada en el pie, con un degradado radial de rgba(60,40,20,.30) a transparente.
private const val SOMBRA_OBJETO_ANCHO = 1.1f
private const val SOMBRA_OBJETO_ALTO = 0.18f
private val SOMBRA_OBJETO = Color(red = 60, green = 40, blue = 20, alpha = 77)

/** La sombra que cae bajo el pie de un objeto de jardín de caja [r]. */
private fun DrawScope.drawSombraObjeto(r: SceneRect) {
    val ancho = r.width * SOMBRA_OBJETO_ANCHO
    val centro = Offset(r.left + r.width / 2f, r.top + r.height)
    withTransform({ scale(1f, SOMBRA_OBJETO_ALTO / SOMBRA_OBJETO_ANCHO, pivot = centro) }) {
        drawCircle(
            brush = Brush.radialGradient(listOf(SOMBRA_OBJETO, SOMBRA_OBJETO.copy(alpha = 0f)), center = centro, radius = ancho / 2f),
            radius = ancho / 2f,
            center = centro,
        )
    }
}

/** La pose de un especial que llega (G15), según cómo llegue; reducido, solo se funde. */
private fun posarLlegada(
    llegada: AnimLlegada,
    r: SceneRect,
    base: Float,
    reducido: Boolean,
    pose: Pose,
) {
    val p = llegada.p.value
    if (reducido) {
        pose.alpha = p.coerceIn(0f, 1f)
        return
    }
    when (llegada.especial.llegada()) {
        Llegada.CAE -> {
            pose.dy = -(1f - p) * base
            val rebote = llegada.rebote.value
            pose.escalaY = rebote
            pose.escalaX = 1f + (1f - rebote) * APLASTE_ENSANCHA
        }
        Llegada.BROTA -> {
            pose.dy = (1f - p) * r.height
            pose.recorteAbajo = base
        }
        // De delante hacia el fondo: se descubre de abajo hacia arriba.
        Llegada.PIEDRAS -> pose.recorteArriba = r.top + r.height * (1f - p.coerceIn(0f, 1f))
        Llegada.ASIENTA -> {
            pose.alpha = p.coerceIn(0f, 1f)
            val e = ASIENTA_ESCALA_MIN + (1f - ASIENTA_ESCALA_MIN) * p
            pose.escalaX = e
            pose.escalaY = e
        }
    }
}

private const val ASIENTA_ESCALA_MIN = 0.9f

private fun DrawScope.drawPieza(
    image: ImageBitmap,
    r: SceneRect,
    planta: Boolean,
    wilt: Float,
    filtro: ColorFilter?,
    giro: Float,
    pose: Pose,
) {
    val pivot = Offset(r.left + r.width / 2f, r.top + r.height)
    withTransform({
        if (pose.recorteArriba > 0f || pose.recorteAbajo < size.height) {
            clipRect(top = pose.recorteArriba, bottom = pose.recorteAbajo.coerceAtMost(size.height))
        }
        if (pose.dy != 0f) translate(0f, pose.dy)
        if (planta && giro != 0f) rotate(giro, pivot)
        if (pose.escalaX != 1f || pose.escalaY != 1f) scale(pose.escalaX, pose.escalaY, pivot)
        if (planta) {
            // Orden CSS: el último se aplica primero, así que el punto se cizalla y luego encoge.
            scale(1f, 1f - GardenLookTable.PLANT_SHRINK * wilt, pivot)
            val shear = tan(Math.toRadians((-GardenLookTable.PLANT_LEAN_DEGREES * wilt).toDouble())).toFloat()
            if (shear != 0f) {
                translate(pivot.x, pivot.y)
                transform(Matrix().apply { values[Matrix.SkewX] = shear })
                translate(-pivot.x, -pivot.y)
            }
        }
        translate(r.left, r.top)
        scale(r.width / image.width, r.height / image.height, Offset.Zero)
    }) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(image.width, image.height),
            alpha = pose.alpha,
            colorFilter = filtro,
            filterQuality = FilterQuality.Medium,
        )
        if (pose.brillo > 0f) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(image.width, image.height),
                alpha = pose.brillo * BRILLO_ALFA,
                colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** El polvo de lo que cae (G15): cinco motas que se abren y se apagan en 420 ms. */
private fun DrawScope.drawPolvo(
    t: Float,
    pivot: Offset,
    r: SceneRect,
    color: Color,
) {
    if (t <= 0f || t >= 1f) return
    repeat(POLVO_MOTAS) { k ->
        val x = pivot.x + (k - POLVO_MOTAS / 2) * r.width * POLVO_ABRE * (0.4f + t)
        val y = pivot.y - t * r.height * 0.05f
        drawCircle(color.copy(alpha = (1f - t) * POLVO_ALFA), radius = r.width * POLVO_RADIO * (0.5f + t), center = Offset(x, y))
    }
}

/** La salpicadura del estanque que se asienta (G15): un anillo que se abre sobre el agua y se apaga. */
private fun DrawScope.drawSalpicadura(
    t: Float,
    r: SceneRect,
    color: Color,
) {
    if (t <= 0f || t >= 1f) return
    val ancho = r.width * (0.4f + 0.6f * t)
    val alto = ancho * 0.3f
    drawOval(
        color = color.copy(alpha = (1f - t) * SALPICA_ALFA),
        topLeft = Offset(r.left + r.width / 2f - ancho / 2f, r.top + r.height * 0.6f - alto / 2f),
        size = Size(ancho, alto),
        style = Stroke(width = 2.dp.toPx()),
    )
}

/**
 * El globito del especial tocado, centrado encima de él sin salirse de la escena: el nombre de su
 * logro y, con [conOrigen] (Camino), de qué especial es y con qué logro llegó.
 */
@Composable
private fun SpecialBubble(
    item: SceneItem.Special,
    sceneW: Float,
    conOrigen: Boolean = false,
) {
    val logro = stringResource(BadgeStrings.badgeNameRes(item.special.badgeId))
    val texto =
        if (conOrigen) stringResource(R.string.jardin_llego_con, stringResource(nombreDeEspecial(item.special)), logro) else logro
    Surface(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(placeable.width, placeable.height) {
                        val centered = item.rect.left + item.rect.width / 2f - placeable.width / 2f
                        val x = centered.coerceIn(0f, (sceneW - placeable.width).coerceAtLeast(0f))
                        val y = (item.rect.top - placeable.height).coerceAtLeast(0f)
                        placeable.place(x.roundToInt(), y.roundToInt())
                    }
                }
                .testTag("garden-bubble-${item.special.key}"),
        shape = RoundedCornerShape(12.dp),
        color = Tarjeta,
        border = BorderStroke(1.dp, Borde),
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            color = Tinta,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
