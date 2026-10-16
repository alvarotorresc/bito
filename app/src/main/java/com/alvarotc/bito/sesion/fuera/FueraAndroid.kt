package com.alvarotc.bito.sesion.fuera

import android.content.Context
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.data.sesion.SesionStore
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.sesion.FueraDeLaApp
import com.alvarotc.bito.sesion.NoMolestar
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.sistema.TactoMudo
import kotlinx.coroutines.flow.first

/**
 * Lo que pasa fuera de la pantalla, sobre Android: el aviso fijo, la alarma de la siguiente frontera
 * (y, con sonido continuo, el servicio de la sesión).
 */
class FueraAndroid internal constructor(private val entorno: EntornoFuera) : FueraDeLaApp {
    constructor(
        context: Context,
        store: SesionStore,
        noMolestar: NoMolestar,
        settings: SettingsRepository,
    ) : this(
        EntornoFuera(
            context = context.applicationContext,
            store = store,
            noMolestar = noMolestar,
            voz = { settings.settings.first().personality },
            titulo = { tituloDe(context.applicationContext, it) },
            tacto = { (context.applicationContext as? BitoApp)?.container?.tacto ?: TactoMudo },
        ),
    )

    override suspend fun alCambiar(
        r: RegistroSesion?,
        sonando: Boolean,
    ) {
        val context = entorno.context
        if (r == null) {
            AlarmaSesion.cancelar(context)
            PresenciaSesion.cancelar(context)
            return
        }
        NotificationChannels.ensure(context)
        SesionSync.aplicar(entorno, r, entorno.ahora(), restaurarDnd = false)
    }

    override fun respirando(
        activo: Boolean,
        finElapsed: Long?,
    ) = Unit
}
