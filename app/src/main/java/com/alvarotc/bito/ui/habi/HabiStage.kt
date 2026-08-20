package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.theme.Borde

/** Sampled from the mockup, between HojaTinte (#E3EDE0) and HabiSalvia (#A9C9A1) — art-phase-tunable. */
private val HabiStageColor = Color(0xFFDDE9D6)

// Proportions lifted from the 4a mockup's 220dp stage: a 92x22dp shadow ellipse offset 66dp down.
private const val ELLIPSE_WIDTH_RATIO = 92f / 220f
private const val ELLIPSE_HEIGHT_RATIO = 22f / 220f
private const val ELLIPSE_OFFSET_RATIO = 66f / 220f

/**
 * Habi's mood stage: a soft-green circle with a shadow ellipse under the avatar. Shared by
 * [HabiScreen] (mockup 4a, the Habi screen's own scenario) and
 * [com.alvarotc.bito.ui.review.SealedDayContent] (E2 "día sellado", GUIA 5b), both at the same
 * 220dp/150dp defaults. [onTap] is optional — E2 shows Habi still, no tap affordance — and the
 * shadow ellipse scales with [stageSize] so a differently sized stage keeps its proportions.
 */
@Composable
fun HabiStage(
    spec: HabiSpec,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    stageSize: Dp = 220.dp,
    avatarSize: Dp = 150.dp,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(stageSize)
                .clip(CircleShape)
                .background(HabiStageColor)
                .testTag("habi-stage"),
        )
        // A squat pill stands in for an ellipse — Compose has no ellipse shape primitive.
        Box(
            Modifier
                .size(width = stageSize * ELLIPSE_WIDTH_RATIO, height = stageSize * ELLIPSE_HEIGHT_RATIO)
                .offset(y = stageSize * ELLIPSE_OFFSET_RATIO)
                .clip(RoundedCornerShape(percent = 50))
                .background(Borde.copy(alpha = 0.6f)),
        )
        HabiAvatar(spec = spec, modifier = Modifier.size(avatarSize), onTap = onTap)
    }
}
