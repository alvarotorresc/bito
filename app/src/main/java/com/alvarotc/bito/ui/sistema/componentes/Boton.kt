package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.LocalTacto
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.fuerte

enum class EstiloBoton { PRINCIPAL, NORMAL, FANTASMA, PELIGRO, BORRAR }

/**
 * Píldora (G32). [apagado] no deshabilita: se puede pulsar y llama a [alPulsarApagado] para que la
 * pantalla explique qué falta (D13). [progreso] (0 a 1) pinta un relleno de agua de izquierda a derecha.
 * [llena] estira la píldora al ancho que le da quien la contiene (`.btnrow.llena .pill{flex:1}`).
 */
@Composable
fun Boton(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    estilo: EstiloBoton = EstiloBoton.NORMAL,
    apagado: Boolean = false,
    alPulsarApagado: () -> Unit = {},
    icono: ImageVector? = null,
    alto: Dp = Medidas.boton,
    progreso: () -> Float = { 0f },
    llena: Boolean = false,
) {
    val colores = BitoTema.colores
    val tacto = LocalTacto.current
    val interaccion = remember { MutableInteractionSource() }
    val relleno =
        when (estilo) {
            EstiloBoton.PRINCIPAL -> colores.agua
            EstiloBoton.NORMAL -> colores.canto
            EstiloBoton.PELIGRO -> colores.peligro
            EstiloBoton.FANTASMA, EstiloBoton.BORRAR -> Color.Transparent
        }
    val tinta =
        when (estilo) {
            EstiloBoton.PELIGRO -> colores.canto
            EstiloBoton.BORRAR -> colores.peligroTexto
            else -> colores.tinta
        }
    val conContorno = estilo != EstiloBoton.FANTASMA && estilo != EstiloBoton.BORRAR
    val textoApagado = stringResource(R.string.sistema_apagado)
    Box(
        modifier
            .heightIn(min = Medidas.toqueMinimo)
            .alpha(if (apagado) 0.45f else 1f)
            .semantics(mergeDescendants = true) { if (apagado) stateDescription = textoApagado }
            .clickable(interactionSource = interaccion, indication = null, role = Role.Button) {
                if (apagado) {
                    tacto.haptica(Haptica.LOW)
                    alPulsarApagado()
                } else {
                    alPulsar()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .then(if (llena) Modifier.fillMaxWidth() else Modifier)
                .pulsar(interaccion)
                .heightIn(min = alto)
                .clip(BitoFormas.canto)
                .background(relleno)
                .drawBehind {
                    val p = progreso()
                    if (p > 0f) drawRect(colores.agua, size = Size(size.width * p, size.height))
                }.then(if (conContorno) Modifier.border(BorderStroke(Medidas.linea, colores.contorno), BitoFormas.canto) else Modifier)
                .padding(horizontal = Medidas.e18),
            horizontalArrangement = Arrangement.spacedBy(Medidas.e8, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icono != null) Icon(icono, contentDescription = null, Modifier.size(20.dp), tint = tinta)
            Text(texto, style = BitoTema.tipos.t16.fuerte(), color = tinta)
        }
    }
}

/** La salida suave como texto de 44 dp (D12). */
@Composable
fun BotonTexto(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaccion = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = Medidas.salidaSuave)
            .defaultMinSize(minWidth = Medidas.toqueMinimo)
            .clickable(interactionSource = interaccion, indication = null, role = Role.Button, onClick = alPulsar),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, Modifier.pulsar(interaccion), style = BitoTema.tipos.t16.fuerte(), color = BitoTema.colores.tintaSuave)
    }
}

/** Orden de D12: la salida suave como texto encima; la acción principal abajo y ancha (48 dp). */
@Composable
fun BotonesHoja(
    principal: String,
    alPrincipal: () -> Unit,
    modifier: Modifier = Modifier,
    suave: String? = null,
    alSuave: () -> Unit = {},
    peligro: Boolean = false,
    apagado: Boolean = false,
    alPulsarApagado: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        if (suave != null) BotonTexto(suave, alSuave, Modifier.fillMaxWidth())
        Boton(
            principal,
            alPrincipal,
            Modifier.fillMaxWidth(),
            estilo = if (peligro) EstiloBoton.PELIGRO else EstiloBoton.PRINCIPAL,
            apagado = apagado,
            alPulsarApagado = alPulsarApagado,
            alto = Medidas.botonHoja,
        )
    }
}
