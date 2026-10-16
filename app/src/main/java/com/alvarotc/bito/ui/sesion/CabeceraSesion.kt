package com.alvarotc.bito.ui.sesion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.anclaParticula
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Saldo
import com.alvarotc.bito.ui.sistema.componentes.pulsar
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

/** El botón de atrás de la sesión: su etiqueta cambia por estado («Volver», «Saltar y volver a preparar»). */
data class AccionAtras(val etiqueta: String, val alPulsar: () -> Unit)

// `.ses-redondo`: canto al 92 % sobre el arte, `rgba(243,231,211,.92)`.
private val CantoSobreArte = Color(0xEBF3E7D3)

// `#ses-cab`: a 8 dp de arriba, 64 dp de alto (`.cabecera`), 12 dp a los lados y 2 dp entre piezas.
private val ARRIBA_CABECERA = 8.dp
private val ALTO_CABECERA = 64.dp
private val LADO_CABECERA = 12.dp
private val HUECO_CABECERA = 2.dp

// `.ses-redondo + .ses-redondo`, `.ses-redondo + .saldo`: 8 dp más entre botones.
private val ENTRE_BOTONES = 8.dp

// Iconos de 20 dp (`data-s="20"`, `ico('music-notes', 20)`).
private val ICONO = 20.dp

// `.saldo`: 36 dp de alto, 6 dp a la izquierda y 12 a la derecha, moneda de 24.
private val ALTO_SALDO = 36.dp
private val SALDO_IZQUIERDA = 6.dp
private val SALDO_DERECHA = 12.dp
private val MONEDA_SALDO = 24.dp

// `#ses-cielo`: 96 dp desde arriba, `cielo` del 90 % al 0 %.
private val ALTO_CIELO = 96.dp
private const val CIELO_ARRIBA = 0.9f

/**
 * La cabecera de la sesión (`#ses-cab`) con el fundido del cielo debajo (`#ses-cielo`): atrás a la izquierda y, a
 * la derecha, la música (interruptor) y el saldo. Cada pieza nula no se pinta.
 */
@Composable
fun CabeceraSesion(
    atras: AccionAtras?,
    musica: Boolean?,
    alMusica: (Boolean) -> Unit,
    saldo: Int?,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val gesto = rememberGesto()
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .testTag("ses-cielo")
                .fillMaxWidth()
                .height(ALTO_CIELO)
                .background(Brush.verticalGradient(listOf(colores.cielo.copy(alpha = CIELO_ARRIBA), colores.cielo.copy(alpha = 0f)))),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = ARRIBA_CABECERA)
                .height(ALTO_CABECERA)
                .padding(horizontal = LADO_CABECERA),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HUECO_CABECERA),
        ) {
            if (atras != null) {
                BotonRedondo(
                    icono = IconosSistema.ArrowLeft,
                    modifier =
                        Modifier
                            .testTag("ses-atras")
                            .semantics { contentDescription = atras.etiqueta },
                    alPulsar = { interaccion ->
                        Modifier.clickable(interaccion, indication = null, role = Role.Button, onClick = atras.alPulsar)
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            if (musica != null) {
                val etiqueta = stringResource(if (musica) R.string.breathing_music_on_cd else R.string.breathing_music_off_cd)
                BotonRedondo(
                    icono = IconosSistema.MusicNotes,
                    encendido = musica,
                    modifier =
                        Modifier
                            .testTag("ses-musica")
                            .semantics { contentDescription = etiqueta },
                    alPulsar = { interaccion ->
                        Modifier.toggleable(musica, interaccion, indication = null, role = Role.Switch) { on ->
                            gesto(if (on) Snd.INTERRUPTOR_ON else Snd.INTERRUPTOR_OFF, Haptica.TICK)
                            alMusica(on)
                        }
                    },
                )
            }
            if (saldo != null) {
                Box(
                    Modifier
                        .padding(start = if (musica != null) ENTRE_BOTONES else 0.dp)
                        .testTag("ses-saldo")
                        .anclaParticula(ClavesAncla.SALDO)
                        .semantics(mergeDescendants = true) {}
                        .height(ALTO_SALDO)
                        .background(CantoSobreArte, BitoFormas.canto)
                        .border(Medidas.linea, colores.contorno, BitoFormas.canto)
                        .padding(start = SALDO_IZQUIERDA, end = SALDO_DERECHA),
                    contentAlignment = Alignment.Center,
                ) {
                    Saldo(saldo, tamanoMoneda = MONEDA_SALDO, estilo = BitoTema.tipos.t16.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

/** `.ses-redondo`: 44 dp, canto al 92 % (o `agua` encendido), contorno y pulsar a .94. */
@Composable
private fun BotonRedondo(
    icono: ImageVector,
    modifier: Modifier,
    alPulsar: (MutableInteractionSource) -> Modifier,
    encendido: Boolean = false,
) {
    val colores = BitoTema.colores
    val interaccion = remember { MutableInteractionSource() }
    val fondo by animateColorAsState(if (encendido) colores.agua else CantoSobreArte, tween(Duraciones.COLOR), label = "redondo")
    Box(
        modifier
            .then(alPulsar(interaccion))
            .pulsar(interaccion, redondo = true)
            .size(Medidas.toqueMinimo)
            .background(fondo, CircleShape)
            .border(Medidas.linea, colores.contorno, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icono, contentDescription = null, tint = colores.tinta, modifier = Modifier.size(ICONO))
    }
}
