package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.HoraPropuesta
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * §5.7: las notas que valen para un hábito, en el orden de [com.alvarotc.bito.domain.habla.Prioridades.FICHA].
 * Un hábito que no está en los hechos (archivado) no tiene nota. Las recaídas nunca se comentan:
 * ninguna regla las mira. El motor se queda con la primera que no haya descartado «No hace falta».
 */
object ReglasFicha {
    private const val OBJETIVO_PCT_MAX = 40
    private const val OBJETIVO_JUZGADOS_MIN = 14
    private const val NUEVO_DIAS = 7
    private const val RACHA_MIN = 3
    private const val MEJOR_DIA_SEMANAS = 6
    private const val MEJOR_DIA_PUNTOS = 15

    fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
        habitId: String?,
    ): List<Candidata> {
        val habito = h.habitos.firstOrNull { it.id == habitId } ?: return emptyList()
        if (habito.enPausa) return listOf(pausa(habito))
        return listOfNotNull(
            hoyFalta(habito, ctx),
            objetivo(habito),
            nuevo(habito),
            rachaVsMejor(habito),
            limpio(habito),
            mejorDia(habito),
            constancia(habito),
        )
    }

    private fun nota(
        situacion: Situacion,
        habito: HabitoHoy,
        huecos: Map<Hueco, ValorHueco> = emptyMap(),
        acciones: List<Respuesta> = emptyList(),
        destino: Destino? = null,
        hora: Int? = null,
    ) = Candidata(
        situacion = situacion,
        ref = habito.id,
        huecos = mapOf(Hueco.HABITO to ValorHueco.Texto(habito.nombre)) + huecos,
        acciones = acciones,
        destino = destino,
        hora = hora,
    )

    private fun pausa(habito: HabitoHoy) =
        Candidata(
            situacion = Situacion.FI_PAUSA,
            ref = habito.id,
            huecos = habito.pausadoDesde?.let { mapOf(Hueco.FECHA to ValorHueco.Fecha(it.toLong())) }.orEmpty(),
            acciones = emptyList(),
        )

    private fun hoyFalta(
        habito: HabitoHoy,
        ctx: Contexto,
    ): Candidata? {
        if (!habito.pendienteHoy() || habito.tipo.esDeQuitar()) return null
        val hora = HoraPropuesta.de(habito, ctx) ?: return null
        return nota(
            Situacion.FI_HOY_FALTA,
            habito,
            huecos = mapOf(Hueco.HORA to ValorHueco.Hora(hora)),
            acciones = listOf(Respuesta.VALE, Respuesta.HOY_NO),
            destino = Destino.Habito(habito.id),
            hora = hora,
        )
    }

    private fun objetivo(habito: HabitoHoy): Candidata? {
        val pct = habito.pct30 ?: return null
        if (habito.tipo.esDeQuitar() || pct >= OBJETIVO_PCT_MAX || habito.juzgados30 < OBJETIVO_JUZGADOS_MIN) return null
        // La mitad, con el paso como mínimo; si eso no baja el objetivo, no hay nada que proponer.
        val sugerido = maxOf(habito.paso, habito.objetivo / 2)
        if (sugerido >= habito.objetivo) return null
        return nota(
            Situacion.FI_OBJETIVO,
            habito,
            huecos =
                mapOf(
                    Hueco.PCT to ValorHueco.Numero(pct),
                    Hueco.OBJETIVO to ValorHueco.Numero(sugerido),
                ),
            acciones = listOf(Respuesta.VALE, Respuesta.HOY_NO),
            destino = Destino.Habito(habito.id),
        )
    }

    private fun nuevo(habito: HabitoHoy): Candidata? = nota(Situacion.FI_NUEVO, habito).takeIf { habito.creadoHaceDias < NUEVO_DIAS }

    private fun rachaVsMejor(habito: HabitoHoy): Candidata? =
        nota(
            Situacion.FI_RACHA_VS_MEJOR,
            habito,
            huecos = mapOf(Hueco.RACHA to ValorHueco.Numero(habito.racha), Hueco.MEJOR to ValorHueco.Numero(habito.mejorRacha)),
        ).takeIf { habito.racha >= RACHA_MIN }

    private fun limpio(habito: HabitoHoy): Candidata? =
        nota(Situacion.FI_LIMPIO, habito, huecos = mapOf(Hueco.N to ValorHueco.Numero(habito.diasLimpios)))
            .takeIf { habito.tipo == TipoHabito.DEJAR && habito.diasLimpios >= 1 }

    private fun mejorDia(habito: HabitoHoy): Candidata? {
        val dia = habito.mejorDiaSemana ?: return null
        val pctDia = habito.pctMejorDiaSemana ?: return null
        val pct30 = habito.pct30 ?: return null
        if (habito.semanasHistoria < MEJOR_DIA_SEMANAS || pctDia < pct30 + MEJOR_DIA_PUNTOS) return null
        return nota(
            Situacion.FI_MEJOR_DIA,
            habito,
            huecos = mapOf(Hueco.DIA_SEMANA to ValorHueco.DiaSemana(dia), Hueco.PCT to ValorHueco.Numero(pctDia)),
        )
    }

    private fun constancia(habito: HabitoHoy): Candidata =
        nota(
            Situacion.FI_CONSTANCIA,
            habito,
            huecos = habito.pct30?.let { mapOf(Hueco.PCT to ValorHueco.Numero(it)) }.orEmpty(),
        )

    private fun TipoHabito.esDeQuitar() = this == TipoHabito.LIMITE || this == TipoHabito.DEJAR
}
