package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.FilaValor
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.HojaPasos

private const val LISTA = 0
private const val MANANA = 1
private const val NOCHE = 2

/** Avisos de Habi: la mañana (6:00 a 11:00) y la noche (19:00 a 23:30), cada una con su reloj acotado. */
@Composable
fun HojaAvisosHabi(
    abierta: Boolean,
    manana: Int,
    noche: Int,
    alGuardarManana: (Int) -> Unit,
    alGuardarNoche: (Int) -> Unit,
    alCerrar: () -> Unit,
) {
    var paso by rememberSaveable { mutableIntStateOf(LISTA) }
    val colores = BitoTema.colores
    Hoja(
        abierta = abierta,
        alCerrar = {
            // Cerrar con atrás o con el velo no pasa por Guardar: la próxima vez debe abrirse en la lista.
            paso = LISTA
            alCerrar()
        },
        seccion = colores.estratos.avisos,
        etiqueta = stringResource(R.string.hojas_estrato_avisos),
        titulo = stringResource(R.string.hojas_habi_titulo),
    ) {
        HojaPasos(paso) { p ->
            when (p) {
                MANANA ->
                    PasoReloj(
                        titulo = stringResource(R.string.hojas_habi_aviso_manana),
                        descripcion = stringResource(R.string.hojas_habi_reloj_manana),
                        minutos = manana,
                        guardar = stringResource(R.string.sistema_guardar),
                        alGuardar = {
                            alGuardarManana(it)
                            paso = LISTA
                        },
                        suave = stringResource(R.string.hojas_atras),
                        alSuave = { paso = LISTA },
                        paso = 5,
                        desde = 6 * 60,
                        hasta = 11 * 60,
                    )
                NOCHE ->
                    PasoReloj(
                        titulo = stringResource(R.string.hojas_habi_aviso_noche),
                        descripcion = stringResource(R.string.hojas_habi_reloj_noche),
                        minutos = noche,
                        guardar = stringResource(R.string.sistema_guardar),
                        alGuardar = {
                            alGuardarNoche(it)
                            paso = LISTA
                        },
                        suave = stringResource(R.string.hojas_atras),
                        alSuave = { paso = LISTA },
                        paso = 5,
                        desde = 19 * 60,
                        hasta = 23 * 60 + 30,
                    )
                else ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
                        Text(stringResource(R.string.hojas_habi_desc), style = BitoTema.tipos.t14, color = colores.tintaSuave)
                        Column {
                            FilaValor(stringResource(R.string.hojas_habi_manana), hora(manana), { paso = MANANA })
                            FilaValor(
                                stringResource(R.string.hojas_habi_noche),
                                hora(noche),
                                { paso = NOCHE },
                                subtitulo = stringResource(R.string.hojas_habi_noche_nota),
                            )
                        }
                        Text(stringResource(R.string.hojas_habi_rangos), style = BitoTema.tipos.t14, color = colores.tintaSuave)
                        BotonesHoja(stringResource(R.string.hojas_rec_hecho), { cerrar() })
                    }
            }
        }
    }
}
