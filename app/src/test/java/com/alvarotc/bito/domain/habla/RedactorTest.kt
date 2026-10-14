package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RedactorTest {
    private val nucleos = (0 until 10).map { TrozoInfo(it, emptySet(), setOf(Hueco.HABITO)) }
    private val catalogo =
        catalogoFalso(
            Bolsa.NU_AYER_ESCAPO to nucleos,
            Bolsa.AP_MANANA to (0 until 5).map { TrozoInfo(it, emptySet(), emptySet()) },
        )
    private val cand =
        Candidata(Situacion.AYER_SE_ESCAPO, "h1", mapOf(Hueco.HABITO to ValorHueco.Texto("Leer")), listOf(Respuesta.VALE))

    private fun lisos(n: Int) = (0 until n).map { TrozoInfo(it, emptySet(), emptySet()) }

    private fun Redaccion.indice(bolsa: Bolsa): Int? = frase.trozos.singleOrNull { it.bolsa == bolsa }?.indice

    @Test
    fun `same seed same phrase`() {
        val a = Redactor.redactar(cand, ctx(), catalogo, Memoria(), Random(42))
        val b = Redactor.redactar(cand, ctx(), catalogo, Memoria(), Random(42))
        assertEquals(a.frase, b.frase)
    }

    @Test
    fun `a nucleus used in the last seven days is not chosen`() {
        val usados = (0 until 9).map { UsoTrozo(Personality.NEUTRA, Bolsa.NU_AYER_ESCAPO, it, "-|$it|-", TODAY - 1, 0L) }
        repeat(20) { semilla ->
            val r = Redactor.redactar(cand, ctx(), catalogo, Memoria(usos = usados), Random(semilla))
            assertEquals(9, r.indice(Bolsa.NU_AYER_ESCAPO))
        }
    }

    @Test
    fun `a nucleus used seven days ago can come back`() {
        val usados = (0 until 9).map { UsoTrozo(Personality.NEUTRA, Bolsa.NU_AYER_ESCAPO, it, "-|$it|-", TODAY - 7, 0L) }
        val vistos = (0 until 200).map { Redactor.redactar(cand, ctx(), catalogo, Memoria(usos = usados), Random(it)) }
        assertTrue(vistos.any { it.indice(Bolsa.NU_AYER_ESCAPO) != 9 })
    }

    @Test
    fun `another voice's uses do not count`() {
        val usados = (0 until 9).map { UsoTrozo(Personality.SARGENTO, Bolsa.NU_AYER_ESCAPO, it, "-|$it|-", TODAY - 1, 0L) }
        val vistos = (0 until 200).map { Redactor.redactar(cand, ctx(), catalogo, Memoria(usos = usados), Random(it)) }
        assertTrue(vistos.any { it.indice(Bolsa.NU_AYER_ESCAPO) != 9 })
    }

    @Test
    fun `when all were used the oldest one comes back`() {
        // El índice 6 es el de usadoMillis menor: el que se usó hace más tiempo.
        val usados =
            (0 until 10).map {
                val millis = if (it == 6) 500L else (it + 1) * 1000L
                UsoTrozo(Personality.NEUTRA, Bolsa.NU_AYER_ESCAPO, it, "-|$it|-", TODAY - 1, millis)
            }
        repeat(20) { semilla ->
            val r = Redactor.redactar(cand, ctx(), catalogo, Memoria(usos = usados), Random(semilla))
            assertEquals(6, r.indice(Bolsa.NU_AYER_ESCAPO))
        }
    }

    @Test
    fun `a piece whose gaps are not in the candidate is never chosen`() {
        val conHora =
            (0 until 6).map {
                TrozoInfo(
                    it,
                    emptySet(),
                    if (it % 2 == 0) setOf(Hueco.HABITO, Hueco.HORA) else setOf(Hueco.HABITO),
                )
            }
        val cat = catalogoFalso(Bolsa.NU_AYER_ESCAPO to conHora)
        repeat(100) { semilla ->
            val r = Redactor.redactar(cand, ctx(), cat, Memoria(), Random(semilla))
            assertEquals(1, r.indice(Bolsa.NU_AYER_ESCAPO)!! % 2)
        }
    }

    @Test
    fun `tags that do not hold filter and tags that hold weigh double`() {
        val dos = listOf(TrozoInfo(0, setOf(Etiqueta.LUNES), setOf(Hueco.HABITO)), TrozoInfo(1, emptySet(), setOf(Hueco.HABITO)))
        val cat = catalogoFalso(Bolsa.NU_AYER_ESCAPO to dos)
        // TODAY es viernes: [lunes] no se cumple.
        repeat(100) { semilla ->
            assertEquals(1, Redactor.redactar(cand, ctx(), cat, Memoria(), Random(semilla)).indice(Bolsa.NU_AYER_ESCAPO))
        }
        val lunes = ctx(hoy = TODAY + 3)
        val conEtiqueta =
            (0 until 1000).count { Redactor.redactar(cand, lunes, cat, Memoria(), Random(it)).indice(Bolsa.NU_AYER_ESCAPO) == 0 }
        val ratio = conEtiqueta.toDouble() / (1000 - conEtiqueta)
        assertTrue("ratio $ratio", ratio in 1.7..2.3)
    }

    @Test
    fun `the name appears at most once and never without a name`() {
        val conNombre =
            (0 until 4).map {
                TrozoInfo(
                    it,
                    emptySet(),
                    if (it < 2) setOf(Hueco.NOMBRE, Hueco.HABITO) else setOf(Hueco.HABITO),
                )
            }
        val aperturas = (0 until 4).map { TrozoInfo(it, emptySet(), if (it < 2) setOf(Hueco.NOMBRE) else emptySet()) }
        val cat = catalogoFalso(Bolsa.NU_AYER_ESCAPO to conNombre, Bolsa.AP_MANANA to aperturas)
        var vecesNombre = 0
        repeat(300) { semilla ->
            val r = Redactor.redactar(cand, ctx(), cat, Memoria(), Random(semilla), forzarApertura = true)
            val ap = r.indice(Bolsa.AP_MANANA)!!
            val nu = r.indice(Bolsa.NU_AYER_ESCAPO)!!
            assertFalse("apertura $ap y núcleo $nu", ap < 2 && nu < 2)
            if (ap < 2 || nu < 2) vecesNombre++
            assertEquals(ValorHueco.Texto("Álvaro"), r.frase.huecos[Hueco.NOMBRE])
        }
        assertTrue(vecesNombre > 0)
        repeat(300) { semilla ->
            val r = Redactor.redactar(cand, ctx(nombre = null), cat, Memoria(), Random(semilla), forzarApertura = true)
            assertTrue(r.indice(Bolsa.AP_MANANA)!! >= 2)
            assertTrue(r.indice(Bolsa.NU_AYER_ESCAPO)!! >= 2)
            assertNull(r.frase.huecos[Hueco.NOMBRE])
        }
    }

    @Test
    fun `openings and closings are not repeated in their last five uses`() {
        val cat =
            catalogoFalso(
                Bolsa.NU_AYER_ESCAPO to nucleos,
                Bolsa.AP_MANANA to lisos(6),
                Bolsa.CI_EMPUJE to lisos(6),
            )
        val usados =
            (0 until 5).flatMap {
                listOf(
                    UsoTrozo(Personality.NEUTRA, Bolsa.AP_MANANA, it, "$it|0|$it", TODAY - 40, it * 1000L),
                    UsoTrozo(Personality.NEUTRA, Bolsa.CI_EMPUJE, it, "$it|0|$it", TODAY - 40, it * 1000L),
                )
            }
        var cierres = 0
        repeat(50) { semilla ->
            val r = Redactor.redactar(cand, ctx(), cat, Memoria(usos = usados), Random(semilla), forzarApertura = true)
            assertEquals(5, r.indice(Bolsa.AP_MANANA))
            r.indice(Bolsa.CI_EMPUJE)?.let {
                assertEquals(5, it)
                cierres++
            }
        }
        assertTrue(cierres > 0)
    }

    @Test
    fun `the whole phrase is not repeated in thirty days`() {
        val cat =
            catalogoFalso(
                Bolsa.NU_AYER_ESCAPO to listOf(TrozoInfo(0, emptySet(), setOf(Hueco.HABITO))),
                Bolsa.AP_MANANA to lisos(1),
                Bolsa.CI_EMPUJE to lisos(2),
            )
        val usados = listOf(UsoTrozo(Personality.NEUTRA, Bolsa.NU_AYER_ESCAPO, 0, "0|0|0", TODAY - 29, 0L, Situacion.AYER_SE_ESCAPO))
        var cierres = 0
        repeat(50) { semilla ->
            val r = Redactor.redactar(cand, ctx(), cat, Memoria(usos = usados), Random(semilla), forzarApertura = true)
            r.indice(Bolsa.CI_EMPUJE)?.let {
                assertEquals(1, it)
                cierres++
            }
        }
        assertTrue(cierres > 0)
    }

    @Test
    fun `uses carry the combination and the nucleus use carries the situation`() {
        val cat = catalogoFalso(Bolsa.NU_AYER_ESCAPO to nucleos, Bolsa.AP_MANANA to lisos(5), Bolsa.CI_EMPUJE to lisos(5))
        val r =
            (0 until 50)
                .map { Redactor.redactar(cand, ctx(), cat, Memoria(), Random(it), forzarApertura = true) }
                .first { it.frase.trozos.size == 3 }
        val (ap, nu, ci) = r.frase.trozos.map { it.indice }
        val combinacion = "$ap|$nu|$ci"
        assertEquals(listOf(Bolsa.AP_MANANA, Bolsa.NU_AYER_ESCAPO, Bolsa.CI_EMPUJE), r.usos.map { it.bolsa })
        assertTrue(r.usos.all { it.combinacion == combinacion && it.dia == TODAY && it.voz == Personality.NEUTRA })
        assertEquals(listOf(null, Situacion.AYER_SE_ESCAPO, null), r.usos.map { it.situacion })
    }

    @Test
    fun `the nucleus use carries the situation for the daily cap`() {
        val r = Redactor.redactar(cand, ctx(), catalogo, Memoria(), Random(3))
        assertEquals(Situacion.AYER_SE_ESCAPO, r.usos.single { it.bolsa == Bolsa.NU_AYER_ESCAPO }.situacion)
    }

    @Test
    fun `the opening follows the time of day and appears about half the time`() {
        val cat = catalogoFalso(Bolsa.NU_AYER_ESCAPO to nucleos, Bolsa.AP_MANANA to lisos(5), Bolsa.AP_NOCHE to lisos(5))
        val conApertura =
            (0 until 1000).count { Redactor.redactar(cand, ctx(), cat, Memoria(), Random(it)).indice(Bolsa.AP_MANANA) != null }
        assertTrue("$conApertura", conApertura in 430..570)
        val madrugada = Redactor.redactar(cand, ctx(ahoraMin = 3 * 60), cat, Memoria(), Random(1), forzarApertura = true)
        assertTrue(madrugada.indice(Bolsa.AP_MANANA) != null)
        val noche = Redactor.redactar(cand, ctx(ahoraMin = 22 * 60), cat, Memoria(), Random(1), forzarApertura = true)
        assertEquals(listOf(Bolsa.AP_NOCHE, Bolsa.NU_AYER_ESCAPO), noche.frase.trozos.map { it.bolsa }.take(2))
    }

    @Test
    fun `no opening outside Hoy nor before a greeting`() {
        val cat =
            catalogoFalso(
                Bolsa.NU_SALUDO to lisos(5),
                Bolsa.FI_CONSTANCIA to lisos(5),
                Bolsa.AP_MANANA to lisos(5),
            )
        val saludo = Candidata(Situacion.SALUDO, null, emptyMap(), emptyList())
        val ficha = Candidata(Situacion.FI_CONSTANCIA, "h1", emptyMap(), emptyList())
        repeat(100) { semilla ->
            assertNull(Redactor.redactar(saludo, ctx(), cat, Memoria(), Random(semilla)).indice(Bolsa.AP_MANANA))
            assertNull(Redactor.redactar(ficha, ctx(), cat, Memoria(), Random(semilla)).indice(Bolsa.AP_MANANA))
        }
        val conNucleo = catalogoFalso(Bolsa.NU_AYER_ESCAPO to nucleos, Bolsa.AP_MANANA to lisos(5))
        assertTrue((0 until 100).any { Redactor.redactar(cand, ctx(), conNucleo, Memoria(), Random(it)).indice(Bolsa.AP_MANANA) != null })
    }

    @Test
    fun `the closing is a push with buttons and calm without them, about forty percent`() {
        val cat =
            catalogoFalso(
                Bolsa.NU_AYER_ESCAPO to nucleos,
                Bolsa.NU_TRAS_HOY_NO to lisos(5),
                Bolsa.CI_EMPUJE to lisos(5),
                Bolsa.CI_CALMA to lisos(5),
            )
        val conCierre = (0 until 1000).map { Redactor.redactar(cand, ctx(), cat, Memoria(), Random(it)) }
        assertTrue(conCierre.none { it.indice(Bolsa.CI_CALMA) != null })
        val empuje = conCierre.count { it.indice(Bolsa.CI_EMPUJE) != null }
        assertTrue("$empuje", empuje in 330..470)
        val trasHoyNo = Candidata(Situacion.TRAS_HOY_NO, null, emptyMap(), emptyList())
        val calma = (0 until 200).map { Redactor.redactar(trasHoyNo, ctx(), cat, Memoria(), Random(it)) }
        assertTrue(calma.none { it.indice(Bolsa.CI_EMPUJE) != null })
        assertTrue(calma.any { it.indice(Bolsa.CI_CALMA) != null })
    }

    @Test
    fun `a memory replaces the closing and is mentioned`() {
        val cat =
            catalogoFalso(
                Bolsa.NU_AYER_ESCAPO to nucleos,
                Bolsa.CI_EMPUJE to lisos(5),
                Bolsa.RE_BIEN_AYER to lisos(5),
            )
        val recuerdo = Recuerdo(Bolsa.RE_BIEN_AYER, "evento-7", emptyMap())
        repeat(50) { semilla ->
            val r = Redactor.redactar(cand, ctx(), cat, Memoria(), Random(semilla), recuerdo = recuerdo)
            assertEquals(Bolsa.RE_BIEN_AYER, r.frase.trozos.last().bolsa)
            assertNull(r.indice(Bolsa.CI_EMPUJE))
            assertEquals("evento-7", r.mencion)
            assertTrue(r.usos.any { it.bolsa == Bolsa.RE_BIEN_AYER })
        }
    }

    @Test
    fun `a memory is not said where it does not belong`() {
        val cat =
            catalogoFalso(
                Bolsa.NU_RACHA_CERCA to lisos(5),
                Bolsa.NU_SALUDO to lisos(5),
                Bolsa.CIE_SELLO to lisos(5),
                Bolsa.RE_BIEN_AYER to lisos(5),
            )
        val racha = Candidata(Situacion.RACHA_CERCA, "h1", emptyMap(), listOf(Respuesta.AHORA))
        val recuerdo = Recuerdo(Bolsa.RE_BIEN_AYER, "evento-7", emptyMap())
        repeat(50) { semilla ->
            val r = Redactor.redactar(racha, ctx(), cat, Memoria(), Random(semilla), recuerdo = recuerdo)
            assertNull(r.indice(Bolsa.RE_BIEN_AYER))
            assertNull(r.mencion)
        }
        val saludo = Candidata(Situacion.SALUDO, null, emptyMap(), emptyList())
        assertEquals("evento-7", Redactor.redactar(saludo, ctx(), cat, Memoria(), Random(1), recuerdo = recuerdo).mencion)
        // El sello no es una situación: es el núcleo CIE_SELLO de una candidata del cierre.
        val sello = Candidata(Situacion.CIE_SENCILLO, null, emptyMap(), emptyList(), nucleo = Bolsa.CIE_SELLO)
        assertEquals("evento-7", Redactor.redactar(sello, ctx(), cat, Memoria(), Random(1), recuerdo = recuerdo).mencion)
    }

    @Test
    fun `a memory without any eligible piece is not mentioned and the closing comes back`() {
        val cat = catalogoFalso(Bolsa.NU_AYER_ESCAPO to nucleos, Bolsa.CI_EMPUJE to lisos(5))
        val recuerdo = Recuerdo(Bolsa.RE_BIEN_AYER, "evento-7", emptyMap())
        val rs = (0 until 100).map { Redactor.redactar(cand, ctx(), cat, Memoria(), Random(it), recuerdo = recuerdo) }
        assertTrue(rs.all { it.mencion == null })
        assertTrue(rs.any { it.indice(Bolsa.CI_EMPUJE) != null })
    }

    @Test
    fun `a memory whose gap clashes with the candidate is not said`() {
        val cat =
            catalogoFalso(
                Bolsa.NU_AYER_ESCAPO to nucleos,
                Bolsa.RE_CUMPLISTE_HORA to listOf(TrozoInfo(0, emptySet(), setOf(Hueco.HABITO, Hueco.HORA))),
            )
        val huecos = mapOf(Hueco.HABITO to ValorHueco.Texto("Correr"), Hueco.HORA to ValorHueco.Hora(18 * 60))
        val r = Redactor.redactar(cand, ctx(), cat, Memoria(), Random(1), recuerdo = Recuerdo(Bolsa.RE_CUMPLISTE_HORA, "e1", huecos))
        assertNull(r.mencion)
        assertEquals(ValorHueco.Texto("Leer"), r.frase.huecos[Hueco.HABITO])
    }

    @Test
    fun `a memory brings its own gaps into the phrase`() {
        val cat =
            catalogoFalso(
                Bolsa.NU_SALUDO to lisos(3),
                Bolsa.RE_CUMPLISTE_HORA to listOf(TrozoInfo(0, emptySet(), setOf(Hueco.HABITO, Hueco.HORA))),
            )
        val saludo = Candidata(Situacion.SALUDO, null, emptyMap(), emptyList())
        val huecos = mapOf(Hueco.HABITO to ValorHueco.Texto("Correr"), Hueco.HORA to ValorHueco.Hora(18 * 60))
        val r = Redactor.redactar(saludo, ctx(), cat, Memoria(), Random(1), recuerdo = Recuerdo(Bolsa.RE_CUMPLISTE_HORA, "e1", huecos))
        assertEquals(0, r.indice(Bolsa.RE_CUMPLISTE_HORA))
        assertEquals(ValorHueco.Texto("Correr"), r.frase.huecos[Hueco.HABITO])
        assertEquals(ValorHueco.Hora(18 * 60), r.frase.huecos[Hueco.HORA])
    }

    @Test
    fun `without any eligible piece the nucleus falls back to index zero alone`() {
        val r = Redactor.redactar(cand, ctx(), CatalogoHablaVacio, Memoria(), Random(1), forzarApertura = true)
        assertEquals(listOf(ClaveTrozo(Bolsa.NU_AYER_ESCAPO, Personality.NEUTRA, 0)), r.frase.trozos)
        assertTrue(r.usos.isEmpty())
        assertNull(r.mencion)
    }

    @Test
    fun `the seed depends on every input and only on them`() {
        val base = Redactor.semilla(TODAY, Situacion.SALUDO, "h1", Personality.NEUTRA, 0)
        assertEquals(base, Redactor.semilla(TODAY, Situacion.SALUDO, "h1", Personality.NEUTRA, 0))
        assertNotEquals(base, Redactor.semilla(TODAY + 1, Situacion.SALUDO, "h1", Personality.NEUTRA, 0))
        assertNotEquals(base, Redactor.semilla(TODAY, Situacion.VUELTA, "h1", Personality.NEUTRA, 0))
        assertNotEquals(base, Redactor.semilla(TODAY, Situacion.SALUDO, "h2", Personality.NEUTRA, 0))
        assertNotEquals(base, Redactor.semilla(TODAY, Situacion.SALUDO, "h1", Personality.SARGENTO, 0))
        assertNotEquals(base, Redactor.semilla(TODAY, Situacion.SALUDO, "h1", Personality.NEUTRA, 1))
    }
}
