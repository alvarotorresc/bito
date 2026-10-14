package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * Las candidatas que salen como respuesta y no por prioridad (spec §5.1, segunda tabla).
 * Provisional: candidatas mínimas de su situación; la Tarea 14 escribe los cuerpos.
 */
object Respuestas {
    /** Tras «Vale, a las…». Con lo gordo sin hacer, sesión o luego; sin él, solo «Otra hora». */
    fun confirmaVale(
        c: Candidata,
        hora: Int?,
        h: Hechos,
    ): Candidata =
        Candidata(
            situacion = Situacion.CONFIRMA_VALE,
            ref = c.ref,
            huecos = if (hora == null) emptyMap() else mapOf(Hueco.HORA to ValorHueco.Hora(hora)),
            acciones = if (h.gordo.isEmpty()) listOf(Respuesta.OTRA_HORA) else listOf(Respuesta.AHORA, Respuesta.LUEGO),
            hora = hora,
        )

    /** Tras guardar la hoja de lo gordo. */
    fun gordoElegido(h: Hechos): Candidata = Candidata(Situacion.GORDO_ELEGIDO, null, emptyMap(), listOf(Respuesta.AHORA, Respuesta.LUEGO))

    /** Tras «Hoy no» o «Ahora no»: la frase corta, sin acciones ni huecos (HUECOS.md: ni `{n}` ni `{tarea}`). */
    fun trasHoyNo(h: Hechos): Candidata = Candidata(Situacion.TRAS_HOY_NO, null, emptyMap(), emptyList())

    /**
     * El día sellado (Hoy de noche), sin acciones. Sin `{n}`; `{tarea}` es la primera tarea de lo
     * gordo que quedó sin hacer (el arrastre de §5.4); sin arrastre, no hay `{tarea}` (HUECOS.md).
     */
    fun noche(h: Hechos): Candidata {
        val arrastre =
            h.gordo
                .filter { it.tipo == TipoRefGordo.TAREA && it !in h.gordoHecho }
                .firstNotNullOfOrNull { ref -> h.tareas.firstOrNull { it.id == ref.id } }
        val huecos = arrastre?.let { mapOf(Hueco.TAREA to ValorHueco.Texto(it.titulo)) } ?: emptyMap()
        return Candidata(Situacion.NOCHE, null, huecos, emptyList())
    }
}
