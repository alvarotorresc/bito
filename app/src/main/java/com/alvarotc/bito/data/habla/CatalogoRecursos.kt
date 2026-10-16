package com.alvarotc.bito.data.habla

import android.content.res.Resources
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.CatalogoHabla
import com.alvarotc.bito.domain.habla.Plantilla
import com.alvarotc.bito.domain.habla.TrozoInfo
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.habla.BolsasRecursos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Las bolsas de Habi leídas de los `string-array` (spec §4.4, §11.3). De cada item guarda su índice,
 * sus etiquetas y sus huecos; el texto no, porque se vuelve a leer del recurso al componer la frase.
 * Se calcula una vez por idioma y se recalcula si el idioma de [resources] cambia. Seguro entre hilos.
 */
class CatalogoRecursos(
    private val resources: () -> Resources,
) : CatalogoHabla {
    private data class Clave(
        val idioma: String,
        val bolsa: Bolsa,
        val voz: Personality,
    )

    private val cache = ConcurrentHashMap<Clave, List<TrozoInfo>>()

    override fun trozos(
        bolsa: Bolsa,
        voz: Personality,
    ): List<TrozoInfo> = trozos(resources(), bolsa, voz)

    /** Lee todas las bolsas del idioma actual fuera del hilo principal. */
    suspend fun precargar() {
        withContext(Dispatchers.Default) {
            val res = resources()
            Bolsa.entries.forEach { b -> Personality.entries.forEach { v -> trozos(res, b, v) } }
        }
    }

    internal fun idiomasCargados(): Set<String> = cache.keys.map { it.idioma }.toSet()

    private fun trozos(
        res: Resources,
        bolsa: Bolsa,
        voz: Personality,
    ): List<TrozoInfo> {
        val clave = Clave(res.configuration.locales[0].language, bolsa, voz)
        return cache.getOrPut(clave) {
            res.getStringArray(BolsasRecursos.array(bolsa, voz)).mapIndexed { i, item ->
                val (etiquetas, texto) = Plantilla.separarEtiquetas(item)
                TrozoInfo(i, etiquetas, Plantilla.huecosDe(texto))
            }
        }
    }
}
