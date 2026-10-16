package com.alvarotc.bito.sesion.sonido

import android.content.Context
import android.media.AudioManager
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.sesion.Aire
import com.alvarotc.bito.sesion.Fondo
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.sonido.AjustesSonido
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

// Hasta la primera emisión de los ajustes no se decide nada con un estado de relleno: Bito callado.
private val SIN_AJUSTES = AjustesSonido.de(Settings()).copy(general = false)

/**
 * El aire de la sesión y de respirar. No usa la ganancia de [sonido]: esa calla con el móvil en
 * silencio y el tono no (spec §9.1), así que lee el canal ambiente de los ajustes guardados.
 */
@Suppress("UNUSED_PARAMETER")
fun crearAire(
    context: Context,
    sonido: MotorSonido,
    tacto: Tacto,
    settings: SettingsRepository,
): Aire {
    val ajustes = AtomicReference(SIN_AJUSTES)
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        settings.settings.collect { ajustes.set(AjustesSonido.de(it)) }
    }
    return MotorAire(
        audio = context.getSystemService(AudioManager::class.java),
        foco = FocoAudio.de(context),
        tacto = tacto,
        ganancia = { MotorAire.ganancia(ajustes.get()) },
    )
}

/** El fondo del foco: pista o bucle corto, con el foco de audio que comparte con el aire. */
@Suppress("UNUSED_PARAMETER")
fun crearFondo(
    context: Context,
    sonido: MotorSonido,
    settings: SettingsRepository,
): Fondo {
    val ajustes = AtomicReference(SIN_AJUSTES)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val fondo =
        FondoSesion(
            salida = SalidaFondoAndroid(context.applicationContext),
            foco = FocoAudio.de(context),
            ganancia = { MotorAire.ganancia(ajustes.get()) },
            scope = scope,
        )
    scope.launch {
        settings.settings.collect {
            ajustes.set(AjustesSonido.de(it))
            fondo.alCambiarGanancia()
        }
    }
    return fondo
}
