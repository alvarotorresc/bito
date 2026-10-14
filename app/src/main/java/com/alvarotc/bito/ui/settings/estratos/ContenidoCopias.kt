package com.alvarotc.bito.ui.settings.estratos

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.AutoBackupError
import com.alvarotc.bito.data.settings.BackupFrequency
import com.alvarotc.bito.ui.settings.BackupMessage
import com.alvarotc.bito.ui.settings.BackupUiState
import com.alvarotc.bito.ui.settings.BackupViewModel
import com.alvarotc.bito.ui.settings.hojas.HojaExportar
import com.alvarotc.bito.ui.settings.hojas.HojaPassphrase
import com.alvarotc.bito.ui.settings.hojas.HojaRestaurar
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.LocalEstrato
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonAgua
import com.alvarotc.bito.ui.sistema.componentes.Dialogo
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.Fila
import com.alvarotc.bito.ui.sistema.componentes.FilaEnlace
import com.alvarotc.bito.ui.sistema.componentes.FilaInterruptor
import com.alvarotc.bito.ui.sistema.componentes.Segmentado
import com.alvarotc.bito.ui.sistema.componentes.Stepper
import com.alvarotc.bito.ui.sistema.componentes.rememberAvisoEstado
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MIN_COPIAS = 1
private const val MAX_COPIAS = 30
private const val AGUA_MS = 900L

/**
 * El cuerpo del estrato «Copias de seguridad». Aquí viven los lanzadores de carpeta, el aviso de los
 * mensajes del [BackupViewModel] y las hojas de passphrase, restaurar y exportar: ninguna otra pantalla
 * toca las copias. Los mensajes de la importación (passphrase incorrecta, archivo no válido) los recoge
 * [HojaRestaurar] mientras está abierta.
 */
@Composable
fun ContenidoCopias(
    backupViewModel: BackupViewModel,
    modifier: Modifier = Modifier,
) {
    val estado by backupViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val estrato = BitoTema.colores.estratos.copias
    val gesto = rememberGesto()
    val scope = rememberCoroutineScope()
    val avisos = rememberAvisoEstado()
    var verPassphrase by remember { mutableStateOf(false) }
    var verApagar by remember { mutableStateOf(false) }
    var restaurando by rememberSaveable { mutableStateOf(false) }
    var exportando by rememberSaveable { mutableStateOf(false) }
    var haciendoAhora by remember { mutableStateOf(false) }

    val lanzadorCarpeta =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                backupViewModel.setFolder(context.contentResolver, it)
            }
        }

    val textos =
        mapOf(
            BackupMessage.EXPORT_DONE to stringResource(R.string.msg_export_done),
            BackupMessage.IMPORT_DONE to stringResource(R.string.msg_import_done),
            BackupMessage.INVALID_FILE to stringResource(R.string.msg_invalid_file),
            BackupMessage.IO_ERROR to stringResource(R.string.msg_io_error),
            BackupMessage.ENCRYPTION_ON to stringResource(R.string.backup_encrypt_enabled_snack),
            BackupMessage.ENCRYPTION_OFF to stringResource(R.string.backup_encrypt_disabled_snack),
            BackupMessage.MISSING_KEY to stringResource(R.string.backup_missing_key_snack),
        )
    // Cada mensaje se consume, tenga texto o no; si no, uno idéntico seguido no volvería a disparar el efecto.
    // Con la hoja de restaurar abierta, los suyos (passphrase incorrecta, archivo no válido) son de la hoja.
    LaunchedEffect(estado.message, restaurando) {
        val mensaje = estado.message ?: return@LaunchedEffect
        val deLaHoja =
            restaurando && mensaje in setOf(BackupMessage.WRONG_PASSPHRASE, BackupMessage.INVALID_FILE, BackupMessage.IO_ERROR)
        if (deLaHoja) return@LaunchedEffect
        textos[mensaje]?.let { texto -> scope.launch { avisos.mostrar(texto) } }
        backupViewModel.consumeMessage()
    }

    CompositionLocalProvider(LocalEstrato provides estrato) {
        Box(modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
                Text(stringResource(R.string.backups_body), style = BitoTema.tipos.t16, color = estrato.tinta2)
                EstadoCopias(estado, alElegirCarpeta = { lanzadorCarpeta.launch(null) }, alReintentar = backupViewModel::backupNow) {
                    verPassphrase = true
                }
                if (estado.folderName != null) {
                    Fila(stringResource(R.string.backup_auto)) {
                        Segmentado(
                            opciones =
                                listOf(
                                    stringResource(R.string.backup_frequency_daily),
                                    stringResource(R.string.backup_frequency_weekly),
                                ),
                            elegida = if (estado.frequency == BackupFrequency.WEEKLY) 1 else 0,
                            alElegir = { backupViewModel.setFrequency(if (it == 1) BackupFrequency.WEEKLY else BackupFrequency.DAILY) },
                            modifier = Modifier.padding(start = Medidas.e8),
                        )
                    }
                    Fila(
                        etiqueta = stringResource(R.string.backup_keep),
                        subtitulo = pluralStringResource(R.plurals.copias_conservar_sub, estado.copies, estado.copies),
                    ) {
                        Stepper(
                            valor = estado.copies,
                            alCambiar = backupViewModel::setCopies,
                            min = MIN_COPIAS,
                            max = MAX_COPIAS,
                            descripcion = stringResource(R.string.copias_conservar_cd),
                        )
                    }
                }
                FilaInterruptor(
                    etiqueta = stringResource(R.string.backup_encrypt),
                    activo = estado.encryptionOn,
                    // La clave perdida con el cifrado aún activo se rehace por la misma hoja; solo «activo y sano» lo apaga.
                    alCambiar = { if (estado.encryptionOn && !estado.encryptionNeedsKey) verApagar = true else verPassphrase = true },
                    subtitulo =
                        stringResource(
                            if (estado.encryptionNeedsKey) R.string.backup_encrypt_needs_key else R.string.copias_cifrado_sub,
                        ),
                )
                Column(Modifier.padding(top = Medidas.e8), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
                    if (estado.folderName != null) {
                        BotonAgua(
                            texto = stringResource(R.string.backup_now),
                            trabajando = estado.busy || haciendoAhora,
                            alPulsar = {
                                // La copia la hace el trabajo en segundo plano: el agua sube durante lo que tarda en ponerse en marcha.
                                gesto(Snd.GORGOTEO, Haptica.TICK)
                                haciendoAhora = true
                                backupViewModel.backupNow()
                                scope.launch {
                                    delay(AGUA_MS)
                                    haciendoAhora = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Boton(stringResource(R.string.copias_restaurar), { restaurando = true }, Modifier.fillMaxWidth())
                    Boton(
                        stringResource(R.string.backup_export_manual),
                        { exportando = true },
                        Modifier.fillMaxWidth(),
                        estilo = EstiloBoton.FANTASMA,
                    )
                }
            }
            AvisoHost(avisos, Modifier.align(Alignment.BottomCenter))
        }

        HojaPassphrase(
            abierta = verPassphrase,
            alCifrar = { passphrase ->
                backupViewModel.enableEncryption(passphrase)
                verPassphrase = false
            },
            alCerrar = { verPassphrase = false },
        )
        Dialogo(
            abierto = verApagar,
            titulo = stringResource(R.string.copias_off_titulo),
            verbo = stringResource(R.string.copias_off_verbo),
            alConfirmar = {
                backupViewModel.disableEncryption()
                verApagar = false
            },
            alCancelar = { verApagar = false },
            cuerpo = stringResource(R.string.copias_off_cuerpo),
            peligro = true,
        )
        if (restaurando) HojaRestaurar(backupViewModel) { restaurando = false }
        if (exportando) HojaExportar(backupViewModel) { exportando = false }
    }
}

/** La fila de estado: sin carpeta, «Elegir carpeta»; con error, el recuadro con su acción; si no, carpeta y última copia. */
@Composable
private fun EstadoCopias(
    estado: BackupUiState,
    alElegirCarpeta: () -> Unit,
    alReintentar: () -> Unit,
    alPonerPassphrase: () -> Unit,
) {
    val folder = estado.folderName
    val error = estado.lastAutoBackupError
    when {
        folder == null -> FilaEnlace(stringResource(R.string.backup_pick_folder), alElegirCarpeta)
        error != null -> {
            val (titulo, detalle, accion) =
                when (error) {
                    AutoBackupError.FOLDER ->
                        Triple(R.string.copias_err_carpeta_t, R.string.copias_err_carpeta_d, R.string.copias_err_carpeta_a)
                    AutoBackupError.WRITE ->
                        Triple(R.string.copias_err_escritura_t, R.string.copias_err_escritura_d, R.string.copias_err_escritura_a)
                    AutoBackupError.MISSING_KEY ->
                        Triple(R.string.copias_err_clave_t, R.string.copias_err_clave_d, R.string.copias_err_clave_a)
                }
            val alAccion =
                when (error) {
                    AutoBackupError.FOLDER -> alElegirCarpeta
                    AutoBackupError.WRITE -> alReintentar
                    AutoBackupError.MISSING_KEY -> alPonerPassphrase
                }
            ErrorCopia(stringResource(titulo), stringResource(detalle), stringResource(accion), alAccion)
        }
        else -> {
            val cuenta =
                estado.backupCount?.let { pluralStringResource(R.plurals.backup_status_count, it, it, folder) }
                    ?: stringResource(R.string.backup_status_folder_only, folder)
            val ultima = estado.lastAutoBackupAtMillis
            if (ultima != null) {
                FilaEnlace(stringResource(R.string.backup_last_ok, relativo(ultima)), alElegirCarpeta, subtitulo = cuenta)
            } else {
                FilaEnlace(cuenta, alElegirCarpeta)
            }
        }
    }
}

/** El error se queda hasta resolverse: contorno de peligro, nunca relleno de culpa, y siempre con su acción. */
@Composable
private fun ErrorCopia(
    titulo: String,
    detalle: String,
    accion: String,
    alAccion: () -> Unit,
) {
    val colores = BitoTema.colores
    Column(
        Modifier
            .fillMaxWidth()
            .background(colores.peligroFondo, BitoFormas.campo)
            .border(Medidas.linea, colores.peligro, BitoFormas.campo)
            .padding(Medidas.e12),
        verticalArrangement = Arrangement.spacedBy(Medidas.e4),
    ) {
        Text(titulo, style = BitoTema.tipos.t16.fuerte(), color = colores.peligroTexto)
        Text(detalle, style = BitoTema.tipos.t14, color = colores.peligroTexto)
        Boton(accion, alAccion, Modifier.padding(top = Medidas.e8), estilo = EstiloBoton.NORMAL)
    }
}

/** «hoy, 03:12» / «ayer, 03:12» / «14 ago, 03:12». «Hoy» sale del reloj real del móvil, no del del ViewModel. */
@Composable
private fun relativo(millis: Long): String {
    val zona = ZoneId.systemDefault()
    val instante = Instant.ofEpochMilli(millis).atZone(zona)
    val hoy = LocalDate.now(zona)
    val dia =
        when (instante.toLocalDate()) {
            hoy -> stringResource(R.string.backup_status_today)
            hoy.minusDays(1) -> stringResource(R.string.backup_status_yesterday)
            else -> instante.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        }
    return "$dia, ${instante.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

/** La línea del estrato cerrado: carpeta y última copia, o el error. */
@Composable
fun resumenCopias(backupViewModel: BackupViewModel): String {
    val estado by backupViewModel.state.collectAsStateWithLifecycle()
    val error = estado.lastAutoBackupError
    return when {
        estado.folderName == null -> stringResource(R.string.copias_resumen_sin_carpeta)
        error != null ->
            stringResource(
                when (error) {
                    AutoBackupError.FOLDER -> R.string.copias_err_carpeta_t
                    AutoBackupError.WRITE -> R.string.copias_err_escritura_t
                    AutoBackupError.MISSING_KEY -> R.string.copias_err_clave_t
                },
            )
        else -> {
            val frecuencia =
                stringResource(
                    if (estado.frequency == BackupFrequency.WEEKLY) R.string.backup_frequency_weekly else R.string.backup_frequency_daily,
                )
            val cifrado = stringResource(if (estado.encryptionOn) R.string.copias_resumen_cifradas else R.string.copias_resumen_sin_cifrar)
            val ultima = estado.lastAutoBackupAtMillis?.let { stringResource(R.string.copias_resumen_ultima, relativo(it)) }
            listOfNotNull(frecuencia, cifrado, ultima).joinToString(" · ")
        }
    }
}
