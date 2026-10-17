package com.alvarotc.bito.ui.tienda.jardin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.ui.garden.GardenArt
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Gesto
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.stats.BadgeStrings
import com.alvarotc.bito.ui.tienda.AroHornacina
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.EtiquetaHornacina
import com.alvarotc.bito.ui.tienda.HornacinaPieza
import com.alvarotc.bito.ui.tienda.PruebaTienda

/** Las medidas del catálogo del Jardín (tienda-jardin.css `.tj-sec`, `.tj-grid`, `.tj-fin`, `.tj-lista`). Sin token. */
private object MedidasCatalogo {
    val COLUMNAS = 3
    val MARGEN_CABECERA = 20.dp
    val ARRIBA_CABECERA = 20.dp
    val ARRIBA_PISTA = 4.dp
    val HUECO_X = 10.dp
    val HUECO_Y = 16.dp
    val ARRIBA_REJILLA = 14.dp
    val LADOS_REJILLA = 16.dp
    val FINAL = 40.dp
    val RELLENO_SCROLL = 160.dp
    val LETRA_CABECERA = 19.sp
    val LETRA_PISTA = 14.sp
    val ALTO_PISTA = 19.6.sp // 14 sp a 1,4 de interlineado
}

/** Un objeto o especial del Jardín. [clave] es la del arte («banco», «farolillo»); [itemId] solo lo tienen los que se compran. */
private class Elemento(val clave: String, val itemId: String?, val especial: GardenSpecial?)

private val OBJETOS = listOf(Elemento("banco", "garden-banco", null), Elemento("regadera", "garden-regadera", null))
private val ESPECIALES = GardenSpecial.entries.map { Elemento(it.key, null, it) }

/**
 * El gesto de tocar algo del Jardín, el mismo desde el catálogo que desde la escena: lo que se prueba se quita; lo que ya
 * es tuyo (un objeto comprado o un especial ganado) quita la prueba, va a su sitio y se mece, sin barra; lo demás se prueba.
 */
internal fun tocarElementoJardin(
    ctx: ContextoPanel,
    gesto: Gesto,
    clave: String,
) {
    val estado = ctx.estado
    val objeto = OBJETOS.firstOrNull { it.clave == clave }
    val especial = GardenSpecial.entries.firstOrNull { it.key == clave }
    if (objeto == null && especial == null) return
    val enPrueba =
        when (val p = estado.prueba) {
            is PruebaTienda.Objeto -> objeto?.itemId == p.itemId
            is PruebaTienda.Especial -> especial?.badgeId == p.logroId
            else -> false
        }
    val tuyo = if (objeto != null) clave in estado.jardin.objects else especial in estado.jardin.specials
    gesto(h = Haptica.TICK)
    when {
        enPrueba -> {
            ctx.acciones.quitarPrueba()
            gesto(h = Haptica.LOW)
        }
        tuyo -> {
            if (estado.prueba != null) ctx.acciones.quitarPrueba()
            ctx.jardin.tocarTuyo(clave)
        }
        objeto != null -> ctx.acciones.tocarPieza(objeto.itemId.orEmpty())
        else -> ctx.acciones.tocarEspecial(especial?.badgeId.orEmpty())
    }
}

/**
 * La lista de la pestaña Jardín: «Para tu jardín» (banco y regadera, con precio) y «Por logros» (los seis especiales), en
 * rejilla de tres columnas, sin riel de raíz.
 */
@Composable
fun CatalogoJardin(
    ctx: ContextoPanel,
    modifier: Modifier = Modifier,
) {
    val gesto = rememberGesto()
    val ctxVivo by rememberUpdatedState(ctx)
    val alTocar: (String) -> Unit = { clave -> tocarElementoJardin(ctxVivo, gesto, clave) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = MedidasCatalogo.RELLENO_SCROLL)
            .testTag("tj-catalogo"),
    ) {
        Seccion(stringResource(R.string.store_garden_for_you), stringResource(R.string.store_garden_hint1), "tj-cabecera-venta")
        Rejilla(OBJETOS, ctx, alTocar)
        Seccion(stringResource(R.string.store_garden_badges), stringResource(R.string.store_garden_hint2), "tj-cabecera-logros")
        Rejilla(ESPECIALES, ctx, alTocar)
        Spacer(Modifier.height(MedidasCatalogo.FINAL))
    }
}

@Composable
private fun Seccion(
    titulo: String,
    pista: String,
    etiqueta: String,
) {
    val colores = BitoTema.colores
    val tipos = BitoTema.tipos
    Text(
        titulo,
        Modifier
            .padding(start = MedidasCatalogo.MARGEN_CABECERA, end = MedidasCatalogo.MARGEN_CABECERA, top = MedidasCatalogo.ARRIBA_CABECERA)
            .testTag(etiqueta),
        style = tipos.t19.copy(fontSize = MedidasCatalogo.LETRA_CABECERA, fontWeight = FontWeight.Bold),
        color = colores.tinta,
    )
    Text(
        pista,
        Modifier.padding(
            start = MedidasCatalogo.MARGEN_CABECERA,
            end = MedidasCatalogo.MARGEN_CABECERA,
            top = MedidasCatalogo.ARRIBA_PISTA,
        ),
        style = tipos.t14.copy(fontSize = MedidasCatalogo.LETRA_PISTA, lineHeight = MedidasCatalogo.ALTO_PISTA),
        color = colores.tintaSuave,
    )
}

@Composable
private fun Rejilla(
    elementos: List<Elemento>,
    ctx: ContextoPanel,
    alTocar: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = MedidasCatalogo.LADOS_REJILLA, end = MedidasCatalogo.LADOS_REJILLA, top = MedidasCatalogo.ARRIBA_REJILLA),
        verticalArrangement = Arrangement.spacedBy(MedidasCatalogo.HUECO_Y),
    ) {
        elementos.chunked(MedidasCatalogo.COLUMNAS).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MedidasCatalogo.HUECO_X)) {
                fila.forEach { e -> Box(Modifier.weight(1f)) { Celda(e, ctx, alTocar) } }
                repeat(MedidasCatalogo.COLUMNAS - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Celda(
    e: Elemento,
    ctx: ContextoPanel,
    alTocar: (String) -> Unit,
) {
    val estado = ctx.estado
    val prueba = estado.prueba
    val probando =
        when (prueba) {
            is PruebaTienda.Objeto -> e.itemId == prueba.itemId
            is PruebaTienda.Especial -> e.especial?.badgeId == prueba.logroId
            else -> false
        }
    val tuyo = if (e.especial == null) e.clave in estado.jardin.objects else e.especial in estado.jardin.specials
    val nombre = stringResource(nombreDe(e))
    val logro = e.especial?.let { stringResource(BadgeStrings.badgeNameRes(it.badgeId)) }
    val etiqueta =
        when {
            probando -> EtiquetaHornacina.PROBANDO
            tuyo -> EtiquetaHornacina.EN_TU_JARDIN
            e.especial != null -> EtiquetaHornacina.LOGRO
            else -> EtiquetaHornacina.PRECIO
        }
    val res = (GardenArt.objectPieces[e.clave] ?: GardenArt.specials.getValue(e.clave)).res
    Box(Modifier.testTag("tj-${e.clave}")) {
        HornacinaPieza(
            imagen = { Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) },
            nombre = nombre,
            etiqueta = etiqueta,
            textoEtiqueta = if (etiqueta == EtiquetaHornacina.LOGRO) logro else null,
            precio = e.itemId?.let { HabiCatalog.byId(it)?.price },
            aro =
                when {
                    probando -> AroHornacina.PROBANDO
                    tuyo -> AroHornacina.PUESTO
                    else -> AroHornacina.NINGUNO
                },
            bloqueada = e.especial != null && !tuyo,
            tapada = false,
            alTocar = { alTocar(e.clave) },
            modifier = Modifier.testTag("item-${e.itemId ?: e.clave}"),
        )
    }
}

private fun nombreDe(e: Elemento): Int =
    when (e.especial) {
        null -> if (e.clave == "banco") R.string.store_garden_bench else R.string.store_garden_can
        GardenSpecial.LANTERN -> R.string.jardin_especial_farolillo
        GardenSpecial.STONE_PATH -> R.string.jardin_especial_caminito
        GardenSpecial.POND -> R.string.jardin_especial_estanque
        GardenSpecial.MUSHROOMS -> R.string.jardin_especial_setas
        GardenSpecial.BIG_CHERRY -> R.string.jardin_especial_cerezo_grande
        GardenSpecial.GOLDEN_TREE -> R.string.jardin_especial_arbol_dorado
    }
