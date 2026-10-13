package com.alvarotc.bito.ui.sistema

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.createComposeRule
import com.alvarotc.bito.ui.theme.BitoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TemaTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `BitoTheme maps Material to the new palette and to Gabarito`() {
        lateinit var esquema: ColorScheme
        lateinit var tipos: Typography
        rule.setContent {
            BitoTheme {
                esquema = MaterialTheme.colorScheme
                tipos = MaterialTheme.typography
            }
        }
        rule.waitForIdle()
        assertEquals(BitoClaro.agua, esquema.primary)
        assertEquals(BitoClaro.tAlta, esquema.background)
        assertEquals(BitoClaro.canto, esquema.surface)
        assertEquals(BitoClaro.peligro, esquema.error)
        assertEquals(BitoClaro.tinta, esquema.onSurface)
        assertEquals(Gabarito, tipos.bodyLarge.fontFamily)
    }
}
