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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.FilaInterruptor
import com.alvarotc.bito.ui.sistema.componentes.FilaValor
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.HojaPasos

private const val LISTA = 0
private const val DESDE = 1
private const val HASTA = 2

/** No molestar: horario propio de Bito. Apagado, las dos horas quedan al 45 % y sin respuesta. */
@Composable
fun HojaNoMolestar(
    abierta: Boolean,
    activo: Boolean,
    desde: Int,
    hasta: Int,
    permisoDenegado: Boolean,
    alCambiarActivo: (Boolean) -> Unit,
    alCambiarDesde: (Int) -> Unit,
    alCambiarHasta: (Int) -> Unit,
    alPedirPermiso: () -> Unit,
    alCerrar: () -> Unit,
) {
    var paso by rememberSaveable { mutableIntStateOf(LISTA) }
    val colores = BitoTema.colores
    Hoja(
        abierta = abierta,
        alCerrar = alCerrar,
        seccion = colores.estratos.avisos,
        etiqueta = stringResource(R.string.hojas_estrato_avisos),
        titulo = stringResource(R.string.hojas_nm_titulo),
    ) {
        HojaPasos(paso) { p ->
            if (p == LISTA) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
                    Text(stringResource(R.string.hojas_nm_desc), style = BitoTema.tipos.t14, color = colores.tintaSuave)
                    Column {
                        FilaInterruptor(stringResource(R.string.hojas_nm_activar), activo, alCambiarActivo)
                        val bloqueo = Modifier.semantics(mergeDescendants = true) { if (!activo) disabled() }
                        FilaValor(stringResource(R.string.hojas_nm_desde), hora(desde), { paso = DESDE }, bloqueo, habilitada = activo)
                        FilaValor(stringResource(R.string.hojas_nm_hasta), hora(hasta), { paso = HASTA }, bloqueo, habilitada = activo)
                    }
                    if (permisoDenegado) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(colores.campo, BitoFormas.campo)
                                .border(Medidas.linea, colores.contorno, BitoFormas.campo)
                                .padding(Medidas.e12),
                            verticalArrangement = Arrangement.spacedBy(Medidas.e8),
                        ) {
                            Text(stringResource(R.string.hojas_nm_permiso), style = BitoTema.tipos.t14, color = colores.tinta)
                            Boton(stringResource(R.string.hojas_nm_permitir), alPedirPermiso, estilo = EstiloBoton.NORMAL)
                        }
                    }
                    BotonesHoja(stringResource(R.string.hojas_rec_hecho), { cerrar() })
                }
            } else {
                val esDesde = p == DESDE
                PasoReloj(
                    titulo = stringResource(if (esDesde) R.string.hojas_nm_reloj_desde else R.string.hojas_nm_reloj_hasta),
                    descripcion = stringResource(if (esDesde) R.string.hojas_nm_desde else R.string.hojas_nm_hasta),
                    minutos = if (esDesde) desde else hasta,
                    guardar = stringResource(R.string.sistema_guardar),
                    alGuardar = {
                        if (esDesde) alCambiarDesde(it) else alCambiarHasta(it)
                        paso = LISTA
                    },
                    suave = stringResource(R.string.hojas_atras),
                    alSuave = { paso = LISTA },
                    paso = 30,
                )
            }
        }
    }
}
