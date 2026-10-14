package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.alvarotc.bito.ui.sistema.Medidas

enum class EstiloBoton { PRINCIPAL, NORMAL, FANTASMA, PELIGRO, BORRAR }

/**
 * Píldora (G32). [apagado] no deshabilita: se puede pulsar y llama a [alPulsarApagado] para que la
 * pantalla explique qué falta (D13).
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
) {
    Button(onClick = if (apagado) alPulsarApagado else alPulsar, modifier = modifier.heightIn(min = alto)) { Text(texto) }
}

/** La salida suave como texto de 44 dp (D12). */
@Composable
fun BotonTexto(
    texto: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = alPulsar, modifier = modifier.heightIn(min = Medidas.salidaSuave)) { Text(texto) }
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
