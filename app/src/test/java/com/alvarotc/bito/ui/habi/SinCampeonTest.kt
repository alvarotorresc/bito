package com.alvarotc.bito.ui.habi

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Quien no ha dado su nombre no recibe un apodo: ningún sitio de la app (código ni textos) conserva el
 * respaldo «campeón»/«champ» ni el recurso `habi_name_fallback`.
 */
class SinCampeonTest {
    private val prohibido = Regex("habi_name_fallback|campe[oó]n|champ", RegexOption.IGNORE_CASE)

    private fun ficheros(): List<File> {
        val base = File("src/main")
        val codigo = File(base, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }
        val textos =
            (base.resolve("res").listFiles { f -> f.isDirectory && f.name.startsWith("values") } ?: emptyArray())
                .flatMap { dir -> dir.listFiles { f -> f.extension == "xml" }?.toList().orEmpty() }
        return (codigo + textos).toList()
    }

    @Test
    fun `no hay respaldo con apodo en el codigo ni en los textos`() {
        val encontrados = ficheros()
        assertTrue("no se han leído los ficheros de la app", encontrados.size > 100)
        val faltas =
            encontrados.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, linea ->
                    if (prohibido.containsMatchIn(linea)) "${f.path}:${i + 1}: ${linea.trim()}" else null
                }
            }
        assertTrue("Quedan ${faltas.size} apariciones:\n${faltas.joinToString("\n")}", faltas.isEmpty())
    }
}
