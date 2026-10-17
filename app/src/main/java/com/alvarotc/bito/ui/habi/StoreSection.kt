package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.StoreItemState
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.GhostIconButton
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.detail.FreezerInfoSheet
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/** Ejes que pueden quedarse vacíos: los únicos que un toque en lo puesto puede quitar. */
private val UNPINNABLE_AXES =
    setOf(
        CustomizationCategory.HEAD,
        CustomizationCategory.FACE,
        CustomizationCategory.NECK,
        CustomizationCategory.FEET,
        CustomizationCategory.COSTUME,
    )

/**
 * Habi's store (mockup 4a-4d): the axis-tabbed item grid plus the freezer card underneath it.
 * Pure presentation — every write is a callback, so this composable never talks to a repository
 * or a ViewModel directly; [HabiScreen] wires the callbacks to [HabiViewModel].
 */
@Composable
fun StoreSection(
    state: HabiUiState,
    onPreview: (String?) -> Unit,
    onEquip: (String) -> Unit,
    onUnequipDefault: (CatalogItem) -> Unit,
    onUnequip: (CustomizationCategory) -> Unit,
    onBuy: (String) -> Unit,
    onBuyFreezer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // `store` seeds empty for one frame while HabiViewModel's first combine emission is in
        // flight — skip the card rather than flash a title over an empty grid.
        if (state.store.isNotEmpty()) {
            StoreCard(state.store, onPreview, onEquip, onUnequipDefault, onUnequip)
        }
        FreezerCard(
            owned = state.freezersOwned,
            price = state.freezerPrice,
            balance = state.balance,
            personality = state.spec.personality,
            userName = state.userName,
            equipped = state.spec.equipped,
            onBuy = onBuyFreezer,
        )
    }

    val previewedItem = state.previewItemId?.let(HabiCatalog::byId)
    if (previewedItem != null) {
        PurchaseSheet(
            item = previewedItem,
            balance = state.balance,
            onBuy = { onBuy(previewedItem.id) },
            onDismiss = { onPreview(null) },
        )
    }
}

@Composable
private fun StoreCard(
    store: Map<CustomizationCategory, List<StoreEntry>>,
    onPreview: (String?) -> Unit,
    onEquip: (String) -> Unit,
    onUnequipDefault: (CatalogItem) -> Unit,
    onUnequip: (CustomizationCategory) -> Unit,
) {
    var selectedAxis by remember { mutableStateOf(store.keys.first()) }
    BitoCard(modifier = Modifier.fillMaxWidth().testTag("store-card")) {
        Text(
            stringResource(R.string.store_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            color = Tinta,
        )
        Spacer(Modifier.height(12.dp))
        AxisPillRow(store.keys.toList(), selectedAxis, onSelect = { selectedAxis = it })
        Spacer(Modifier.height(14.dp))
        val entries = store[selectedAxis].orEmpty()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { entry ->
                        StoreItemCard(
                            entry = entry,
                            onTap = { tapAction(entry, onPreview, onEquip, onUnequipDefault, onUnequip) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Resolves what a tap on [entry] does, following docs/05 §4 and the GUIA's tap-behavior notes:
 * Equipped+default or Equipped body/eyes -> nothing (those axes always resolve to something);
 * Equipped pattern/upper/lower -> take it off; Owned default -> re-equip the default (clears the
 * row); Owned non-default -> equip it; Affordable/MissingPoints -> preview it (the sheet resolves
 * the actual buy/refuse); Locked -> nothing.
 */
private fun tapAction(
    entry: StoreEntry,
    onPreview: (String?) -> Unit,
    onEquip: (String) -> Unit,
    onUnequipDefault: (CatalogItem) -> Unit,
    onUnequip: (CustomizationCategory) -> Unit,
) {
    when (entry.state) {
        StoreItemState.Equipped -> if (entry.item.category in UNPINNABLE_AXES) onUnequip(entry.item.category)
        StoreItemState.Owned -> if (entry.item.default) onUnequipDefault(entry.item) else onEquip(entry.item.id)
        StoreItemState.Affordable, is StoreItemState.MissingPoints -> onPreview(entry.item.id)
        is StoreItemState.Locked -> Unit
        StoreItemState.OutOfSeason -> Unit
        // La tienda antigua no pinta objetos ni especiales de jardín (los trae la nueva).
        StoreItemState.OwnedInGarden, is StoreItemState.FromBadge -> Unit
    }
}

/** Whether tapping [entry] does anything at all — mirrors [tapAction]'s no-op branches exactly,
 * so a dead tap never even attaches a clickable (better a11y than a handler that does nothing). */
private fun isTappable(entry: StoreEntry): Boolean =
    when (entry.state) {
        is StoreItemState.Locked, StoreItemState.OutOfSeason, StoreItemState.OwnedInGarden, is StoreItemState.FromBadge -> false
        StoreItemState.Equipped -> entry.item.category in UNPINNABLE_AXES
        else -> true
    }

@Composable
private fun AxisPillRow(
    axes: List<CustomizationCategory>,
    selected: CustomizationCategory,
    onSelect: (CustomizationCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).testTag("store-axis-row"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        axes.forEach { axis ->
            val active = axis == selected
            // [C]: same bug/fix as HabiScreen's PersonalityPills — the active pill used to skip
            // `.clickable` entirely (no sign to TalkBack a 3rd option even existed), and
            // `indication = null` on it keeps the old visual byte-for-byte (it never had a ripple).
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .clip(CircleShape)
                    .then(if (active) Modifier.background(Tarjeta).border(1.dp, Borde, CircleShape) else Modifier)
                    .selectable(
                        selected = active,
                        interactionSource = interactionSource,
                        indication = if (active) null else LocalIndication.current,
                        role = Role.Tab,
                        onClick = { onSelect(axis) },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("store-axis-${axis.name}"),
            ) {
                Text(
                    stringResource(axisLabelRes(axis)),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) Tinta else TintaSuave,
                )
            }
        }
    }
}

@Composable
private fun StoreItemCard(
    entry: StoreEntry,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val equipped = entry.state is StoreItemState.Equipped
    val locked = entry.state is StoreItemState.Locked || entry.state == StoreItemState.OutOfSeason
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Tarjeta)
            .border(if (equipped) 1.5.dp else 1.dp, if (equipped) Hoja else Borde, shape)
            .then(if (isTappable(entry)) Modifier.clickable(onClick = onTap) else Modifier)
            // [E]: when tappable, `.clickable` above already auto-merges name + footer into one
            // stop (the reference pattern). When it's NOT (Locked, or an always-resolved axis),
            // there was no merging ancestor at all, so name and status footer read as 2 separate
            // stops. Unconditional here — harmless alongside `.clickable`, which implies the same
            // merge already — fixes the untappable case: the item as one name+price/status unit.
            .semantics(mergeDescendants = true) {}
            .alpha(if (locked) 0.5f else 1f)
            .padding(10.dp)
            .testTag("store-item-${entry.item.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(36.dp)) {
            StoreItemThumb(entry.item, Modifier.fillMaxSize())
            if (equipped) {
                Box(
                    Modifier
                        .size(15.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .clip(CircleShape)
                        .background(Hoja),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BitoIcons.Check, contentDescription = null, tint = Tarjeta, modifier = Modifier.size(9.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(itemNameRes(entry.item.id)),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = Tinta,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(4.dp))
        StoreItemFooter(entry)
    }
}

@Composable
private fun StoreItemFooter(entry: StoreEntry) {
    when (val s = entry.state) {
        StoreItemState.Equipped ->
            Text(
                stringResource(R.string.store_equipped),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = Hoja,
            )
        StoreItemState.Owned ->
            Text(
                stringResource(R.string.store_tap_to_equip),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = TintaSuave,
                textAlign = TextAlign.Center,
            )
        // MissingPoints shows the same plain price as Affordable — the grid never spells out the
        // shortfall (GUIA Fidelidad M6 checkpoint ruling); the PurchaseSheet is where refusal shows.
        StoreItemState.Affordable, is StoreItemState.MissingPoints -> entry.item.price?.let { PriceRow(it) }
        StoreItemState.OutOfSeason ->
            Text(
                stringResource(R.string.store_out_of_season),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = TintaSuave,
                textAlign = TextAlign.Center,
            )
        StoreItemState.OwnedInGarden, is StoreItemState.FromBadge -> Unit
        is StoreItemState.Locked ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(BitoIcons.Lock, contentDescription = null, tint = TintaSuave, modifier = Modifier.size(11.dp))
                Text(
                    stringResource(R.string.store_locked_streak, s.requiredStreak),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = TintaSuave,
                )
            }
    }
}

@Composable
private fun PriceRow(price: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "$price",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = Tinta,
        )
        Text(
            stringResource(R.string.store_pts_label),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = TintaSuave,
        )
    }
}

@Composable
private fun FreezerCard(
    owned: Int,
    price: Int,
    balance: Int,
    personality: Personality,
    userName: String,
    equipped: EquippedSet,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBuySheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    BitoCard(modifier = modifier.fillMaxWidth().testTag("freezer-card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BitoIcons.Snowflake, contentDescription = null, tint = Hoja, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.freezer_sheet_title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                color = Tinta,
                modifier = Modifier.weight(1f),
            )
            GhostIconButton(
                BitoIcons.Info,
                contentDescription = stringResource(R.string.freezer_info_hint),
                onClick = { showInfoSheet = true },
                modifier = Modifier.testTag("freezer-info"),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.freezers_owned_label, owned),
            style = MaterialTheme.typography.labelMedium,
            color = TintaSuave,
        )
        Spacer(Modifier.height(10.dp))
        PillButton(
            stringResource(R.string.store_freezer_buy, price),
            onClick = { showBuySheet = true },
            modifier = Modifier.fillMaxWidth().testTag("open-freezer-sheet"),
        )
    }
    if (showBuySheet) {
        FreezerSheet(owned = owned, price = price, balance = balance, onBuy = onBuy, onDismiss = { showBuySheet = false })
    }
    if (showInfoSheet) {
        FreezerInfoSheet(personality = personality, userName = userName, equipped = equipped, onDismiss = { showInfoSheet = false })
    }
}

private fun axisLabelRes(category: CustomizationCategory): Int =
    when (category) {
        CustomizationCategory.BODY_COLOR -> R.string.store_axis_colors
        CustomizationCategory.EYE_COLOR -> R.string.store_axis_eyes
        CustomizationCategory.HEAD -> R.string.store_axis_head
        CustomizationCategory.FACE -> R.string.store_axis_face
        CustomizationCategory.NECK -> R.string.store_axis_neck
        CustomizationCategory.FEET -> R.string.store_axis_feet
        CustomizationCategory.COSTUME -> R.string.store_axis_costumes
        // La tienda antigua no tiene eje de jardín; se borra con la nueva (pieza 6, tarea 20).
        CustomizationCategory.GARDEN_OBJECT -> error("la tienda antigua no pinta objetos de jardín")
    }

/** Nombres por id, solo en ui/ (docs/05 §2). */
fun itemNameRes(id: String): Int =
    when (id) {
        "body-salvia" -> R.string.item_body_salvia
        "body-vainilla" -> R.string.item_body_vainilla
        "body-melocoton" -> R.string.item_body_melocoton
        "body-cielo" -> R.string.item_body_cielo
        "body-lavanda" -> R.string.item_body_lavanda
        "body-rosa" -> R.string.item_body_rosa
        "body-terracota" -> R.string.item_body_terracota
        "body-carbon" -> R.string.item_body_carbon
        "body-dorado" -> R.string.item_body_dorado
        "eyes-tinta" -> R.string.item_eyes_tinta
        "eyes-avellana" -> R.string.item_eyes_avellana
        "eyes-verde" -> R.string.item_eyes_verde
        "eyes-azul" -> R.string.item_eyes_azul
        "eyes-ambar" -> R.string.item_eyes_ambar
        "eyes-violeta" -> R.string.item_eyes_violeta
        "head-gorro-lana" -> R.string.item_head_gorro_lana
        "head-boina" -> R.string.item_head_boina
        "head-paja" -> R.string.item_head_paja
        "head-auriculares" -> R.string.item_head_auriculares
        "head-copa" -> R.string.item_head_copa
        "head-flor" -> R.string.item_head_flor
        "head-corona" -> R.string.item_head_corona
        "face-gafas-redondas" -> R.string.item_face_gafas_redondas
        "face-gafas-sol" -> R.string.item_face_gafas_sol
        "neck-bufanda" -> R.string.item_neck_bufanda
        "neck-panuelo" -> R.string.item_neck_panuelo
        "neck-collar-flores" -> R.string.item_neck_collar_flores
        "neck-panuelo-brasa" -> R.string.item_neck_panuelo_brasa
        "feet-calcetines" -> R.string.item_feet_calcetines
        "feet-botas-agua" -> R.string.item_feet_botas_agua
        "feet-zapatillas" -> R.string.item_feet_zapatillas
        "costume-calabaza" -> R.string.item_costume_calabaza
        "costume-reno" -> R.string.item_costume_reno
        "costume-dinosaurio" -> R.string.item_costume_dinosaurio
        "garden-banco" -> R.string.store_garden_bench
        "garden-regadera" -> R.string.store_garden_can
        // Inalcanzable para un artículo real: cae a un texto que existe en vez de romper con un id corrupto.
        else -> R.string.store_title
    }
