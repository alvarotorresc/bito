package com.alvarotc.bito.ui.sesion

import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.sesion.Aire
import com.alvarotc.bito.sesion.MotorSesion

/** Lo que el Inicio necesita para vivir la sesión: el motor, el aire (la música de la cabecera) y los ajustes. */
class SesionInicio(
    val motor: MotorSesion,
    val aire: Aire,
    val ajustes: SettingsRepository,
)
