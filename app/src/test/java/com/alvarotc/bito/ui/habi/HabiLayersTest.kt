package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La descripción pura de Habi (spec §3.2, §5.2 y §9.1 «habiLayers»). */
class HabiLayersTest {
    private val delta = 1e-5f
    private val dressed =
        EquippedSet(
            bodyColor = "body-cielo",
            eyeColor = "eyes-azul",
            head = "head-boina",
            face = "face-gafas-sol",
            neck = "neck-bufanda",
            feet = "feet-calcetines",
        )

    private fun spec(
        mood: Mood = Mood.NORMAL,
        personality: Personality = Personality.SARGENTO,
        equipped: EquippedSet = dressed,
    ) = HabiSpec(mood, personality, equipped)

    @Test
    fun `layers come in the spec order, iris right after the eyes`() {
        val parts = habiLayers(spec()).map { it.part }
        assertEquals(
            listOf(
                HabiPart.SHADOW, HabiPart.BODY, HabiPart.FEET, HabiPart.NECK, HabiPart.EYES, HabiPart.IRIS,
                HabiPart.MOUTH, HabiPart.MARKS, HabiPart.BROWS, HabiPart.GLASSES, HabiPart.HEAD,
            ),
            parts.distinct(),
        )
        assertEquals(parts.sortedBy { it.ordinal }, parts)
    }

    @Test
    fun `each equipped item is painted exactly once`() {
        val keys = habiLayers(spec()).map { it.key }
        for (id in listOf("head-boina", "face-gafas-sol", "neck-bufanda", "feet-calcetines")) {
            assertEquals(id, 1, keys.count { it == id })
        }
        assertEquals(1, keys.count { it == "cuerpo" })
    }

    @Test
    fun `a costume replaces the body and hides head, face, neck and feet but keeps the eye color`() {
        val layers = habiLayers(spec(equipped = dressed.copy(costume = "costume-reno")))
        val body = layers.single { it.part == HabiPart.BODY }
        assertEquals("costume-reno", body.key)
        assertNull(body.tint)
        val parts = layers.map { it.part }.toSet()
        assertTrue(parts.none { it in setOf(HabiPart.FEET, HabiPart.NECK, HabiPart.GLASSES, HabiPart.HEAD) })
        assertTrue(layers.filter { it.part == HabiPart.IRIS }.all { it.tint == HabiTintTable.eyes["eyes-azul"] && it.iris })
        assertTrue(layers.any { it.part == HabiPart.IRIS })
    }

    @Test
    fun `the body takes the catalog tint, salvia stays untinted and a scene override wins`() {
        assertEquals(HabiTintTable.body["body-cielo"], habiLayers(spec()).single { it.part == HabiPart.BODY }.tint)
        assertNull(habiLayers(spec(equipped = EquippedSet())).single { it.part == HabiPart.BODY }.tint)
        val muted = HabiSpec(Mood.WILTED, Personality.NEUTRA, dressed, bodyToneOverride = HabiTintTable.muted)
        assertEquals(HabiTintTable.muted, habiLayers(muted).single { it.part == HabiPart.BODY }.tint)
    }

    @Test
    fun `the default eyes carry no iris layer`() {
        assertTrue(habiLayers(spec(equipped = EquippedSet())).none { it.part == HabiPart.IRIS })
    }

    @Test
    fun `the twelve cells of the personality by mood table`() {
        val table =
            mapOf(
                (Mood.RADIANT to Personality.NEUTRA) to HabiFace(Eyes.HAPPY, Mouth.HAPPY, null),
                (Mood.NORMAL to Personality.NEUTRA) to HabiFace(Eyes.OPEN, Mouth.SMILE, null),
                (Mood.WILTED to Personality.NEUTRA) to HabiFace(Eyes.SAD, Mouth.SAD, null),
                (Mood.DRAMATIC to Personality.NEUTRA) to HabiFace(Eyes.TEARY, Mouth.GRIMACE, null),
                (Mood.RADIANT to Personality.CHEERLEADER) to HabiFace(Eyes.HAPPY, Mouth.HAPPY, Brows.CHEERFUL),
                (Mood.NORMAL to Personality.CHEERLEADER) to HabiFace(Eyes.OPEN, Mouth.SMILE, Brows.CHEERFUL),
                (Mood.WILTED to Personality.CHEERLEADER) to HabiFace(Eyes.SAD, Mouth.SAD, Brows.WORRIED),
                (Mood.DRAMATIC to Personality.CHEERLEADER) to HabiFace(Eyes.TEARY, Mouth.O, null),
                (Mood.RADIANT to Personality.SARGENTO) to HabiFace(Eyes.HAPPY, Mouth.SMILE, Brows.HARD),
                (Mood.NORMAL to Personality.SARGENTO) to HabiFace(Eyes.OPEN, Mouth.NEUTRAL, Brows.HARD),
                (Mood.WILTED to Personality.SARGENTO) to HabiFace(Eyes.OPEN, Mouth.SAD, Brows.WORRIED),
                // Validado por el arquitecto: los ojos enfadados traen el párpado, sin cejas sueltas.
                (Mood.DRAMATIC to Personality.SARGENTO) to HabiFace(Eyes.ANGRY, Mouth.GRIMACE, null),
            )
        assertEquals(12, table.size)
        for ((cell, face) in table) {
            assertEquals("$cell", face, faceOf(cell.first, cell.second))
        }
        val keys = habiLayers(spec(Mood.DRAMATIC, Personality.SARGENTO, EquippedSet())).map { it.key }
        assertTrue(keys.containsAll(listOf("ojo-enfadado-i", "ojo-enfadado-d", "boca-mueca")))
        assertTrue(keys.none { it.startsWith("ceja-") })
    }

    @Test
    fun `marks are always on for sargento and cheerleader and never for neutra`() {
        for (mood in Mood.entries) {
            fun marks(p: Personality) = habiLayers(spec(mood, p, EquippedSet())).filter { it.part == HabiPart.MARKS }.map { it.key }
            assertEquals(listOf("pintura-i-a", "pintura-i-b", "pintura-d-a", "pintura-d-b"), marks(Personality.SARGENTO))
            assertEquals(listOf("mofletes-i", "mofletes-d"), marks(Personality.CHEERLEADER))
            assertEquals(emptyList<String>(), marks(Personality.NEUTRA))
        }
    }

    @Test
    fun `closedEyes swaps in the closed pieces and drops the brows`() {
        val closed = HabiSpec(Mood.WILTED, Personality.CHEERLEADER, EquippedSet(), closedEyes = true)
        val layers = habiLayers(closed)
        assertEquals(listOf("ojo-cerrado-i", "ojo-cerrado-d"), layers.filter { it.part == HabiPart.EYES }.map { it.key })
        assertTrue(layers.none { it.part == HabiPart.BROWS })
    }

    @Test
    fun `delighted forces happy eyes and mouth and keeps the brows of the mood`() {
        val face = faceOf(Mood.WILTED, Personality.SARGENTO, delighted = true)
        assertEquals(HabiFace(Eyes.HAPPY, Mouth.HAPPY, Brows.WORRIED), face)
    }

    @Test
    fun `a blink fades the open eyes into the closed piece`() {
        val half = habiLayers(spec(), HabiLook(eyeClosure = 0.25f)).filter { it.part == HabiPart.EYES }
        assertEquals(0.75f, half.first { it.key == "ojo-abierto-i" }.alpha, delta)
        assertEquals(0.25f, half.first { it.key == "ojo-cerrado-i" }.alpha, delta)
        val shut = habiLayers(spec(), HabiLook(eyeClosure = 1f)).filter { it.part == HabiPart.EYES }.map { it.key }
        assertEquals(listOf("ojo-cerrado-i", "ojo-cerrado-d"), shut)
    }

    @Test
    fun `the face and the glasses follow the gaze, the body and the hat stay`() {
        val layers = habiLayers(spec(), HabiLook(gazeX = 1f, gazeY = -2f))
        for (layer in layers) {
            val moves = layer.part in FACE_PARTS || layer.part == HabiPart.GLASSES
            assertEquals(layer.key, if (moves) GAZE_MAX_X else 0f, layer.dx, delta)
            assertEquals(layer.key, if (moves) -GAZE_MAX_Y else 0f, layer.dy, delta)
        }
    }

    @Test
    fun `crossfadeFace fades the old face out and the new one in over the same body`() {
        val from = habiLayers(spec(Mood.NORMAL))
        val to = habiLayers(spec(Mood.DRAMATIC))
        val mid = crossfadeFace(from, to, 0.4f)
        assertEquals(0.6f, mid.first { it.key == "boca-neutra" }.alpha, delta)
        assertEquals(0.4f, mid.first { it.key == "boca-mueca" }.alpha, delta)
        assertEquals(1, mid.count { it.part == HabiPart.BODY })
        // El cuerpo y lo puesto debajo, las dos caras en medio y las gafas y la cabeza encima.
        assertEquals(listOf(HabiPart.SHADOW, HabiPart.BODY, HabiPart.FEET, HabiPart.NECK), mid.take(4).map { it.part })
        assertEquals(listOf(HabiPart.GLASSES, HabiPart.HEAD), mid.takeLast(2).map { it.part })
        assertEquals(to, crossfadeFace(from, to, 1f))
    }

    @Test
    fun `rigFit centers the canvas and stands it on the bottom edge`() {
        val canvas = RigCanvas(left = -10f, top = -40f, width = 120f, height = 160f)
        val fit = rigFit(240f, 400f, canvas)
        assertEquals(2f, fit.scale, delta)
        // Ancho 240 lleno; alto 320 de 400, así que sobran 80 arriba.
        assertEquals(20f, fit.originX, delta)
        assertEquals(80f + 80f, fit.originY, delta)
    }

    @Test
    fun `small avatars decode subsampled and big ones never`() {
        assertEquals(1, sampleSizeFor(drawHeightPx = 500f, drawHeightDp = 200f, sourceHeightPx = 962f))
        assertEquals(1, sampleSizeFor(drawHeightPx = 100f, drawHeightDp = 100f, sourceHeightPx = 962f))
        assertEquals(4, sampleSizeFor(drawHeightPx = 150f, drawHeightDp = 56f, sourceHeightPx = 962f))
        assertEquals(8, sampleSizeFor(drawHeightPx = 96f, drawHeightDp = 0f, sourceHeightPx = 962f))
    }

    @Test
    fun `a costume shifts the whole face by its offset and masks it, the body and the rest stay`() {
        val reno = HabiAtlas.costumeFaces.getValue("costume-reno")
        assertEquals(-19f, reno.dx, delta)
        assertEquals(-71f, reno.dy, delta)
        val layers = habiLayers(spec(Mood.NORMAL, Personality.SARGENTO, EquippedSet(costume = "costume-reno")), HabiLook(gazeX = 1f))
        for (layer in layers) {
            if (layer.part in FACE_PARTS) {
                assertTrue(layer.key, layer.masked)
                assertEquals(layer.key, GAZE_MAX_X + reno.dx, layer.dx, delta)
                assertEquals(layer.key, reno.dy, layer.dy, delta)
            } else {
                assertEquals(layer.key, false, layer.masked)
                assertEquals(layer.key, 0f, layer.dx, delta)
                assertEquals(layer.key, 0f, layer.dy, delta)
            }
        }
        assertTrue(layers.any { it.part == HabiPart.MARKS && it.masked })
        assertTrue(layers.any { it.part == HabiPart.BROWS && it.masked })
    }

    @Test
    fun `without a costume nothing is masked or shifted`() {
        assertTrue(habiLayers(spec()).none { it.masked || it.dx != 0f || it.dy != 0f })
    }

    @Test
    fun `the face table is read from the generated table, every cell resolves to atlas pieces`() {
        for (mood in Mood.entries) {
            for (personality in Personality.entries) {
                for (layer in habiLayers(spec(mood, personality, EquippedSet())).filter { it.part != HabiPart.IRIS }) {
                    assertTrue("${layer.key} ($mood/$personality)", layer.key in HabiAtlas.pieces)
                }
            }
        }
    }

    @Test
    fun `the exported canvas is symmetric around the body so rigFit centers Habi`() {
        val canvas = HabiAtlas.canvas
        assertEquals(HabiAtlas.BODY_WIDTH / 2f, canvas.left + canvas.width / 2f, 1f)
        val fit = rigFit(canvas.width, canvas.height)
        assertEquals(-canvas.left, fit.originX, delta)
    }
}
