package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.HoraPropuesta
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Regla
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/**
 * §5.1 n.º 8: un hábito diario (no de límite ni de dejar) que se escapó ayer, tras haberse cumplido
 * al menos uno de los tres días anteriores, y que sigue pendiente hoy. Gana el de mejor racha.
 */
object ReglaAyerSeEscapo : Regla {
    override fun candidatas(
        h: Hechos,
        m: Memoria,
        ctx: Contexto,
    ): List<Candidata> =
        h.habitos
            .filter {
                it.diario &&
                    it.tipo != TipoHabito.LIMITE &&
                    it.tipo != TipoHabito.DEJAR &&
                    it.falladoAyer &&
                    it.cumplidosTresAnteriores >= 1 &&
                    it.pendienteHoy()
            }.map { habito ->
                val hora = HoraPropuesta.de(habito, ctx)
                val minutos = habito.objetivo.takeIf { habito.tipo == TipoHabito.DURACION && it >= 1 }
                Candidata(
                    situacion = Situacion.AYER_SE_ESCAPO,
                    ref = habito.id,
                    huecos =
                        buildMap {
                            put(Hueco.HABITO, ValorHueco.Texto(habito.nombre))
                            minutos?.let { put(Hueco.MINUTOS, ValorHueco.Numero(it)) }
                            hora?.let { put(Hueco.HORA, ValorHueco.Hora(it)) }
                        },
                    acciones =
                        if (hora != null) {
                            listOf(
                                Respuesta.VALE,
                                Respuesta.AHORA,
                                Respuesta.HOY_NO,
                            )
                        } else {
                            listOf(Respuesta.AHORA, Respuesta.HOY_NO)
                        },
                    destino = Destino.Habito(habito.id),
                    hora = hora,
                    peso = habito.racha,
                )
            }
}

/** Exigible hoy y sin hacer ni fallar: lo que sigue "pendiente hoy" (§5.1). */
internal fun HabitoHoy.pendienteHoy(): Boolean = exigibleHoy && !hechoHoy && !falladoHoy

/** «Vale, a las…» si hay hora; si no, «Ahora». Siempre acaba en «Hoy no». */
internal fun accionesConHora(hora: Int?): List<Respuesta> =
    if (hora != null) listOf(Respuesta.VALE, Respuesta.HOY_NO) else listOf(Respuesta.AHORA, Respuesta.HOY_NO)
