// Generado por tools/arte/exportar.py. No editar a mano.
package com.alvarotc.bito.ui.camino

import com.alvarotc.bito.R

/** El arte de cada logro (pieza 3, §10.1), por `BadgeDef.id`. */
internal object LogrosArte {
    val porId: Map<String, Int> =
        mapOf(
            "streak-7" to R.drawable.logro_r1,
            "streak-30" to R.drawable.logro_r2,
            "streak-100" to R.drawable.logro_r3,
            "streak-365" to R.drawable.logro_r4,
            "perfect-day-1" to R.drawable.logro_c1,
            "perfect-days-10" to R.drawable.logro_c2,
            "perfect-days-50" to R.drawable.logro_c3,
            "perfect-days-100" to R.drawable.logro_c4,
            "perfect-week" to R.drawable.logro_c5,
            "perfect-month" to R.drawable.logro_c6,
            "first-habit" to R.drawable.logro_m1,
            "first-week" to R.drawable.logro_m2,
            "resurrection" to R.drawable.logro_m3,
            "first-freezer" to R.drawable.logro_m4,
        )

    fun de(badgeId: String): Int? = porId[badgeId]
}
