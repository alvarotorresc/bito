package com.alvarotc.bito.ui.habi

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import com.alvarotc.bito.R

/**
 * El grano de Habi: el material de objeto pintado (biblia §3.4, «mate, con textura, como madera o
 * cerámica pintada a mano»), resuelto como un tile de ruido gris enmascarado a la silueta y
 * multiplicado sobre el cuerpo.
 *
 * **El tile NO se escala con el avatar**: se ancla en píxeles de pantalla, no en el viewport
 * normalizado, así el grano mide lo mismo a 40 dp que a 150 dp. Eso es exactamente lo que lo hace
 * leer como material y no como un patrón que crece con el bicho. En el widget, que dibuja a 96 px
 * con densidad 1, el tile se repite vez y media y el grano sale proporcionalmente más grueso — que
 * es lo que hace falta para que no se pierda a ese tamaño.
 *
 * Decodificado UNA vez por proceso: un bitmap de 64x64 en escala de grises son ~4 KB de RAM.
 */
object HabiGrain {
    const val TILE_PX = 64
    const val ALPHA = 0.10f

    @Volatile
    private var cached: ShaderBrush? = null

    fun brush(context: Context): ShaderBrush =
        cached ?: synchronized(this) {
            cached ?: ShaderBrush(
                ImageShader(
                    BitmapFactory.decodeResource(context.resources, R.drawable.habi_grain).asImageBitmap(),
                    TileMode.Repeated,
                    TileMode.Repeated,
                ),
            ).also { cached = it }
        }

    /** Tests y previews: sin contexto no hay grano, y el dibujo sigue siendo correcto. */
    fun brushOrNull(context: Context?): ShaderBrush? = context?.let(::brush)
}
