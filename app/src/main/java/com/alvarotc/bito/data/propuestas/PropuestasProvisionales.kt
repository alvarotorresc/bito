package com.alvarotc.bito.data.propuestas

import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habi.HabiVoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId

/**
 * Hasta la pieza 4 (decisión 11): en Hoy, el saludo de siempre con el nombre; en la ficha, el
 * comentario del ánimo. Sin acciones y sin textos nuevos.
 */
class PropuestasProvisionales(
    private val estado: Flow<DomainState>,
    private val ajustes: Flow<Settings>,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : FuentePropuestas {
    override fun propuesta(
        ambito: Ambito,
        habitId: String?,
    ): Flow<Propuesta?> =
        combine(estado, ajustes) { state, prefs ->
            val today = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())
            val mood = MoodEngine.moodOf(state, today, StatsEngine.lastActivityDay(state))
            when (ambito) {
                Ambito.HOY ->
                    Propuesta(
                        id = "saludo",
                        texto = TextoHabi(HabiVoice.greetingRes(mood, prefs.personality), listOf(nombre(prefs.userName))),
                        acciones = emptySet(),
                    )
                Ambito.FICHA ->
                    Propuesta(
                        id = "comentario:${habitId.orEmpty()}",
                        texto = TextoHabi(HabiVoice.bubbleRes(mood, prefs.personality)),
                        acciones = emptySet(),
                    )
            }
        }

    override suspend fun responder(
        id: String,
        respuesta: Respuesta,
    ) = Unit

    private fun nombre(userName: String): Any = userName.ifBlank { TextoHabi(R.string.habi_name_fallback) }
}
