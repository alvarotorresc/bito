package com.alvarotc.bito.data.sesion

import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.ResultadoSesion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

/** El cableado de «tareas empezadas hoy» de la fuente de Habi, con un reloj que cruza el corte del día (RF1). */
class TareasEmpezadasHoyTest {
    private val medianoche = 20_000L * 24 * 3_600_000L
    private val ajustes = MutableStateFlow(Settings())
    private var ahora = medianoche - 60_000L

    private fun cerrada(dia: LogicalDay) = SesionCerrada("s$dia", Objetivo.Tarea("t$dia"), dia, 0, 0, 25, 1, 1, ResultadoSesion.HECHA)

    private val ayer: LogicalDay get() = LogicalDays.logicalDayOf(medianoche - 1, 0, ZoneOffset.UTC)
    private val hoy: LogicalDay get() = LogicalDays.logicalDayOf(medianoche, 0, ZoneOffset.UTC)

    private fun cerradasDe(dia: LogicalDay): Flow<List<SesionCerrada>> = flowOf(listOf(cerrada(dia)))

    @Test
    fun `after the day cut the tasks begun yesterday stop counting as begun without any setting changing`() =
        runTest {
            val ticks = MutableSharedFlow<Unit>(replay = 1)
            val vistos = mutableListOf<Set<String>>()
            val trabajo =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    tareasEmpezadasHoy(
                        ajustes = ajustes,
                        ticks = ticks,
                        ahora = { ahora },
                        zona = { ZoneOffset.UTC },
                        cerradasDe = ::cerradasDe,
                        viva = MutableStateFlow(null),
                    ).collect { vistos += it }
                }
            ticks.emit(Unit)
            assertEquals(listOf(setOf("t$ayer")), vistos.toList())

            // La app sigue abierta y el reloj cruza la medianoche: el siguiente tick de minuto cambia el día.
            ahora = medianoche + 30_000L
            ticks.emit(Unit)

            assertEquals(setOf("t$hoy"), vistos.last())
            trabajo.cancel()
        }
}
