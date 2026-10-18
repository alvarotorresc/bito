package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiArt
import com.alvarotc.bito.ui.habi.HabiAtlas
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiLayer
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.drawHabiLayers
import com.alvarotc.bito.ui.habi.recursosDe
import com.alvarotc.bito.ui.habi.rememberArte
import com.alvarotc.bito.ui.habi.sampleSizeFor
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import com.alvarotc.bito.ui.tienda.EscenaArmario
import com.alvarotc.bito.ui.tienda.EscenaArmarioEstado
import com.alvarotc.bito.ui.tienda.MedidasArmario
import com.alvarotc.bito.ui.tienda.PruebaTienda

/** Lo último que se vio, para saber qué ha cambiado al llegar un estado nuevo. */
private class Visto(
    var look: EquippedSet,
    var habiaPrueba: Boolean,
)

/**
 * El armario de la pestaña Habi: lo que lleva y lo que se prueba, con la pieza cayendo, brotando o
 * fundiéndose en el momento en que llega. [look] ya trae la prueba puesta; aquí se decide cómo aparece.
 */
@Composable
internal fun ArmarioHabi(
    look: EquippedSet,
    prueba: PruebaTienda?,
    armario: EscenaArmarioEstado,
    luz: () -> Float,
    sonido: MotorSonido,
    tacto: Tacto,
    modifier: Modifier = Modifier,
    animacion: AnimacionPrueba = remember { AnimacionPrueba() },
) {
    val reducido = BitoTema.reducido
    val visto = remember { Visto(look, prueba != null) }
    // Se calcula en composición: el primer fotograma del cambio ya es el de salida, sin parpadeo.
    val cambio =
        remember(look, reducido) {
            CambioArmario(visto.look, look, clasificarCambio(visto.look, look, visto.habiaPrueba, prueba != null), reducido)
        }
    SideEffect {
        visto.look = look
        visto.habiaPrueba = prueba != null
        animacion.cambio = cambio
    }
    LaunchedEffect(cambio) { cambio.reproducir(armario, sonido, tacto) }
    Box(modifier) {
        EscenaArmario(
            look = cambio.base,
            luz = luz,
            estado = armario,
            capaSuperior = { CapasDelCambio(cambio, armario) },
            conCostra = false,
        )
        // Fuera del recorte de la hornacina, como la capa `fx` del mock: los granos caen sobre la costra.
        if (cambio.granos > 0 && !cambio.listo) GranosDeTierra(cambio, Modifier.matchParentSize())
    }
}

/** Sobre Habi: la Habi que se funde y la pieza que cae o brota, con el mismo aplastamiento que ella. */
@Composable
private fun BoxScope.CapasDelCambio(
    cambio: CambioArmario,
    armario: EscenaArmarioEstado,
) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val caja = MedidasArmario.cajaHabi(maxWidth.value, maxHeight.value)
        // Los pies, como en el armario: el cuerpo termina a esta fracción del lienzo.
        val pies = (HabiAtlas.BODY_HEIGHT - HabiAtlas.canvas.top) / HabiAtlas.canvas.height
        val densidad = LocalDensity.current
        val piesPx = with(densidad) { caja.height.dp.toPx() } * pies
        // El suelo del recorte del brote, 156 unidades del lienzo por debajo de los pies.
        val sueloPx = with(densidad) { caja.height.dp.toPx() } * (pies + SUELO_BAJO_PIES / HabiAtlas.canvas.height)
        cambio.dpPorUnidad = caja.height / HabiAtlas.canvas.height
        val marco =
            Modifier
                .offset(caja.left.dp, caja.top.dp)
                .size(caja.width.dp, caja.height.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, pies)
                    scaleY = armario.escalaY
                    scaleX = 1f + (1f - armario.escalaY) * ENSANCHA
                }
        cambio.fundida?.let { otra ->
            Box(marco.graphicsLayer { alpha = cambio.fundido.coerceIn(0f, 1f) }) {
                HabiAvatar(spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, otra), modifier = Modifier.fillMaxSize())
            }
        }
        val id = cambio.itemId
        if (!cambio.listo && id != null && (cambio.gesto == GestoPrueba.CAE || cambio.gesto == GestoPrueba.BROTA)) {
            val categoria = requireNotNull(HabiCatalog.byId(id)).category
            val recorte = if (cambio.gesto == GestoPrueba.BROTA) sueloPx else Float.MAX_VALUE
            Box(
                marco.drawWithContent { clipRect(bottom = recorte) { this@drawWithContent.drawContent() } },
            ) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationY = (-cambio.caidaDp + cambio.reboteDp + cambio.brotarDp).dp.toPx()
                        alpha = cambio.alfaPieza
                    },
                ) {
                    CapaSuelta(HabiLayer(id, parteDe(categoria)), caja.height, Modifier.fillMaxSize())
                }
            }
        }
    }
}

// `squash(a)` del mock estira un 80 % de lo que aplasta (igual que el armario).
private const val ENSANCHA = 0.8f

/** Una pieza del atlas sola, en el mismo marco y a la misma escala que Habi (de [altoDp] de alto). */
@Composable
private fun CapaSuelta(
    capa: HabiLayer,
    altoDp: Float,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val art = remember(context) { HabiArt.get(context) }
    val capas = remember(capa) { listOf(capa) }
    val alto = with(LocalDensity.current) { altoDp.dp.toPx() }
    val sample = if (alto > 0f) sampleSizeFor(alto, altoDp) else 0
    val listo = rememberArte(art, if (sample > 0) recursosDe(capas, sample) else emptyList())
    Canvas(modifier) {
        if (listo && sample > 0) drawHabiLayers(art, capas, sample)
    }
}

/** Los diez granos que salta la tierra al brotar la pieza: salen del suelo, se abren y se apagan (ver [GranosBrote]). */
@Composable
private fun GranosDeTierra(
    cambio: CambioArmario,
    modifier: Modifier,
) {
    val color = BitoTema.colores.tMedia2
    BoxWithConstraints(modifier) {
        // La hornacina dentro de la escena, y Habi dentro de la hornacina: los mismos números que `EscenaArmario`.
        val nicho = MedidasArmario.cajaHabi(maxWidth.value - 2 * MedidasArmario.nichoLados.value, Medidas.hornacinaArmario.value)
        val pies = (HabiAtlas.BODY_HEIGHT - HabiAtlas.canvas.top) / HabiAtlas.canvas.height
        val dpPorUnidad = nicho.height / HabiAtlas.canvas.height
        val x0 = MedidasArmario.nichoLados.value + nicho.left + nicho.width / 2f
        val piesY = MedidasArmario.nichoArriba.value + nicho.top + nicho.height * pies
        val y0 = GranosBrote.origenY(piesY, dpPorUnidad)
        Canvas(Modifier.matchParentSize()) {
            val t = cambio.progresoGranos.coerceIn(0f, 1f) * GranosBrote.TOTAL_MS
            val ancho = GranosBrote.ancho.toPx()
            val alto = GranosBrote.alto.toPx()
            repeat(GranosBrote.N) { i ->
                val p = GranosBrote.progreso(i, t)
                if (p <= 0f || p >= 1f) return@repeat
                drawRoundRect(
                    color = color,
                    topLeft = Offset(GranosBrote.x(i, p, x0).dp.toPx() - ancho / 2f, GranosBrote.y(i, p, y0).dp.toPx() - alto / 2f),
                    size = Size(ancho, alto),
                    cornerRadius = CornerRadius(ancho * GranosBrote.REDONDEO, alto * GranosBrote.REDONDEO),
                    alpha = GranosBrote.alfa(p),
                )
            }
        }
    }
}
