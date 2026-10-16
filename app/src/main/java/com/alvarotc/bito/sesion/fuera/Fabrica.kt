package com.alvarotc.bito.sesion.fuera

import android.content.Context
import com.alvarotc.bito.data.sesion.SesionStore
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.sesion.FueraDeLaApp
import com.alvarotc.bito.sesion.FueraMuda
import com.alvarotc.bito.sesion.NoMolestar

/** El carril de fuera de la app sustituye el cuerpo por la implementación real. */
@Suppress("UNUSED_PARAMETER")
fun crearFuera(
    context: Context,
    store: SesionStore,
    noMolestar: NoMolestar,
    settings: SettingsRepository,
): FueraDeLaApp = FueraMuda
