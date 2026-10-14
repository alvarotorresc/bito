package com.alvarotc.bito.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.annotation.DrawableRes
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.renderHabiBitmap
import com.alvarotc.bito.ui.today.CardKind

/** Filas de pendientes que caben en el widget Hoy antes de poner «y N más». */
internal const val FILAS_MAX = 3

/** Minutos que suma un toque en el widget de un hábito de duración (D13). */
internal const val MINUTOS_POR_TOQUE = 5

/** Nivel del aro del widget, de 0 a 8 (octavos de vuelta); nunca se pasa aunque el progreso exceda el objetivo. */
internal fun aroDeProgreso(
    hecho: Int,
    objetivo: Int,
): Int {
    if (objetivo <= 0 || hecho <= 0) return 0
    return (hecho.toLong() * 8 / objetivo).toInt().coerceIn(0, 8)
}

@DrawableRes
internal fun aroDrawable(nivel: Int): Int =
    when (nivel.coerceIn(0, 8)) {
        0 -> R.drawable.widget_aro_0
        1 -> R.drawable.widget_aro_1
        2 -> R.drawable.widget_aro_2
        3 -> R.drawable.widget_aro_3
        4 -> R.drawable.widget_aro_4
        5 -> R.drawable.widget_aro_5
        6 -> R.drawable.widget_aro_6
        7 -> R.drawable.widget_aro_7
        else -> R.drawable.widget_aro_8
    }

/** Cuántos pendientes se quedan fuera de las [FILAS_MAX] filas. */
internal fun pendientesOcultos(pendientes: Int): Int = (pendientes - FILAS_MAX).coerceAtLeast(0)

/** Habi del widget no lleva accesorios: la caché se indexa por expresión, voz y tamaño. */
internal data class ClaveHabi(val spec: HabiSpec, val sizePx: Int)

internal fun claveHabi(
    spec: HabiSpec,
    sizePx: Int,
): ClaveHabi = ClaveHabi(spec.copy(equipped = EquippedSet()), sizePx)

/** Bitmaps de Habi ya dibujados: no se regeneran si la expresión, la voz y el tamaño no cambian. */
internal object HabiWidgetCache {
    private val cache = LruCache<ClaveHabi, Bitmap>(8)

    fun bitmap(
        context: Context,
        spec: HabiSpec,
        sizePx: Int,
    ): Bitmap {
        val clave = claveHabi(spec, sizePx)
        synchronized(cache) { cache.get(clave) }?.let { return it }
        val nuevo = renderHabiBitmap(context, clave.spec, sizePx)
        synchronized(cache) { cache.put(clave, nuevo) }
        return nuevo
    }
}

/** Nivel del aro de un hábito: contadores y duraciones por fracción; check y abstinencia, vacío o lleno. */
internal fun SingleHabitModel.Active.aro(): Int =
    when (kind) {
        CardKind.CHECK -> if (doneToday) 8 else 0
        CardKind.ABSTINENCE -> if (failed) 0 else 8
        else -> if (doneToday) 8 else aroDeProgreso(progress, target)
    }
