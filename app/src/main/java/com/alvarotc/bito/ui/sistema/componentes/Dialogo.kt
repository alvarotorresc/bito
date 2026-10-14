package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

/** Diálogo de confirmación (G10): brota desde abajo; el sí repite [verbo]; «Cancelar» como texto encima. */
@Composable
fun Dialogo(
    abierto: Boolean,
    titulo: String,
    verbo: String,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
    modifier: Modifier = Modifier,
    cuerpo: String? = null,
    peligro: Boolean = false,
) {
    if (!abierto) return
    val gesto = rememberGesto()
    val reducido = BitoTema.reducido
    val colores = BitoTema.colores
    val posicion = remember { Animatable(1f) } // 1 = fuera, 0 = en su sitio
    val alto = remember { floatArrayOf(0f) }
    LaunchedEffect(Unit) {
        gesto(Snd.BROTE, if (peligro) Haptica.REJECT else Haptica.TICK)
        posicion.animateTo(0f, if (reducido) tween(Duraciones.REDUCIDO) else Muelles.hoja())
    }

    fun cancelar() {
        gesto(Snd.HUNDIR, Haptica.LOW)
        alCancelar()
    }
    Dialog(
        onDismissRequest = { cancelar() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
                dismissOnBackPress = true,
            ),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        val dentro = (1f - posicion.value).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = dentro }
                    .background(colores.velo)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { cancelar() },
            )
            Column(
                modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(Medidas.margen)
                    .onSizeChanged { alto[0] = it.height.toFloat() }
                    .graphicsLayer {
                        if (reducido) alpha = dentro else translationY = posicion.value * (alto[0] + 48.dp.toPx())
                    }.fillMaxWidth()
                    .clip(BitoFormas.tarjeta)
                    .background(colores.canto)
                    .border(Medidas.linea, colores.contorno, BitoFormas.tarjeta)
                    .padding(Medidas.e18),
            ) {
                Text(titulo, style = BitoTema.tipos.t19.fuerte(), color = colores.tinta)
                cuerpo?.let {
                    Text(
                        it,
                        style = BitoTema.tipos.t16,
                        color = colores.tintaSuave,
                        modifier = Modifier.padding(top = Medidas.e8),
                    )
                }
                BotonesHoja(
                    principal = verbo,
                    alPrincipal = {
                        gesto(h = if (peligro) Haptica.FALL else Haptica.CONFIRM)
                        alConfirmar()
                    },
                    modifier = Modifier.padding(top = Medidas.e16),
                    suave = stringResource(R.string.sistema_cancelar),
                    alSuave = { cancelar() },
                    peligro = peligro,
                )
            }
        }
    }
}
