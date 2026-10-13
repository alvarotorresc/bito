package com.alvarotc.bito.ui.habi

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Las piezas pintadas, decodificadas una vez por tamaño de submuestreo y guardadas en una caché con
 * tope de memoria (spec §3.3): entrar y salir de pantallas no la hace crecer. Una por proceso
 * ([get]); `LruCache` es segura entre hilos y los filtros van sincronizados, porque los widgets
 * pintan fuera del hilo principal.
 */
class HabiArt internal constructor(
    private val resources: Resources,
    maxBytes: Int = CACHE_BYTES,
) {
    private val images =
        object : LruCache<Long, ImageBitmap>(maxBytes) {
            override fun sizeOf(
                key: Long,
                value: ImageBitmap,
            ): Int = value.width * value.height * BYTES_PER_PIXEL
        }

    // Una entrada por combinación de tintes: la acotan la tabla de tintes y los tonos de las escenas.
    private val filters = HashMap<List<ColorTint>, ColorFilter>()

    /** La pieza [res] decodificada con submuestreo [sample] (1, 2, 4…). Null si no se puede decodificar. */
    fun image(
        res: Int,
        sample: Int = 1,
    ): ImageBitmap? {
        val key = (res.toLong() shl SAMPLE_BITS) or sample.toLong()
        images.get(key)?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = runCatching { BitmapFactory.decodeResource(resources, res, options) }.getOrNull() ?: return null
        return bitmap.asImageBitmap().also { images.put(key, it) }
    }

    @Synchronized
    fun filter(tints: List<ColorTint>): ColorFilter =
        filters.getOrPut(tints) { ColorFilter.colorMatrix(ColorMatrix(combinedMatrix(tints))) }

    /** Bytes ocupados ahora mismo por la caché de imágenes. */
    internal val cachedBytes: Int get() = images.size()

    companion object {
        private const val CACHE_BYTES = 48 * 1024 * 1024
        private const val BYTES_PER_PIXEL = 4
        private const val SAMPLE_BITS = 8

        @Volatile
        private var shared: HabiArt? = null

        fun get(context: Context): HabiArt =
            shared ?: synchronized(this) {
                shared ?: HabiArt(context.applicationContext.resources).also { shared = it }
            }
    }
}
