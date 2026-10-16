package com.alvarotc.bito.sesion.fuera

import android.content.Context
import com.alvarotc.bito.data.sesion.SesionStore
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.sesion.FueraDeLaApp
import com.alvarotc.bito.sesion.NoMolestar

fun crearFuera(
    context: Context,
    store: SesionStore,
    noMolestar: NoMolestar,
    settings: SettingsRepository,
): FueraDeLaApp = FueraAndroid(context, store, noMolestar, settings)
