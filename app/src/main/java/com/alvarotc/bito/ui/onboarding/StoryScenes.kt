package com.alvarotc.bito.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.Tinta
import com.alvarotc.bito.ui.theme.TintaSuave

// No token for these — sampled straight off the 7b-7d mockups, same precedent as HabiStage's own
// HabiStageColor (art-phase-tunable). Everything that DOES land on a token keeps the token:
// the 7b card is Borde, calendars/notes/ring-card are Tarjeta with Borde chrome, X's and strikes
// are Brasa (the mockup's #D9704F is Brasa exactly — NOT Peligro), squiggle is TintaSuave.
private val SofaBack = Color(0xFFBFB49C)
private val SofaSeat = Color(0xFFCCC2AC)
private val SofaArm = Color(0xFFB3A78E)
private val PropGrey = Color(0xFFB8B0A0) // dropped remote keys + 7c's frustration dashes
private val PropGreyLite = Color(0xFFD9D2C2) // the remote's rolled-away buttons
private val NoteLine = Color(0xFFC6C0B1) // pencil lines on 7c's crossed-out notes
private val PotClay = Color(0xFFC98D6B) // 7d's flower pot (warmer/lighter than Brasa on purpose)
private val LeafLite = Color(0xFF6FB183) // 7d's second sprout leaf — a lighter Hoja

// Every scene sits on the same soft floor ellipse: Tinta at 6% reproduces the sampled shadow on
// BOTH card tints (#DCD6C9 over Borde, #DAE2D6 over HojaTinte) with one paint.
private const val FLOOR_SHADOW_ALPHA = 0.06f

/** The mockup cards' own corner radius (~23px ≈ 20dp) — measurably tighter than BitoCard's 28. */
private val SceneCardShape = RoundedCornerShape(20.dp)

/**
 * The onboarding lore's 3 illustrated beats (mockups 7b-7d) — reused as-is by the pager's motion
 * and the Ajustes replay. Real Habi ([HabiAvatar], `animated = false` for test idling), everything
 * else (sofa, calendar, notes, plant, ring) is plain Compose shapes.
 *
 * Geometry is TRANSCRIBED from the mockups, not eyeballed: each mockup card renders at 417x300px,
 * the scene composes at 363x259dp on the reference 411dp phone (24dp gutters, aspect 1.4), so
 * every size below is mockup px x 363/417 and every position is a center-anchored offset (element
 * center minus card center, converted the same way) — center anchoring keeps the composition
 * intact across device widths. Habi's own box sizes derive from the egg filling 0.68x0.84 of the
 * avatar box with its center at (0.5, 0.55) (HabiDrawing's BODY_* constants), which is also why
 * each avatar offset carries a small extra -0.05*box on y.
 *
 * Personality stays [Personality.NEUTRA] across all three — the lore is Habi's own history, told
 * before the user picks a personality (docs/07 §4), so one consistent face reads as the same
 * character. 7c's angry brows (mockup-explicit, and NEUTRA never gets `browAngleDeg` from
 * [com.alvarotc.bito.domain.model.faceParamsOf] — only SARGENTO does, dragging war paint and a
 * smirk along) are therefore drawn HERE as a scene prop overlaid on the avatar ([AngryBrows]),
 * not by switching personality.
 */
@Composable
fun StoryScene(
    step: OnboardingStep,
    modifier: Modifier = Modifier,
) {
    when (step) {
        OnboardingStep.STORY_1 -> SofaScene(modifier)
        OnboardingStep.STORY_2 -> BlockedScene(modifier)
        OnboardingStep.STORY_3 -> SystemScene(modifier)
        else -> error("StoryScene only draws the 3 story beats (STORY_1..STORY_3), not $step")
    }
}

/**
 * 7b: Habi slumped IN the couch, mood WILTED — sunk behind the seat cushion, the remote dropped
 * on the floor beside it. Paint order is the scene's whole trick: back rest, then Habi, then the
 * seat cushion OVER Habi's lap, then the arm rests over both — that overlap is what turned the
 * mockup's "two pills and a loose rectangle" into a couch someone is actually sitting in.
 */
@Composable
private fun SofaScene(modifier: Modifier) {
    Box(modifier.clip(SceneCardShape).background(Borde)) {
        FloorShadow(offsetX = (-10).dp)
        // Back rest — one tall rounded mass, darker than the seat, behind everything.
        Box(
            Modifier
                .align(Alignment.Center)
                .offset((-32).dp, 16.dp)
                .size(188.dp, 91.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SofaBack),
        )
        HabiAvatar(
            spec = HabiSpec(Mood.WILTED, Personality.NEUTRA, EquippedSet()),
            modifier = Modifier.align(Alignment.Center).offset((-24).dp, (-23).dp).size(92.dp),
            animated = false,
        )
        // Seat cushion, lightest tone, painted over Habi's lap so the bean sinks into the couch.
        Box(
            Modifier
                .align(Alignment.Center)
                .offset((-31).dp, 38.dp)
                .size(168.dp, 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SofaSeat),
        )
        SofaArmRest(offsetX = (-123).dp)
        SofaArmRest(offsetX = 61.dp)
        // The dropped remote on the floor to the couch's right: two tilted keys, two rolled-away
        // round buttons — all resting on the shadow line.
        RemoteKey(x = 94.dp, y = 61.dp, rotation = -8f)
        RemoteKey(x = 115.dp, y = 67.dp, rotation = 18f)
        RemoteButton(x = 72.dp, y = 70.dp, diameter = 11.dp)
        RemoteButton(x = 104.dp, y = 80.dp, diameter = 8.dp)
    }
}

/** Darkest sofa tone, capsule-shaped, overlapping both the back rest's ends and the seat's. */
@Composable
private fun BoxScope.SofaArmRest(offsetX: Dp) {
    Box(
        Modifier
            .align(Alignment.Center)
            .offset(offsetX, 21.dp)
            .size(26.dp, 77.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(SofaArm),
    )
}

@Composable
private fun BoxScope.RemoteKey(
    x: Dp,
    y: Dp,
    rotation: Float,
) {
    Box(
        Modifier
            .align(Alignment.Center)
            .offset(x, y)
            .rotate(rotation)
            .size(12.dp, 23.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(PropGrey),
    )
}

@Composable
private fun BoxScope.RemoteButton(
    x: Dp,
    y: Dp,
    diameter: Dp,
) {
    Box(
        Modifier
            .align(Alignment.Center)
            .offset(x, y)
            .size(diameter)
            .clip(CircleShape)
            .background(PropGreyLite),
    )
}

/**
 * 7c: Habi stuck and cross, mood DRAMATIC (worry-wave mouth) plus the [AngryBrows] overlay — a
 * calendar of missed days top-right, two crossed-out attempt notes scattered bottom-left, and two
 * frustration ticks radiating off the head toward the top-left corner.
 */
@Composable
private fun BlockedScene(modifier: Modifier) {
    Box(modifier.clip(SceneCardShape).background(HojaTinte)) {
        FloorShadow()
        FrustrationTicks(
            Modifier.align(Alignment.Center).offset((-101).dp, (-60).dp).size(30.dp, 24.dp),
        )
        Box(Modifier.align(Alignment.Center).offset((-37).dp, (-20).dp).size(94.dp)) {
            HabiAvatar(
                spec = HabiSpec(Mood.DRAMATIC, Personality.NEUTRA, EquippedSet()),
                modifier = Modifier.matchParentSize(),
                animated = false,
            )
            AngryBrows(Modifier.matchParentSize())
        }
        MissedCalendar(Modifier.align(Alignment.Center).offset(89.dp, (-44).dp))
        CrossedNote(x = (-95).dp, y = 58.dp, width = 42.dp, height = 32.dp, rotation = -12f, struck = true)
        CrossedNote(x = (-49).dp, y = 69.dp, width = 30.dp, height = 24.dp, rotation = 8f, struck = false)
    }
}

/** Two comic anger ticks, upper-left of Habi's head, both pointing away from it. */
@Composable
private fun FrustrationTicks(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 3.5.dp.toPx()
        drawLine(
            PropGrey,
            Offset(12.6.dp.toPx(), 2.1.dp.toPx()),
            Offset(25.4.dp.toPx(), 9.9.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            PropGrey,
            Offset(6.3.dp.toPx(), 17.3.dp.toPx()),
            Offset(15.7.dp.toPx(), 20.7.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * The mockup's furrowed brows, drawn over the avatar box. Anchored to HabiDrawing's published eye
 * geometry (eyes at x = 0.5 +- 0.14, y = 0.5 of the box — EYE_Y/EYE_DX), sitting just above each
 * eye and descending toward the center. Static-scene-only: the avatar underneath renders its
 * resting frame (`animated = false`), so the anchor cannot drift mid-life.
 */
@Composable
private fun AngryBrows(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 3.5.dp.toPx()
        drawLine(
            Tinta,
            Offset(size.width * 0.30f, size.height * 0.395f),
            Offset(size.width * 0.455f, size.height * 0.458f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            Tinta,
            Offset(size.width * 0.70f, size.height * 0.395f),
            Offset(size.width * 0.545f, size.height * 0.458f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 7c's calendar of misses, mockup-literal: header strip, 3x3 grid with hairline dividers, Brasa
 * X's on the failed days (r1: two, r2: first and last) and a lost little squiggle where the
 * bottom row's middle day should have been.
 */
@Composable
private fun MissedCalendar(modifier: Modifier) {
    Box(
        modifier
            .size(90.dp, 79.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Tarjeta)
            .border(1.dp, Borde, RoundedCornerShape(9.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(16.dp).background(Borde))
        Canvas(Modifier.matchParentSize()) {
            val headerH = 16.dp.toPx()
            val cellW = size.width / 3f
            val cellH = (size.height - headerH) / 3f
            val hairline = 1.dp.toPx()
            repeat(2) { i ->
                val x = cellW * (i + 1)
                drawLine(Borde, Offset(x, headerH), Offset(x, size.height), hairline)
                val y = headerH + cellH * (i + 1)
                drawLine(Borde, Offset(0f, y), Offset(size.width, y), hairline)
            }
            val arm = 5.5.dp.toPx()
            val xStroke = 2.5.dp.toPx()
            listOf(0 to 0, 0 to 1, 1 to 0, 1 to 2).forEach { (row, col) ->
                val cx = cellW * col + cellW / 2f
                val cy = headerH + cellH * row + cellH / 2f
                drawLine(Brasa, Offset(cx - arm, cy - arm), Offset(cx + arm, cy + arm), xStroke, StrokeCap.Round)
                drawLine(Brasa, Offset(cx - arm, cy + arm), Offset(cx + arm, cy - arm), xStroke, StrokeCap.Round)
            }
            val sx = cellW * 1.5f
            val sy = headerH + cellH * 2.5f
            val squiggle =
                Path().apply {
                    moveTo(sx - 5.dp.toPx(), sy - 3.dp.toPx())
                    cubicTo(
                        sx - 1.dp.toPx(),
                        sy - 6.dp.toPx(),
                        sx + 3.dp.toPx(),
                        sy - 4.dp.toPx(),
                        sx + 2.dp.toPx(),
                        sy - 1.dp.toPx(),
                    )
                    cubicTo(
                        sx + 1.dp.toPx(),
                        sy + 2.dp.toPx(),
                        sx - 2.dp.toPx(),
                        sy + 2.dp.toPx(),
                        sx + 4.dp.toPx(),
                        sy + 4.dp.toPx(),
                    )
                }
            drawPath(squiggle, TintaSuave, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/**
 * A scattered attempt note, tilted like it was tossed. The card clips its own rounded shape, but
 * the pencil lines and the Brasa strike live in a sibling canvas on the SAME rotated layer, so
 * the strike can overhang the note's edges the way the mockup's does.
 */
@Composable
private fun BoxScope.CrossedNote(
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    rotation: Float,
    struck: Boolean,
) {
    Box(Modifier.align(Alignment.Center).offset(x, y).rotate(rotation).size(width, height)) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(4.dp))
                .background(Tarjeta)
                .border(1.dp, Borde, RoundedCornerShape(4.dp)),
        )
        Canvas(Modifier.matchParentSize()) {
            val lineStroke = 2.5.dp.toPx()
            if (struck) {
                drawLine(
                    NoteLine,
                    Offset(size.width * 0.15f, size.height * 0.40f),
                    Offset(size.width * 0.80f, size.height * 0.40f),
                    lineStroke,
                    StrokeCap.Round,
                )
                drawLine(
                    NoteLine,
                    Offset(size.width * 0.15f, size.height * 0.62f),
                    Offset(size.width * 0.62f, size.height * 0.62f),
                    lineStroke,
                    StrokeCap.Round,
                )
                val overhang = 3.dp.toPx()
                drawLine(
                    Brasa,
                    Offset(-overhang, size.height * 0.58f),
                    Offset(size.width + overhang, size.height * 0.38f),
                    3.dp.toPx(),
                    StrokeCap.Round,
                )
            } else {
                drawLine(
                    NoteLine,
                    Offset(size.width * 0.20f, size.height * 0.48f),
                    Offset(size.width * 0.72f, size.height * 0.48f),
                    lineStroke,
                    StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * 7d: Habi thriving, mood RADIANT — a tilted mini habit-card (progress ring + streak dots), a
 * potted sprout, and three plus sparks (two Brasa, one Hoja) drifting around the group.
 */
@Composable
private fun SystemScene(modifier: Modifier) {
    Box(modifier.clip(SceneCardShape).background(HojaTinte)) {
        FloorShadow(offsetX = (-10).dp)
        PlusSpark(x = (-14).dp, y = (-74).dp, span = 12.dp, color = Brasa)
        PlusSpark(x = (-122).dp, y = 0.dp, span = 10.dp, color = Hoja)
        PlusSpark(x = 111.dp, y = (-32).dp, span = 10.dp, color = Brasa)
        HabiAvatar(
            spec = HabiSpec(Mood.RADIANT, Personality.NEUTRA, EquippedSet()),
            modifier = Modifier.align(Alignment.Center).offset((-47).dp, (-23).dp).size(100.dp),
            animated = false,
        )
        HabitMiniCard(Modifier.align(Alignment.Center).offset(24.dp, 20.dp))
        PottedPlant(Modifier.align(Alignment.Center).offset(104.dp, 34.dp).size(40.dp, 77.dp))
    }
}

/** A chunky rounded plus — the mockup's sparks are thicker than the stock 2px icon stroke. */
@Composable
private fun BoxScope.PlusSpark(
    x: Dp,
    y: Dp,
    span: Dp,
    color: Color,
) {
    Canvas(Modifier.align(Alignment.Center).offset(x, y).size(span)) {
        val stroke = 3.dp.toPx()
        drawLine(color, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke, StrokeCap.Round)
    }
}

/** The tilted mini habit-card: Hoja progress ring (gap at the lower-left) over 2-of-3 streak dots. */
@Composable
private fun HabitMiniCard(modifier: Modifier) {
    Box(
        modifier
            .rotate(10f)
            .size(44.dp, 70.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Tarjeta)
            .border(1.dp, Borde, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(26.dp)) {
                drawArc(
                    Hoja,
                    startAngle = 140f,
                    sweepAngle = 310f,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { i ->
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (i < 2) Hoja else Borde))
                }
            }
        }
    }
}

/** Two-leaf sprout in a clay trapezoid pot — the darker leaf leans left, the lighter one right. */
@Composable
private fun PottedPlant(modifier: Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val potH = 25.dp.toPx()
        val potTop = size.height - potH
        drawLine(
            Hoja,
            Offset(cx, potTop),
            Offset(cx, potTop - 12.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val leftCenter = Offset(cx - 6.dp.toPx(), potTop - 16.dp.toPx())
        rotate(degrees = -32f, pivot = leftCenter) {
            drawOval(
                Hoja,
                topLeft = Offset(leftCenter.x - 5.5.dp.toPx(), leftCenter.y - 11.dp.toPx()),
                size = Size(11.dp.toPx(), 22.dp.toPx()),
            )
        }
        val rightCenter = Offset(cx + 7.dp.toPx(), potTop - 21.dp.toPx())
        rotate(degrees = 36f, pivot = rightCenter) {
            drawOval(
                LeafLite,
                topLeft = Offset(rightCenter.x - 6.dp.toPx(), rightCenter.y - 13.dp.toPx()),
                size = Size(12.dp.toPx(), 26.dp.toPx()),
            )
        }
        val corner = 3.dp.toPx()
        val pot =
            Path().apply {
                moveTo(cx - 14.dp.toPx(), potTop)
                lineTo(cx + 14.dp.toPx(), potTop)
                lineTo(cx + 11.4.dp.toPx(), size.height - corner)
                cubicTo(
                    cx + 11.dp.toPx(),
                    size.height,
                    cx + 11.dp.toPx(),
                    size.height,
                    cx + 8.dp.toPx(),
                    size.height,
                )
                lineTo(cx - 8.dp.toPx(), size.height)
                cubicTo(
                    cx - 11.dp.toPx(),
                    size.height,
                    cx - 11.dp.toPx(),
                    size.height,
                    cx - 11.4.dp.toPx(),
                    size.height - corner,
                )
                close()
            }
        drawPath(pot, PotClay)
    }
}

/**
 * The floor every beat stands on: 250x15dp soft ellipse, center 87dp below the card's center
 * (83% of the card's height, all three mockups agree), nudged left where the mockup nudges it.
 */
@Composable
private fun BoxScope.FloorShadow(offsetX: Dp = 0.dp) {
    Canvas(Modifier.align(Alignment.Center).offset(offsetX, 87.dp).size(250.dp, 15.dp)) {
        drawOval(Tinta.copy(alpha = FLOOR_SHADOW_ALPHA))
    }
}
