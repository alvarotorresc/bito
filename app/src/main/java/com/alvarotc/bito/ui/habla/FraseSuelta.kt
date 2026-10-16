package com.alvarotc.bito.ui.habla

import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.CatalogoHabla
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.TrozoInfo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.cumple
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.TextoHabi
import kotlin.random.Random

/**
 * Una frase de una sola bolsa suelta (la sesión, el descanso, «¿Cómo estás ahora?»): sin apertura, sin
 * cierre y sin memoria. Elige un item que los huecos y las etiquetas del [contexto] admitan (sin contexto, solo los sin etiquetas) con el azar de [semilla], que pone quien llama
 * (la misma semilla dice lo mismo).
 */
object FraseSuelta {
    fun elegir(
        bolsa: Bolsa,
        voz: Personality,
        nombre: String?,
        semilla: Long,
        catalogo: CatalogoHabla,
        huecos: Map<Hueco, ValorHueco> = emptyMap(),
        contexto: Contexto? = null,
    ): TextoHabi.Frase {
        val rellenos = if (nombre != null) huecos + (Hueco.NOMBRE to ValorHueco.Texto(nombre)) else huecos - Hueco.NOMBRE
        val trozos = catalogo.trozos(bolsa, voz)
        val admitidos = trozos.filter { admite(it, rellenos, contexto) }
        val elegido =
            if (admitidos.isNotEmpty()) {
                admitidos[Random(semilla).nextInt(admitidos.size)].indice
            } else {
                trozos.firstOrNull { Hueco.NOMBRE !in it.huecos && it.etiquetas.isEmpty() }?.indice
                    ?: trozos.firstOrNull { Hueco.NOMBRE !in it.huecos }?.indice
                    ?: 0
            }
        return TextoHabi.Frase(listOf(ClaveTrozo(bolsa, voz, elegido)), rellenos)
    }

    private fun admite(
        t: TrozoInfo,
        rellenos: Map<Hueco, ValorHueco>,
        contexto: Contexto?,
    ) = t.huecos.all { it in rellenos } && t.etiquetas.all { contexto?.cumple(it) == true }
}
