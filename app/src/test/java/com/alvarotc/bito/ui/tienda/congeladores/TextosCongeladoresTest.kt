package com.alvarotc.bito.ui.tienda.congeladores

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alvarotc.bito.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TextosCongeladoresTest {
    @Test
    fun `every freezer info text says a missed day and never a red one`() {
        val ids =
            listOf(
                R.string.freezer_info_neutra,
                R.string.freezer_info_neutra_sin_nombre,
                R.string.freezer_info_sargento,
                R.string.freezer_info_sargento_sin_nombre,
                R.string.freezer_info_cheerleader,
                R.string.freezer_info_cheerleader_sin_nombre,
            )
        for (id in ids) {
            val es = recursos("es").getString(id, "Ana")
            val en = recursos("en").getString(id, "Ana")
            assertTrue(es, es.contains("sin hacer") && !es.contains("en rojo"))
            assertTrue(en, en.contains("missed day") && !en.contains("red day"))
        }
    }
}
