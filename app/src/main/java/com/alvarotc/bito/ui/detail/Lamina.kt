package com.alvarotc.bito.ui.detail

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.PlantaFicha
import com.alvarotc.bito.domain.WindowStats
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.garden.GardenArt
import com.alvarotc.bito.ui.habi.HabiArt
import com.alvarotc.bito.ui.habi.rememberArte
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.CifraRodillo
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.today.CardKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Medidas del prototipo (13-mezcla/index.html `drawLamina`), en dp sobre una lámina de 412 de ancho.
// Tiempos de la lámina que el catálogo (movimiento.md 2.1 y 2.2, G16) fija y `Duraciones` no trae.
private const val PIN_MS = 160
private const val DATO_MS = 220
private const val FUNDIDO_ETAPA_MS = 260
private const val ASENTAR_MS = 900
private const val ALTO_LAMINA = 340f
private const val ALTO_SUELO = 34f
private const val ANCHO_TOPE_PLANTA = 236f
private const val CENTRO_PLANTA = 130f
private const val ANCHO_DATO = 132f
private const val ANCHO_REFERENCIA = 412f
private const val HUECO_DATOS = 146f
private const val SEPARACION_PUNTA = 8f
private const val MARGEN_PLANTA = 24f
private const val LARGO_PUNTA = 9f
private const val ABERTURA_PUNTA = 0.5f

/** Alto de cada dato, de arriba: mejor racha, porcentaje y racha actual. */
private val DATOS_Y = floatArrayOf(26f, 112f, 200f)
private val DATOS_FIN_DY = floatArrayOf(16f, 16f, 24f)
private val CURVAS = floatArrayOf(0.35f, -0.15f, -0.35f)
private val ANCLAS_POR_DEFECTO = floatArrayOf(0.8f, 0.2f, 0.85f, 0.45f, 0.5f, 0.7f)

/**
 * El tamaño de la planta grande: alto `min(280, 140 + 28·n)` con `n` la etapa de 1 a 5 (SEED da 168 dp y TREE
 * llega al tope de 280). Si queda más ancha que [anchoMax], se escala por el ancho y mantiene la proporción.
 */
internal fun tamanoPlanta(
    etapa: GrowthStage,
    aspecto: Float,
    anchoMax: Dp = ANCHO_TOPE_PLANTA.dp,
): DpSize {
    var alto = min(280, 140 + 28 * (etapa.ordinal + 1)).toFloat()
    var ancho = alto * aspecto
    if (ancho > anchoMax.value) {
        ancho = anchoMax.value
        alto = ancho / aspecto
    }
    return DpSize(ancho.dp, alto.dp)
}

/** Un texto de la lámina: el recurso y, si es un plural, la cantidad. */
internal data class EtiquetaRacha(
    val res: Int,
    val plural: Boolean,
    val n: Int,
)

internal data class TextosRacha(
    val actual: EtiquetaRacha,
    val mejor: EtiquetaRacha,
)

/**
 * Lo que dicen los dos datos de racha: la unidad de la actual (días, semanas o meses seguidos; «días limpios»
 * en abstinencia; con racha 0, «hoy empieza otra») y si la mejor es la de ahora (solo si pasa de 2).
 */
internal fun unidadRacha(
    periodo: Period,
    kind: CardKind,
    n: Int,
    esMejor: Boolean,
): TextosRacha {
    val unidad =
        when {
            kind == CardKind.ABSTINENCE -> R.plurals.ficha_dias_limpios
            periodo == Period.WEEK -> R.plurals.ficha_semanas_seguidas
            periodo == Period.MONTH -> R.plurals.ficha_meses_seguidos
            else -> R.plurals.ficha_dias_seguidos
        }
    val actual =
        if (n == 0) {
            EtiquetaRacha(R.string.ficha_hoy_empieza_otra, plural = false, n = 0)
        } else {
            EtiquetaRacha(unidad, plural = true, n = n)
        }
    val mejorRes = if (esMejor && n > 2) R.string.ficha_mejor_ahora else R.string.ficha_mejor_racha
    return TextosRacha(actual, EtiquetaRacha(mejorRes, plural = false, n = 0))
}

@Composable
private fun EtiquetaRacha.texto(): String = if (plural) pluralStringResource(res, n) else stringResource(res)

/** Una flecha calculada en dp de la lámina: el punto de la planta, el control de la curva, el dato y las dos aristas de la punta. */
internal data class FlechaLamina(
    val x1: Float,
    val y1: Float,
    val mx: Float,
    val my: Float,
    val x2: Float,
    val y2: Float,
    val puntaA: Offset,
    val puntaB: Offset,
)

/** Las tres flechas desde los anclajes de la planta (en fracciones de su rectángulo) hasta cada dato. */
internal fun flechasLamina(
    plantaX: Float,
    plantaY: Float,
    plantaAncho: Float,
    plantaAlto: Float,
    anclas: FloatArray,
    xFin: Float,
): List<FlechaLamina> =
    List(3) { i ->
        val x1 = plantaX + plantaAncho * anclas[i * 2]
        val y1 = plantaY + plantaAlto * anclas[i * 2 + 1]
        val y2 = DATOS_Y[i] + DATOS_FIN_DY[i]
        val curva = CURVAS[i]
        val mx = (x1 + xFin) / 2f + curva * (y2 - y1) * 0.3f
        val my = (y1 + y2) / 2f - curva * (xFin - x1) * 0.3f
        val ang = atan2(y2 - my, xFin - mx)
        FlechaLamina(
            x1,
            y1,
            mx,
            my,
            xFin,
            y2,
            puntaA = Offset(xFin - LARGO_PUNTA * cos(ang - ABERTURA_PUNTA), y2 - LARGO_PUNTA * sin(ang - ABERTURA_PUNTA)),
            puntaB = Offset(xFin - LARGO_PUNTA * cos(ang + ABERTURA_PUNTA), y2 - LARGO_PUNTA * sin(ang + ABERTURA_PUNTA)),
        )
    }

/** Los trazos de una flecha en píxeles, con su largo medido una sola vez. */
private class FlechaTrazada(
    val linea: Path,
    val punta: Path,
    val largoLinea: Float,
    val largoPunta: Float,
    val pin: Offset,
)

private fun FlechaLamina.trazar(
    densidad: Float,
    medida: PathMeasure,
): FlechaTrazada {
    val linea =
        Path().apply {
            moveTo(x1 * densidad, y1 * densidad)
            quadraticTo(mx * densidad, my * densidad, x2 * densidad, y2 * densidad)
        }
    val punta =
        Path().apply {
            moveTo(puntaA.x * densidad, puntaA.y * densidad)
            lineTo(x2 * densidad, y2 * densidad)
            lineTo(puntaB.x * densidad, puntaB.y * densidad)
        }
    medida.setPath(linea, false)
    val largoLinea = medida.length
    medida.setPath(punta, false)
    return FlechaTrazada(linea, punta, largoLinea, medida.length, Offset(x1 * densidad, y1 * densidad))
}

/** El avance de cada flecha: el punto, la línea, la punta y el dato que llega (movimiento.md 2.2). */
private class AvanceFlecha {
    val pin = Animatable(0f)
    val linea = Animatable(0f)
    val punta = Animatable(0f)
    val dato = Animatable(0f)
}

private val Asentar = CubicBezierEasing(0.33f, 0f, 0.15f, 1f)

/**
 * La lámina de herbario de la ficha: la planta del hábito a la izquierda, posada sobre la costra, y tres datos
 * a la derecha con una flecha cada uno que sale de la planta (el fruto lleva a la mejor racha, el follaje al
 * porcentaje de 30 días y el tronco a la racha actual). Tocar la planta la mece (G20, 70 °/s).
 */
@Composable
fun Lamina(
    planta: PlantaFicha,
    mejor: Int,
    pct30: WindowStats,
    actual: Int,
    periodo: Period,
    kind: CardKind,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val densidad = LocalDensity.current.density
    val gesto = rememberGesto()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val art = remember(context) { HabiArt.get(context) }
    val pieza = GardenArt.plants[planta.especie.ordinal][planta.etapa.ordinal]
    rememberArte(art, listOf(pieza.res to 1))

    val textos = unidadRacha(periodo, kind, actual, esMejor = actual == mejor)
    val pct = pct30.percent ?: -1
    val avances = remember { List(3) { AvanceFlecha() } }
    val aparicion = remember { Animatable(if (reducido) 0f else 1f) }
    val giro = remember { Animatable(0f) }
    val brillo = remember { Animatable(0f) }

    // Al entrar: la planta se asienta (-1,5° a 0,8° a 0) y cada dato sale de su hoja, uno tras otro.
    LaunchedEffect(reducido) {
        if (reducido) {
            avances.forEach {
                it.pin.snapTo(1f)
                it.linea.snapTo(1f)
                it.punta.snapTo(1f)
                it.dato.snapTo(1f)
            }
            aparicion.snapTo(0f)
            aparicion.animateTo(1f, tween(Duraciones.REDUCIDO))
        } else {
            launch {
                delay(80)
                giro.snapTo(-1.5f)
                giro.animateTo(
                    0f,
                    keyframes {
                        durationMillis = ASENTAR_MS
                        0.8f at ASENTAR_MS / 2 using Asentar
                        0f at ASENTAR_MS using Asentar
                    },
                )
            }
            avances.forEachIndexed { i, a ->
                val d0 = 120L + i * Duraciones.FLECHA_ESCALON
                launch {
                    delay(d0)
                    a.pin.animateTo(1f, tween(PIN_MS, easing = Curvas.out))
                }
                launch {
                    delay(d0 + 60)
                    a.linea.animateTo(1f, tween(Duraciones.FLECHA_LINEA, easing = Curvas.out))
                }
                launch {
                    delay(d0 + 300)
                    a.punta.animateTo(1f, tween(Duraciones.FLECHA_PUNTA, easing = Curvas.out))
                }
                launch {
                    delay(d0 + 300)
                    a.dato.animateTo(1f, tween(DATO_MS, easing = Curvas.out))
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(ALTO_LAMINA.dp).background(colores.cielo).testTag("lamina")) {
        val datosX = maxWidth.value - HUECO_DATOS
        val xFin = datosX - SEPARACION_PUNTA
        val anchoMax = min(ANCHO_TOPE_PLANTA, datosX - MARGEN_PLANTA).dp
        val tamano = tamanoPlanta(planta.etapa, pieza.aspect, anchoMax)
        val suelo = ALTO_LAMINA - ALTO_SUELO
        val centro = CENTRO_PLANTA * datosX / (ANCHO_REFERENCIA - HUECO_DATOS)
        val izquierda = maxOf(8f, centro - tamano.width.value / 2f)
        val arriba = suelo + tamano.height.value * 0.03f - tamano.height.value
        val anclas = AnclasPlanta.tabla["${planta.especie.key}-${planta.etapa.ordinal + 1}"] ?: ANCLAS_POR_DEFECTO
        val trazadas =
            remember(izquierda, arriba, tamano, anclas, xFin, densidad) {
                val medida = PathMeasure()
                flechasLamina(izquierda, arriba, tamano.width.value, tamano.height.value, anclas, xFin)
                    .map { it.trazar(densidad, medida) }
            }

        // El suelo: la costra de 6 dp y, debajo, la tierra del calendario.
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(Medidas.costra).background(colores.costra))
            Box(Modifier.fillMaxWidth().height(ALTO_SUELO.dp - Medidas.costra).background(colores.tAlta2))
        }

        val descripcion = stringResource(R.string.ficha_planta_cd)
        val fuente = remember { MutableInteractionSource() }
        Box(
            Modifier
                .offset(izquierda.dp, arriba.dp)
                .size(tamano.width, tamano.height)
                .graphicsLayer {
                    rotationZ = giro.value
                    alpha = 1f - 0.3f * brillo.value
                    transformOrigin = TransformOrigin(0.5f, 0.96f)
                }.testTag("lamina-planta")
                .semantics { contentDescription = descripcion }
                .clickable(interactionSource = fuente, indication = null, role = Role.Button) {
                    gesto(Snd.PLANTA, Haptica.TICK)
                    scope.launch {
                        if (reducido) {
                            brillo.animateTo(1f, tween(Duraciones.REDUCIDO))
                            brillo.animateTo(0f, tween(Duraciones.REDUCIDO))
                        } else {
                            giro.snapTo(0f)
                            giro.animateTo(0f, Muelles.planta(), initialVelocity = 70f)
                        }
                    }
                },
        ) {
            // G16: la etapa cambia con un fundido entre imágenes, sin salto.
            Crossfade(
                targetState = planta.especie to planta.etapa,
                animationSpec = tween(FUNDIDO_ETAPA_MS),
                label = "lamina-etapa",
            ) { (especie, etapa) ->
                val p = GardenArt.plants[especie.ordinal][etapa.ordinal]
                val t = tamanoPlanta(etapa, p.aspect, anchoMax)
                Box(Modifier.fillMaxSize().wrapContentSize(Alignment.BottomCenter, unbounded = true).size(t.width, t.height)) {
                    ImagenPlanta(art, p.res, recortarHoja = especie == GardenSpecies.APPLE_TREE && etapa == GrowthStage.TREE)
                }
            }
        }

        // Las flechas: nacen en la planta y apuntan al dato.
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = aparicion.value }) {
            val dst = Path()
            val medida = PathMeasure()
            val halo = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val tinta = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            trazadas.forEachIndexed { i, f ->
                val a = avances[i]
                if (a.linea.value > 0f) {
                    medida.setPath(f.linea, false)
                    dst.rewind()
                    medida.getSegment(0f, f.largoLinea * a.linea.value, dst, true)
                    drawPath(dst, colores.canto, style = halo)
                    drawPath(dst, colores.contorno, style = tinta)
                }
                if (a.punta.value > 0f) {
                    medida.setPath(f.punta, false)
                    dst.rewind()
                    medida.getSegment(0f, f.largoPunta * a.punta.value, dst, true)
                    drawPath(dst, colores.canto, style = halo)
                    drawPath(dst, colores.contorno, style = tinta)
                }
                if (a.pin.value > 0f) {
                    val r = 3.5.dp.toPx() * (0.6f + 0.4f * a.pin.value)
                    drawCircle(colores.canto, r, f.pin, alpha = a.pin.value)
                    drawCircle(colores.contorno, r, f.pin, alpha = a.pin.value, style = Stroke(1.5.dp.toPx()))
                }
            }
        }

        val normal = BitoTema.tipos.t32.fuerte().copy(fontFeatureSettings = "tnum", letterSpacing = (-0.01).sp, lineHeight = 34.sp)
        val grande = BitoTema.tipos.t48.cifra().copy(letterSpacing = (-0.02).sp, lineHeight = 50.sp)
        Dato(0, datosX, mejor, { "$it" }, normal, textos.mejor.texto(), avances[0])
        Dato(1, datosX, pct, { if (it < 0) "—" else "$it %" }, normal, stringResource(R.string.ficha_ultimos_30), avances[1])
        Dato(2, datosX, actual, { "$it" }, grande, textos.actual.texto(), avances[2])
    }
}

@Composable
private fun ImagenPlanta(
    art: HabiArt,
    res: Int,
    recortarHoja: Boolean,
) {
    Canvas(Modifier.fillMaxSize()) {
        val imagen = art.image(res) ?: return@Canvas
        val dibujar = {
            drawImage(
                image = imagen,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(imagen.width, imagen.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
        if (recortarHoja) {
            // El arte del manzano trae una hoja suelta pegada al borde izquierdo: se recorta como en el prototipo.
            val w = size.width
            val h = size.height
            val recorte =
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h)
                    lineTo(w * 0.035f, h)
                    lineTo(w * 0.035f, h * 0.58f)
                    lineTo(0f, h * 0.58f)
                    close()
                }
            clipPath(recorte) { dibujar() }
        } else {
            dibujar()
        }
    }
}

@Composable
private fun Dato(
    indice: Int,
    x: Float,
    valor: Int,
    formato: (Int) -> String,
    estilo: TextStyle,
    etiqueta: String,
    avance: AvanceFlecha,
) {
    val colores = BitoTema.colores
    val dx = with(LocalDensity.current) { (-8).dp.toPx() }
    Column(
        Modifier
            .offset(x.dp, DATOS_Y[indice].dp)
            .width(ANCHO_DATO.dp)
            .graphicsLayer {
                alpha = avance.dato.value
                translationX = (1f - avance.dato.value) * dx
            }.testTag("lamina-dato-$indice")
            .semantics(mergeDescendants = true) {},
    ) {
        CifraRodillo(valor, Modifier.clipToBounds(), estilo = estilo, color = colores.tinta, formato = formato)
        Text(
            etiqueta,
            Modifier.offset(y = 4.dp),
            style = BitoTema.tipos.t14.copy(fontWeight = FontWeight.Medium),
            color = colores.tintaSuave,
        )
    }
}
