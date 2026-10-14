package com.alvarotc.bito.ui.inicio

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.TextoHabi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

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

    @Test
    fun `a phrase without catalogue renders empty and does not crash`() {
        compose.setContent { Text(textoHabi(TextoHabi.Frase(emptyList(), emptyMap())), Modifier.testTag("t")) }
        compose.onNodeWithTag("t").assertTextEquals("")
    }

    private fun frase(
        vararg trozos: ClaveTrozo,
        huecos: Map<Hueco, ValorHueco> = emptyMap(),
    ) = TextoHabi.Frase(trozos.toList(), huecos)

    private fun clave(
        bolsa: Bolsa,
        indice: Int,
        voz: Personality = Personality.NEUTRA,
    ) = ClaveTrozo(bolsa, voz, indice)

    private fun pinta(t: TextoHabi) {
        compose.setContent { Text(textoHabi(t), Modifier.testTag("t")) }
    }

    @Test
    fun `a phrase resolves pieces, strips tags and fills gaps`() {
        pinta(frase(clave(Bolsa.NU_SALUDO, 0), huecos = mapOf(Hueco.NOMBRE to ValorHueco.Texto("Álvaro"))))
        compose.onNodeWithTag("t").assertTextEquals("Hola, Álvaro.")
    }

    @Test
    fun `pieces are joined with one space in order`() {
        pinta(
            frase(
                clave(Bolsa.AP_MANANA, 0),
                clave(Bolsa.NU_SALUDO, 0),
                huecos = mapOf(Hueco.NOMBRE to ValorHueco.Texto("Álvaro")),
            ),
        )
        compose.onNodeWithTag("t").assertTextEquals("Buenos días. Hola, Álvaro.")
    }

    @Test
    fun `an index past the bag renders the nucleus fallback without crashing`() {
        pinta(frase(clave(Bolsa.NU_SALUDO, 999), huecos = mapOf(Hueco.NOMBRE to ValorHueco.Texto("Álvaro"))))
        compose.onNodeWithTag("t").assertTextEquals("Hola, Álvaro.")
    }

    @Test
    fun `an index past the bag leaves a non nucleus piece empty`() {
        pinta(
            frase(
                clave(Bolsa.AP_MANANA, 999),
                clave(Bolsa.NU_SALUDO, 0),
                huecos = mapOf(Hueco.NOMBRE to ValorHueco.Texto("Álvaro")),
            ),
        )
        compose.onNodeWithTag("t").assertTextEquals("Hola, Álvaro.")
    }

    @Test
    fun `a fallback piece missing a gap is skipped without crashing`() {
        // El item 0 de nu_ayer_escapo pide {habito}, que esta frase no trae.
        pinta(frase(clave(Bolsa.AP_MANANA, 0), clave(Bolsa.NU_AYER_ESCAPO, 999)))
        compose.onNodeWithTag("t").assertTextEquals("Buenos días.")
    }

    @Test
    fun `a Hora argument renders as 14 30`() {
        pinta(TextoHabi.Recurso(R.string.habla_vale_a_las, listOf(ValorHueco.Hora(870))))
        compose.onNodeWithTag("t").assertTextEquals("Vale, a las 14:30")
    }

    @Test
    fun `a Texto argument renders its text`() {
        pinta(TextoHabi.Recurso(R.string.habla_vale_habito, listOf(ValorHueco.Texto("Leer"))))
        compose.onNodeWithTag("t").assertTextEquals("Vale, Leer")
    }

    private fun iguales(t: TextoHabi) {
        val resources = (RuntimeEnvironment.getApplication() as Context).resources
        val esperado = resolverTextoHabi(t, resources)
        assertTrue(esperado.isNotEmpty())
        pinta(t)
        compose.onNodeWithTag("t").assertTextEquals(esperado)
    }

    @Test
    fun `the resolver outside Compose gives the same text for a phrase`() =
        iguales(
            frase(
                clave(Bolsa.AP_MANANA, 0),
                clave(Bolsa.NU_SALUDO, 999),
                huecos = mapOf(Hueco.NOMBRE to ValorHueco.Texto("Álvaro")),
            ),
        )

    @Test
    fun `the resolver outside Compose gives the same text for a resource`() =
        iguales(TextoHabi.Recurso(R.string.habla_vale_a_las, listOf(ValorHueco.Hora(870))))

    @Test
    fun `the resolver reads the language from the resources it is given`() {
        val base = RuntimeEnvironment.getApplication() as Context
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(java.util.Locale.ENGLISH)
        val en = base.createConfigurationContext(config).resources
        val t = frase(clave(Bolsa.NU_SALUDO, 0), huecos = mapOf(Hueco.NOMBRE to ValorHueco.Texto("Álvaro")))
        assertEquals("Hi, Álvaro.", resolverTextoHabi(t, en))
    }
}
