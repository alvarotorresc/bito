package com.alvarotc.bito.ui.sesion.respirar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.delay
import kotlin.math.min

/** Lo que se contesta. [valor] es el que se escribe en `habi_memoria`: 0 mejor, 1 igual, 2 aún me cuesta. */
enum class AnimoSuelta(val valor: Int) { MEJOR(0), IGUAL(1), CUESTA(2) }

// `.ses-opciones`: 14 dp arriba y 12 entre ellas. `.ses-opcion`: 56 dp, radio 28 (sin token), relleno 0 / 20.
private val ARRIBA_OPCIONES = 14.dp
private val ENTRE_OPCIONES = 12.dp
private val ALTO_OPCION = 56.dp
private val RADIO_OPCION = 28.dp
private val RELLENO_OPCION = 20.dp
private val ARRIBA_SALTAR = 12.dp

// G24: las demás bajan a 0,35 de opacidad y 4 dp en 220 ms; pasa al fin a los 900 ms (500 en reducido).
private const val OPACIDAD_RESTO = 0.35f
private val BAJA_RESTO = 4.dp
private const val RESTO_MS = 220
private const val PASA_AL_FIN_MS = 900L
private const val PASA_AL_FIN_REDUCIDO_MS = 500L
private const val AGUA_TOPE = 1.04f

/**
 * ¿Cómo estás ahora? (`data-b="animo"`): título, tres opciones con agua que sube y «Saltar» (`a-saltar`). Al elegir
 * ([alElegir], en el mismo fotograma, para escribir el recuerdo) suena `gorgoteo` + `ticks`, y a los 900 ms [alSeguir].
 */
@Composable
fun ComoEstas(
    alElegir: (AnimoSuelta) -> Unit,
    alSeguir: (AnimoSuelta) -> Unit,
    alSaltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gesto = rememberGesto()
    val reducido = BitoTema.reducido
    var elegida by remember { mutableStateOf<AnimoSuelta?>(null) }
    LaunchedEffect(elegida) {
        val e = elegida ?: return@LaunchedEffect
        delay(if (reducido) PASA_AL_FIN_REDUCIDO_MS else PASA_AL_FIN_MS)
        alSeguir(e)
    }
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.ses_como_estas),
            style = BitoTema.tipos.t19.fuerte(),
            color = BitoTema.colores.tinta,
            modifier = Modifier.escalon(0),
        )
        Column(
            Modifier.escalon(1).padding(top = ARRIBA_OPCIONES).fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ENTRE_OPCIONES),
        ) {
            AnimoSuelta.entries.forEach { animo ->
                Opcion(
                    texto =
                        stringResource(
                            when (animo) {
                                AnimoSuelta.MEJOR -> R.string.ses_mejor
                                AnimoSuelta.IGUAL -> R.string.ses_igual
                                AnimoSuelta.CUESTA -> R.string.ses_cuesta
                            },
                        ),
                    etiqueta = "como-${animo.name.lowercase()}",
                    estado = if (elegida == null) null else elegida == animo,
                    alPulsar = {
                        if (elegida == null) {
                            gesto(Snd.GORGOTEO, Haptica.TICKS)
                            elegida = animo
                            alElegir(animo)
                        }
                    },
                )
            }
        }
        Row(Modifier.escalon(2).fillMaxWidth().padding(top = ARRIBA_SALTAR), horizontalArrangement = Arrangement.Center) {
            Boton(
                stringResource(R.string.ses_saltar),
                alPulsar = { if (elegida == null) alSaltar() },
                modifier = Modifier.testTag("a-saltar"),
                estilo = EstiloBoton.FANTASMA,
            )
        }
    }
}

/** [estado]: `null` = aún sin elegir, `true` = esta, `false` = otra. */
@Composable
private fun Opcion(
    texto: String,
    etiqueta: String,
    estado: Boolean?,
    alPulsar: () -> Unit,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val agua = remember { Animatable(0f) }
    val resto = remember { Animatable(0f) }
    LaunchedEffect(estado) {
        when (estado) {
            true ->
                if (reducido) agua.snapTo(1f) else agua.animateTo(1f, Muelles.marcar())
            false -> resto.animateTo(1f, tween(RESTO_MS, easing = Curvas.out))
            null -> Unit
        }
    }
    val forma = RoundedCornerShape(RADIO_OPCION)
    Box(
        Modifier
            .testTag(etiqueta)
            .fillMaxWidth()
            .height(ALTO_OPCION)
            .graphicsLayer {
                alpha = 1f - (1f - OPACIDAD_RESTO) * resto.value
                if (!reducido) translationY = BAJA_RESTO.toPx() * resto.value
            }.clip(forma)
            .background(colores.canto, forma)
            .semantics { selected = estado == true }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = alPulsar),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = (1f - min(AGUA_TOPE, agua.value)) * size.height }
                .background(colores.agua),
        )
        Text(
            texto,
            style = BitoTema.tipos.t16.fuerte(),
            color = colores.tinta,
            modifier = Modifier.padding(horizontal = RELLENO_OPCION),
        )
        Box(Modifier.fillMaxSize().border(Medidas.linea, colores.contorno, forma))
    }
}
