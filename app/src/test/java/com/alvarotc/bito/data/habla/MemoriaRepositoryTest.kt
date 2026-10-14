package com.alvarotc.bito.data.habla

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.LoGordoEntity
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.habla.UsoTrozo
import com.alvarotc.bito.domain.model.Personality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HOY = 20_000
private const val DIA_MS = 86_400_000L
private const val AHORA = HOY * DIA_MS + 10 * 3_600_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MemoriaRepositoryTest {
    private lateinit var db: BitoDatabase
    private lateinit var repo: MemoriaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        repo = MemoriaRepository(db) { AHORA }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun evento(
        id: String,
        dia: Int,
    ) = EventoMemoria(id, TipoMemoria.ANIMO, dia, "h1", 2, AHORA)

    private fun uso(
        indice: Int,
        combinacion: String,
        dia: Int = HOY,
        millis: Long = AHORA,
        situacion: Situacion? = Situacion.SALUDO,
    ) = UsoTrozo(Personality.NEUTRA, Bolsa.NU_SALUDO, indice, combinacion, dia, millis, situacion)

    @Test
    fun `observe emits the memory with what was noted`() =
        runTest {
            repo.anotar(evento("e1", HOY))
            repo.anotarUsos(listOf(uso(1, "c1")))
            db.hablaDao().reemplazarGordo(HOY, listOf(LoGordoEntity(HOY, 0, "HABITO", "h1", 777L)))

            val memoria = repo.observe(flowOf(HOY)).first()

            assertEquals(listOf("e1"), memoria.eventos.map { it.id })
            assertEquals(777L, memoria.gordoElegidoMillis)
            assertEquals(1, memoria.usos.size)
            assertEquals(1, memoria.mostradasHoy.size)
        }

    @Test
    fun `without big-thing rows today the chosen time is null`() =
        runTest {
            assertNull(repo.observe(flowOf(HOY)).first().gordoElegidoMillis)
        }

    @Test
    fun `purging drops events past 400 days and uses past 60 and keeps the rest`() =
        runTest {
            repo.anotar(evento("viejo", HOY - 401))
            repo.anotar(evento("limite", HOY - 400))
            repo.anotar(evento("hoy", HOY))
            repo.anotarUsos(
                listOf(
                    uso(1, "viejo", dia = HOY - 61, millis = AHORA - 61 * DIA_MS),
                    uso(2, "reciente", dia = HOY - 59, millis = AHORA - 59 * DIA_MS),
                ),
            )

            repo.purgar(HOY, AHORA)

            assertEquals(listOf("limite", "hoy"), db.hablaDao().todosEventos().map { it.id })
            val usos = db.hablaDao().usosDesde(0).first()
            assertEquals(listOf(2), usos.map { it.indice })
        }

    @Test
    fun `shown today counts one per combination of today with a situation`() =
        runTest {
            repo.anotarUsos(
                listOf(
                    uso(1, "c1"),
                    uso(2, "c1"),
                    uso(3, "c2"),
                    uso(4, "c3", situacion = null),
                    uso(5, "c4", dia = HOY - 1, millis = AHORA - 1000),
                ),
            )

            val memoria = repo.observe(flowOf(HOY)).first()

            assertEquals(2, memoria.mostradasHoy.size)
            assertEquals(5, memoria.usos.size)
        }
}
