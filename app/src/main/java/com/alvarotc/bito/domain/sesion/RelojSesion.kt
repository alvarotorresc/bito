package com.alvarotc.bito.domain.sesion

import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.model.BreathingMode
import kotlin.math.abs

/**
 * La cuenta atras es aritmetica sobre un fin guardado, no un contador que alguien baje: cualquiera
 * que sepa la hora puede decir cuanto queda. Dos relojes porque ninguno solo vale:
 * elapsedRealtime se va a cero al reiniciar y currentTimeMillis salta si se cambia la hora.
 */
object FocusClock {
    /** Margen de la firma de arranque: el par de relojes no se lee en el mismo instante. */
    const val BOOT_TOLERANCE_MILLIS = 5_000L

    fun bootSignatureOf(
        nowMillis: Long,
        elapsedMillis: Long,
    ): Long = nowMillis - elapsedMillis
}

/**
 * El tiempo de la sesion como funcion pura de su [RegistroSesion] y de los dos relojes (spec §6,
 * §11.5). Nadie guarda «cuanto queda»: se recalcula desde el plan y los eventos, tambien con la
 * app muerta o tras reiniciar.
 */
object RelojSesion {
    const val TOLERANCIA_ARRANQUE_MILLIS = FocusClock.BOOT_TOLERANCE_MILLIS

    private const val MILLIS_POR_MINUTO = 60_000L

    /**
     * Ms de sesion desde Empezar. Dentro del mismo arranque manda elapsedRealtime; tras reiniciar,
     * el reloj de pared.
     *
     * Si el registro y [ahora] traen el contador de arranques del sistema, mismo arranque es que
     * coincida: ni un cambio de hora grande ni un reinicio con la pared atrasada lo confunden.
     *
     * Si falta en alguno, mismo arranque es la firma de [FocusClock] dentro de su tolerancia, o bien que el elapsed no
     * haya bajado del ancla y la firma no diga que el movil arranco despues de Empezar. Lo segundo
     * cubre un cambio de hora dentro del arranque, que mueve la firma tanto como el salto: con solo
     * la tolerancia, adelantar la hora movería el reloj. Un salto hacia delante mayor que el tiempo
     * que llevaba encendido el movil al Empezar se lee como reinicio.
     */
    fun transcurrido(
        r: RegistroSesion,
        ahora: Ahora,
    ): Long {
        val contadorGuardado = r.arranques
        val contadorAhora = ahora.arranques
        val mismoArranque =
            if (contadorGuardado != null && contadorAhora != null) {
                contadorGuardado == contadorAhora
            } else {
                val firma = FocusClock.bootSignatureOf(ahora.paredMillis, ahora.elapsedMillis)
                val mismaFirma = abs(firma - r.arranque) <= TOLERANCIA_ARRANQUE_MILLIS
                mismaFirma || (ahora.elapsedMillis >= r.anclaElapsed && firma < r.inicioPared)
            }
        val ms = if (mismoArranque) ahora.elapsedMillis - r.anclaElapsed else ahora.paredMillis - r.inicioPared
        return ms.coerceAtLeast(0L)
    }

    fun en(
        r: RegistroSesion,
        ahora: Ahora,
    ): Instante = enSesion(r, transcurrido(r, ahora))

    /**
     * Recorre los tramos aplicando los eventos en orden de [Evento.enMillis]. Un evento que ya no
     * encaja con el tramo de su instante no hace nada: un [Evento.Saltar] que llega cuando el
     * descanso ya acabo solo no se come el foco siguiente, un [Evento.Sumar] fuera de un foco no
     * suma y un [Evento.EmpezarDescanso] sin descanso esperando se ignora. [Evento.Pausa] y
     * [Evento.Seguir] solo actuan en un foco: una pausa que cae ya en el descanso o en Respirar
     * no congela nada, y al cambiar de tramo la pausa se suelta.
     */
    fun enSesion(
        r: RegistroSesion,
        msSesion: Long,
    ): Instante {
        val t = msSesion.coerceAtLeast(0L)
        val recorrido = Recorrido(r.plan)
        r.eventos
            .sortedBy { it.enMillis }
            .filter { it.enMillis <= t }
            .forEach { evento ->
                recorrido.avanzar(evento.enMillis.coerceAtLeast(0L))
                recorrido.aplicar(evento)
            }
        recorrido.avanzar(t)
        return recorrido.instante(t)
    }

    /**
     * Hora de pared en la que la sesion llega a [msSesion], segun el reloj de pared de [ahora]. Vale
     * para el cronometro. Para una alarma conviene `ELAPSED_REALTIME_WAKEUP` en
     * `ahora.elapsedMillis + (msSesion - transcurrido(r, ahora))`: un cambio de hora despues de
     * programarla no la mueve, y esta hora de pared si se moveria.
     */
    fun paredDe(
        r: RegistroSesion,
        msSesion: Long,
        ahora: Ahora,
    ): Long = ahora.paredMillis + (msSesion - transcurrido(r, ahora))

    private fun duracionBase(tramo: Tramo): Long =
        when (tramo) {
            is Tramo.Respirar -> BreathingRhythm.totalMillis(BreathingMode.CALM, tramo.ciclos)
            is Tramo.Foco -> tramo.minutos * MILLIS_POR_MINUTO
            is Tramo.Descanso -> tramo.minutos * MILLIS_POR_MINUTO
        }

    /** El estado de la simulacion: tramo en curso, cuanto lleva y si esta parado. */
    private class Recorrido(private val plan: PlanSesion) {
        private val tramos = Tramos.de(plan)
        private var indice = 0
        private var extra = 0L
        private var llevado = 0L
        private var ultimo = 0L
        private var pausado = false
        private var esperando = false
        private var foco = 0L

        private val actual: Tramo? get() = tramos.getOrNull(indice)

        private fun duracion(): Long = actual?.let { duracionBase(it) + extra } ?: 0L

        fun avanzar(hasta: Long) {
            while (!pausado && !esperando && indice < tramos.size) {
                val queda = duracion() - llevado
                if (ultimo + queda <= hasta) {
                    contarFoco(queda)
                    ultimo += queda
                    siguiente()
                } else {
                    contarFoco(hasta - ultimo)
                    llevado += hasta - ultimo
                    break
                }
            }
            ultimo = maxOf(ultimo, hasta)
        }

        fun aplicar(evento: Evento) {
            val tramo = actual ?: return
            when (evento) {
                is Evento.Pausa -> if (tramo is Tramo.Foco) pausado = true
                is Evento.Seguir -> if (tramo is Tramo.Foco) pausado = false
                is Evento.Sumar -> if (tramo is Tramo.Foco) extra += evento.minutos * MILLIS_POR_MINUTO
                is Evento.Saltar -> if (tramo is Tramo.Respirar || tramo is Tramo.Descanso) siguiente()
                is Evento.EmpezarDescanso -> esperando = false
            }
        }

        fun instante(t: Long): Instante {
            val tramo = actual
            val resta = (duracion() - llevado).coerceAtLeast(0L)
            val parado = tramo == null || pausado || esperando
            return Instante(
                tramo = indice,
                actual = tramo,
                restaMillis = resta,
                duracionTramoMillis = duracion(),
                focoMillis = foco,
                pausado = tramo != null && pausado,
                esperandoDescanso = tramo != null && esperando,
                respiracion = (tramo as? Tramo.Respirar)?.let { BreathingRhythm.at(BreathingMode.CALM, llevado, it.ciclos) },
                transcurridoMillis = t,
                finTramoEnSesion = if (parado) null else t + resta,
            )
        }

        private fun contarFoco(ms: Long) {
            if (actual is Tramo.Foco) foco += ms
        }

        private fun siguiente() {
            indice++
            extra = 0L
            llevado = 0L
            pausado = false
            esperando = actual is Tramo.Descanso && !plan.descansoSolo
        }
    }
}
