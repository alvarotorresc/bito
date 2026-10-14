package com.alvarotc.bito.ui.premios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Escena
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.Monedita
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.delay

private const val CLICK_RACHA_MS = 420L

/**
 * La franja de 64 dp de una celebración (G12, §6.3): la tierra de su tipo, el [arte] a la izquierda, [titulo] y
 * [frase], y «+[n]» en oro con su monedita. El sonido y la háptica salen al aparecer; descubrirla de arriba abajo y
 * el vuelo de las monedas son de quien la coloca. El «+N» registra el ancla [ORIGEN_VETA]: de ahí salen sus monedas.
 */
@Composable
fun Veta(
    tipo: TipoVeta,
    titulo: String,
    frase: String,
    n: Int,
    arte: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val tierra =
        when (tipo) {
            TipoVeta.DIA, TipoVeta.SEMANA -> colores.tMedia2
            TipoVeta.MES -> colores.tHonda2
            TipoVeta.RACHA -> colores.tHonda
        }
    val gesto = rememberGesto()
    LaunchedEffect(tipo) {
        when (tipo) {
            TipoVeta.DIA, TipoVeta.SEMANA -> gesto(Snd.DIA_COMPLETO, Haptica.CONFIRM)
            TipoVeta.MES -> gesto(Snd.DIA_COMPLETO)
            TipoVeta.RACHA -> {
                gesto(Snd.RACHA, Haptica.RISE)
                delay(CLICK_RACHA_MS)
                gesto(h = Haptica.CLICK)
            }
        }
    }
    val costra = colores.costra
    Row(
        modifier
            .testTag("veta-$tipo")
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(tierra)
            .drawBehind { drawRect(costra, size = Size(size.width, 6.dp.toPx())) }
            .padding(start = Medidas.e12, end = Medidas.e18, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Medidas.e12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { arte() }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = BitoTema.tipos.t19.fuerte(), color = colores.tintaClara, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (frase.isNotBlank()) {
                Text(frase, style = BitoTema.tipos.t14, color = colores.tintaClara, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.anclaParticula(ORIGEN_VETA), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.premios_mas_n, n), style = BitoTema.tipos.t24.cifra(), color = Escena.monedaClaro)
            Box(Modifier.padding(start = 6.dp)) { Monedita(18.dp) }
        }
    }
}
