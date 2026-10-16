package com.alvarotc.bito.sesion.fuera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alvarotc.bito.domain.sesion.RelojSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.domain.sesion.Tramos
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Suena la alarma de una frontera con la app fuera (spec pieza 5 §11.3). Lee el [com.alvarotc.bito.data.sesion.SesionStore]
 * y [RelojSesion], nunca el motor: con la app muerta no hay motor. Con la app delante no hace nada (lo hace el motor).
 * Mueve el audio de la frontera con el del contenedor, pone el aviso del tramo nuevo y programa la siguiente alarma.
 * Lo que no hace nunca es arrancar el servicio de sonido: si el proceso no lo tenía, no hay fondo que mover.
 */
class SesionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        val tramoEsperado = intent.getIntExtra(AlarmaSesion.EXTRA_TRAMO, -1)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Un fallo de DataStore o de la bandeja no debe tumbar el proceso; finish() tiene que llegar.
                runCatching { procesar(PuertaFuera.entornoDe(context), PuertaFuera.audioDe(context), tramoEsperado) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal companion object {
        /** [tramoEsperado]: el tramo que debía acabar con esta alarma; si el actual no lo pasó, solo se reprograma. */
        suspend fun procesar(
            e: EntornoFuera,
            audio: AudioFrontera,
            tramoEsperado: Int,
        ) {
            if (e.visible()) return
            val r = e.store.leer()
            if (r == null) {
                AlarmaSesion.cancelar(e.context)
                return
            }
            val ahora = e.ahora()
            val instante = RelojSesion.en(r, ahora)
            val tramos = Tramos.de(r.plan)
            if (tramoEsperado in tramos.indices && instante.tramo > tramoEsperado) {
                sonarFrontera(e, audio, r.plan.fondo, previo = tramos[instante.tramo - 1], nuevo = instante.actual)
            }
            SesionSync.aplicar(e, r, ahora, restaurarDnd = true)
        }

        /** El audio de la frontera: el motor lo hace con la app delante; aquí, con ella fuera. */
        private fun sonarFrontera(
            e: EntornoFuera,
            audio: AudioFrontera,
            fondo: com.alvarotc.bito.data.settings.SessionBackground,
            previo: Tramo,
            nuevo: Tramo?,
        ) {
            when (previo) {
                is Tramo.Foco -> {
                    audio.fondo.callar()
                    if (nuevo != null) {
                        audio.sonido.play(Snd.CAMPANILLA)
                        e.tacto().haptica(Haptica.CLICK)
                    }
                }
                is Tramo.Descanso -> {
                    if (nuevo != null) {
                        audio.fondo.sonar(fondo)
                        audio.sonido.play(Snd.BROTE)
                        e.tacto().haptica(Haptica.RISE)
                    }
                }
                is Tramo.Respirar -> {
                    audio.aire.parar()
                    if (nuevo != null) {
                        audio.fondo.sonar(fondo)
                        audio.sonido.play(Snd.ALEJAR)
                        e.tacto().haptica(Haptica.TICK)
                    }
                }
            }
        }
    }
}
