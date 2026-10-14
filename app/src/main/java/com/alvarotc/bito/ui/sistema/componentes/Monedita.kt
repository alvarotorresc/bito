package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra

/** La monedita de oro con la carita de Habi (D15, §7.1). 18, 36 o 48 dp. Decorativa: sin descripción. */
@Composable
fun Monedita(
    tamano: Dp,
    modifier: Modifier = Modifier,
) {
    val id = if (tamano <= 24.dp) R.drawable.coin_small else R.drawable.coin
    Image(painterResource(id), contentDescription = null, modifier = modifier.size(tamano))
}

/** Saldo de bitos: monedita y cifra en rodillo; tic a .94 cuando cambia. */
@Composable
fun Saldo(
    bitos: Int,
    modifier: Modifier = Modifier,
) {
    val reducido = BitoTema.reducido
    val escala = remember { Animatable(1f) }
    var anterior by remember { mutableIntStateOf(bitos) }
    LaunchedEffect(bitos) {
        if (bitos == anterior) return@LaunchedEffect
        anterior = bitos
        if (!reducido) {
            escala.snapTo(Duraciones.ESCALA_TIC_SALDO)
            escala.animateTo(1f, tween(Duraciones.TIC_SALDO, easing = Curvas.out))
        }
    }
    val descripcion = pluralStringResource(R.plurals.sistema_saldo, bitos, bitos)
    Row(
        modifier
            .graphicsLayer {
                scaleX = escala.value
                scaleY = escala.value
            }.clearAndSetSemantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monedita(18.dp)
        Spacer(Modifier.width(Medidas.e4))
        CifraRodillo(bitos, estilo = BitoTema.tipos.t19.cifra(), color = BitoTema.colores.oroTexto)
    }
}
