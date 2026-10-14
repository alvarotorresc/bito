package com.alvarotc.bito.ui.sistema

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** La única forma de crear el tacto (la llama `AppContainer`). */
fun crearTacto(
    context: Context,
    settings: SettingsRepository,
): Tacto {
    val app = context.applicationContext
    val vibrador =
        if (Build.VERSION.SDK_INT >= 31) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    val cache = AtomicReference(true to 7)
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        settings.settings.collect { cache.set(it.logHapticEnabled to it.vibStrength) }
    }
    return TactoAndroid(
        vibrador = vibrador,
        ajustes = { cache.get() },
        respuestaSistema = {
            @Suppress("DEPRECATION")
            Settings.System.getInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        },
    )
}
