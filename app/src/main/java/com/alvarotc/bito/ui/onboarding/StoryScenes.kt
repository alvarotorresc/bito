package com.alvarotc.bito.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.icons.BitoIcons
import com.alvarotc.bito.ui.theme.Borde
import com.alvarotc.bito.ui.theme.Brasa
import com.alvarotc.bito.ui.theme.Hoja
import com.alvarotc.bito.ui.theme.HojaTinte
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.theme.Peligro
import com.alvarotc.bito.ui.theme.Tarjeta
import com.alvarotc.bito.ui.theme.TintaSuave

// No token for these two — sampled off the mockup, same precedent as HabiStage's own
// HabiStageColor (art-phase-tunable). The 7b card background itself is the Borde token (below) —
// it reads close enough to the mockup's neutral flat card that a bespoke color isn't warranted.
private val SofaBody = Color(0xFFB9AE8C)
private val SofaArm = Color(0xFFC9BFA0)
private val RemoteColor = Color(0xFF8B8168)

/**
 * The onboarding lore's 3 illustrated beats (mockups 7b-7d) — reused as-is by T8 for the pager's
 * motion. Real Habi ([HabiAvatar], `animated = false` for test idling — T8 wires the real motion),
 * everything else (sofa, calendar, notes, plant, ring) is plain Compose shapes: these read as
 * stylized silhouettes, not pixel-matched art.
 *
 * Personality stays [Personality.NEUTRA] across all three — the lore is Habi's own history, told
 * before the user picks a personality (docs/07 §4), so keeping one consistent face across the arc
 * reads as the same character. That constraint costs 7c its furrowed brow: [Personality.NEUTRA]
 * never gets `browAngleDeg` from [com.alvarotc.bito.domain.model.faceParamsOf] under any mood —
 * only [Personality.SARGENTO] does — so the "wanted to change" frown leans on the calendar's red
 * X's and the struck-through notes to carry the beat, not the face.
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

/** 7b: Habi slumped on the couch, mood WILTED — the remote dropped on the floor beside it. */
@Composable
private fun SofaScene(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Borde)) {
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.size(width = 22.dp, height = 62.dp).clip(RoundedCornerShape(10.dp)).background(SofaArm))
            Box(Modifier.size(width = 130.dp, height = 48.dp).clip(RoundedCornerShape(14.dp)).background(SofaBody))
            Box(Modifier.size(width = 22.dp, height = 62.dp).clip(RoundedCornerShape(10.dp)).background(SofaArm))
        }
        HabiAvatar(
            spec = HabiSpec(Mood.WILTED, Personality.NEUTRA, EquippedSet()),
            modifier = Modifier.size(72.dp).align(Alignment.Center),
            animated = false,
        )
        // The dropped remote, two stray blocks on the floor to the sofa's right.
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.size(width = 9.dp, height = 20.dp).clip(RoundedCornerShape(3.dp)).background(RemoteColor))
            Box(Modifier.size(width = 9.dp, height = 14.dp).clip(RoundedCornerShape(3.dp)).background(RemoteColor))
        }
    }
}

/** 7c: Habi stuck, mood DRAMATIC (prolonged-absence face) — a calendar of missed days and crossed-out notes. */
@Composable
private fun BlockedScene(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(HojaTinte)) {
        // Frustration dashes, top-left.
        Canvas(Modifier.align(Alignment.TopStart).padding(top = 36.dp, start = 36.dp).size(width = 20.dp, height = 24.dp)) {
            repeat(3) { i ->
                val y = size.height * (i / 2f)
                drawLine(
                    TintaSuave,
                    Offset(0f, y),
                    Offset(size.width, y - 10f),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        HabiAvatar(
            spec = HabiSpec(Mood.DRAMATIC, Personality.NEUTRA, EquippedSet()),
            modifier = Modifier.size(80.dp).align(Alignment.Center),
            animated = false,
        )
        CalendarOfMisses(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 28.dp))
        CrossedNotes(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 26.dp))
    }
}

@Composable
private fun CalendarOfMisses(modifier: Modifier) {
    Box(modifier.size(width = 62.dp, height = 58.dp).clip(RoundedCornerShape(10.dp)).background(Tarjeta)) {
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Row 1: two missed days. Row 2: one missed, one still open.
            repeat(2) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) { col ->
                        val missed = row == 0 || col == 0
                        Box(
                            Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(Papel),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (missed) {
                                Icon(BitoIcons.X, contentDescription = null, tint = Peligro, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrossedNotes(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) {
            Box(Modifier.size(width = 40.dp, height = 13.dp).clip(RoundedCornerShape(3.dp)).background(Tarjeta)) {
                Canvas(Modifier.matchParentSize().padding(horizontal = 4.dp)) {
                    drawLine(
                        Peligro,
                        Offset(0f, size.height / 2f),
                        Offset(size.width, size.height / 2f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** 7d: Habi thriving, mood RADIANT — a tiny ring-progress card and a potted sprout, plus signs drifting around it. */
@Composable
private fun SystemScene(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(HojaTinte)) {
        Icon(
            BitoIcons.Plus,
            contentDescription = null,
            tint = Brasa,
            modifier = Modifier.size(16.dp).align(Alignment.TopCenter).padding(top = 26.dp),
        )
        Icon(
            BitoIcons.Plus,
            contentDescription = null,
            tint = Brasa,
            modifier = Modifier.size(20.dp).align(Alignment.CenterStart).padding(start = 22.dp),
        )
        Icon(
            BitoIcons.Plus,
            contentDescription = null,
            tint = Brasa,
            modifier = Modifier.size(18.dp).align(Alignment.TopEnd).padding(top = 52.dp, end = 36.dp),
        )
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HabiAvatar(
                spec = HabiSpec(Mood.RADIANT, Personality.NEUTRA, EquippedSet()),
                modifier = Modifier.size(80.dp),
                animated = false,
            )
            RingCard()
            PottedPlant()
        }
    }
}

@Composable
private fun RingCard() {
    Box(
        Modifier.size(width = 42.dp, height = 62.dp).clip(RoundedCornerShape(10.dp)).background(Tarjeta),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(22.dp)) {
                drawArc(
                    Hoja,
                    startAngle = -90f,
                    sweepAngle = 260f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { i ->
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (i < 2) Hoja else Borde))
                }
            }
        }
    }
}

@Composable
private fun PottedPlant() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(BitoIcons.Sprout, contentDescription = null, tint = Hoja, modifier = Modifier.size(22.dp))
        Box(
            Modifier.size(width = 22.dp, height = 15.dp)
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 5.dp, bottomEnd = 5.dp))
                .background(Brasa),
        )
    }
}
