package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Hoja

/**
 * Hoja informativa del estrato Acerca (Apoya el proyecto, Licencia libre). Con [accion] lleva una acción
 * principal (por ejemplo «Ver licencia») y «Cerrar» como salida suave; sin ella, solo «Entendido».
 */
@Composable
fun HojaInfo(
    abierta: Boolean,
    titulo: String,
    cuerpo: String,
    alCerrar: () -> Unit,
    accion: String? = null,
    alAccion: () -> Unit = {},
) {
    Hoja(
        abierta = abierta,
        alCerrar = alCerrar,
        seccion = BitoTema.colores.estratos.acerca,
        etiqueta = stringResource(R.string.about_section_title),
        titulo = titulo,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e16)) {
            Text(cuerpo, style = BitoTema.tipos.t16, color = BitoTema.colores.tintaSuave)
            if (accion != null) {
                BotonesHoja(
                    principal = accion,
                    alPrincipal = {
                        cerrarY(alAccion)
                    },
                    suave = stringResource(R.string.sistema_cerrar),
                    alSuave = { cerrar() },
                )
            } else {
                BotonesHoja(principal = stringResource(R.string.donate_sheet_ok), alPrincipal = { cerrar() })
            }
        }
    }
}
