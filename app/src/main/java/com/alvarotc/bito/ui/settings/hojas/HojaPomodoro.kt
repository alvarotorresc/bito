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
import com.alvarotc.bito.ui.sistema.componentes.Fila
import com.alvarotc.bito.ui.sistema.componentes.FilaInterruptor
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.Stepper

data class Pomodoro(
    val foco: Int,
    val corto: Int,
    val largo: Int,
    val rondas: Int,
    val descansoSolo: Boolean,
)

/** Cómo se parte una sesión de foco. Cada cambio se guarda en el acto. */
@Composable
fun HojaPomodoro(
    abierta: Boolean,
    valores: Pomodoro,
    alCambiar: (Pomodoro) -> Unit,
    alCerrar: () -> Unit,
) {
    val minutos = stringResource(R.string.hojas_pomo_minutos)
    Hoja(
        abierta = abierta,
        alCerrar = alCerrar,
        seccion = BitoTema.colores.estratos.avisos,
        etiqueta = stringResource(R.string.hojas_estrato_avisos),
        titulo = stringResource(R.string.hojas_pomo_titulo),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            Text(stringResource(R.string.hojas_pomo_desc), style = BitoTema.tipos.t14, color = BitoTema.colores.tintaSuave)
            Column {
                val foco = stringResource(R.string.hojas_pomo_foco)
                Fila(foco, subtitulo = minutos) {
                    Stepper(valores.foco, { alCambiar(valores.copy(foco = it)) }, 5, 90, foco, paso = 5)
                }
                val corto = stringResource(R.string.hojas_pomo_corto)
                Fila(corto, subtitulo = minutos) {
                    Stepper(valores.corto, { alCambiar(valores.copy(corto = it)) }, 1, 30, corto)
                }
                val largo = stringResource(R.string.hojas_pomo_largo)
                Fila(largo, subtitulo = minutos) {
                    Stepper(valores.largo, { alCambiar(valores.copy(largo = it)) }, 5, 45, largo, paso = 5)
                }
                val rondas = stringResource(R.string.hojas_pomo_rondas)
                Fila(rondas) {
                    Stepper(valores.rondas, { alCambiar(valores.copy(rondas = it)) }, 2, 8, rondas)
                }
                FilaInterruptor(
                    stringResource(R.string.hojas_pomo_solo),
                    valores.descansoSolo,
                    { alCambiar(valores.copy(descansoSolo = it)) },
                    subtitulo = stringResource(R.string.hojas_pomo_solo_nota),
                )
            }
            BotonesHoja(stringResource(R.string.hojas_rec_hecho), { cerrar() })
        }
    }
}
