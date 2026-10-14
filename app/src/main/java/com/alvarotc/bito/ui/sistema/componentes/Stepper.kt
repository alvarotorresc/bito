package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.BitoTiposBase
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlin.math.roundToInt

private const val OPACIDAD_TOPE = 0.35f

/** G27: ± y la cifra rueda hacia donde va. En el tope el botón queda al 35 %, no responde y tiembla. */
@Composable
fun Stepper(
    valor: Int,
    alCambiar: (Int) -> Unit,
    min: Int,
    max: Int,
    descripcion: String,
    modifier: Modifier = Modifier,
    paso: Int = 1,
    formato: (Int) -> String = { it.toString() },
) {
    val gesto = rememberGesto()
    var empujones by remember { mutableIntStateOf(0) }

    // Un paso válido suena y vibra; en el tope solo vibra flojo y la cifra tiembla.
    fun intentar(nuevo: Int): Boolean {
        if (nuevo in min..max) {
            gesto(Snd.TIC, Haptica.SEG)
            alCambiar(nuevo)
            return true
        }
        gesto(h = Haptica.LOW)
        empujones++
        return false
    }

    val enMin = valor - paso < min
    val enMax = valor + paso > max
    Row(
        modifier
            .semantics {
                contentDescription = descripcion
                progressBarRangeInfo = ProgressBarRangeInfo(valor.toFloat(), min.toFloat()..max.toFloat())
                setProgress { destino -> intentar(destino.roundToInt().coerceIn(min, max)) }
            }.onKeyEvent { evento ->
                if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (evento.key) {
                    Key.DirectionUp -> intentar(valor + paso).let { true }
                    Key.DirectionDown -> intentar(valor - paso).let { true }
                    else -> false
                }
            }.focusable(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonPaso(IconosSistema.Minus, stringResource(R.string.sistema_menos), enMin) { intentar(valor - paso) }
        Box(Modifier.defaultMinSize(minWidth = 56.dp), contentAlignment = Alignment.Center) {
            CifraRodillo(
                valor,
                Modifier.temblor(empujones),
                color = BitoTema.colores.tinta,
                formato = formato,
            )
        }
        BotonPaso(IconosSistema.Plus, stringResource(R.string.sistema_mas), enMax) { intentar(valor + paso) }
    }
}

@Composable
private fun BotonPaso(
    icono: ImageVector,
    descripcion: String,
    tope: Boolean,
    alPulsar: () -> Unit,
) {
    val colores = BitoTema.colores
    val fuente = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(Medidas.toqueMinimo)
            .pulsar(fuente, redondo = true)
            .alpha(if (tope) OPACIDAD_TOPE else 1f)
            .clip(CircleShape)
            .background(colores.canto)
            .border(Medidas.linea, colores.contorno, CircleShape)
            .clickable(interactionSource = fuente, indication = null, role = Role.Button, onClick = alPulsar)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(20.dp))
    }
}

/** La cifra que rueda (G27): entra desde abajo si sube, desde arriba si baja. */
@Composable
fun CifraRodillo(
    valor: Int,
    modifier: Modifier = Modifier,
    estilo: TextStyle = BitoTiposBase.t24.cifra(),
    color: Color = Color.Unspecified,
    formato: (Int) -> String = { it.toString() },
) {
    val reducido = BitoTema.reducido
    AnimatedContent(
        targetState = valor,
        modifier = modifier,
        transitionSpec = {
            val sube = targetState > initialState
            if (reducido) {
                ContentTransform(
                    fadeIn(tween(Duraciones.REDUCIDO)),
                    fadeOut(tween(Duraciones.REDUCIDO)),
                )
            } else {
                val medida = tween<Float>(Duraciones.RODILLO, easing = Curvas.out)
                val posicion = tween<IntOffset>(Duraciones.RODILLO, easing = Curvas.out)
                (slideInVertically(posicion) { if (sube) it else -it } + fadeIn(medida)) togetherWith
                    (slideOutVertically(posicion) { if (sube) -it else it } + fadeOut(medida))
            }
        },
        label = "cifraRodillo",
    ) { v ->
        Text(formato(v), style = estilo, color = color, textAlign = TextAlign.Center)
    }
}
