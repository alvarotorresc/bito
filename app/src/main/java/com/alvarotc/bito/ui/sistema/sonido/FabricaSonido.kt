package com.alvarotc.bito.ui.sistema.sonido

import android.content.Context
import com.alvarotc.bito.data.settings.SettingsRepository

/** La única forma de crear el motor (la llama `AppContainer`). Mudo hasta la Tarea 20, que cambia solo este cuerpo. */
fun crearMotorSonido(
    context: Context,
    settings: SettingsRepository,
): MotorSonido = MotorSonidoMudo
