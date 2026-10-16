package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco

/**
 * §5.9.3: lo que Habi comenta al cerrar el día. Devuelve todas las que valen; el motor se queda con
 * la primera de [com.alvarotc.bito.domain.habla.Prioridades.CIERRE] y, dentro de ella, la de mayor racha.
 */
object ReglasCierre : Regla {
    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> {
        val nombre = ctx.nombre?.takeIf { it.isNotBlank() }?.let { mapOf(Hueco.NOMBRE to ValorHueco.Texto(it)) }.orEmpty()

        fun sin(
            situacion: Situacion,
            huecos: Map<Hueco, ValorHueco> = emptyMap(),
            peso: Int = 0,
            ref: String? = null,
        ) = Candidata(situacion, ref, nombre + huecos, emptyList(), peso = peso)

        fun deHabito(
            situacion: Situacion,
            habito: HabitoHoy,
        ) = sin(
            situacion,
            mapOf(Hueco.HABITO to ValorHueco.Texto(habito.nombre), Hueco.RACHA to ValorHueco.Numero(habito.racha)),
            peso = habito.racha,
            ref = habito.id,
        )

        val pendientes = h.gordo.filter { it !in h.gordoHecho }
        return buildList {
            if (h.gordo.isNotEmpty() && pendientes.isEmpty()) {
                val nombres = h.gordo.mapNotNull { Respuestas.nombreDeGordo(it, h) }
                add(sin(Situacion.CIE_GORDO_HECHO, mapOf(Hueco.GORDO to ValorHueco.Lista(nombres))))
            }
            h.habitos.filter { it.falladoAyer && it.hechoHoy }.forEach { add(deHabito(Situacion.CIE_LOGRO, it)) }
            h.habitos.filter { it.rachaHoyRedonda }.forEach { add(deHabito(Situacion.CIE_RACHA_AVANZA, it)) }
            if (h.pendientesCierre == 0) add(sin(Situacion.CIE_NADA_PENDIENTE))
            add(sin(Situacion.CIE_SENCILLO))
        }
    }
}
