package com.alvarotc.bito.ui.notifications

/**
 * Los avisos de Habi (la mañana, la noche y los aceptados). Lo llaman el cierre y la fuente tras sellar o
 * responder, y la restauración antes de vaciar `aviso_aceptado`. Vive en `ui/notifications` aunque la fuente de
 * `data/habla` lo reciba: es una excepción conocida de dependencia data → ui (ruling P26 del plan).
 */
interface AvisosHabi {
    /** Tras sellar, responder, marcar o cambiar ajustes. */
    suspend fun replanificar()

    /** Antes de vaciar `aviso_aceptado` al restaurar. */
    suspend fun cancelarAceptados(ids: List<String>)
}

/** Sin avisos de Habi: lo que se cablea hasta que existan los de Android, y lo que usan los tests. */
object AvisosHabiNulo : AvisosHabi {
    override suspend fun replanificar() = Unit

    override suspend fun cancelarAceptados(ids: List<String>) = Unit
}
