package com.alvarotc.bito.ui.sistema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Radios del catálogo §1.3. */
object BitoFormas {
    val canto = RoundedCornerShape(percent = 50)
    val tarjeta = RoundedCornerShape(24.dp)
    val tarjetaArriba = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val campo = RoundedCornerShape(16.dp)
    val globo = RoundedCornerShape(14.dp)
    val hornacina = RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
}

/** Espaciado y medidas del catálogo §1.3 y §1.4. */
object Medidas {
    val linea = 1.5.dp
    val costra = 6.dp
    val e4 = 4.dp
    val e8 = 8.dp
    val e12 = 12.dp
    val e16 = 16.dp
    val e18 = 18.dp
    val e24 = 24.dp
    val margen = 16.dp
    val toqueMinimo = 44.dp
    val entreFilas = 12.dp
    val boton = 40.dp
    val botonHoja = 48.dp
    val salidaSuave = 44.dp
    val mas = 56.dp
    val hornacinaArmario = 236.dp
    val hornacinaPersonalidad = DpSize(236.dp, 220.dp)
}
