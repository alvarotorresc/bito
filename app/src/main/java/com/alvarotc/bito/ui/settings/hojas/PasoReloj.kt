package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.RelojCielo
import com.alvarotc.bito.ui.sistema.componentes.RelojLogica
import com.alvarotc.bito.ui.sistema.fuerte

internal fun hora(minutos: Int): String = "%d:%02d".format(minutos / 60, minutos % 60)

/** Un paso de hoja con el reloj de cielo: título, reloj, nota opcional y los dos botones. */
@Composable
internal fun PasoReloj(
    titulo: String?,
    descripcion: String,
    minutos: Int,
    guardar: String,
    alGuardar: (Int) -> Unit,
    suave: String,
    alSuave: () -> Unit,
    paso: Int = 5,
    desde: Int = 0,
    hasta: Int = RelojLogica.DIA - 1,
    nota: String? = null,
) {
    var valor by rememberSaveable { mutableIntStateOf(minutos) }
    val colores = BitoTema.colores
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
        if (titulo != null) Text(titulo, style = BitoTema.tipos.t16.fuerte(), color = colores.tinta)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RelojCielo(valor, { valor = it }, Modifier.size(260.dp), paso = paso, desde = desde, hasta = hasta, descripcion = descripcion)
        }
        if (nota != null) Text(nota, style = BitoTema.tipos.t14, color = colores.tintaSuave)
        BotonesHoja(guardar, { alGuardar(valor) }, Modifier.padding(top = Medidas.e8), suave = suave, alSuave = alSuave)
    }
}
