package com.alvarotc.bito.ui.habla

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Las bolsas de Habi tal y como quedan en los recursos: mismas reglas que
 * `tools/habla/comprobar.py`, leídas de `tools/habla/bolsas.json` y de los XML.
 * JVM puro, sin Robolectric.
 */
class TextosHablaTest {
    private class Bolsa(
        val clave: String,
        val archivo: String,
        val tipo: String,
        val huecos: Set<String>,
        val minimo: Int,
        val nombre: Boolean,
    )

    private val voces = listOf("sargento", "cheerleader", "neutra")

    private val etiquetasValidas =
        setOf(
            "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo",
            "finde", "primavera", "verano", "otono", "invierno",
            "madrugada", "manana", "mediodia", "tarde", "noche",
            "despierta", "esperando", "durmiendo",
        )

    private val titulos = setOf("av_manana_titulo", "av_manana_titulo_gordo", "av_noche_titulo", "av_aceptado_titulo")
    private val cuerpos = setOf("av_recordatorio", "av_noche_cuerpo", "av_aceptado_cuerpo", "av_ausencia")
    private val libres = setOf("habito", "tarea")

    private val reHueco = Regex("\\{([^{}]*)\\}")
    private val reEtiquetas = Regex("^\\s*\\[([^\\]]*)\\]\\s*")
    private val reNombreHueco = Regex("[a-z_]+")
    private val reEmoji =
        Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B50}\\x{2B55}\\x{FE0F}\\x{200D}\\x{2190}-\\x{21FF}\\x{2300}-\\x{23FF}]")
    private val reIdentificadores = Regex("\\b(review|stats|CHECK|COUNTER|DURATION|ABSTINENCE|habitId|null)\\b")
    private val reCampeon = Regex("campe[oó]n\\w*|champ\\w*", RegexOption.IGNORE_CASE)
    private val reGeneroEs =
        Regex(
            "\\b(estoy|me siento|me quedo|soy|ando|estaba|quedo)\\s+" +
                "(content|cansad|dormid|list|orgullos|preocupad|agotad|emocionad|ilusionad|satisfech|tranquil|nervios)[oa]s?\\b",
            RegexOption.IGNORE_CASE,
        )
    private val rePronombreEs = Regex("\\b(él|ella)\\b", RegexOption.IGNORE_CASE)
    private val reGeneroEn = Regex("\\b(he|she|him|her|his|hers)\\b", RegexOption.IGNORE_CASE)
    private val rePorcientoSuelto = Regex("(?<!\\} )%")

    private val bolsas: List<Bolsa> by lazy {
        val json = File("../tools/habla/bolsas.json").readText()
        (Json.parseToJsonElement(json) as JsonArray).map {
            val o = it.jsonObject
            Bolsa(
                clave = o.getValue("bolsa").jsonPrimitive.content,
                archivo = o.getValue("archivo").jsonPrimitive.content,
                tipo = o.getValue("tipo").jsonPrimitive.content,
                huecos = (o.getValue("huecos") as JsonArray).map { h -> h.jsonPrimitive.content }.toSet(),
                minimo = o.getValue("minimo").jsonPrimitive.int,
                nombre = o.getValue("nombre").jsonPrimitive.boolean,
            )
        }
    }

    /** `archivo` -> (`nombre del array` -> items sin escapar de Android). */
    private fun leer(
        carpeta: String,
        archivo: String,
    ): Map<String, List<String>> {
        val doc =
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/$carpeta/strings_habla_$archivo.xml"))
        val arrays = doc.getElementsByTagName("string-array")
        return (0 until arrays.length).associate { i ->
            val a = arrays.item(i) as org.w3c.dom.Element
            val items = a.getElementsByTagName("item")
            a.getAttribute("name") to (0 until items.length).map { j -> desescapar(items.item(j).textContent) }
        }
    }

    private fun desescapar(t: String): String = Regex("\\\\(.)").replace(t) { it.groupValues[1] }

    private fun items(idioma: String): Map<String, List<String>> {
        val carpeta = if (idioma == "es") "values-es" else "values"
        return bolsas.map { it.archivo }.distinct().flatMap { leer(carpeta, it).entries }.associate { it.key to it.value }
    }

    private fun etiquetasDe(item: String): List<String> =
        reEtiquetas.find(item)?.groupValues?.get(1)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun sinEtiquetas(item: String): String = reEtiquetas.replaceFirst(item, "")

    private fun nombresDe(texto: String): List<String> = reHueco.findAll(texto).map { it.groupValues[1].split('|')[0] }.toList()

    private fun limite(b: Bolsa): Int =
        when {
            b.clave in titulos -> 50
            b.clave in cuerpos -> 120
            b.tipo == "NUCLEO" -> 100
            else -> 150
        }

    private fun libreBienPuesto(
        texto: String,
        idioma: String,
    ): List<String> {
        val (ab, ce) = if (idioma == "es") "«" to "»" else "“" to "”"
        return reHueco.findAll(texto).filter { m ->
            val nombre = m.groupValues[1].split('|')[0]
            if (nombre !in libres) return@filter false
            val ant = texto.substring(0, m.range.first)
            val post = texto.substring(m.range.last + 1)
            !(ant.isEmpty() || ant.endsWith(": ") || (ant.endsWith(ab) && post.startsWith(ce)))
        }.map { it.value }.toList()
    }

    private fun comprobarTexto(
        b: Bolsa,
        voz: String,
        idioma: String,
        texto: String,
        donde: String,
        fallos: MutableList<String>,
    ) {
        fun fallo(m: String) = fallos.add("$donde [$idioma] $m: $texto")
        val resto = reHueco.replace(texto, "")
        if ('{' in resto || '}' in resto) fallo("llaves sin equilibrar")
        val nombres = mutableListOf<String>()
        reHueco.findAll(texto).forEach { m ->
            val partes = m.groupValues[1].split('|')
            if (!reNombreHueco.matches(partes[0])) {
                fallo("hueco mal formado {${m.groupValues[1]}}")
            } else {
                nombres.add(partes[0])
            }
            if (partes.size != 1 && partes.size != 3) fallo("plural mal formado {${m.groupValues[1]}}")
        }
        nombres.distinct().filter { it !in b.huecos }.forEach { fallo("hueco no permitido {$it}") }
        if (nombres.count { it == "nombre" } > 1) fallo("{nombre} más de una vez")
        libreBienPuesto(texto, idioma).forEach { fallo("hueco libre mal puesto $it") }
        if (texto.trimStart().startsWith("[")) fallo("etiquetas dentro del texto")
        if (reEmoji.containsMatchIn(texto)) fallo("emoji")
        if ("—" in texto) fallo("raya larga")
        if (rePorcientoSuelto.containsMatchIn(texto)) fallo("% suelto")
        if (reIdentificadores.containsMatchIn(texto)) fallo("identificador")
        if (reCampeon.containsMatchIn(texto)) fallo("campeón")
        val genero =
            if (idioma == "es") {
                reGeneroEs.containsMatchIn(texto) || rePronombreEs.containsMatchIn(texto)
            } else {
                reGeneroEn.containsMatchIn(
                    texto,
                )
            }
        if (genero) fallo("género")
        if (voz == "neutra" && "!" in texto) fallo("la Neutra lleva «!»")
        if (voz == "cheerleader") {
            if (texto.count { it == '!' } > 1) fallo("más de un «!»")
            if (idioma == "es" && texto.count { it == '¡' } != texto.count { it == '!' }) fallo("«¡» sin cerrar")
        }
        val largo = reHueco.replace(texto, "x".repeat(20)).length
        if (largo > limite(b)) fallo("longitud $largo > ${limite(b)}")
    }

    @Test
    fun `every bag and voice has its minimum of items in both languages`() {
        val es = items("es")
        val en = items("en")
        val fallos = mutableListOf<String>()
        bolsas.forEach { b ->
            voces.forEach { v ->
                val nombre = "habla_${b.clave}_$v"
                val ie = es[nombre]?.size ?: 0
                val ing = en[nombre]?.size ?: 0
                // Las bolsas de la sesión llevan una semilla por voz hasta el volcado de los textos aprobados.
                val semilla = b.archivo == "sesion"
                if (!semilla && ie < b.minimo) fallos.add("$nombre ES tiene $ie y necesita ${b.minimo}")
                if (!semilla && ing < b.minimo) fallos.add("$nombre EN tiene $ing y necesita ${b.minimo}")
                if (semilla && (ie < 1 || ing < 1)) fallos.add("$nombre no tiene ni la semilla")
                if (ie != ing) fallos.add("$nombre tiene $ie en ES y $ing en EN")
            }
        }
        assertTrue(fallos.joinToString("\n"), fallos.isEmpty())
    }

    @Test
    fun `tags and gaps match by index between languages and tags are allowed`() {
        val es = items("es")
        val en = items("en")
        val fallos = mutableListOf<String>()
        bolsas.forEach { b ->
            voces.forEach { v ->
                val nombre = "habla_${b.clave}_$v"
                val ies = es[nombre].orEmpty()
                val ien = en[nombre].orEmpty()
                ies.zip(ien).forEachIndexed { i, (a, e) ->
                    val donde = "$nombre[$i]"
                    if (etiquetasDe(a) != etiquetasDe(e)) fallos.add("$donde etiquetas distintas: ${etiquetasDe(a)} / ${etiquetasDe(e)}")
                    (etiquetasDe(a) + etiquetasDe(e)).filter { it !in etiquetasValidas }
                        .forEach { fallos.add("$donde etiqueta desconocida $it") }
                    val ha = nombresDe(sinEtiquetas(a)).toSortedSet()
                    val he = nombresDe(sinEtiquetas(e)).toSortedSet()
                    if (ha != he) fallos.add("$donde huecos distintos: ES $ha / EN $he")
                }
            }
        }
        assertTrue(fallos.joinToString("\n"), fallos.isEmpty())
    }

    @Test
    fun `braces plurals gaps blacklist voice and length rules hold for every text`() {
        val fallos = mutableListOf<String>()
        listOf("es", "en").forEach { idioma ->
            val todo = items(idioma)
            bolsas.forEach { b ->
                voces.forEach { v ->
                    todo["habla_${b.clave}_$v"].orEmpty().forEachIndexed { i, item ->
                        comprobarTexto(b, v, idioma, sinEtiquetas(item), "habla_${b.clave}_$v[$i]", fallos)
                    }
                }
            }
        }
        assertTrue(fallos.joinToString("\n"), fallos.isEmpty())
    }

    @Test
    fun `no array in the bag files is outside the catalogue`() {
        val esperados = bolsas.flatMap { b -> voces.map { "habla_${b.clave}_$it" } }.toSet()
        listOf("values", "values-es").forEach { carpeta ->
            val reales = bolsas.map { it.archivo }.distinct().flatMap { leer(carpeta, it).keys }.toSet()
            assertEquals(carpeta, esperados, reales)
        }
    }
}
