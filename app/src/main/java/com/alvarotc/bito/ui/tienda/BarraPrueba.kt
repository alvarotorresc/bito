package com.alvarotc.bito.ui.tienda

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.fuerte

// (sin token) `.barra-prueba` de sistema.css: radio de arriba, relleno y letra de la nota.
private val RADIO_BARRA = 24.dp
private val RELLENO_BARRA = PaddingValues(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 18.dp)
private val TITULO_BARRA = 19.sp
private val ALTO_TITULO = 22.8.sp // 19 sp a 1,2 de interlineado
private val ALTO_NOTA = 19.6.sp // 14 sp a 1,4 de interlineado
private val AIRE_NOTA = 2.dp
private val AIRE_BOTONES = 12.dp
private const val LINEAS_NOTA = 2

/** Un hueco de más para que la barra se vea entera fuera de pantalla antes de brotar (`barraH + 20`). */
private val HUECO_SALIDA = 20.dp

/**
 * La barra de prueba (spec §3.3.4): sin velo y fuera de cualquier hoja modal, brota desde abajo con
 * `hoja` y se hunde con `hundir`. Con [barra] nula se hunde y desaparece; el que la coloca (abajo del
 * todo) es quien la usa. Los textos de los botones llegan ya traducidos. En un objeto del jardín el
 * botón de salida es «Quitarlo» y no [txtQuitar] (un banco no se lleva puesto). Con `SinSaldo` o
 * `NoSeCompra` no hay botón principal: nunca un botón muerto. Sin sonido ni háptica propios.
 */
@Composable
fun BarraPruebaView(
    barra: BarraPrueba?,
    txtComprar: String,
    txtAhora: String,
    txtQuitar: String,
    alComprar: () -> Unit,
    alAhoraNo: () -> Unit,
    alQuitar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducido = BitoTema.reducido
    // Mientras se hunde sigue pintando lo último que dijo, para que no cambie de texto al salir.
    val ultima = remember { arrayOfNulls<BarraPrueba>(1) }
    if (barra != null) ultima[0] = barra
    val mostrada = barra ?: ultima[0]
    val destino = if (barra != null) 1f else 0f
    val progreso by animateFloatAsState(
        targetValue = destino,
        animationSpec =
            when {
                reducido -> tween(Duraciones.REDUCIDO)
                barra != null -> Muelles.hoja(umbral = 0.001f)
                else -> Muelles.hundir(umbral = 0.001f)
            },
        label = "barra-prueba",
    )
    if (mostrada == null || (barra == null && progreso <= 0.001f)) return

    val colores = BitoTema.colores
    val tipos = BitoTema.tipos
    val formaArriba = BitoFormas.tarjetaArriba
    val salida = HUECO_SALIDA
    Column(
        modifier
            .testTag("barra")
            .fillMaxWidth()
            .graphicsLayer {
                if (reducido) {
                    alpha = progreso.coerceIn(0f, 1f)
                } else {
                    translationY = (1f - progreso) * (size.height + salida.toPx())
                }
            }.semantics { liveRegion = LiveRegionMode.Polite }
            .clip(formaArriba)
            .drawBehind {
                drawRect(colores.canto)
                // el borde de `.barra-prueba`: línea fina arriba y a los lados, sin cerrar por abajo
                val linea = Medidas.linea.toPx()
                val r = RADIO_BARRA.toPx()
                val m = linea / 2f
                val diametro = 2 * (r - m)
                val camino =
                    Path().apply {
                        moveTo(m, size.height)
                        lineTo(m, r)
                        arcTo(Rect(m, m, m + diametro, m + diametro), 180f, 90f, false)
                        lineTo(size.width - r, m)
                        arcTo(Rect(size.width - m - diametro, m, size.width - m, m + diametro), 270f, 90f, false)
                        lineTo(size.width - m, size.height)
                    }
                drawPath(camino, colores.contorno, style = Stroke(width = linea))
            }.padding(top = Medidas.linea)
            .padding(RELLENO_BARRA),
    ) {
        Text(
            mostrada.nombre,
            style = tipos.t16.fuerte().copy(fontSize = TITULO_BARRA, lineHeight = ALTO_TITULO),
            color = colores.tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            textoDe(mostrada),
            Modifier.padding(top = AIRE_NOTA).testTag("barra-nota"),
            style = tipos.t14.copy(lineHeight = ALTO_NOTA),
            color = colores.tintaSuave,
            maxLines = LINEAS_NOTA,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.padding(top = AIRE_BOTONES).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
        ) {
            val llena = Modifier.weight(1f)
            when (mostrada) {
                is BarraPrueba.Comprar -> {
                    Boton(
                        txtComprar,
                        alComprar,
                        llena.testTag("barra-comprar"),
                        estilo = EstiloBoton.PRINCIPAL,
                        alto = Medidas.botonHoja,
                        llena = true,
                        pildoraMock = true,
                    )
                    Boton(
                        txtAhora,
                        alAhoraNo,
                        llena.testTag("barra-ahora-no"),
                        alto = Medidas.botonHoja,
                        llena = true,
                        pildoraMock = true,
                    )
                }
                else ->
                    Boton(
                        if (mostrada.esObjeto) stringResource(R.string.store_take_off_object) else txtQuitar,
                        alQuitar,
                        llena.testTag("barra-quitar"),
                        alto = Medidas.botonHoja,
                        llena = true,
                        pildoraMock = true,
                    )
            }
        }
    }
}

private val BarraPrueba.esObjeto: Boolean
    get() =
        when (this) {
            is BarraPrueba.Comprar -> objeto
            is BarraPrueba.SinSaldo -> objeto
            is BarraPrueba.NoSeCompra -> objeto
        }

/** La nota de la barra: la que trae el caso o, sin ella, «N bitos. Te quedarían M.». Sin saldo, la frase única de «te faltan». */
@Composable
private fun textoDe(barra: BarraPrueba): String =
    when (barra) {
        is BarraPrueba.Comprar -> {
            val nota = barra.nota
            if (nota is TextoBarra.Plano) {
                stringResource(nota.res, *nota.args.toTypedArray())
            } else {
                stringResource(R.string.store_bar_price, barra.precio, barra.quedan)
            }
        }
        is BarraPrueba.SinSaldo -> stringResource(R.string.store_bar_missing, barra.precio, barra.tienes, barra.precio - barra.tienes)
        is BarraPrueba.NoSeCompra ->
            (barra.texto as TextoBarra.Plano).let { stringResource(it.res, *it.args.toTypedArray()) }
    }
