package com.alvarotc.bito.ui.tienda

import android.content.res.Resources
import com.alvarotc.bito.R

/** Las frases de precio que comparten la tienda y el resto de la app: una sola fuente. */
object TextoPrecio {
    /** «Cuesta 120 bitos y tienes 50: te faltan 70. …»: la misma en Habi y en Jardín. */
    fun sinSaldo(
        res: Resources,
        precio: Int,
        tienes: Int,
    ): String = res.getString(R.string.store_bar_missing, precio, tienes, precio - tienes)
}
