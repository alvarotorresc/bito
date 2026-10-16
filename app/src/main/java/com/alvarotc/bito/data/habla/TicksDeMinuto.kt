package com.alvarotc.bito.data.habla

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val MINUTO_MS = 60_000L

/**
 * Un tick al empezar a escuchar y otro en cada cambio de minuto de [now], mientras alguien escuche: es
 * un flujo frío, así que sin nadie que lo recoja no hay bucle. La espera se mide cada vez contra [now],
 * para quedar alineado al minuto aunque el proceso se haya dormido.
 */
fun ticksDeMinuto(now: () -> Long): Flow<Unit> =
    flow {
        while (true) {
            emit(Unit)
            delay(MINUTO_MS - Math.floorMod(now(), MINUTO_MS))
        }
    }
