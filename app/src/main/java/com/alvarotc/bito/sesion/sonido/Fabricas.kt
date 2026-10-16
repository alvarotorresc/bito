package com.alvarotc.bito.sesion.sonido

import android.content.Context
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.sesion.Aire
import com.alvarotc.bito.sesion.AireMudo
import com.alvarotc.bito.sesion.Fondo
import com.alvarotc.bito.sesion.FondoMudo
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido

/** El carril de sonido sustituye el cuerpo por la implementación real. */
@Suppress("UNUSED_PARAMETER")
fun crearAire(
    context: Context,
    sonido: MotorSonido,
    tacto: Tacto,
    settings: SettingsRepository,
): Aire = AireMudo

/** El carril de sonido sustituye el cuerpo por la implementación real. */
@Suppress("UNUSED_PARAMETER")
fun crearFondo(
    context: Context,
    sonido: MotorSonido,
    settings: SettingsRepository,
): Fondo = FondoMudo
