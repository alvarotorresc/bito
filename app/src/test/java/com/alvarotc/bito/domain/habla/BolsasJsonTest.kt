package com.alvarotc.bito.domain.habla

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** `tools/habla/bolsas.json` es la fuente única de las bolsas; el enum la sigue al pie de la letra. */
class BolsasJsonTest {
    private val json =
        Json
            .parseToJsonElement(File("../tools/habla/bolsas.json").readText())
            .jsonArray
            .map { it.jsonObject }

    private fun texto(
        o: Map<String, kotlinx.serialization.json.JsonElement>,
        clave: String,
    ): String = o.getValue(clave).jsonPrimitive.content

    @Test
    fun `the Bolsa enum matches tools habla bolsas json`() {
        val delJson = json.associateBy { texto(it, "bolsa") }
        assertEquals(delJson.keys, Bolsa.entries.map { it.clave }.toSet())
        Bolsa.entries.forEach { b ->
            val j = delJson.getValue(b.clave)
            assertEquals(b.clave, texto(j, "tipo"), b.tipo.name)
            assertEquals(b.clave, j.getValue("minimo").jsonPrimitive.int, b.minimo)
            val huecos = j.getValue("huecos").jsonArray.map { it.jsonPrimitive.content }.toSet()
            assertEquals(b.clave, huecos, b.huecos.map { it.clave }.toSet())
        }
    }

    @Test
    fun `the enum keeps the order of the json, that is the order of the spec`() =
        assertEquals(json.map { texto(it, "bolsa") }, Bolsa.entries.map { it.clave })

    @Test
    fun `a bag admits the name exactly when the json says so`() =
        json.forEach { j ->
            val b = Bolsa.entries.first { it.clave == texto(j, "bolsa") }
            assertEquals(b.clave, j.getValue("nombre").jsonPrimitive.boolean, Hueco.NOMBRE in b.huecos)
        }

    @Test
    fun `there are seventy five bags`() = assertEquals(75, Bolsa.entries.size)

    @Test
    fun `the four notice bags that can greet by name admit it`() =
        listOf(Bolsa.AV_RECORDATORIO, Bolsa.AV_NOCHE_CUERPO, Bolsa.AV_ACEPTADO_TITULO, Bolsa.AV_ACEPTADO_CUERPO)
            .forEach { assertEquals(it.clave, true, Hueco.NOMBRE in it.huecos) }

    @Test
    fun `the json has as many rows as the enum`() = assertEquals(json.size, Bolsa.entries.size)
}
