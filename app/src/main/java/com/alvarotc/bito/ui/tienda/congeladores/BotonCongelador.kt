package com.alvarotc.bito.ui.tienda.congeladores

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Monedita
import com.alvarotc.bito.ui.sistema.componentes.pulsar
import com.alvarotc.bito.ui.sistema.componentes.temblor
import com.alvarotc.bito.ui.sistema.rememberGesto

// (sin token) `.tc-buy` y `.tc-linea` de tienda-congeladores.css.
internal val ALTO_BOTON = 60.dp
private val RELLENO_IZQUIERDA = 20.dp
private val RELLENO_DERECHA = 18.dp
private val COPITO_BOTON = 20.dp
private val MONEDA = 18.dp
private val AIRE_LINEA = 10.dp
private val ALTO_LINEA = 40.dp
private const val ALFA_APAGADO = 0.5f
private val DESENFOQUE_RELEVO = 2.dp

/**
 * El botón directo «Un congelador» con su línea de confirmación (J22). Un toque con saldo llama a [alComprar]. Sin
 * saldo se atenúa a 0,5 pero sigue vivo: al tocarlo tiembla, la línea pasa a negrita y tinta plena y solo vibra
 * (sin sonido, sin rojo). Con [comprando] hay una compra en el aire y el botón no atiende otro toque hasta que
 * llega. [tintaSuave] es la de la banda donde se coloca.
 */
@Composable
fun BotonCongelador(
    precio: Int,
    saldo: Int,
    alComprar: () -> Unit,
    modifier: Modifier = Modifier,
    tintaSuave: Color = BitoTema.colores.tintaSuave,
    comprando: Boolean = false,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    val sinSaldo = saldo < precio
    var toques by remember { mutableIntStateOf(0) }
    var tocado by remember(saldo, precio) { mutableStateOf(false) }
    val interaccion = remember { MutableInteractionSource() }
    val alfa by animateFloatAsState(
        targetValue = if (sinSaldo) ALFA_APAGADO else 1f,
        animationSpec = tween(Duraciones.COLOR),
        label = "boton-congelador-alfa",
    )
    val linea =
        if (sinSaldo) {
            stringResource(R.string.store_freezer_missing, precio - saldo)
        } else {
            stringResource(R.string.store_freezer_left, precio, saldo - precio)
        }
    val descripcion =
        stringResource(R.string.store_freezer_buy_desc, precio) + if (sinSaldo) ". $linea" else ""
    val forma = RoundedCornerShape(ALTO_BOTON / 2)

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .testTag("tc-buy")
                .fillMaxWidth()
                .height(ALTO_BOTON)
                .temblor(toques)
                .pulsar(interaccion)
                .graphicsLayer { this.alpha = alfa }
                .clip(forma)
                .background(colores.canto)
                .border(BorderStroke(Medidas.linea, colores.contorno), forma)
                .clickable(interactionSource = interaccion, indication = null, role = Role.Button, enabled = !comprando) {
                    if (sinSaldo) {
                        tocado = true
                        toques++
                        gesto(h = Haptica.REJECT)
                    } else {
                        alComprar()
                    }
                }.semantics { contentDescription = descripcion }
                .padding(start = RELLENO_IZQUIERDA, end = RELLENO_DERECHA),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(BitoIcons.Snowflake, null, Modifier.size(COPITO_BOTON), tint = colores.copo)
                Spacer(Modifier.width(Medidas.e8))
                Text(
                    stringResource(R.string.store_freezer_buy),
                    style = BitoTema.tipos.t16.copy(fontWeight = FontWeight.Medium),
                    color = colores.tinta,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monedita(MONEDA)
                Spacer(Modifier.width(Medidas.e4))
                Text(precio.toString(), style = BitoTema.tipos.t14.copy(fontWeight = FontWeight.Bold), color = colores.tinta)
            }
        }
        // El relevo de la línea (J22): 140 ms fuera con 2 dp de desenfoque y 180 dentro; reducido, fundidos de 150 ms.
        AnimatedContent(
            targetState = linea,
            modifier = Modifier.fillMaxWidth().padding(top = AIRE_LINEA).heightIn(min = ALTO_LINEA),
            transitionSpec = {
                val entra = if (reducido) Duraciones.REDUCIDO else Duraciones.RELEVO_ENTRA
                val sale = if (reducido) Duraciones.REDUCIDO else Duraciones.RELEVO_SALE
                (fadeIn(tween(entra, delayMillis = sale)) togetherWith fadeOut(tween(sale))).using(SizeTransform(clip = false))
            },
            label = "tc-linea-relevo",
        ) { texto ->
            val desenfoque by transition.animateDp(label = "tc-linea-blur") {
                if (it == EnterExitState.Visible) 0.dp else DESENFOQUE_RELEVO
            }
            val enBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !reducido
            Text(
                texto,
                modifier = Modifier.testTag("tc-linea").fillMaxWidth().then(if (enBlur) Modifier.blur(desenfoque) else Modifier),
                style =
                    BitoTema.tipos.t14.copy(
                        lineHeight = 19.6.sp,
                        fontWeight = if (tocado) FontWeight.Bold else FontWeight.Normal,
                    ),
                color = if (tocado) colores.tinta else tintaSuave,
            )
        }
    }
}
