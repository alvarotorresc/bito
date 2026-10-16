package com.alvarotc.bito.data.sesion

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.alvarotc.bito.domain.sesion.Ahora

/**
 * Los dos relojes y `Settings.Global.BOOT_COUNT` leídos a la vez. El motor y los receptores
 * construyen el [Ahora] con esta función, y el registro guarda su `arranques` al Empezar. Si el
 * sistema no da el contador, `arranques` es null y el reloj usa la firma de arranque.
 */
fun ahoraDelSistema(context: Context): Ahora =
    Ahora(
        paredMillis = System.currentTimeMillis(),
        elapsedMillis = SystemClock.elapsedRealtime(),
        arranques = contadorDeArranques(context),
    )

private fun contadorDeArranques(context: Context): Int? =
    try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    } catch (e: Settings.SettingNotFoundException) {
        null
    } catch (e: SecurityException) {
        null
    }
