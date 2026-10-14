package com.alvarotc.bito.ui.settings.hojas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.settings.BackupViewModel
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

/** Hoja corta de «Exportar copia…»: el nombre sugerido y «Guardar archivo», que abre el selector de documentos. */
@Composable
fun HojaExportar(
    backupViewModel: BackupViewModel,
    alCerrar: () -> Unit,
) {
    val context = LocalContext.current
    val estado by backupViewModel.state.collectAsStateWithLifecycle()
    val gesto = rememberGesto()
    val nombre = remember { backupViewModel.suggestedFileName() }
    // Se cierra en cuanto el selector devuelve un documento; si se cancela, la hoja sigue abierta.
    val cerrarAlGuardar = remember { arrayOfNulls<() -> Unit>(1) }
    val lanzador =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            uri?.let {
                backupViewModel.export(context.contentResolver, it)
                gesto(Snd.LLEGADA, Haptica.CONFIRM)
                cerrarAlGuardar[0]?.invoke()
            }
        }
    val colores = BitoTema.colores
    Hoja(
        abierta = true,
        alCerrar = alCerrar,
        seccion = colores.estratos.copias,
        etiqueta = stringResource(R.string.backups_title),
        titulo = stringResource(R.string.copias_exp_titulo),
    ) {
        cerrarAlGuardar[0] = { cerrar() }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e12)) {
            Text(stringResource(R.string.copias_exp_desc), style = BitoTema.tipos.t16, color = colores.tintaSuave)
            Text(
                stringResource(if (estado.encryptionOn) R.string.copias_exp_cifrada else R.string.copias_exp_plana),
                style = BitoTema.tipos.t14,
                color = colores.tintaSuave,
            )
            Text(nombre, style = BitoTema.tipos.t14, color = colores.tinta)
            BotonesHoja(
                principal = stringResource(R.string.copias_exp_guardar),
                alPrincipal = { lanzador.launch(nombre) },
                suave = stringResource(R.string.sistema_cancelar),
                alSuave = { cerrar() },
                apagado = estado.busy,
            )
        }
    }
}
