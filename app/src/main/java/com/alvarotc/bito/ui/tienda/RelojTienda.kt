package com.alvarotc.bito.ui.tienda

import java.time.ZoneId

/** Reloj inyectable: la tienda no lee `System.currentTimeMillis` suelto (fecha del E2E y ventanas de venta). */
class RelojTienda(
    val ahora: () -> Long = System::currentTimeMillis,
    val zona: () -> ZoneId = ZoneId::systemDefault,
)
