package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Campo
import com.alvarotc.bito.ui.sistema.componentes.Hoja

/** tech doc 5.2: más corta, Argon2 defiende una passphrase fácil de adivinar. */
internal const val MIN_PASSPHRASE_LENGTH = 8

/** El recuadro rojo de «esto no se deshace» (aviso de passphrase y de restaurar). */
@Composable
internal fun AvisoPeligro(
    texto: String,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    Text(
        texto,
        modifier
            .fillMaxWidth()
            .background(colores.peligroFondo, BitoFormas.campo)
            .border(Medidas.linea, colores.peligro, BitoFormas.campo)
            .padding(Medidas.e12)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = BitoTema.tipos.t14,
        color = colores.peligroTexto,
    )
}

/**
 * Crea la passphrase de las copias (o la rehace si falta la clave). Las dos cadenas viven solo mientras la
 * hoja está abierta: se vacían al cifrar y al cerrar. [alCifrar] recibe la passphrase y el llamador la limpia.
 */
@Composable
fun HojaPassphrase(
    abierta: Boolean,
    alCifrar: (CharArray) -> Unit,
    alCerrar: () -> Unit,
) {
    var clave by remember { mutableStateOf("") }
    var repetida by remember { mutableStateOf("") }
    var temblorClave by remember { mutableIntStateOf(0) }
    var temblorRepetida by remember { mutableIntStateOf(0) }
    val colores = BitoTema.colores
    val largo = clave.length
    val corta = largo in 1 until MIN_PASSPHRASE_LENGTH
    val coinciden = largo >= MIN_PASSPHRASE_LENGTH && clave == repetida
    val noCoinciden = largo >= MIN_PASSPHRASE_LENGTH && repetida.length >= largo && repetida != clave

    fun limpiar() {
        clave = ""
        repetida = ""
    }
    Hoja(
        abierta = abierta,
        alCerrar = {
            limpiar()
            alCerrar()
        },
        seccion = colores.estratos.copias,
        etiqueta = stringResource(R.string.backups_title),
        titulo = stringResource(R.string.copias_pass_titulo),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Medidas.e12),
        ) {
            Text(stringResource(R.string.copias_pass_desc), style = BitoTema.tipos.t16, color = colores.tintaSuave)
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.e4)) {
                Campo(
                    valor = clave,
                    alCambiar = { clave = it },
                    etiqueta = stringResource(R.string.backup_passphrase),
                    oculto = true,
                    error =
                        if (corta) {
                            pluralStringResource(
                                R.plurals.copias_pass_faltan,
                                MIN_PASSPHRASE_LENGTH - largo,
                                MIN_PASSPHRASE_LENGTH - largo,
                            )
                        } else {
                            null
                        },
                    temblorClave = temblorClave,
                )
                if (!corta) {
                    Text(
                        stringResource(if (largo == 0) R.string.copias_pass_min else R.string.copias_pass_ok),
                        style = BitoTema.tipos.t14,
                        color = if (largo == 0) colores.tintaSuave else colores.okTexto,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Medidas.e4)) {
                Campo(
                    valor = repetida,
                    alCambiar = { repetida = it },
                    etiqueta = stringResource(R.string.backup_passphrase_repeat),
                    oculto = true,
                    error = if (noCoinciden) stringResource(R.string.copias_pass_no_coinciden) else null,
                    temblorClave = temblorRepetida,
                )
                if (!noCoinciden && largo >= MIN_PASSPHRASE_LENGTH) {
                    Text(
                        stringResource(if (coinciden) R.string.copias_pass_coinciden else R.string.copias_pass_repite),
                        style = BitoTema.tipos.t14,
                        color = if (coinciden) colores.okTexto else colores.tintaSuave,
                    )
                }
            }
            AvisoPeligro(stringResource(R.string.backup_encrypt_warning))
            BotonesHoja(
                principal = stringResource(R.string.copias_pass_boton),
                alPrincipal = {
                    val chars = clave.toCharArray()
                    limpiar()
                    alCifrar(chars)
                },
                suave = stringResource(R.string.sistema_cancelar),
                alSuave = { cerrar() },
                apagado = !coinciden,
                // Apagado, el botón explica temblando el campo que falla.
                alPulsarApagado = { if (largo < MIN_PASSPHRASE_LENGTH) temblorClave++ else temblorRepetida++ },
            )
        }
    }
}
