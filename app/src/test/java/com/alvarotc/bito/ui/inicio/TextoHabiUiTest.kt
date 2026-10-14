package com.alvarotc.bito.ui.inicio

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.propuestas.TextoHabi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es")
class TextoHabiUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `greeting shows the resolved fallback name, not the placeholder`() {
        val context = RuntimeEnvironment.getApplication() as Context
        val saludo = R.string.habi_greeting_neutra_normal
        val esperado = context.getString(saludo, context.getString(R.string.habi_name_fallback))
        assertTrue(!esperado.contains("%1\$s"))

        compose.setContent {
            Text(textoHabi(TextoHabi(saludo, listOf(TextoHabi(R.string.habi_name_fallback)))))
        }

        compose.onNodeWithText(esperado).assertExists()
    }
}
