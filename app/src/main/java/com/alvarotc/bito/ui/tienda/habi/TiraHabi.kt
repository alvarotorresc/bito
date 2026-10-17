package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.ui.habi.itemNameRes
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.tienda.HornacinaPieza
import com.alvarotc.bito.ui.tienda.TiendaUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// (sin token) `.tgrid`, `.nota-*`, `.fin-tira` de tienda-habi.css.
private val RELLENO_IZQUIERDA = 16.dp
private val RELLENO_DERECHA = 62.dp
private val AIRE_PRIMERA_FILA = 14.dp
private val ENTRE_FILAS = 16.dp
private val ENTRE_COLUMNAS = 10.dp
private val FIN_TIRA = 200.dp
private val RELLENO_ABAJO = 160.dp
private val ARRIBA_RAIZ = 46.dp
private val NOTA_INTRO = PaddingValues(start = 20.dp, top = 14.dp, end = 64.dp)
private val NOTA_TIRA = PaddingValues(start = 20.dp, top = 12.dp, end = 64.dp, bottom = 4.dp)
private val NOTA_VACIA = PaddingValues(start = 20.dp, top = 14.dp, end = 64.dp)
private const val DURACION_SALTO = 420

/**
 * La tira de Habi: las siete ranuras en una sola lista (filas de tres, cabecera anclada por ranura) con la
 * raíz fija a la derecha. [ranuraVaciada] es la ranura de la que acaban de quitar la pieza (J5).
 * Tocar una pieza llama a [alTocar]; el catálogo entero sale de `HabiCatalog.all`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TiraHabi(
    estado: TiendaUiState,
    alTocar: (String) -> Unit,
    modifier: Modifier = Modifier,
    ranuraVaciada: RanuraHabi? = null,
    lista: LazyListState = rememberLazyListState(),
) {
    val colores = BitoTema.colores
    val tipos = BitoTema.tipos
    val densidad = LocalDensity.current
    val reducido = BitoTema.reducido
    val scope = rememberCoroutineScope()
    val primeraVez = esPrimeraVez(estado)
    val entradas = construirTira(primeraVez, ranuraVaciada, todoComprado(estado), estado.puestos)
    val visible by remember(entradas) { derivedStateOf { ranuraVisible(entradas, lista.firstVisibleItemIndex) } }

    // El riel se alinea con la primera hornacina: se mide, y solo se vuelve a medir con la tira arriba.
    var arribaPieza by remember { mutableStateOf<Float?>(null) }
    var arribaRiel by remember { mutableStateOf<Float?>(null) }
    var medida by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(lista) {
        snapshotFlow {
            val p = arribaPieza
            val r = arribaRiel
            val enReposo = lista.firstVisibleItemIndex == 0 && lista.firstVisibleItemScrollOffset == 0
            if (p != null && r != null && enReposo) (p - r) / densidad.density else null
        }.collect { y -> if (y != null) medida = y }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("tira-habi"),
            state = lista,
            contentPadding = PaddingValues(bottom = RELLENO_ABAJO),
        ) {
            entradas.forEachIndexed { indice, entrada ->
                when (entrada) {
                    EntradaTira.Intro ->
                        item(key = "intro") {
                            Text(
                                stringResource(R.string.store_welcome),
                                Modifier.padding(NOTA_INTRO).testTag("nota-intro"),
                                style = tipos.t16.copy(fontWeight = FontWeight.Medium, lineHeight = 22.4.sp),
                                color = colores.tinta,
                            )
                        }
                    is EntradaTira.Cabecera ->
                        stickyHeader(key = "cabecera-${entrada.ranura.id}") {
                            CabeceraRanura(entrada.ranura, metaDe(entrada.ranura, estado.puestos))
                        }
                    is EntradaTira.Vacia ->
                        item(key = "vacia-${entrada.ranura.id}") {
                            Text(
                                stringResource(R.string.store_slot_empty),
                                Modifier.padding(NOTA_VACIA).testTag("nota-vacia"),
                                style = tipos.t14.copy(lineHeight = 19.6.sp),
                                color = colores.tintaSuave,
                            )
                        }
                    is EntradaTira.Fila ->
                        item(key = "fila-${entrada.ranura.id}-$indice") {
                            val primera = indice == entradas.indexOfFirst { it is EntradaTira.Fila }
                            FilaPiezas(
                                entrada,
                                estado,
                                alTocar,
                                alMedir = if (primera) ({ y -> arribaPieza = y }) else null,
                            )
                        }
                    is EntradaTira.NotaRanura ->
                        item(key = "nota-${entrada.ranura.id}") {
                            Text(
                                stringResource(requireNotNull(entrada.ranura.nota)),
                                Modifier.padding(NOTA_TIRA).testTag("nota-${entrada.ranura.id}"),
                                style = tipos.t14.copy(lineHeight = 19.6.sp),
                                color = colores.tintaSuave,
                            )
                        }
                    EntradaTira.Todo ->
                        item(key = "todo") {
                            Text(
                                stringResource(R.string.store_all_bought),
                                Modifier.padding(NOTA_TIRA).testTag("nota-todo"),
                                style = tipos.t14.copy(lineHeight = 19.6.sp),
                                color = colores.tintaSuave,
                            )
                        }
                    EntradaTira.Fin -> item(key = "fin") { Spacer(Modifier.height(FIN_TIRA)) }
                }
            }
        }
        RaizRanuras(
            activa = visible,
            paddingSuperior = paddingRiel(medida).dp,
            alPulsar = { r ->
                val destino = entradas.indexOfFirst { it is EntradaTira.Cabecera && it.ranura == r }
                scope.launch { bajarHasta(lista, destino, reducido) }
            },
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = ARRIBA_RAIZ)
                    .onGloballyPositioned { arribaRiel = it.positionInRoot().y },
        )
    }
}

@Composable
private fun FilaPiezas(
    fila: EntradaTira.Fila,
    estado: TiendaUiState,
    alTocar: (String) -> Unit,
    alMedir: ((Float) -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = RELLENO_IZQUIERDA,
                end = RELLENO_DERECHA,
                top = if (fila.primeraDeLaRanura) AIRE_PRIMERA_FILA else ENTRE_FILAS,
            ).then(if (alMedir != null) Modifier.onGloballyPositioned { alMedir(it.positionInRoot().y) } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(ENTRE_COLUMNAS),
        verticalAlignment = Alignment.Top,
    ) {
        fila.piezas.forEach { item -> PiezaDeLaTira(item, estado, { alTocar(item.id) }, Modifier.weight(1f)) }
        repeat(COLUMNAS - fila.piezas.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun PiezaDeLaTira(
    item: CatalogItem,
    estado: TiendaUiState,
    alTocar: () -> Unit,
    modifier: Modifier,
) {
    val datos = datosDe(item, estado)
    val texto =
        when {
            datos.racha != null -> stringResource(R.string.store_label_streak, datos.racha)
            datos.temporada != null -> stringResource(datos.temporada)
            else -> null
        }
    HornacinaPieza(
        imagen = { MiniaturaPieza(item) },
        nombre = stringResource(itemNameRes(item.id)),
        etiqueta = datos.etiqueta,
        textoEtiqueta = texto,
        precio = datos.precio,
        aro = datos.aro,
        bloqueada = datos.bloqueada,
        tapada = datos.tapada,
        alTocar = alTocar,
        modifier = modifier.testTag("item-${item.id}"),
    )
}

/**
 * Baja la tira hasta la cabecera [destino] en 420 ms con `drawer` (salto instantáneo en reducido). Con el
 * destino a la vista el recorrido es exacto; si no, se estima con lo medido y se remata al final.
 */
private suspend fun bajarHasta(
    lista: LazyListState,
    destino: Int,
    reducido: Boolean,
) {
    if (destino < 0) return
    if (reducido) {
        lista.scrollToItem(destino)
        return
    }
    val visibles = lista.layoutInfo.visibleItemsInfo
    val exacto = visibles.firstOrNull { it.index == destino }
    val distancia =
        if (exacto != null) {
            exacto.offset.toFloat()
        } else if (visibles.isNotEmpty()) {
            val media = visibles.sumOf { it.size } / visibles.size.toFloat()
            (destino - visibles.first().index) * media + visibles.first().offset
        } else {
            0f
        }
    try {
        lista.animateScrollBy(distancia, androidx.compose.animation.core.tween(DURACION_SALTO, easing = Curvas.drawer))
        if (lista.firstVisibleItemIndex != destino || lista.firstVisibleItemScrollOffset != 0) lista.scrollToItem(destino)
    } catch (e: CancellationException) {
        throw e
    }
}

/** La ranura de la que el usuario acaba de quitar la pieza; se limpia sola al llenarse. */
@Composable
fun rememberRanuraVaciada(puestos: EquippedSet): RanuraHabi? {
    var anterior by remember { mutableStateOf(puestos) }
    var vaciada by remember { mutableStateOf<RanuraHabi?>(null) }
    LaunchedEffect(puestos) {
        RanuraHabi.entries.forEach { r ->
            if (anterior.puestoEn(r) != null && puestos.puestoEn(r) == null) vaciada = r
        }
        if (vaciada?.let { puestos.puestoEn(it) } != null) vaciada = null
        anterior = puestos
    }
    return vaciada
}
