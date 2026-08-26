@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvarotc.bito.ui.celebration

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.BadgeDef
import com.alvarotc.bito.ui.components.PillButton
import com.alvarotc.bito.ui.components.SpeechBubble
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiVoice
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.stats.BadgeStrings
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta

/**
 * Global perfect-day celebration sheet (GUIA M7 «Hojas de celebración globales»): Habi centered,
 * the same headline and voice line E2's [com.alvarotc.bito.ui.review.SealedDayContent] uses,
 * today's points chip and a single way out. Hosted by [com.alvarotc.bito.ui.BitoNavHost] above
 * the NavHost, everywhere except the `review` route — E2 already owns that beat there, with its
 * own cue and `markCelebrated`.
 */
@Composable
fun PerfectDaySheet(
    state: CelebrationsUiState,
    onDismiss: () -> Unit,
) {
    // skipPartiallyExpanded + scrollable content, same TimePickerSheet precedent (QA finding,
    // M7): a half-expanded sheet on a short device can otherwise clip "Continue" below the
    // viewport, reachable only by a drag the user never discovers.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Tarjeta) {
        // Entrance scale 0.9 -> 1 (200ms ease-out), same beat as E2's SealedDayContent.
        var entered by remember { mutableStateOf(false) }
        val scale by
            animateFloatAsState(
                targetValue = if (entered) 1f else 0.9f,
                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                label = "perfect-day-scale",
            )
        LaunchedEffect(Unit) { entered = true }

        Column(
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .testTag("perfect-day-sheet"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Static, not bobbing/blinking (T9 note): the sheet already has its own entrance
            // animation, and an infinite transition here would never let a test settle.
            HabiAvatar(state.spec, Modifier.size(120.dp), animated = false)

            Text(
                stringResource(R.string.review_perfect_title),
                style = MaterialTheme.typography.headlineLarge,
                color = Tinta,
            )

            val fallbackName = stringResource(R.string.habi_name_fallback)
            SpeechBubble(
                speaker = stringResource(R.string.habi_speaker, stringResource(HabiVoice.labelRes(state.personality))),
                text = stringResource(HabiVoice.perfectDayRes(state.personality), state.userName.ifBlank { fallbackName }),
                modifier = Modifier.fillMaxWidth(),
            )

            PointsChip(state.pointsToday)

            PillButton(
                text = stringResource(R.string.celebration_continue),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Global badge-unlock celebration sheet: every badge earned since the shelf was last opened,
 * listed by name and its own icon ([BadgeStrings.badgeIcon]), with Habi's line naming the first
 * one. `skipPartiallyExpanded` + a scrollable [Column] (QA finding, M7): a night with many badges
 * (10 seen live on the Pixel) overflows a half-expanded sheet, leaving "Continue" unreachable.
 */
@Composable
fun BadgeUnlockSheet(
    state: CelebrationsUiState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Tarjeta) {
        var entered by remember { mutableStateOf(false) }
        val scale by
            animateFloatAsState(
                targetValue = if (entered) 1f else 0.9f,
                animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                label = "badge-unlock-scale",
            )
        LaunchedEffect(Unit) { entered = true }

        Column(
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .testTag("badge-sheet"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HabiAvatar(state.spec, Modifier.size(120.dp), animated = false)

            Text(
                stringResource(R.string.celebration_badge_title),
                style = MaterialTheme.typography.headlineLarge,
                color = Tinta,
            )

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.newBadges.forEach { def -> BadgeRow(def) }
            }

            val fallbackName = stringResource(R.string.habi_name_fallback)
            val firstBadgeName = state.newBadges.firstOrNull()?.let { stringResource(BadgeStrings.badgeNameRes(it.id)) }.orEmpty()
            SpeechBubble(
                speaker = stringResource(R.string.habi_speaker, stringResource(HabiVoice.labelRes(state.personality))),
                text =
                    stringResource(
                        HabiVoice.badgeUnlockedRes(state.personality),
                        state.userName.ifBlank { fallbackName },
                        firstBadgeName,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )

            PillButton(
                text = stringResource(R.string.celebration_continue),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One unlocked badge: icon + name, titleMedium per the brief. */
@Composable
private fun BadgeRow(
    def: BadgeDef,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().testTag("badge-row-${def.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(BadgeStrings.badgeIcon(def), contentDescription = null, tint = Hoja, modifier = Modifier.size(20.dp))
        Text(stringResource(BadgeStrings.badgeNameRes(def.id)), style = MaterialTheme.typography.titleMedium, color = Tinta)
    }
}

/**
 * "+N pts": tarjeta+borde pill, chispa icon — mirrors
 * [com.alvarotc.bito.ui.review.SealedDayContent]'s points chip (private there, so reimplemented
 * here rather than imported).
 */
@Composable
private fun PointsChip(
    points: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("celebration-points"),
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
