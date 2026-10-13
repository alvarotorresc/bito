package com.alvarotc.bito.ui.habi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabiArtPrecargaTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `precargar fills the cache off the caller and enCache sees it`() {
        val art = HabiArt(context.resources)
        val piezas = recursosDe(habiLayers(HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet())), sample = 2)
        assertTrue(piezas.isNotEmpty())
        assertFalse(piezas.all { (res, s) -> art.enCache(res, s) })
        runBlocking { art.precargar(piezas) }
        assertTrue(piezas.all { (res, s) -> art.enCache(res, s) })
    }

    @Test
    fun `recursosDe skips keys the atlas does not have`() {
        assertTrue(recursosDe(listOf(HabiLayer("no-existe", HabiPart.HEAD)), sample = 1).isEmpty())
    }
}
