package com.alvarotc.bito.ui.notifications

import android.content.Context
import android.graphics.Bitmap
import com.alvarotc.bito.ui.habi.HabiGrain
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.renderHabiBitmap
import java.util.Objects

/**
 * La cara de Habi como icono grande de una notificación (catálogo, biblia §6): el MISMO
 * `renderHabiBitmap` que pinta el widget, así que sombra, textura y la pose del día vienen
 * incluidas — el transform del cuerpo vive dentro de `drawHabi`, no en un `graphicsLayer` que un
 * canvas por software no tendría, y `renderHabiBitmap` lo deriva de `spec.pose`.
 *
 * 128x128 px: la guía pide ~64 dp, el sistema reescala, y 128 sobra para una geometría vectorial
 * sin detalle fino. En ARGB_8888 son 64 KB.
 *
 * Caché de UNA entrada, y basta: [TrayRefresher] re-publica el recordatorio GLOBAL en cada
 * escritura in-app mientras está en bandeja, y en esa ráfaga el spec no cambia. Un mood distinto
 * invalida y re-renderiza (~2-4 ms).
 *
 * Su voz NO viaja aquí: los sonidos de Habi nunca suenan en notificaciones (docs/01 §5.3).
 */
object HabiNotificationIcon {
    const val SIZE_PX = 128

    private var cachedKey: Int? = null
    private var cached: Bitmap? = null

    @Synchronized
    fun bitmapOf(
        context: Context,
        spec: HabiSpec,
    ): Bitmap {
        val key =
            Objects.hash(
                spec.mood,
                spec.personality,
                spec.equipped,
                spec.eyesPainted,
                spec.pose,
                spec.bodyToneOverride,
                spec.closedEyes,
            )
        cached?.let { if (cachedKey == key) return it }
        val bitmap = renderHabiBitmap(spec, SIZE_PX, HabiGrain.brush(context))
        cachedKey = key
        cached = bitmap
        return bitmap
    }
}
