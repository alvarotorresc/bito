package com.alvarotc.bito.ui.garden

import com.alvarotc.bito.domain.model.GardenLight
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.LightPhase
import com.alvarotc.bito.ui.habi.RigCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenGeometryTest {
    private val delta = 1e-3f
    private val w = 1339f
    private val h = 1000f

    @Test
    fun `a spot anchors its piece bottom center`() {
        val r = spotRect(GardenSpot(50f, 90f, 40f), 40f, aspect = 0.5f, sceneW = 200f, sceneH = 100f)
        assertEquals(SceneRect(90f, 50f, 20f, 40f), r)
    }

    @Test
    fun `habi stands with the feet on its spot and the canvas around the body`() {
        val canvas = RigCanvas(left = -10f, top = -40f, width = 120f, height = 160f)
        val r = habiRect(200f, 100f, GardenSpot(50f, 90f, 40f), canvas, bodyWidth = 100f, bodyHeight = 100f)
        assertEquals(76f, r.left, delta)
        assertEquals(34f, r.top, delta)
        assertEquals(48f, r.width, delta)
        assertEquals(64f, r.height, delta)
        // Los pies (y = 100 en el cuerpo) caen justo en el 90 % del alto.
        assertEquals(90f, r.top + (100f - canvas.top) * (r.height / canvas.height), delta)
    }

    @Test
    fun `items are sorted back to front and every open plot has soil and a plant`() {
        val garden =
            GardenState.INITIAL.copy(
                plots = plotsWith(3, GrowthStage.FLOWERS),
                specials = setOf(GardenSpecial.LANTERN, GardenSpecial.MUSHROOMS),
            )
        val items = sceneItems(garden, w, h)
        assertEquals(3, items.count { it is SceneItem.Soil })
        assertEquals(3, items.count { it is SceneItem.Plant })
        assertEquals(2, items.count { it is SceneItem.Special })
        assertEquals(items.map { it.depth }.sorted(), items.map { it.depth })
    }

    @Test
    fun `a closed plot and a missing special draw nothing`() {
        val items = sceneItems(GardenState.INITIAL, w, h)
        assertEquals(1, items.count { it is SceneItem.Plant })
        assertTrue(items.none { it is SceneItem.Special })
    }

    @Test
    fun `a tap hits the frontmost plant or special and never the soil`() {
        val garden = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.MUSHROOMS))
        val items = sceneItems(garden, w, h)
        val mushrooms = items.first { it is SceneItem.Special }
        val hit = hitTest(items, mushrooms.rect.left + mushrooms.rect.width / 2f, mushrooms.rect.top + mushrooms.rect.height / 2f)
        assertEquals(GardenSpecial.MUSHROOMS, (hit as SceneItem.Special).special)
        assertNull(hitTest(items, 1f, 1f))
    }

    @Test
    fun `the soil sinks whole below its spot and keeps its height`() {
        val spot = GardenArt.plotSpots[0]
        val soil = sceneItems(GardenState.INITIAL, w, h).single { it is SceneItem.Soil }.rect
        val unsunk = spotRect(spot, spot.heightPct * GardenArt.plots[0].heightRatio, GardenArt.plots[0].aspect, w, h)
        assertEquals(unsunk.height, soil.height, delta)
        assertEquals(unsunk.width, soil.width, delta)
        assertEquals(h * spot.yPct / 100f + GardenLookTable.SOIL_SINK * soil.height, soil.top + soil.height, delta)
    }

    @Test
    fun `the soil sits just behind its plant`() {
        val items = sceneItems(GardenState.INITIAL, w, h)
        assertTrue(items.indexOfFirst { it is SceneItem.Soil } < items.indexOfFirst { it is SceneItem.Plant })
    }

    @Test
    fun `tapping a plant sways that plot`() {
        val garden = GardenState.INITIAL.copy(plots = plotsWith(3, GrowthStage.TREE))
        val items = sceneItems(garden, w, h)
        val plant = items.filterIsInstance<SceneItem.Plant>().first { it.plot == 2 }.rect
        assertEquals(SceneTap.Sway(2), onSceneTap(items, plant.left + plant.width / 2f, plant.top + plant.height / 2f))
    }

    @Test
    fun `tapping a special opens its bubble and empty ground does nothing`() {
        val garden = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.MUSHROOMS))
        val items = sceneItems(garden, w, h)
        val mushrooms = items.filterIsInstance<SceneItem.Special>().single()
        val tap = onSceneTap(items, mushrooms.rect.left + mushrooms.rect.width / 2f, mushrooms.rect.top + mushrooms.rect.height / 2f)
        assertEquals(SceneTap.Bubble(mushrooms), tap)
        assertEquals(SceneTap.None, onSceneTap(items, 1f, 1f))
    }

    @Test
    fun `tapping only the soil does nothing`() {
        val items = sceneItems(GardenState.INITIAL, w, h)
        val soil = items.single { it is SceneItem.Soil }.rect
        // El borde de abajo de la tierra queda por debajo de la planta: ahí solo hay tierra.
        assertEquals(SceneTap.None, onSceneTap(items, soil.left + soil.width / 2f, soil.top + soil.height - 1f))
    }

    @Test
    fun `plotsWith opens trees before the last plot`() {
        val plots = plotsWith(3, GrowthStage.SPROUT)
        assertEquals(listOf(GrowthStage.TREE, GrowthStage.TREE, GrowthStage.SPROUT), plots.map { it.stage })
    }

    @Test
    fun `habi's body box is the body alone, feet on the spot`() {
        val r = habiBodyRect(200f, 100f, GardenSpot(50f, 90f, 40f), bodyWidth = 100f, bodyHeight = 100f)
        assertEquals(SceneRect(80f, 50f, 40f, 40f), r)
    }

    @Test
    fun `tapping the lantern next to habi opens its bubble, not habi`() {
        val garden = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.LANTERN))
        val items = sceneItems(garden, w, h)
        val lantern = items.filterIsInstance<SceneItem.Special>().single()
        val body = habiBodyRect(w, h)
        val x = lantern.rect.left + lantern.rect.width / 2f
        val y = lantern.rect.top + lantern.rect.height / 2f
        // El centro del farolillo cae dentro de la caja del lienzo de Habi, pero fuera de su cuerpo.
        assertTrue(habiRect(w, h).contains(x, y))
        assertEquals(SceneTap.Bubble(lantern), onSceneTap(items, x, y, body))
    }

    @Test
    fun `inside the body habi wins over what is behind, and what is in front wins over habi`() {
        val body = habiBodyRect(w, h)
        val cx = body.left + body.width / 2f
        val cy = body.top + body.height / 2f
        val over = SceneRect(cx - 10f, cy - 10f, 20f, 20f)
        val behind = SceneItem.Special(GardenSpecial.LANTERN, 0, over, GardenArt.habiSpot.yPct - 10f)
        val front = SceneItem.Special(GardenSpecial.MUSHROOMS, 0, over, GardenArt.habiSpot.yPct + 5f)
        assertEquals(SceneTap.Habi(cx, cy), onSceneTap(listOf(behind), cx, cy, body))
        assertEquals(SceneTap.Bubble(front), onSceneTap(listOf(behind, front), cx, cy, body))
        // Sin Habi que tocar, el toque pasa a lo de detrás.
        assertEquals(SceneTap.Bubble(behind), onSceneTap(listOf(behind), cx, cy, habiBody = null))
    }

    @Test
    fun `at night the lantern goes over the light, never by day nor the other specials`() {
        val night = GardenLight(LightPhase.NOCHE, LightPhase.NOCHE, LightPhase.NOCHE, 0f)
        val day = GardenLight(LightPhase.DIA, LightPhase.DIA, LightPhase.DIA, 0f)
        val garden = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.LANTERN, GardenSpecial.MUSHROOMS))
        val items = sceneItems(garden, w, h)
        val over = itemsOverLight(items, night)
        assertEquals(listOf(GardenSpecial.LANTERN), over.map { (it as SceneItem.Special).special })
        assertTrue(itemsOverLight(items, day).isEmpty())
        // Sin fundido, como el prototipo: cuenta la fase, no la transición.
        assertTrue(itemsOverLight(items, GardenLight(LightPhase.TARDE, LightPhase.TARDE, LightPhase.NOCHE, 0.9f)).isEmpty())
        assertEquals(1, itemsOverLight(items, GardenLight(LightPhase.NOCHE, LightPhase.TARDE, LightPhase.NOCHE, 0.1f)).size)
    }
}
