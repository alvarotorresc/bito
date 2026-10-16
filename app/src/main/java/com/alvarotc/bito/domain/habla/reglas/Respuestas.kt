package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * Las candidatas que salen como respuesta y no por prioridad (spec §5.1, segunda tabla).
 */
object Respuestas {
    /**
     * Tras «Vale, a las…». Con lo gordo sin hacer, ofrece la sesión de lo gordo o luego (`{gordo}` es
     * lo que queda por hacer; la hora es la del hábito, nunca la de lo gordo); sin él, solo «Otra hora».
     */
    fun confirmaVale(
        c: Candidata,
        hora: Int?,
        h: Hechos,
    ): Candidata {
        val huecos = hora?.let { mapOf(Hueco.HORA to ValorHueco.Hora(it)) }.orEmpty()
        val pendientes = h.gordo.filter { it !in h.gordoHecho }
        if (pendientes.isEmpty()) {
            return Candidata(
                situacion = Situacion.CONFIRMA_VALE,
                ref = c.ref,
                huecos = huecos,
                acciones = listOf(Respuesta.OTRA_HORA),
                hora = hora,
                nucleo = Bolsa.NU_CONFIRMA_VALE,
            )
        }
        val nombres = pendientes.mapNotNull { nombreDeGordo(it, h) }
        return Candidata(
            situacion = Situacion.CONFIRMA_VALE,
            ref = c.ref,
            huecos = if (nombres.isEmpty()) huecos else huecos + (Hueco.GORDO to ValorHueco.Lista(nombres)),
            acciones = listOf(Respuesta.AHORA, Respuesta.LUEGO),
            destino = Destino.LoGordo,
            hora = hora,
            nucleo = Bolsa.NU_CONFIRMA_VALE_GORDO,
        )
    }

    internal fun nombreDeGordo(
        ref: RefGordo,
        h: Hechos,
    ): String? =
        when (ref.tipo) {
            TipoRefGordo.TAREA -> h.tareas.firstOrNull { it.id == ref.id }?.titulo
            TipoRefGordo.HABITO -> h.habitos.firstOrNull { it.id == ref.id }?.nombre
        }

    /**
     * Tras guardar la hoja de lo gordo. `{gordo}` es lo elegido, de 1 a 3 nombres; `{primer_paso}`, el de la
     * primera tarea de lo gordo que lo tenga (HUECOS.md). Sin nada que nombrar, null: la frase pide `{gordo}`.
     */
    fun gordoElegido(h: Hechos): Candidata? {
        val nombres = h.gordo.mapNotNull { nombreDeGordo(it, h) }
        if (nombres.isEmpty()) return null
        val huecos =
            buildMap {
                put(Hueco.GORDO, ValorHueco.Lista(nombres))
                primerPasoDeGordo(h)?.let { put(Hueco.PRIMER_PASO, ValorHueco.Texto(it)) }
            }
        return Candidata(Situacion.GORDO_ELEGIDO, null, huecos, listOf(Respuesta.AHORA, Respuesta.LUEGO))
    }

    /** El primer paso de la primera tarea de lo gordo que lo tenga, o null. */
    internal fun primerPasoDeGordo(h: Hechos): String? =
        h.gordo
            .filter { it.tipo == TipoRefGordo.TAREA }
            .firstNotNullOfOrNull { ref -> h.tareas.firstOrNull { it.id == ref.id }?.primerPaso }

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
