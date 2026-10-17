package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoTema
import kotlin.math.tan

// (sin token) `.tc-veta` de tienda-congeladores.css: la tinta suave baja al valor AA del estrato 2 dentro de la banda.
private val TINTA_SUAVE_VETA = Color(0xFF5E4A3A)
private val RELLENO_VERTICAL = 34.dp
private val RELLENO_LATERAL = 20.dp

/** `skewY(-4deg)` del mock: el lado izquierdo baja y el derecho sube. */
internal const val INCLINACION_GRADOS = -4f

/**
 * Las cuatro esquinas de la banda inclinada, en orden (arriba-izquierda, arriba-derecha, abajo-derecha,
 * abajo-izquierda), sobre la caja de [ancho] por [alto] y con el origen en su centro, como el `skewY` del mock.
 */
internal fun puntosVeta(
    ancho: Float,
    alto: Float,
): List<Offset> {
    val t = tan(Math.toRadians(INCLINACION_GRADOS.toDouble())).toFloat()
    val izquierda = t * (-ancho / 2f)
    val derecha = t * (ancho / 2f)
    return listOf(
        Offset(0f, izquierda),
        Offset(ancho, derecha),
        Offset(ancho, alto + derecha),
        Offset(0f, alto + izquierda),
    )
}

/**
 * La veta fría: una banda de `vetaFria` inclinada -4° cuyo contenido va derecho (el contragiro del mock).
 * Solo el fondo se inclina; el contenido se coloca con 34 dp arriba y abajo y 20 dp a los lados. [content]
 * recibe la tinta suave AA de la banda, que no es la del resto de la app.
 */
@Composable
fun VetaFria(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(tintaSuave: Color) -> Unit,
) {
    val fondo = BitoTema.colores.vetaFria
    Column(
        modifier
            .testTag("tc-veta")
            .fillMaxWidth()
            .drawBehind {
                val p = puntosVeta(size.width, size.height)
                val banda =
                    Path().apply {
                        moveTo(p[0].x, p[0].y)
                        p.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                drawPath(banda, fondo)
            }.padding(horizontal = RELLENO_LATERAL, vertical = RELLENO_VERTICAL),
    ) { content(TINTA_SUAVE_VETA) }
}
