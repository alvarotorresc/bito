package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.HoraPropuesta
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Ritmo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * §5.1 n.º 10: un hábito diario con 6 semanas de historia que flojea este día de la semana
 * (8 apariciones, 5 juzgadas como mínimo, 50 % o menos y 25 puntos por debajo de su media).
 * Como mucho una vez cada [Ritmo.DIA_FLOJO_CADA_DIAS] días por hábito: una respuesta lo calla.
 */
object ReglaDiaFlojo : Regla {
    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> =
        h.habitos
            .filter { it.flojoHoy() && it.pendienteHoy() && !respondidaDeSobra(m, ctx, it) }
            .map { habito ->
                val hora = HoraPropuesta.de(habito, ctx)
                Candidata(
                    situacion = Situacion.DIA_FLOJO,
                    ref = habito.id,
                    huecos =
                        buildMap {
                            put(Hueco.HABITO, ValorHueco.Texto(habito.nombre))
                            put(Hueco.DIA_SEMANA, ValorHueco.DiaSemana(ctx.diaSemana))
                            hora?.let { put(Hueco.HORA, ValorHueco.Hora(it)) }
                        },
                    acciones = if (hora != null) listOf(Respuesta.VALE, Respuesta.HOY_NO) else listOf(Respuesta.AHORA, Respuesta.HOY_NO),
                    destino = Destino.Habito(habito.id),
                    hora = hora,
                )
            }

    private fun HabitoHoy.flojoHoy(): Boolean {
        val dia = pctHoySemana ?: return false
        val media = pct30 ?: return false
        return diario &&
            semanasHistoria >= Ritmo.DIA_FLOJO_SEMANAS &&
            muestrasHoySemana >= Ritmo.DIA_FLOJO_MIN_JUZGADAS &&
            dia <= Ritmo.DIA_FLOJO_PCT_MAX &&
            media - dia >= Ritmo.DIA_FLOJO_PUNTOS
    }

    private fun respondidaDeSobra(
        m: Memoria,
        ctx: Contexto,
        habito: HabitoHoy,
    ): Boolean {
        val prefijo = "${Situacion.DIA_FLOJO.name}:${habito.id}:"
        return m.eventos.any {
            it.tipo == TipoMemoria.RESPUESTA &&
                it.ref?.startsWith(prefijo) == true &&
                ctx.hoy - it.dia < Ritmo.DIA_FLOJO_CADA_DIAS
        }
    }
}
