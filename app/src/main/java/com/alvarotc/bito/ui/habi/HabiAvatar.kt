package com.alvarotc.bito.ui.habi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.theme.BitoTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val BOB_HALF_RANGE_DP = 2f
private const val BOB_PERIOD_MS = 2400
private const val BLINK_HALF_DURATION_MS = 90
private const val BLINK_MIN_DELAY_MS = 3000L
private const val BLINK_MAX_DELAY_MS = 5000L
private const val TAP_SCALE_X = 1.06f
private const val TAP_SCALE_Y = 0.94f

/**
 * The living Habi bean: idle bob, periodic blink, and (when [onTap] is given) a squash-and-stretch
 * tap response. Purely presentational — [spec] already carries mood, personality and the equipped
 * set; this composable owns no state about what Habi wears or feels.
 */
@Composable
fun HabiAvatar(
    spec: HabiSpec,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "habi-bob")
    val bobPhase by
        infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(BOB_PERIOD_MS, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "habi-bob-phase",
        )

    val blink = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(BLINK_MIN_DELAY_MS, BLINK_MAX_DELAY_MS))
            blink.animateTo(1f, tween(BLINK_HALF_DURATION_MS, easing = LinearEasing))
            blink.animateTo(0f, tween(BLINK_HALF_DURATION_MS, easing = LinearEasing))
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scaleX by
        animateFloatAsState(
            targetValue = if (pressed) TAP_SCALE_X else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "habi-scale-x",
        )
    val scaleY by
        animateFloatAsState(
            targetValue = if (pressed) TAP_SCALE_Y else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "habi-scale-y",
        )

    val contentDescription = stringResource(R.string.habi_avatar_cd)
    val density = LocalDensity.current
    val bobPx = with(density) { (bobPhase * BOB_HALF_RANGE_DP).dp.toPx() }

    val canvasModifier =
        modifier
            .semantics { this.contentDescription = contentDescription }
            .let { base ->
                if (onTap != null) {
                    base.clickable(interactionSource = interactionSource, indication = null, onClick = onTap)
                } else {
                    base
                }
            }
            .graphicsLayer {
                translationY = bobPx
                this.scaleX = scaleX
                this.scaleY = scaleY
            }

    Canvas(canvasModifier) {
        drawHabi(spec, blink = blink.value)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2ECE1)
@Composable
private fun HabiAvatarMoodGridPreview() {
    BitoTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            for (personality in Personality.entries) {
                Row {
                    for (mood in Mood.entries) {
                        HabiAvatar(
                            spec = HabiSpec(mood, personality, EquippedSet()),
                            modifier = Modifier.size(72.dp).padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2ECE1)
@Composable
private fun HabiAvatarDoradoPreview() {
    BitoTheme {
        HabiAvatar(
            spec = HabiSpec(Mood.RADIANT, Personality.CHEERLEADER, EquippedSet(bodyColor = "body-dorado")),
            modifier = Modifier.size(150.dp),
        )
    }
}
