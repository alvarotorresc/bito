package com.alvarotc.bito.ui.inicio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.GardenLight
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.LightPhase
import com.alvarotc.bito.ui.garden.GardenArt
import com.alvarotc.bito.ui.garden.GardenLook
import com.alvarotc.bito.ui.garden.GardenScene
import com.alvarotc.bito.ui.garden.NOCHE_ABAJO
import com.alvarotc.bito.ui.garden.NOCHE_ARRIBA
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Escena
import com.alvarotc.bito.ui.sistema.Medidas
import kotlin.math.max
import kotlin.math.roundToInt

/** Las proporciones del jardín del Inicio (spec §5.2 y §7.1). */
internal object MedidasInicio {
    const val ALTO_HOY = 0.40f
    const val ALTO_MIN = 0.18f
    const val ALTO_CAMINO = 0.60f
    const val ESCALA_MIN = 0.88f

    /** Los pies de Habi, en la fracción del alto de la escena desde la que encoge. */
    const val PIES_HABI = 0.88f
    val ARRIBA_CAMINO = 150.dp
    val ASTRO = 46.dp
}

internal fun lerpF(
    a: Float,
    b: Float,
    t: Float,
): Float = a + (b - a) * t

/** El alto del jardín en Hoy: del 40 % al 18 % de [alto] según [encogido] (0 a 1). */
internal fun altoJardinHoy(
    alto: Float,
    encogido: Float,
): Float = lerpF(MedidasInicio.ALTO_HOY * alto, MedidasInicio.ALTO_MIN * alto, encogido)

/** El alto del jardín en Camino: el 60 % de [alto], y como mínimo el escenario más [extra] de cielo. */
internal fun altoJardinCamino(
    alto: Float,
    escenaAlto: Float,
    extra: Float,
): Float = max(MedidasInicio.ALTO_CAMINO * alto, escenaAlto + extra)

/** La y del borde de abajo del jardín: parte del alto de Hoy y llega al de Camino (menos lo que [subida] se lleva). */
internal fun bordeJardin(
    q: Float,
    encogido: Float,
    alto: Float,
    escenaAlto: Float,
    extra: Float,
    subida: Float,
): Float = lerpF(altoJardinHoy(alto, encogido), altoJardinCamino(alto, escenaAlto, extra) - subida, q)

/** La escala del escenario: en Hoy cubre el hueco (y encoge a 0,88 con [encogido]); en Camino mide 1. */
internal fun escalaJardin(
    q: Float,
    encogido: Float,
    alto: Float,
    escenaAlto: Float,
): Float {
    val cubrir = max(1f, MedidasInicio.ALTO_HOY * alto / escenaAlto)
    return lerpF(cubrir * lerpF(1f, MedidasInicio.ESCALA_MIN, encogido), 1f, q)
}

/** El degradado del cielo de Camino para [light]: entre el de `from` y el de `to` según la transición. */
internal fun cieloDe(light: GardenLight): Pair<Color, Color> {
    val a = paletaCielo(light.from)
    val b = paletaCielo(light.to)
    return lerp(a[0], b[0], light.transition) to lerp(a[1], b[1], light.transition)
}

private fun paletaCielo(fase: LightPhase): List<Color> =
    when (fase) {
        LightPhase.MANANA -> Escena.caminoManana
        LightPhase.DIA -> Escena.caminoDia
        LightPhase.TARDE -> Escena.caminoTarde
        LightPhase.NOCHE -> Escena.caminoNoche
    }

/** La luz que se multiplica sobre el cielo: cálida de mañana y de tarde, ninguna de día y de noche. */
internal fun luzDe(light: GardenLight): Color = lerp(luzDeFase(light.from), luzDeFase(light.to), light.transition)

private fun luzDeFase(fase: LightPhase): Color =
    when (fase) {
        LightPhase.MANANA -> Escena.caminoMananaLuz
        LightPhase.TARDE -> Escena.caminoTardeLuz
        else -> Color.Transparent
    }

/** El astro: cuánto sol y cuánta luna se ven, y su sitio como fracción del ancho y del cielo. */
internal data class Astro(
    val sol: Float,
    val luna: Float,
    val x: Float,
    val y: Float,
)

internal fun astroDe(light: GardenLight): Astro {
    val (x0, y0) = sitioAstro(light.from)
    val (x1, y1) = sitioAstro(light.to)
    val noche = GardenLook.nightness(light).coerceIn(0f, 1f)
    return Astro(sol = 1f - noche, luna = noche, x = lerpF(x0, x1, light.transition), y = lerpF(y0, y1, light.transition))
}

private fun sitioAstro(fase: LightPhase): Pair<Float, Float> =
    when (fase) {
        LightPhase.MANANA -> 0.22f to 0.62f
        LightPhase.DIA -> 0.80f to 0.28f
        LightPhase.TARDE -> 0.86f to 0.66f
        LightPhase.NOCHE -> 0.26f to 0.34f
    }

/** El jardín de Hoy va a sangre: su borde de abajo es recto contra la costra, sin las esquinas de la tarjeta. */
internal val FormaJardinHoy: Shape = RectangleShape

/**
 * El jardín del Inicio: una sola escena que sirve a Hoy y a Camino. [q] (0 Hoy, 1 Camino) y [encogido]
 * (0 a 1, lo que se ha encogido al bajar la lista) se leen solo al dibujar, así que moverlos no recompone.
 * La caja que lo contiene ocupa todo el alto del Inicio; fuera de la escena no recoge toques.
 * [subida] son los píxeles que Camino ha subido el jardín al desplazar sus estadísticas.
 */
@Composable
fun JardinCompartido(
    garden: GardenState,
    spec: HabiSpec,
    q: () -> Float,
    encogido: () -> Float,
    modifier: Modifier = Modifier,
    contento: Boolean = false,
    animado: Boolean = true,
    alTocarHabi: () -> Unit = {},
    alTocarFondo: () -> Unit = {},
    llegando: GardenSpecial? = null,
    creciendo: Int? = null,
    destacar: GardenSpecial? = null,
    saltoHabi: Int = 0,
    subida: () -> Float = { 0f },
    fiesta: Int = 0,
    // 0 de día, 1 de noche: se pasa tal cual a la escena y oscurece también el cielo bajo la cabecera.
    noche: () -> Float = { 0f },
    // Cada incremento, Habi se aplasta para dormirse («Hasta mañana»).
    aplastarHabi: Int = 0,
) {
    val densidad = LocalDensity.current
    val colores = BitoTema.colores
    val luz by rememberUpdatedState(garden.light)
    BoxWithConstraints(modifier) {
        val alto = constraints.maxHeight.toFloat()
        val ancho = constraints.maxWidth.toFloat()
        val escenaAlto = ancho / GardenArt.SCENE_ASPECT
        val extra = with(densidad) { MedidasInicio.ARRIBA_CAMINO.toPx() }
        val altoCamino = altoJardinCamino(alto, escenaAlto, extra)
        val costra = with(densidad) { Medidas.costra.toPx() }
        val astro = with(densidad) { MedidasInicio.ASTRO.toPx() }
        val borde = { bordeJardin(q(), encogido(), alto, escenaAlto, extra, subida()) }

        // Mide el borde de abajo del jardín sin pintar nada: lo miran los tests.
        Box(
            Modifier
                .testTag("jardin-alto")
                .layout { medible, c ->
                    val p = medible.measure(Constraints.fixed(0, 0))
                    layout(c.maxWidth, borde().roundToInt().coerceAtLeast(0)) { p.place(0, 0) }
                },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(densidad) { altoCamino.toDp() })
                .drawWithContent {
                    val b = borde()
                    clipRect(bottom = b) { this@drawWithContent.drawContent() }
                    drawRect(colores.costra, topLeft = Offset(0f, b), size = Size(size.width, costra))
                }.drawBehind {
                    val qq = q()
                    if (qq <= 0f) return@drawBehind
                    val b = borde()
                    val (arriba, abajo) = cieloDe(luz)
                    val inicio = b - altoCamino
                    val cielo = Brush.verticalGradient(listOf(arriba, abajo), startY = inicio, endY = b)
                    drawRect(cielo, size = Size(size.width, b), alpha = qq)
                    val tinte = luzDe(luz)
                    if (tinte.alpha > 0f) drawRect(tinte, size = Size(size.width, b), alpha = qq, blendMode = BlendMode.Multiply)
                    // El anochecer del cierre (G40) también oscurece este cielo, con el mismo tinte que la escena.
                    val n = noche().coerceIn(0f, 1f)
                    if (n > 0f) {
                        val oscuro = Brush.verticalGradient(listOf(NOCHE_ARRIBA, NOCHE_ABAJO), startY = inicio, endY = b)
                        drawRect(oscuro, size = Size(size.width, b), alpha = qq * n, blendMode = BlendMode.Multiply)
                    }
                    val a = astroDe(luz)
                    val centro = Offset(size.width * a.x, inicio + (altoCamino - escenaAlto) * a.y)
                    if (a.sol > 0.01f) {
                        val halo = Brush.radialGradient(listOf(Escena.sol.copy(alpha = 0.35f), Color.Transparent), centro, astro * 1.4f)
                        drawCircle(halo, astro * 1.4f, centro, alpha = qq * a.sol)
                        drawCircle(Escena.sol, astro / 2f, centro, alpha = qq * a.sol)
                    }
                    if (a.luna > 0.01f) {
                        val disco = Path().apply { addOval(Rect(centro, astro / 2f)) }
                        val mordisco = Path().apply { addOval(Rect(centro + Offset(astro * 0.28f, -astro * 0.12f), astro * 0.42f)) }
                        drawPath(Path.combine(PathOperation.Difference, disco, mordisco), Escena.luna, alpha = qq * a.luna)
                    }
                },
        ) {
            val pieY = MedidasInicio.PIES_HABI
            GardenScene(
                garden,
                spec,
                Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        val s = escalaJardin(q(), encogido(), alto, escenaAlto)
                        val pivote = pieY * escenaAlto
                        transformOrigin = TransformOrigin(0.5f, pieY)
                        scaleX = s
                        scaleY = s
                        // El pie de la escena queda en el borde de abajo del jardín.
                        val fondo = altoCamino - escenaAlto + pivote + (escenaAlto - pivote) * s
                        translationY = borde() - fondo
                    },
                delighted = contento,
                onHabiTap = alTocarHabi,
                animated = animado,
                forma = FormaJardinHoy,
                alTocarFondo = alTocarFondo,
                llegando = llegando,
                creciendo = creciendo,
                destacar = destacar,
                saltoHabi = saltoHabi,
                fiesta = fiesta,
                noche = noche,
                aplastarHabi = aplastarHabi,
            )
        }
    }
}
