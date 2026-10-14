package com.alvarotc.bito.ui.settings.hojas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.data.backup.BackupPreview
import com.alvarotc.bito.ui.settings.BackupMessage
import com.alvarotc.bito.ui.settings.BackupViewModel
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Campo
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.HojaPasos
import com.alvarotc.bito.ui.sistema.componentes.temblor
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

private const val PASO_ARCHIVO = 0
private const val PASO_CLAVE = 1

/**
 * Restaurar una copia en una sola hoja con pasos (D8, sin diálogo): archivo, passphrase si está cifrada, y
 * la vista previa «Ahora» / «La copia». Sin cifrar son dos pasos. Todo el estado de la importación vive en
 * [backupViewModel]; esta hoja solo lo muestra y recoge los mensajes que le tocan (passphrase incorrecta,
 * archivo no válido, error), que [com.alvarotc.bito.ui.settings.estratos.ContenidoCopias] deja pasar.
 */
@Composable
fun HojaRestaurar(
    backupViewModel: BackupViewModel,
    alCerrar: () -> Unit,
) {
    val context = LocalContext.current
    val estado by backupViewModel.state.collectAsStateWithLifecycle()
    val gesto = rememberGesto()
    val colores = BitoTema.colores
    var cifrada by remember { mutableStateOf(false) }
    var clave by remember { mutableStateOf("") }
    var claveMala by remember { mutableStateOf(false) }
    var temblores by remember { mutableIntStateOf(0) }
    var errorArchivo by remember { mutableStateOf<BackupMessage?>(null) }
    var confirmado by remember { mutableStateOf(false) }
    val cerrarRef = remember { arrayOfNulls<() -> Unit>(1) }

    val lanzador =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                errorArchivo = null
                backupViewModel.loadImport(context.contentResolver, it)
            }
        }

    val preview = estado.preview
    val paso =
        when {
            preview != null -> if (cifrada) PASO_CLAVE + 1 else PASO_CLAVE
            estado.askImportPassphrase -> PASO_CLAVE
            else -> PASO_ARCHIVO
        }
    val total = if (cifrada) 3 else 2

    LaunchedEffect(estado.askImportPassphrase, paso) {
        if (estado.askImportPassphrase) {
            cifrada = true
        } else if (paso == PASO_ARCHIVO) {
            cifrada = false
        }
    }
    // El peso del último paso se anuncia al llegar a él.
    LaunchedEffect(preview != null) {
        if (preview != null) gesto(Snd.AVISO, Haptica.REJECT)
    }
    LaunchedEffect(estado.message) {
        when (estado.message) {
            BackupMessage.WRONG_PASSPHRASE -> {
                if (estado.askImportPassphrase) {
                    claveMala = true
                    temblores++
                    gesto(Snd.AVISO, Haptica.REJECT)
                }
                backupViewModel.consumeMessage()
            }
            BackupMessage.INVALID_FILE, BackupMessage.IO_ERROR -> {
                confirmado = false
                errorArchivo = estado.message
                backupViewModel.consumeMessage()
            }
            BackupMessage.IMPORT_DONE ->
                if (confirmado) {
                    gesto(Snd.ASENTAR, Haptica.CONFIRM)
                    cerrarRef[0]?.invoke()
                }
            else -> {}
        }
    }

    val titulo =
        when {
            preview != null -> stringResource(R.string.import_preview_title)
            estado.askImportPassphrase -> stringResource(R.string.copias_rest_cifrada)
            else -> stringResource(R.string.copias_rest_titulo)
        }
    Hoja(
        abierta = true,
        alCerrar = {
            clave = ""
            backupViewModel.dismissImport()
            backupViewModel.dismissImportPassphrase()
            alCerrar()
        },
        seccion = colores.estratos.copias,
        etiqueta = stringResource(R.string.backups_title),
        titulo = titulo,
    ) {
        cerrarRef[0] = { cerrar() }
        Column(Modifier.fillMaxWidth()) {
            PasosPuntos(paso + 1, total)
            HojaPasos(paso) { p ->
                Column(Modifier.fillMaxWidth().padding(top = Medidas.e12), verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
                    when {
                        p == PASO_ARCHIVO -> {
                            Text(stringResource(R.string.copias_rest_desc), style = BitoTema.tipos.t16, color = colores.tintaSuave)
                            errorArchivo?.let {
                                AvisoPeligro(
                                    stringResource(
                                        if (it == BackupMessage.INVALID_FILE) R.string.msg_invalid_file else R.string.msg_io_error,
                                    ),
                                )
                            }
                            BotonesHoja(
                                principal = stringResource(R.string.copias_rest_elegir),
                                alPrincipal = {
                                    // SAF no filtra por una extensión propia («.bito»): se acepta todo y la vista previa rechaza lo que no sea.
                                    lanzador.launch(arrayOf("*/*"))
                                },
                                suave = stringResource(R.string.sistema_cancelar),
                                alSuave = { cerrar() },
                                apagado = estado.busy,
                            )
                        }
                        p == PASO_CLAVE && preview == null -> {
                            Text(stringResource(R.string.copias_rest_cifrada_desc), style = BitoTema.tipos.t16, color = colores.tintaSuave)
                            Campo(
                                valor = clave,
                                alCambiar = {
                                    clave = it
                                    claveMala = false
                                },
                                modifier = Modifier.temblor(temblores, fuerte = true),
                                etiqueta = stringResource(R.string.backup_passphrase),
                                oculto = true,
                                error = if (claveMala) stringResource(R.string.backup_passphrase_wrong) else null,
                            )
                            BotonesHoja(
                                principal = stringResource(R.string.copias_rest_seguir),
                                alPrincipal = {
                                    val chars = clave.toCharArray()
                                    clave = ""
                                    claveMala = false
                                    backupViewModel.submitImportPassphrase(chars)
                                },
                                suave = stringResource(R.string.copias_rest_atras),
                                alSuave = {
                                    clave = ""
                                    claveMala = false
                                    backupViewModel.dismissImportPassphrase()
                                },
                                apagado = clave.isEmpty() || estado.busy,
                            )
                        }
                        preview != null ->
                            VistaPrevia(
                                preview = preview,
                                ahora = estado.ahora,
                                alConfirmar = {
                                    confirmado = true
                                    backupViewModel.confirmImport()
                                },
                                alDescartar = { cerrar() },
                                ocupado = estado.busy,
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasosPuntos(
    actual: Int,
    total: Int,
) {
    val colores = BitoTema.colores
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Medidas.e4)) {
            repeat(total) { i ->
                Box(
                    Modifier.size(8.dp).background(if (i < actual) colores.agua else colores.piedra, CircleShape),
                )
            }
        }
        Text(stringResource(R.string.copias_paso, actual, total), style = BitoTema.tipos.t14, color = colores.tintaSuave)
    }
}

@Composable
private fun VistaPrevia(
    preview: BackupPreview,
    ahora: BackupPreview?,
    alConfirmar: () -> Unit,
    alDescartar: () -> Unit,
    ocupado: Boolean,
) {
    val colores = BitoTema.colores
    Text(
        stringResource(R.string.import_preview_meta, preview.exportedAt.take(10), preview.appVersion),
        style = BitoTema.tipos.t14,
        color = colores.tintaSuave,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
        FilaCuadro("", stringResource(R.string.copias_ahora), stringResource(R.string.copias_la_copia), cabecera = true)
        FilaCuadro(
            stringResource(R.string.copias_habitos),
            ahora?.habits?.toString() ?: "-",
            preview.habits.toString(),
        )
        FilaCuadro(
            stringResource(R.string.copias_registros),
            ahora?.entries?.toString() ?: "-",
            preview.entries.toString(),
        )
        FilaCuadro(
            stringResource(R.string.copias_recuerdos),
            ahora?.recuerdos?.toString() ?: "-",
            preview.recuerdos.toString(),
        )
    }
    AvisoPeligro(stringResource(R.string.import_preview_warning))
    BotonesHoja(
        principal = stringResource(R.string.import_confirm),
        alPrincipal = alConfirmar,
        suave = stringResource(R.string.sistema_ahora_no),
        alSuave = alDescartar,
        peligro = true,
        apagado = ocupado,
    )
}

@Composable
private fun FilaCuadro(
    etiqueta: String,
    ahora: String,
    copia: String,
    cabecera: Boolean = false,
) {
    val colores = BitoTema.colores
    val estilo = if (cabecera) BitoTema.tipos.t14.fuerte() else BitoTema.tipos.t16
    val color = if (cabecera) colores.tintaSuave else colores.tinta
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, Modifier.weight(1.4f), style = estilo, color = color)
        Text(ahora, Modifier.weight(1f), style = estilo, color = color, textAlign = TextAlign.End)
        Text(copia, Modifier.weight(1f), style = estilo, color = color, textAlign = TextAlign.End)
    }
}
