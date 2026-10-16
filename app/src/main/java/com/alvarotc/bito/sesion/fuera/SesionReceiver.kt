package com.alvarotc.bito.sesion.fuera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.sesion.RelojSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.domain.sesion.Tramos
import com.alvarotc.bito.sesion.AccionFondo
import com.alvarotc.bito.sesion.SonidoFrontera
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
                // Con el audio ya movido, la misma regla que con la app delante. Desde aquí solo puede soltar el servicio.
                val suena = audio.aire.sonando.value || audio.fondo.sonando.value != null
                ReglaServicio.aplicar(e, r, instante, ahora, suena, audio.fondo.pendiente.value, puedeArrancar = false)
            }
            SesionSync.aplicar(e, r, ahora, restaurarDnd = true)
        }

        /** El audio de la frontera: el motor lo hace con la app delante; aquí, con ella fuera. */
        private fun sonarFrontera(
            e: EntornoFuera,
            audio: AudioFrontera,
            fondo: SessionBackground,
            previo: Tramo,
            nuevo: Tramo?,
        ) {
            val salida = SonidoFrontera.de(previo, nuevo)
            if (salida.pararAire) audio.aire.parar()
            when (salida.fondo) {
                AccionFondo.SONAR -> if (nuevo != null) audio.fondo.sonar(fondo)
                AccionFondo.CALLAR -> audio.fondo.callar()
            }
            // Al acabar no suena nada: avisa la notificación de fin.
            if (nuevo != null) {
                audio.sonido.play(salida.snd)
                e.tacto().haptica(salida.haptica)
            }
        }
    }
}
