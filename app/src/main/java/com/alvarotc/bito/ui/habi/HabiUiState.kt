package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.HabiEngine
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.StoreItemState
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.HabiDayPhase
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.equippedSetOf
import com.alvarotc.bito.domain.model.toPose

/** One store card: a catalog entry paired with its derived purchase/equip state. */
data class StoreEntry(val item: CatalogItem, val state: StoreItemState)

/**
 * Snapshot the Habi screen renders: the avatar spec (mood + personality + what's worn), the points
 * balance, freezer inventory, and the store grouped by customization axis — T12 is the one that
 * actually renders [store]; T11 only derives it.
 */
data class HabiUiState(
    val spec: HabiSpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
    val balance: Int = 0,
    val freezersOwned: Int = 0,
    val freezerPrice: Int = EconomyConfig().freezerPrice,
    // The full economy behind that price — the points sheet renders every earn value from here so
    // copy can never disagree with PointsEngine (QA 2026-08-23). Same source as freezerPrice.
    val economy: EconomyConfig = EconomyConfig(),
    val store: Map<CustomizationCategory, List<StoreEntry>> = emptyMap(),
    // What Habi is trying on before buying it — ephemeral UI state, never written to the DB
    // (docs/05 §4). Non-null both drives `spec.equipped` below AND opens the PurchaseSheet.
    val previewItemId: String? = null,
    // Settings.userName, may be blank until M9's onboarding writes it — callers fall back to
    // R.string.habi_name_fallback when interpolating a HabiVoice %1$s.
    val userName: String = "",
    val loading: Boolean = true,
    // Su día, con cuentagotas (biblia §7.2): la fase que decide la pose (spec.pose ya la lleva) y,
    // si toca decir algo, qué recurso decirlo con. HabiScreen es el único sitio que lo pinta.
    val dayPhase: HabiDayPhase = HabiDayPhase.AWAKE,
    val dayLineRes: Int? = null,
)

/** Axis order the store's pill row follows (GUIA: Colores · Patrones · Ojos · Arriba · Abajo). */
private val StoreAxisOrder =
    listOf(
        CustomizationCategory.BODY_COLOR,
        CustomizationCategory.PATTERN,
        CustomizationCategory.EYE_COLOR,
        CustomizationCategory.UPPER,
        CustomizationCategory.LOWER,
    )

/**
 * Derives the Habi screen state from [state] as seen on [today]. Pure — no side effects, no
 * storage, no clock reads — so it is trivially testable and safe to call on every state change.
 */
fun buildHabiUiState(
    state: DomainState,
    owned: List<CustomizationItemEntity>,
    balance: Int,
    personality: Personality,
    today: LogicalDay,
    previewItemId: String? = null,
    userName: String = "",
    // Defaults to the same EconomyConfig() the field's own default resolves to — callers that
    // don't pass one (every pre-existing test) are unaffected. HabiViewModel passes its injected
    // economy.freezerPrice so the store card and buyFreezer() can never disagree on the price.
    freezerPrice: Int = EconomyConfig().freezerPrice,
    economy: EconomyConfig = EconomyConfig(),
    dayPhase: HabiDayPhase = HabiDayPhase.AWAKE,
    // 0/1/2 — el ritual del ojo (EyeRitual), directo desde Settings.habiEyesPainted. 2 por
    // defecto: cualquier caller que no lo pase (todo test previo a T13) dibuja la Habi de siempre.
    eyesPainted: Int = 2,
    // El marcador «esta línea ya se dijo hoy» (Settings.habiWaitingSaidDay), mismo patrón que
    // perfectDayCelebratedDay: -1 por defecto = nunca se dijo. Solo importa para WAITING; ASLEEP
    // no lo necesita porque el sellado ocurre una sola vez por día.
    waitingSaidDay: LogicalDay = -1,
): HabiUiState {
    val lastActivityDay = StatsEngine.lastActivityDay(state)
    val mood = MoodEngine.moodOf(state, today, lastActivityDay)
    val equippedIds = owned.filter { it.equipped }.map { it.itemId }
    val equipped = equippedSetOf(equippedIds)
    val ownedIds = owned.mapTo(mutableSetOf()) { it.itemId }

    val itemsByCategory = HabiCatalog.all.groupBy { it.category }
    val store =
        StoreAxisOrder.associateWith { category ->
            // sortedByDescending is a stable sort, so this only promotes the default item to the
            // front of its axis — the rest of the catalog's own order is left untouched. Store
            // entries always derive from the REAL equipped set, never the preview below — the
            // grid must never claim an item is "equipped" just because it's being tried on.
            (itemsByCategory[category] ?: emptyList())
                .sortedByDescending { it.default }
                .map { item -> StoreEntry(item, HabiEngine.storeStateOf(item, ownedIds, equipped, balance)) }
        }

    // The avatar wears the preview on top of what's really equipped; appending it last means it
    // wins its axis (equippedSetOf keeps the last id per category), same as a real equip would.
    val displayEquipped = if (previewItemId != null) equippedSetOf(equippedIds + previewItemId) else equipped

    // La línea de "esperando el cierre" suena una vez por día lógico, no por cada entrada en la
    // fase (biblia §12.2): WAITING se pisa varias veces el mismo día (completar, desmarcar,
    // volver a completar) y el marcador solo se escribe cuando la pantalla se abandona
    // (HabiViewModel.onDayLineSeen) — no aquí, así que el mismo día lógico sigue devolviendo la
    // línea mientras el usuario esté mirando la pantalla. ASLEEP no lleva marcador: el sellado
    // ocurre una sola vez por día, así que su línea siempre acompaña esa fase.
    val dayLineRes =
        when {
            dayPhase == HabiDayPhase.ASLEEP -> HabiVoice.dayPhaseRes(HabiDayPhase.ASLEEP, personality)
            dayPhase == HabiDayPhase.WAITING && waitingSaidDay != today -> HabiVoice.dayPhaseRes(HabiDayPhase.WAITING, personality)
            else -> null
        }

    return HabiUiState(
        spec = HabiSpec(mood, personality, displayEquipped, eyesPainted = eyesPainted, pose = dayPhase.toPose()),
        balance = balance,
        freezersOwned = PointsEngine.freezersOwned(state),
        freezerPrice = freezerPrice,
        economy = economy,
        store = store,
        previewItemId = previewItemId,
        userName = userName,
        loading = false,
        dayPhase = dayPhase,
        dayLineRes = dayLineRes,
    )
}
