package com.alvarotc.bito.ui.review

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.BadgeDef
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.components.BitoCard
import com.alvarotc.bito.ui.components.DayRing
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SpeechBubble
import com.alvarotc.bito.ui.habi.HabiStage
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.stats.BadgeStrings
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.BrasaTinte
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

/**
 * E2 "día sellado" (GUIA 5b): today is already sealed. Habi on stage, its voiced bubble, the
 * final ring as the screen's single accent, points/streaks chips and any badge unlocked since
 * the shelf was last seen, closing with "Hasta mañana". Reusable — [ReviewScreen] wraps it in a
 * [androidx.compose.material3.Scaffold] and its own [androidx.compose.runtime.LaunchedEffect]s
 * (the celebration cue and `markCelebrated`); T12's global celebration sheet has its own compact
 * version and does not call this composable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SealedDayContent(
    state: ReviewUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Entrance scale 0.9 -> 1 (200ms ease-out) every time E2 is shown, per GUIA 5b — no confetti,
    // no emojis; the perfect day's own "juicy" beat is the CELEBRATION cue fired by ReviewScreen.
    var entered by remember { mutableStateOf(false) }
    val scale by
        animateFloatAsState(
            targetValue = if (entered) 1f else 0.9f,
            animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
            label = "sealed-day-scale",
        )
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .scale(scale)
            .padding(20.dp)
            .testTag("review-sealed"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HabiStage(spec = state.spec)

        Text(
            stringResource(if (state.perfectToday) R.string.review_perfect_title else R.string.review_sealed_title),
            style = MaterialTheme.typography.headlineLarge,
            color = Tinta,
        )

        val fallbackName = stringResource(R.string.habi_name_fallback)
        SpeechBubble(
            speaker = stringResource(R.string.habi_speaker, stringResource(personalityLabelRes(state.spec.personality))),
            text =
                stringResource(
                    if (state.perfectToday) {
                        HabiVoice.perfectDayRes(
                            state.spec.personality,
                        )
                    } else {
                        HabiVoice.sealedRes(state.spec.personality)
                    },
                    state.userName.ifBlank { fallbackName },
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        // THE single solid-accent card on the screen: the day's final ring.
        BitoCard(
            container = Hoja,
            border = Hoja,
            modifier = Modifier.fillMaxWidth().testTag("review-ring"),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayRing(state.ringDone, state.ringTotal, Modifier.size(88.dp)) {
                    Text(
                        stringResource(R.string.ring_of, state.ringDone, state.ringTotal),
                        style = MaterialTheme.typography.titleMedium,
                        color = Tarjeta,
                    )
                }
                Text(
                    stringResource(R.string.ring_caption),
                    style = MaterialTheme.typography.labelMedium,
                    color = Tarjeta.copy(alpha = 0.8f),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PointsChip(state.pointsToday)
            if (state.streaksAdvanced > 0) {
                StreaksAdvancedChip(state.streaksAdvanced)
            }
        }

        if (state.newBadges.isNotEmpty()) {
            Text(
                stringResource(R.string.review_new_badges),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.newBadges.forEach { def -> NewBadgeChip(def) }
            }
        }

        PillButton(
            text = stringResource(R.string.review_until_tomorrow),
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().testTag("review-close"),
        )
    }
}

/** "+N pts": tarjeta+borde pill, chispa icon — always shown, never hidden. */
@Composable
private fun PointsChip(
    points: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("review-points"),
        shape = CircleShape,
        color = Tarjeta,
        border = BorderStroke(1.dp, Borde),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(BitoIcons.Sparkle, contentDescription = null, tint = Hoja, modifier = Modifier.size(14.dp))
            Text(
                stringResource(R.string.review_points_today, points),
                style = MaterialTheme.typography.labelMedium,
                color = Tinta,
            )
        }
    }
}

/** "N rachas avanzan": brasa-tinte pill, brasa flame — brasa is emotional heat here, never interaction. */
@Composable
private fun StreaksAdvancedChip(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(BrasaTinte)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("review-streaks"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(BitoIcons.Flame, contentDescription = null, tint = Brasa, modifier = Modifier.size(14.dp))
        Text(
            pluralStringResource(R.plurals.review_streaks_advanced, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = Brasa,
        )
    }
}

/**
 * A new badge's chip, [StreakChip][com.alvarotc.bito.ui.components.StreakChip]-styled on
 * HojaTinte. Ruling R1 (T10): the badge's own icon ([BitoIcons.Sparkle] stand-in) lands in T14
 * once `badgeIcon(def)` exists — [BadgeStrings.badgeNameRes] already resolves the label.
 */
@Composable
private fun NewBadgeChip(
    def: BadgeDef,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(HojaTinte)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("review-badge-${def.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(BitoIcons.Sparkle, contentDescription = null, tint = Hoja, modifier = Modifier.size(14.dp))
        Text(
            stringResource(BadgeStrings.badgeNameRes(def.id)),
            style = MaterialTheme.typography.labelMedium,
            color = Tinta,
        )
    }
}

private fun personalityLabelRes(personality: Personality): Int =
    when (personality) {
        Personality.SARGENTO -> R.string.personality_sargento
        Personality.CHEERLEADER -> R.string.personality_cheerleader
        Personality.NEUTRA -> R.string.personality_neutra
    }
