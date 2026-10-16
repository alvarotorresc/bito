package com.alvarotc.bito.ui.habla

import android.content.Context
import android.content.res.Configuration
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.Plantilla
import com.alvarotc.bito.domain.model.Personality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BolsasRecursosTest {
    private fun contexto(idioma: String): Context {
        val base = RuntimeEnvironment.getApplication() as Context
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(idioma))
        return base.createConfigurationContext(config)
    }

    private val contextEs = contexto("es")
    private val contextEn = contexto("en")

    @Test
    fun `every bag and voice has its array in both languages`() {
        Bolsa.entries.forEach { b ->
            Personality.entries.forEach { v ->
                val id = BolsasRecursos.array(b, v)
                assertTrue("${b.clave} ${v.name}", contextEn.resources.getStringArray(id).isNotEmpty())
                assertTrue("${b.clave} ${v.name}", contextEs.resources.getStringArray(id).isNotEmpty())
            }
        }
    }

    @Test
    fun `each array has the name of its bag and voice`() {
        Bolsa.entries.forEach { b ->
            Personality.entries.forEach { v ->
                val nombre = contextEs.resources.getResourceEntryName(BolsasRecursos.array(b, v))
                assertEquals("habla_${b.clave}_${v.name.lowercase()}", nombre)
            }
        }
    }

    @Test
    fun `the placeholder items only use gaps their bag admits and have valid tags`() {
        listOf(contextEs, contextEn).forEach { ctx ->
            Bolsa.entries.forEach { b ->
                Personality.entries.forEach { v ->
                    ctx.resources.getStringArray(BolsasRecursos.array(b, v)).forEach { item ->
                        val (_, texto) = Plantilla.separarEtiquetas(item)
                        val huecos = Plantilla.huecosDe(texto)
                        assertTrue("${b.clave} ${v.name}: $item", b.huecos.containsAll(huecos))
                    }
                }
            }
        }
    }

    @Test
    fun `the greeting seed keeps the old text with a named gap`() {
        val item = contextEs.resources.getStringArray(BolsasRecursos.array(Bolsa.NU_SALUDO, Personality.NEUTRA))[0]
        assertEquals("Hola, {nombre}.", item)
    }
}
