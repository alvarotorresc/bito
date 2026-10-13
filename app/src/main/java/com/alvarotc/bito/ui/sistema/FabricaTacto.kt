package com.alvarotc.bito.ui.sistema

import android.content.Context
import com.alvarotc.bito.data.settings.SettingsRepository

/** La única forma de crear el tacto (la llama `AppContainer`). Mudo hasta la Tarea 21, que cambia solo este cuerpo. */
fun crearTacto(
    context: Context,
    settings: SettingsRepository,
): Tacto = TactoMudo
