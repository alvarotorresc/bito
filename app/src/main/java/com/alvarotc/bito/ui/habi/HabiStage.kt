package com.alvarotc.bito.ui.habi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sampled from the mockup, between HojaTinte (#E3EDE0) and HabiSalvia (#A9C9A1) — art-phase-tunable. */
private val HabiStageColor = Color(0xFFDDE9D6)

/**
 * Habi's mood stage: a soft-green circle behind the avatar. Shared by [HabiScreen] (mockup 4a, the
 * Habi screen's own scenario) and [com.alvarotc.bito.ui.review.SealedDayContent] (E2 "día
 * sellado", GUIA 5b), both at the same 220dp/150dp defaults. [onTap] is optional — E2 shows Habi
 * still, no tap affordance.
 *
 * La sombra vive ahora dentro de `drawHabi` (una sola, y tambien en el widget); este `Box` habria
 * sido la segunda.
 */
@Composable
fun HabiStage(
    spec: HabiSpec,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    stageSize: Dp = 220.dp,
    avatarSize: Dp = 150.dp,
    delighted: Boolean = false,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(stageSize)
                .clip(CircleShape)
                .background(HabiStageColor)
                .testTag("habi-stage"),
        )
        HabiAvatar(spec = spec, modifier = Modifier.size(avatarSize), onTap = onTap, delighted = delighted)
    }
}
