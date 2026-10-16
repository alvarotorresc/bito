package com.alvarotc.bito.sesion.fuera

import android.content.Context
import android.os.SystemClock
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.R
import com.alvarotc.bito.data.sesion.SesionStore
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.sesion.Instante
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.RelojSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.sesion.FueraDeLaApp
import com.alvarotc.bito.sesion.NoMolestar
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.Notifier
import com.alvarotc.bito.ui.sistema.TactoMudo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lo que pasa fuera de la pantalla, sobre Android: el aviso fijo, la alarma de la siguiente frontera, el servicio en
 * primer plano mientras suena algo continuo y el cerrojo de energía de respirar (spec pieza 5 §11.2, §11.3).
 */
class FueraAndroid internal constructor(
    private val entorno: EntornoFuera,
    private val alcance: CoroutineScope,
) : FueraDeLaApp {
    constructor(
        context: Context,
        store: SesionStore,
        noMolestar: NoMolestar,
        settings: SettingsRepository,
        alcance: CoroutineScope,
    ) : this(
        EntornoFuera(
            context = context.applicationContext,
            store = store,
            noMolestar = noMolestar,
            voz = { settings.settings.first().personality },
            titulo = { tituloDe(context.applicationContext, it) },
            tacto = { (context.applicationContext as? BitoApp)?.container?.tacto ?: TactoMudo },
            retenidos = avisosRetenidosReales(context.applicationContext, store, { settings.settings.first().dayCutoffMinutes }),
        ),
        alcance,
    )

    override suspend fun alCambiar(
        r: RegistroSesion?,
        sonando: Boolean,
        fondoPendiente: Boolean,
    ) {
        val context = entorno.context
        if (r == null) {
            AlarmaSesion.cancelar(context)
            entorno.despierto.liberar()
            // Con servicio vivo el aviso es suyo y lo quita él; un cancel desde aquí no valdría.
            if (!soltarServicio(entorno)) PresenciaSesion.cancelar(context)
            entorno.retenidos.soltar()
            return
        }
        NotificationChannels.ensure(context)
        val ahora = entorno.ahora()
        val instante = RelojSesion.en(r, ahora)
        gestionarCerrojo(instante)
        ReglaServicio.aplicar(entorno, r, instante, ahora, sonando, fondoPendiente)
        SesionSync.aplicar(entorno, r, ahora, restaurarDnd = false)
    }

    /**
     * Con la app fuera cambió lo que suena: la regla del servicio, con el registro guardado y el tramo de [RelojSesion]
     * en este momento. Solo puede soltarlo (la app no está delante). El servicio se lleva su aviso al soltarse: con la
     * sesión viva vuelve el de siempre; al acabar no, porque «Se acabó el tiempo» lo pone el receptor.
     */
    override suspend fun alCambiarElAudioFuera(
        guardado: RegistroSesion,
        suena: Boolean,
        fondoPendiente: Boolean,
    ) {
        val r = guardado
        val ahora = entorno.ahora()
        val instante = RelojSesion.en(r, ahora)
        val soltado = ReglaServicio.aplicar(entorno, r, instante, ahora, suena, fondoPendiente, puedeArrancar = false)
        if (soltado && instante.actual != null) {
            PresenciaSesion.mostrar(entorno.context, r, ahora, entorno.titulo(r.plan.objetivo), entorno.voz())
        }
    }

    /**
     * Respirar suelta: el servicio con su aviso (H18) y el cerrojo hasta el fin del ejercicio. Lo llama quien empieza la
     * respiración, con la app delante: arrancar un servicio desde segundo plano no está permitido, así que sin la app
     * visible no hace nada. `true` y `false` se ordenan: un `false` que llega mientras un `true` se prepara lo anula.
     */
    override fun respirando(
        activo: Boolean,
        finElapsed: Long?,
    ) {
        val context = entorno.context
        if (!activo) {
            synchronized(candadoRespirar) {
                generacion++
                entorno.servicio.parar()
                entorno.despierto.liberar()
            }
            return
        }
        if (!entorno.visible()) return
        val mia = synchronized(candadoRespirar) { ++generacion }
        alcance.launch {
            NotificationChannels.ensure(context)
            val aviso =
                Notifier.notificacionSesion(
                    context,
                    context.getString(R.string.breathing_title),
                    context.getString(R.string.ses_notif_en_marcha),
                    finParedMillis = null,
                    personality = entorno.voz(),
                )
            synchronized(candadoRespirar) {
                if (mia != generacion) return@launch
                entorno.servicio.arrancar(aviso, finElapsed)
                if (finElapsed != null) entorno.despierto.retener((finElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            }
        }
    }

    private val candadoRespirar = Any()
    private var generacion = 0

    /** Respirar para entrar: cerrojo solo lo que dura el tramo; en cualquier otro tramo, suelto. */
    private fun gestionarCerrojo(instante: Instante) {
        if (instante.actual is Tramo.Respirar) entorno.despierto.retener(instante.restaMillis) else entorno.despierto.liberar()
    }
}
