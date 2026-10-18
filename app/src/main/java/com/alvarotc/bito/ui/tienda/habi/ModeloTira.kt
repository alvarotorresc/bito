package com.alvarotc.bito.ui.tienda.habi

import androidx.annotation.StringRes
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.HabiEngine
import com.alvarotc.bito.domain.StoreItemState
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.ui.tienda.AroHornacina
import com.alvarotc.bito.ui.tienda.EtiquetaHornacina
import com.alvarotc.bito.ui.tienda.PruebaTienda
import com.alvarotc.bito.ui.tienda.TiendaUiState

/** Las siete ranuras de la tira, en su orden. [id] es el del mock (`raiz-<id>`, `ranura-<id>`). */
enum class RanuraHabi(
    val id: String,
    val categoria: CustomizationCategory,
    @StringRes val nombre: Int,
    /** Lo que un disfraz tapa al pintar. */
    val tapaElDisfraz: Boolean,
    /** La nota que va bajo la rejilla de la ranura. */
    @StringRes val nota: Int? = null,
) {
    CABEZA("cabeza", CustomizationCategory.HEAD, R.string.store_slot_head, true),
    CARA("cara", CustomizationCategory.FACE, R.string.store_slot_face, true),
    CUELLO("cuello", CustomizationCategory.NECK, R.string.store_slot_neck, true),
    PIES("pies", CustomizationCategory.FEET, R.string.store_slot_feet, true),
    COLORES("colores", CustomizationCategory.BODY_COLOR, R.string.store_slot_colors, true),
    OJOS("ojos", CustomizationCategory.EYE_COLOR, R.string.store_slot_eyes, false, R.string.store_note_eyes),
    DISFRACES("disfraces", CustomizationCategory.COSTUME, R.string.store_slot_costume, false, R.string.store_note_costumes),
    ;

    /** Las piezas de la ranura, en el orden del catálogo. */
    val piezas: List<CatalogItem> get() = HabiCatalog.all.filter { it.category == categoria }
}

/** El id de lo que lleva Habi en [ranura]; nulo si la ranura está vacía. */
fun EquippedSet.puestoEn(ranura: RanuraHabi): String? =
    when (ranura.categoria) {
        CustomizationCategory.HEAD -> head
        CustomizationCategory.FACE -> face
        CustomizationCategory.NECK -> neck
        CustomizationCategory.FEET -> feet
        CustomizationCategory.BODY_COLOR -> bodyColor
        CustomizationCategory.EYE_COLOR -> eyeColor
        CustomizationCategory.COSTUME -> costume
        CustomizationCategory.GARDEN_OBJECT -> null
    }

/** Con disfraz, cabeza, cara, cuello, pies y color quedan tapados; los ojos no (así lo pinta `habiLayers`). */
fun EquippedSet.tapada(ranura: RanuraHabi): Boolean = costume != null && ranura.tapaElDisfraz

/** Lo que dice la cabecera a la derecha del nombre de la ranura. */
sealed interface MetaRanura {
    data object Tapada : MetaRanura

    data class Puesta(val itemId: String) : MetaRanura

    data class Piezas(val cuantas: Int) : MetaRanura
}

fun metaDe(
    ranura: RanuraHabi,
    puestos: EquippedSet,
): MetaRanura =
    when {
        puestos.tapada(ranura) -> MetaRanura.Tapada
        else -> puestos.puestoEn(ranura)?.let { MetaRanura.Puesta(it) } ?: MetaRanura.Piezas(ranura.piezas.size)
    }

/**
 * Primera vez (J10, J11): Habi sin nada puesto, nada tuyo salvo lo de serie y sin bitos. Se deriva del
 * estado: el estado de la tienda no trae ninguna marca de primera visita.
 */
fun esPrimeraVez(estado: TiendaUiState): Boolean {
    val p = estado.puestos
    val nada = p.head == null && p.face == null && p.neck == null && p.feet == null && p.costume == null
    val soloDeSerie = estado.poseidos.none { HabiCatalog.byId(it)?.default != true }
    return nada && soloDeSerie && estado.saldo == 0
}

/** Todo lo que se vende (con precio) es tuyo; los exclusivos por racha no cuentan. */
fun todoComprado(estado: TiendaUiState): Boolean =
    HabiCatalog.all.filter { (it.price ?: 0) > 0 }.all { it.default || it.id in estado.poseidos }

/** Una fila de la lista de la tira, para saber de antemano a qué índice salta cada ranura. */
sealed interface EntradaTira {
    data object Intro : EntradaTira

    data class Cabecera(val ranura: RanuraHabi) : EntradaTira

    data class Vacia(val ranura: RanuraHabi) : EntradaTira

    data class Fila(val ranura: RanuraHabi, val piezas: List<CatalogItem>, val primeraDeLaRanura: Boolean) : EntradaTira

    data class NotaRanura(val ranura: RanuraHabi) : EntradaTira

    data object Todo : EntradaTira

    data object Fin : EntradaTira
}

/**
 * La tira entera como lista plana. [ranuraVacia] solo se pinta si esa ranura sigue vacía y no hay
 * bienvenida (J5: nunca en las siete a la vez).
 */
fun construirTira(
    primeraVez: Boolean,
    ranuraVacia: RanuraHabi?,
    todoComprado: Boolean,
    puestos: EquippedSet,
): List<EntradaTira> =
    buildList {
        if (primeraVez) add(EntradaTira.Intro)
        RanuraHabi.entries.forEach { r ->
            add(EntradaTira.Cabecera(r))
            if (!primeraVez && r == ranuraVacia && puestos.puestoEn(r) == null) add(EntradaTira.Vacia(r))
            r.piezas.chunked(COLUMNAS).forEachIndexed { i, fila -> add(EntradaTira.Fila(r, fila, i == 0)) }
            if (r.nota != null) add(EntradaTira.NotaRanura(r))
        }
        if (todoComprado) add(EntradaTira.Todo)
        add(EntradaTira.Fin)
    }

const val COLUMNAS = 3

/** Lo que pinta una hornacina de la tira, ya decidido (sin textos: los resuelve la vista). */
data class DatosPieza(
    val etiqueta: EtiquetaHornacina,
    val aro: AroHornacina,
    val bloqueada: Boolean,
    val tapada: Boolean,
    val precio: Int?,
    val racha: Int?,
    @StringRes val temporada: Int?,
)

/**
 * La etiqueta de una pieza, como `precioHTML` del mock: probando, puesto o tapado, tuyo, racha,
 * fuera de temporada y, al final, el precio. Se calcula contra lo puesto, nunca contra la prueba.
 */
fun datosDe(
    item: CatalogItem,
    estado: TiendaUiState,
): DatosPieza {
    val ranura = RanuraHabi.entries.first { it.categoria == item.category }
    val st = HabiEngine.storeStateOf(item, estado.poseidos, estado.puestos, estado.saldo, estado.hoy)
    val probando = (estado.prueba as? PruebaTienda.Pieza)?.itemId == item.id
    val bloqueada = st is StoreItemState.Locked || st == StoreItemState.OutOfSeason
    val tapada = st == StoreItemState.Equipped && estado.puestos.tapada(ranura)
    val etiqueta =
        when {
            probando -> EtiquetaHornacina.PROBANDO
            tapada -> EtiquetaHornacina.TAPADO
            st == StoreItemState.Equipped -> EtiquetaHornacina.PUESTO
            st == StoreItemState.Owned || st == StoreItemState.OwnedInGarden -> EtiquetaHornacina.TUYO
            st is StoreItemState.Locked -> EtiquetaHornacina.RACHA
            st == StoreItemState.OutOfSeason -> EtiquetaHornacina.TEMPORADA
            else -> EtiquetaHornacina.PRECIO
        }
    val aro =
        when {
            probando -> AroHornacina.PROBANDO
            st == StoreItemState.Equipped -> AroHornacina.PUESTO
            else -> AroHornacina.NINGUNO
        }
    return DatosPieza(
        etiqueta = etiqueta,
        aro = aro,
        bloqueada = bloqueada,
        tapada = tapada,
        precio = if (etiqueta == EtiquetaHornacina.PRECIO) item.price else null,
        racha = (st as? StoreItemState.Locked)?.requiredStreak,
        temporada = if (st == StoreItemState.OutOfSeason) temporadaDe(item.id) else null,
    )
}

@StringRes
private fun temporadaDe(id: String): Int =
    when (id) {
        "costume-calabaza" -> R.string.store_label_season_october
        "costume-reno" -> R.string.store_label_season_december
        else -> R.string.store_label_season_carnival
    }

/** La ranura que está a la vista: la última cabecera que ya ha llegado arriba. */
fun ranuraVisible(
    entradas: List<EntradaTira>,
    primerVisible: Int,
): RanuraHabi {
    var visible = RanuraHabi.CABEZA
    entradas.forEachIndexed { i, e -> if (e is EntradaTira.Cabecera && i <= primerVisible) visible = e.ranura }
    return visible
}

/**
 * El `padding-top` del riel: `max(56, y - 4)` dp, con `y` la distancia del borde superior del riel al
 * de la primera hornacina, medida (no supuesta).
 */
fun paddingRiel(yDp: Float?): Float = maxOf(PADDING_RIEL_REPOSO, (yDp ?: 0f) - 4f)

const val PADDING_RIEL_REPOSO = 56f
