package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.components.SpeechBubble
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/**
 * Habi's home: mood stage, points balance, the personality selector, and the store. No screen
 * title (mockup 4a): the balance chip in the top-right corner carries the header instead. Buying
 * something in [StoreSection] previews live here too — the trying-on chip sits right under the
 * stage, and the avatar itself already wears the preview via `state.spec` (see [buildHabiUiState]).
 */
@Composable
fun HabiScreen(viewModel: HabiViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPointsSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    // Opening a store preview (a grid tap) scrolls back to the stage, so the dressed bean, the
    // "probando" chip and the purchase sheet are all visible at once — mockup 4b (QA 2026-08-23).
    LaunchedEffect(state.previewItemId) {
        if (state.previewItemId != null) scrollState.animateScrollTo(0)
    }
    Scaffold(containerColor = Papel) { padding ->
        if (state.loading) {
            // First frame after the nav-scoped VM is recreated: calm paper, never a zeroed
            // balance/store flash (QA 2026-08-23).
            Box(Modifier.padding(padding).fillMaxSize().testTag("habi-loading"))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(20.dp)
                .testTag("habi-screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BalanceChip(state.balance, onClick = { showPointsSheet = true }, modifier = Modifier.align(Alignment.End))
            HabiStage(spec = state.spec, onTap = viewModel::onAvatarTap, modifier = Modifier.fillMaxWidth())
            state.previewItemId?.let { previewId ->
                TryingChip(itemNameRes(previewId), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            SpeechBubble(
                speaker = stringResource(R.string.habi_speaker, stringResource(HabiVoice.labelRes(state.spec.personality))),
                // The Habi screen's OWN playful, name-addressed voice — distinct from the Stats
                // commentator's mood-only bubble (HabiVoice.bubbleRes). No avatar slot here: the
                // big Stage above already IS Habi, so a second mini-face would be redundant.
                text =
                    stringResource(
                        HabiVoice.homeRes(state.spec.mood, state.spec.personality),
                        state.userName.ifBlank { stringResource(R.string.habi_name_fallback) },
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
            PersonalityPills(
                selected = state.spec.personality,
                onSelect = viewModel::setPersonality,
                modifier = Modifier.fillMaxWidth(),
            )
            StoreSection(
                state = state,
                onPreview = viewModel::preview,
                onEquip = viewModel::equip,
                onUnequipDefault = viewModel::unequipDefault,
                onUnequip = viewModel::unequip,
                onBuy = viewModel::purchase,
                onBuyFreezer = viewModel::buyFreezer,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (showPointsSheet) {
        PointsInfoSheet(economy = state.economy, onDismiss = { showPointsSheet = false })
    }
}

/** "probando: <ítem>" (mockup 4b), under the stage while Habi is trying something on. */
@Composable
private fun TryingChip(
    nameRes: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("trying-chip"),
        shape = CircleShape,
        color = Tarjeta,
        border = BorderStroke(1.dp, Borde),
    ) {
        Text(
            stringResource(R.string.store_trying, stringResource(nameRes)),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = Tinta,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/**
 * The points chip; tapping it opens [PointsInfoSheet] (QA 2026-08-23 — the app finally explains
 * how points are earned somewhere). `internal` for the same direct-test reason as
 * [PersonalityPills]. Surface's onClick overload carries the Button role for TalkBack.
 */
@Composable
internal fun BalanceChip(
    balance: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag("balance-chip"),
        shape = CircleShape,
        color = Tarjeta,
        border = BorderStroke(1.dp, Borde),
    ) {
        Row(
            // [E]: Icon(null) + 2 plain Text stops (balance number, "pts" unit) — merge so a
            // TalkBack pass over the chip reads "<balance> points" as one stop, not two.
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(BitoIcons.Sparkle, contentDescription = null, tint = Hoja, modifier = Modifier.size(16.dp))
            Text(
                "$balance",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = Tinta,
            )
            Text(
                stringResource(R.string.habi_balance_points),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = TintaSuave,
            )
        }
    }
}

/**
 * The three-way personality switcher. Deliberately NOT [com.alvarotc.bito.ui.components.SegmentedPills]:
 * the mockup's active pill is a solid-Tinta, dark pill with Tarjeta text — the opposite of that
 * component's light "raised chip on a Papel trough" canon — so bending it here would fight its API
 * more than it would save.
 *
 * `internal` (not `private`): lets its `selectable`/`Role.Tab` semantics be tested directly
 * without standing up a whole [HabiScreen] (real [HabiViewModel], Room, the avatar's infinite
 * bob/blink transitions) — same reasoning [StoreSectionTest] gives for testing [StoreSection]
 * standalone.
 */
@Composable
internal fun PersonalityPills(
    selected: Personality,
    onSelect: (Personality) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.testTag("personality-pills"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Personality.entries.forEach { personality ->
            val active = personality == selected
            // [C]: the active pill used to skip `.clickable` entirely (the `!active` gate below),
            // so it wasn't even a focusable node — a TalkBack user saw only the 2 non-selected
            // options with no sign a 3rd, active one existed. `selectable` (always attached, per
            // OnboardingScreen's PersonalityCard) fixes that; `indication = null` on the active
            // pill only keeps the old visual byte-for-byte — it never had a ripple to begin with
            // (a703aef's BitoBottomBar fix is the same pattern for the same reason).
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (active) Tinta else Tarjeta)
                    .then(if (active) Modifier else Modifier.border(1.dp, Borde, CircleShape))
                    .selectable(
                        selected = active,
                        interactionSource = interactionSource,
                        indication = if (active) null else LocalIndication.current,
                        role = Role.Tab,
                        onClick = { onSelect(personality) },
                    )
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(HabiVoice.labelRes(personality)),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = if (active) Tarjeta else Tinta,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
