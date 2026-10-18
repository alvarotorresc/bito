package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.CifraRodillo

/** En una fila caben 7 copitos de 44 dp con 8 dp de hueco (356 de los 372 de la veta). */
internal const val COPITOS_CABEN = 7

private val DIAMETRO = Medidas.toqueMinimo
private val HUECO = Medidas.e8
private val COPO = 26.dp // (sin token) tamaño del copo dentro del círculo
private val AIRE_ARRIBA = 10.dp
private val AIRE_ABAJO = 16.dp

/** Qué se pinta para [total] congeladores: [copos] círculos y, si no caben todos, una pastilla con [mas]. */
internal data class PlanCopitos(
    val copos: Int,
    val mas: Int?,
)

internal fun planCopitos(total: Int): PlanCopitos =
    when {
        total <= COPITOS_CABEN -> PlanCopitos(total.coerceAtLeast(0), null)
        else -> PlanCopitos(COPITOS_CABEN - 1, total - (COPITOS_CABEN - 1))
    }

// (sin token) `copoEn` de tienda-congeladores.js: el copito nuevo sube 16 dp desde 0,9, girando desde -30 grados.
private val SUBIDA_ENTRADA = 16.dp
private const val ESCALA_ENTRADA = 0.9f
private const val GIRO_ENTRADA = -30f
private const val DURACION_ENTRADA = 320
private const val DURACION_SALIDA = 150

/** Dónde está cada hueco de la fila, en coordenadas de la raíz, para que la moneda sepa adónde ir. */
class FilaCopitos {
    private var origen = Offset.Zero
    private var alto = 0f

    internal fun fijar(
        posicion: Offset,
        altoPx: Int,
    ) {
        origen = posicion
        alto = altoPx.toFloat()
    }

    /** El centro del hueco [indice] (0 es el primero), a una densidad de [densidad] píxeles por dp. */
    fun centro(
        indice: Int,
        densidad: Float,
    ): Offset = Offset(origen.x + (indice * (DIAMETRO + HUECO).value + DIAMETRO.value / 2f) * densidad, origen.y + alto / 2f)
}

/** El hueco donde aterriza el copito número [cuantos] (contando el nuevo): del 1 al 7, y a partir del 8 la pastilla. */
internal fun huecoDe(cuantos: Int): Int = minOf(cuantos, COPITOS_CABEN) - 1

/**
 * La fila de copitos: una sola línea, sin scroll. Hasta 7 caben todos; con 8 o más, 6 y una pastilla «+N» del
 * mismo tamaño e igual hielo. Sin ninguno, la frase «Aún no tienes ninguno.». [tintaSuave] es la de la banda
 * donde se coloca (la veta fría baja la suya). Con [entrando] el último copito sube y gira hasta su sitio (o la
 * pastilla recién nacida se asienta de 0,94 a 1); con [saliendo] el último se funde en 150 ms. [fila] guarda dónde
 * está cada hueco.
 */
@Composable
fun Copitos(
    total: Int,
    modifier: Modifier = Modifier,
    tintaSuave: Color = BitoTema.colores.tintaSuave,
    entrando: Boolean = false,
    saliendo: Boolean = false,
    fila: FilaCopitos? = null,
) {
    val plan = planCopitos(total)
    Row(
        modifier
            .testTag("tc-copos")
            .fillMaxWidth()
            .padding(top = AIRE_ARRIBA, bottom = AIRE_ABAJO)
            .heightIn(min = DIAMETRO)
            .onGloballyPositioned { fila?.fijar(it.positionInRoot(), it.size.height) },
        horizontalArrangement = Arrangement.spacedBy(HUECO),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (total <= 0) {
            Text(
                stringResource(R.string.store_freezer_none),
                style = BitoTema.tipos.t16.copy(lineHeight = 44.sp),
                color = tintaSuave,
            )
        } else {
            // Con 7 o menos, el último es el que sube o se va; con 8, la pastilla recién nacida; con más, la cifra rueda sola.
            repeat(plan.copos) { i ->
                val ultimo = plan.mas == null && i == plan.copos - 1
                CopitoAnimado(nuevo = entrando && ultimo, sale = saliendo && ultimo)
            }
            plan.mas?.let { PastillaMas(it, nueva = entrando && total == COPITOS_CABEN + 1, sale = saliendo && total == COPITOS_CABEN + 1) }
        }
    }
}

/** Un copito en su hueco; [nuevo] sube y gira hasta él, [sale] se funde. Reducido: solo fundidos de 150 ms. */
@Composable
private fun CopitoAnimado(
    nuevo: Boolean,
    sale: Boolean,
) {
    val reducido = BitoTema.reducido
    val subida = with(LocalDensity.current) { SUBIDA_ENTRADA.toPx() }
    val llegada = remember { Animatable(if (nuevo) 0f else 1f) }
    val salida = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (nuevo) llegada.animateTo(1f, tween(if (reducido) Duraciones.REDUCIDO else DURACION_ENTRADA, easing = Curvas.out))
    }
    LaunchedEffect(sale) { if (sale) salida.animateTo(0f, tween(DURACION_SALIDA)) }
    Copito(
        Modifier
            .graphicsLayer {
                val q = llegada.value
                if (!reducido) {
                    translationY = (1f - q) * subida
                    scaleX = ESCALA_ENTRADA + (1f - ESCALA_ENTRADA) * q
                    scaleY = scaleX
                }
                alpha = q * salida.value
            }.testTag("tc-copo"),
    ) {
        Icon(
            BitoIcons.Snowflake,
            null,
            Modifier.size(COPO).graphicsLayer { rotationZ = if (reducido) 0f else GIRO_ENTRADA * (1f - llegada.value) },
            tint = BitoTema.colores.copo,
        )
    }
}

@Composable
private fun Copito(
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    val colores = BitoTema.colores
    Box(
        modifier
            .size(DIAMETRO)
            .background(colores.hielo, CircleShape)
            .border(BorderStroke(Medidas.linea, colores.contorno), CircleShape),
        contentAlignment = Alignment.Center,
    ) { contenido() }
}

@Composable
private fun PastillaMas(
    mas: Int,
    nueva: Boolean,
    sale: Boolean,
) {
    val reducido = BitoTema.reducido
    val descripcion = stringResource(R.string.store_freezer_more_desc, mas)
    val asiento = remember { Animatable(if (nueva && !reducido) Duraciones.ESCALA_TIC_SALDO else 1f) }
    val salida = remember { Animatable(1f) }
    LaunchedEffect(Unit) { if (nueva) asiento.animateTo(1f, tween(Duraciones.TIC_SALDO, easing = Curvas.out)) }
    LaunchedEffect(sale) { if (sale) salida.animateTo(0f, tween(DURACION_SALIDA)) }
    Copito(
        Modifier
            .graphicsLayer {
                scaleX = asiento.value
                scaleY = asiento.value
                alpha = salida.value
            }.testTag("tc-mas")
            .clearAndSetSemantics {
                contentDescription = descripcion
                role = Role.Image
            },
    ) {
        CifraRodillo(
            mas,
            estilo = BitoTema.tipos.t16.cifra().copy(fontWeight = FontWeight.Bold),
            color = BitoTema.colores.copo,
            formato = { "+$it" },
        )
    }
}
