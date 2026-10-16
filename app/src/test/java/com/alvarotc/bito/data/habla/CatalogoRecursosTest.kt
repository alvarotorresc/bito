package com.alvarotc.bito.data.habla

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.Plantilla
import com.alvarotc.bito.domain.habla.TrozoInfo
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habla.BolsasRecursos
import kotlinx.coroutines.runBlocking
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
class CatalogoRecursosTest {
    private fun recursos(idioma: String): Resources {
        val base = RuntimeEnvironment.getApplication() as Context
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(idioma))
        return base.createConfigurationContext(config).resources
    }

    @Test
    fun `a bag has as many pieces as its array`() {
        val res = recursos("es")
        val catalogo = CatalogoRecursos { res }
        val array = res.getStringArray(BolsasRecursos.array(Bolsa.NU_SALUDO, Personality.NEUTRA))
        val trozos = catalogo.trozos(Bolsa.NU_SALUDO, Personality.NEUTRA)
        assertTrue(array.isNotEmpty())
        assertEquals(array.size, trozos.size)
        assertEquals(array.indices.toList(), trozos.map { it.indice })
    }

    @Test
    fun `tags and gaps of the first item match what the template reads`() {
        val res = recursos("es")
        val catalogo = CatalogoRecursos { res }
        Bolsa.entries.forEach { b ->
            Personality.entries.forEach { v ->
                val item = res.getStringArray(BolsasRecursos.array(b, v))[0]
                val (etiquetas, texto) = Plantilla.separarEtiquetas(item)
                val primero = catalogo.trozos(b, v)[0]
                assertEquals("${b.clave} ${v.name}", TrozoInfo(0, etiquetas, Plantilla.huecosDe(texto)), primero)
            }
        }
        // El provisional del saludo lleva un hueco, así que la lectura no devuelve huecos vacíos por defecto.
        assertTrue(catalogo.trozos(Bolsa.NU_SALUDO, Personality.NEUTRA)[0].huecos.isNotEmpty())
    }

    @Test
    fun `changing language keeps indices, tags and gaps`() {
        val es = CatalogoRecursos { recursos("es") }
        val en = CatalogoRecursos { recursos("en") }
        Bolsa.entries.forEach { b ->
            Personality.entries.forEach { v ->
                assertEquals("${b.clave} ${v.name}", es.trozos(b, v), en.trozos(b, v))
            }
        }
    }

    @Test
    fun `the catalog is recomputed when the language changes`() {
        var actual = recursos("es")
        val catalogo = CatalogoRecursos { actual }
        val enEs = catalogo.trozos(Bolsa.NU_SALUDO, Personality.NEUTRA)
        actual = recursos("en")
        val enEn = catalogo.trozos(Bolsa.NU_SALUDO, Personality.NEUTRA)
        assertEquals(enEs, enEn)
        assertTrue(catalogo.idiomasCargados().containsAll(listOf("es", "en")))
    }

    @Test
    fun `preloading does not throw and leaves every bag ready`() {
        val res = recursos("es")
        val catalogo = CatalogoRecursos { res }
        runBlocking { catalogo.precargar() }
        assertEquals(setOf("es"), catalogo.idiomasCargados())
        Bolsa.entries.forEach { b ->
            Personality.entries.forEach { v -> assertTrue(catalogo.trozos(b, v).isNotEmpty()) }
        }
    }
}
