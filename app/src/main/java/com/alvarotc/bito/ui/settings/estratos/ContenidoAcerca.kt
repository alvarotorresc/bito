package com.alvarotc.bito.ui.settings.estratos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.BuildConfig
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.settings.hojas.HojaInfo
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.LocalEstrato
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import com.alvarotc.bito.ui.sistema.componentes.Fila
import com.alvarotc.bito.ui.sistema.componentes.FilaEnlace
import com.alvarotc.bito.ui.sistema.componentes.rememberAvisoEstado
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SOURCE_URL = "https://github.com/alvarotorresc/bito"
private const val LICENSE_URL = "$SOURCE_URL/blob/main/LICENSE"

/** El formulario de reporte público (se contesta sin cuenta); la fila lo abre en el navegador. */
private const val FEEDBACK_URL = "https://tally.so/r/dWe65r"

/** Lo que tarda el aviso «Se abre … en el navegador» en leerse antes de salir de la app. */
private const val AVISO_ANTES_DE_SALIR_MS = 700L

/** El cuerpo del estrato «Acerca de»: versión, código, apoyo, licencia y el formulario. */
@Composable
fun ContenidoAcerca(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val estrato = BitoTema.colores.estratos.acerca
    val scope = rememberCoroutineScope()
    val avisos = rememberAvisoEstado()
    var verApoyo by remember { mutableStateOf(false) }
    var verLicencia by remember { mutableStateOf(false) }

    // Salir de la app se avisa antes: el aviso se lee y después se abre el navegador.
    fun abrirFuera(
        aviso: String,
        url: String,
    ) {
        scope.launch { avisos.mostrar(aviso) }
        scope.launch {
            delay(AVISO_ANTES_DE_SALIR_MS)
            abrirUrl(context, url)
        }
    }
    val avisoGithub = stringResource(R.string.copias_abre_github)
    val avisoFormulario = stringResource(R.string.copias_abre_formulario)
    val avisoLicencia = stringResource(R.string.copias_abre_licencia)

    CompositionLocalProvider(LocalEstrato provides estrato) {
        Box(modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Fila(stringResource(R.string.copias_version)) {
                    Text(BuildConfig.VERSION_NAME, style = BitoTema.tipos.t16, color = estrato.tinta2)
                }
                FilaEnlace(
                    stringResource(R.string.about_source_row),
                    { abrirFuera(avisoGithub, SOURCE_URL) },
                    subtitulo = stringResource(R.string.copias_codigo_url),
                    externo = true,
                )
                FilaEnlace(stringResource(R.string.about_support_row), { verApoyo = true })
                FilaEnlace(
                    stringResource(R.string.about_license_row),
                    { verLicencia = true },
                    subtitulo = stringResource(R.string.copias_licencia_sub),
                )
                FilaEnlace(
                    stringResource(R.string.about_feedback_row),
                    { abrirFuera(avisoFormulario, FEEDBACK_URL) },
                    subtitulo = stringResource(R.string.about_feedback_hint),
                    externo = true,
                )
            }
            AvisoHost(avisos, Modifier.align(Alignment.BottomCenter))
        }
        HojaInfo(
            abierta = verApoyo,
            titulo = stringResource(R.string.about_support_row),
            cuerpo = stringResource(R.string.donate_sheet_body),
            alCerrar = { verApoyo = false },
        )
        HojaInfo(
            abierta = verLicencia,
            titulo = stringResource(R.string.about_license_row),
            cuerpo = stringResource(R.string.license_sheet_body),
            alCerrar = { verLicencia = false },
            accion = stringResource(R.string.license_sheet_view),
            alAccion = { abrirFuera(avisoLicencia, LICENSE_URL) },
        )
    }
}

/** runCatching: un móvil sin navegador lanza ActivityNotFoundException; no hacer nada es mejor que cerrar la app. */
private fun abrirUrl(
    context: Context,
    url: String,
) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
fun resumenAcerca(): String = BuildConfig.VERSION_NAME
