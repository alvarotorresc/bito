package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
    Scaffold(containerColor = Papel) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("habi-screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BalanceChip(state.balance, modifier = Modifier.align(Alignment.End))
            Stage(spec = state.spec, onTap = viewModel::onAvatarTap, modifier = Modifier.fillMaxWidth())
            state.previewItemId?.let { previewId ->
                TryingChip(itemNameRes(previewId), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            SpeechBubble(
                speaker = stringResource(R.string.habi_speaker, stringResource(personalityLabelRes(state.spec.personality))),
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

/** Sampled from the mockup, between HojaTinte (#E3EDE0) and HabiSalvia (#A9C9A1) — art-phase-tunable. */
private val HabiStageColor = Color(0xFFDDE9D6)

@Composable
private fun Stage(
    spec: HabiSpec,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(HabiStageColor)
                .testTag("habi-stage"),
        )
        // A squat pill stands in for an ellipse — Compose has no ellipse shape primitive.
        Box(
            Modifier
                .size(width = 92.dp, height = 22.dp)
                .offset(y = 66.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Borde.copy(alpha = 0.6f)),
        )
        HabiAvatar(spec = spec, modifier = Modifier.size(150.dp), onTap = onTap)
    }
}

@Composable
private fun BalanceChip(
    balance: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("balance-chip"),
        shape = CircleShape,
        color = Tarjeta,
        border = BorderStroke(1.dp, Borde),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
 */
@Composable
private fun PersonalityPills(
    selected: Personality,
    onSelect: (Personality) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.testTag("personality-pills"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Personality.entries.forEach { personality ->
            val active = personality == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (active) Tinta else Tarjeta)
                    .then(if (active) Modifier else Modifier.border(1.dp, Borde, CircleShape))
                    .then(if (!active) Modifier.clickable { onSelect(personality) } else Modifier)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(personalityLabelRes(personality)),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = if (active) Tarjeta else Tinta,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun personalityLabelRes(personality: Personality): Int =
    when (personality) {
        Personality.SARGENTO -> R.string.personality_sargento
        Personality.CHEERLEADER -> R.string.personality_cheerleader
        Personality.NEUTRA -> R.string.personality_neutra
    }
