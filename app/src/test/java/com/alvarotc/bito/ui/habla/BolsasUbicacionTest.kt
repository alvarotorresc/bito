package com.alvarotc.bito.ui.habla

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Cada `string-array` de una bolsa vive en `strings_habla_<archivo>.xml` (el `archivo` de
 * `tools/habla/bolsas.json`) y con su nombre exacto: si no, el volcado de los textos definitivos
 * crearía un recurso duplicado.
 */
class BolsasUbicacionTest {
    private val voces = listOf("sargento", "cheerleader", "neutra")
    private val bolsas =
        Json
            .parseToJsonElement(File("../tools/habla/bolsas.json").readText())
            .jsonArray
            .map { it.jsonObject }
            .map { it.getValue("bolsa").jsonPrimitive.content to it.getValue("archivo").jsonPrimitive.content }

    private fun arrays(archivo: File): List<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(archivo)
        val nodos = doc.getElementsByTagName("string-array")
        return (0 until nodos.length).map { nodos.item(it).attributes.getNamedItem("name").nodeValue }
    }

    private fun archivo(
        carpeta: String,
        nombre: String,
    ) = File("src/main/res/$carpeta/strings_habla_$nombre.xml")

    @Test
    fun `each bag array lives in the file the json says, in both languages`() {
        listOf("values", "values-es").forEach { carpeta ->
            val porArchivo = bolsas.map { it.second }.distinct().associateWith { arrays(archivo(carpeta, it)) }
            bolsas.forEach { (bolsa, nombre) ->
                voces.forEach { voz ->
                    val array = "habla_${bolsa}_$voz"
                    assertTrue("$carpeta: $array falta en $nombre", array in porArchivo.getValue(nombre))
                    val otros = porArchivo.filterKeys { it != nombre }.filterValues { array in it }
                    assertTrue("$carpeta: $array duplicado en ${otros.keys}", otros.isEmpty())
                }
            }
        }
    }

    @Test
    fun `the bag files hold only bag arrays, three voices each`() {
        listOf("values", "values-es").forEach { carpeta ->
            bolsas.map { it.second }.distinct().forEach { nombre ->
                val esperados = bolsas.filter { it.second == nombre }.sumOf { voces.size }
                assertEquals("$carpeta $nombre", esperados, arrays(archivo(carpeta, nombre)).size)
            }
        }
    }
}
