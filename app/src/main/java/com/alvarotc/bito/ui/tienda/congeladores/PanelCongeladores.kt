package com.alvarotc.bito.ui.tienda.congeladores

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.componentes.CifraRodillo
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.EscenaArmario
import com.alvarotc.bito.ui.tienda.PanelTienda
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// (sin token) `.tc-sec` y `.tc-veta` de tienda-congeladores.css.
private val ARRIBA_TITULO = 20.dp
private val LATERAL = 20.dp
private val ARRIBA_PISTA = 4.dp
private val ARRIBA_VETA = 24.dp
private val ABAJO_LISTA = 24.dp
private val HUECO_TIENES = 6.dp

/** La pista bajo el título según la voz de Ajustes. */
@StringRes
internal fun pistaDe(voz: Personality): Int =
    when (voz) {
        Personality.NEUTRA -> R.string.store_freezer_hint_neutra
        Personality.SARGENTO -> R.string.store_freezer_hint_sargento
        Personality.CHEERLEADER -> R.string.store_freezer_hint_cheerleader
    }

/** La pestaña Congeladores: el armario sin prueba y, debajo, el título, la pista, la veta fría y el botón directo. */
object PanelCongeladores : PanelTienda {
    @Composable
    override fun Escena(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        EscenaArmario(look = ctx.estado.puestos, luz = ctx.luz, estado = ctx.armario, modifier = modifier)
    }

    @Composable
    override fun Catalogo(
        ctx: ContextoPanel,
        modifier: Modifier,
    ) {
        CatalogoCongeladores(
            voz = vozDeAjustes(),
            congeladores = ctx.estado.congeladores,
            saldo = ctx.estado.saldo,
            precio = EconomyConfig().freezerPrice,
            alComprar = ctx.acciones::comprarCongelador,
            modifier = modifier,
        )
    }
}

/** El cuerpo de la pestaña, sin estado propio: todo entra por parámetros. */
@Composable
internal fun CatalogoCongeladores(
    voz: Personality,
    congeladores: Int,
    saldo: Int,
    precio: Int,
    alComprar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.store_freezer_title),
            Modifier.padding(start = LATERAL, end = LATERAL, top = ARRIBA_TITULO).semantics { heading() },
            style = BitoTema.tipos.t19.copy(fontWeight = FontWeight.Bold),
            color = colores.tinta,
        )
        Text(
            stringResource(pistaDe(voz)),
            Modifier.padding(start = LATERAL, end = LATERAL, top = ARRIBA_PISTA).testTag("tc-pista"),
            style = BitoTema.tipos.t14.copy(lineHeight = 19.6.sp),
            color = colores.tintaSuave,
        )
        Spacer(Modifier.height(ARRIBA_VETA))
        VetaFria { tintaSuave ->
            Tienes(congeladores)
            Copitos(congeladores, tintaSuave = tintaSuave)
            BotonCongelador(precio, saldo, alComprar, tintaSuave = tintaSuave)
        }
        Spacer(Modifier.height(ABAJO_LISTA))
    }
}

/** «Tienes N»: el número rueda. La frase la pone el recurso, que también decide dónde cae la cifra. */
@Composable
private fun Tienes(cantidad: Int) {
    val colores = BitoTema.colores
    val total = stringResource(R.string.freezers_owned_label, cantidad)
    val molde = stringResource(R.string.freezers_owned_label, MARCA)
    val (antes, despues) = molde.split(MARCA.toString()).let { it.first() to it.getOrElse(1) { "" } }
    val estilo = BitoTema.tipos.t16.copy(fontWeight = FontWeight.Bold)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = total },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(antes.trimEnd(), style = estilo, color = colores.tinta)
        Spacer(Modifier.width(HUECO_TIENES))
        CifraRodillo(cantidad, estilo = estilo.cifra().copy(fontWeight = FontWeight.Bold), color = colores.tinta)
        if (despues.isNotBlank()) {
            Spacer(Modifier.width(HUECO_TIENES))
            Text(despues.trimStart(), style = estilo, color = colores.tinta)
        }
    }
}

private const val MARCA = -1

/** La voz que eligió el usuario en Ajustes; Neutra mientras no llega. */
@Composable
private fun vozDeAjustes(): Personality {
    val contexto = LocalContext.current
    val flujo =
        remember(contexto) {
            runCatching { (contexto.applicationContext as BitoApp).container.settings.settings.map { it.personality } }
                .getOrNull() ?: flowOf(Personality.NEUTRA)
        }
    return flujo.collectAsState(Personality.NEUTRA).value
}
