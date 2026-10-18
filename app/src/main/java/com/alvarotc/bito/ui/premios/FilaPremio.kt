package com.alvarotc.bito.ui.premios

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.ui.camino.arteDeEspecial
import com.alvarotc.bito.ui.camino.nombreDeEspecial
import com.alvarotc.bito.ui.habi.StoreItemThumb
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.fuerte

private val IMAGEN_PREMIO = 52.dp
private val ARRIBA_PREMIO = 14.dp
private val RELLENO_V_PREMIO = 10.dp

/** Lo que trae un logro y se ve en su hoja (§3.3.7, J18): una pieza exclusiva de la tienda o un especial del jardín. */
internal sealed interface Premio {
    data class Pieza(
        val item: CatalogItem,
        @StringRes val linea: Int,
        @StringRes val debajo: Int,
    ) : Premio

    data class Especial(val especial: GardenSpecial) : Premio
}

/**
 * El premio que enseña la hoja de [badgeId]. Si el logro da una pieza y un especial a la vez (Primera llama: la flor y
 * el farolillo; Año entero: el dorado y el árbol dorado), la hoja lleva la pieza y el especial se planta después, como
 * momento propio de la cola.
 */
internal fun premioDeLogro(badgeId: String): Premio? {
    val pieza =
        when (badgeId) {
            "streak-7" -> Triple("head-flor", R.string.store_reward_flower, R.string.store_reward_flower_under)
            "streak-30" -> Triple("neck-panuelo-brasa", R.string.store_reward_scarf, R.string.store_reward_scarf_under)
            "streak-100" -> Triple("head-corona", R.string.store_reward_crown, R.string.store_reward_crown_under)
            "streak-365" -> Triple("body-dorado", R.string.store_reward_gold, R.string.store_reward_gold_under)
            else -> null
        }
    if (pieza != null) return Premio.Pieza(HabiCatalog.byId(pieza.first)!!, pieza.second, pieza.third)
    return GardenSpecial.entries.firstOrNull { it.badgeId == badgeId }?.let { Premio.Especial(it) }
}

/** La fila de premio de la hoja de un logro nuevo: imagen de 52 dp, «Tienes la flor.» y dónde encontrarlo. */
@Composable
internal fun FilaPremio(
    premio: Premio,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val linea: String
    val debajo: String
    when (premio) {
        is Premio.Pieza -> {
            linea = stringResource(premio.linea)
            debajo = stringResource(premio.debajo)
        }
        is Premio.Especial -> {
            linea = stringResource(R.string.store_reward_special, stringResource(nombreDeEspecial(premio.especial)))
            debajo = stringResource(R.string.store_reward_special_under)
        }
    }
    Row(
        modifier
            .padding(top = ARRIBA_PREMIO)
            .fillMaxWidth()
            .clip(BitoFormas.campo)
            .background(colores.tAlta)
            .padding(horizontal = Medidas.e12, vertical = RELLENO_V_PREMIO)
            .testTag("fila-premio"),
        horizontalArrangement = Arrangement.spacedBy(Medidas.e12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (premio) {
            is Premio.Pieza -> StoreItemThumb(premio.item, Modifier.size(IMAGEN_PREMIO))
            is Premio.Especial ->
                Image(painterResource(arteDeEspecial(premio.especial)), contentDescription = null, Modifier.size(IMAGEN_PREMIO))
        }
        Column {
            Text(linea, Modifier.testTag("fila-premio-linea"), style = BitoTema.tipos.t16.fuerte(), color = colores.tinta)
            Text(debajo, Modifier.testTag("fila-premio-debajo"), style = BitoTema.tipos.t14, color = colores.tintaSuave)
        }
    }
}
