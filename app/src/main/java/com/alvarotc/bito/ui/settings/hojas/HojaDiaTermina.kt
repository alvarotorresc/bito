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
import com.alvarotc.bito.ui.sistema.componentes.Hoja

/** «El día termina a las»: reloj de 0:00 a 6:00 en pasos de media hora. */
@Composable
fun HojaDiaTermina(
    abierta: Boolean,
    minutos: Int,
    alGuardar: (Int) -> Unit,
    alCerrar: () -> Unit,
) {
    Hoja(
        abierta = abierta,
        alCerrar = alCerrar,
        seccion = BitoTema.colores.estratos.general,
        etiqueta = stringResource(R.string.hojas_estrato_general),
        titulo = stringResource(R.string.hojas_dia_titulo),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
            Text(stringResource(R.string.hojas_dia_desc), style = BitoTema.tipos.t14, color = BitoTema.colores.tintaSuave)
            PasoReloj(
                titulo = null,
                descripcion = stringResource(R.string.hojas_dia_reloj),
                minutos = minutos,
                guardar = stringResource(R.string.sistema_guardar),
                alGuardar = {
                    alGuardar(it)
                    cerrar()
                },
                suave = stringResource(R.string.sistema_ahora_no),
                alSuave = { cerrar() },
                paso = 30,
                desde = 0,
                hasta = 360,
                nota = stringResource(R.string.hojas_dia_nota),
            )
        }
    }
}
