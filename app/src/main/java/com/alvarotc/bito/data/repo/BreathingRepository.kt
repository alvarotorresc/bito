package com.alvarotc.bito.data.repo

import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.BreathingSessionEntity
import com.alvarotc.bito.domain.model.BreathingMode
import java.util.UUID

/**
 * La unica escritura de respiracion: guardar una sesion ya terminada. No pasa por el
 * PointsReconciler porque respirar no entra en la economia (D1). [newId] es inyectable para que
 * un test fije el id.
 */
class BreathingRepository(
    private val db: BitoDatabase,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun save(
        mode: BreathingMode,
        startedAtMillis: Long,
        durationSeconds: Int,
        completed: Boolean,
    ) = db.breathingSessionDao().insert(BreathingSessionEntity(newId(), mode, startedAtMillis, durationSeconds, completed))
}
