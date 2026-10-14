package com.alvarotc.bito.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.ui.garden.GardenScene
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.delay

private const val ESCALA = 0.74f
private const val PARALAJE = 0.55f
private const val FUNDIDO_DESDE = 0.7f
private const val CONTENTO_MS = 1200L

/**
 * La maqueta del jardín de Ajustes (spec §5.1.2): la escena real al 74 %, fundida en la tierra por abajo y
 * con paralaje (sube a 0,45 de la velocidad del scroll). Deja de respirar cuando ya no se ve.
 */
@Composable
fun MaquetaJardin(
    garden: GardenState,
    spec: HabiSpec,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
) {
    val gesto = rememberGesto()
    var alto by remember { mutableIntStateOf(0) }
    val visible by remember { derivedStateOf { scroll.value < alto } }
    var contento by remember { mutableStateOf(false) }
    LaunchedEffect(contento) {
        if (contento) {
            delay(CONTENTO_MS)
            contento = false
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { alto = it.height }
            .graphicsLayer {
                translationY = scroll.value * PARALAJE
                compositingStrategy = CompositingStrategy.Offscreen
            }.drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        FUNDIDO_DESDE to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        GardenScene(
            garden = garden,
            spec = spec,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = ESCALA
                        scaleY = ESCALA
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
            delighted = contento,
            onHabiTap = {
                if (!contento) {
                    contento = true
                    gesto(Snd.HABI, Haptica.TICK)
                }
            },
            animated = visible,
        )
    }
}
