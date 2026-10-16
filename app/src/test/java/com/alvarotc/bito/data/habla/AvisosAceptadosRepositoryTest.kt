package com.alvarotc.bito.data.habla

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.EstadoAviso
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AvisosAceptadosRepositoryTest {
    private lateinit var db: BitoDatabase
    private lateinit var repo: AvisosAceptadosRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        repo = AvisosAceptadosRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun aviso(
        id: String,
        estado: EstadoAviso = EstadoAviso.PENDIENTE,
        dia: Int = 20_000,
    ) = AvisoAceptado(id, dia, "h1", 600, 0, estado, "p-$id", 1L)

    @Test
    fun `postponing adds one to the count and changes the minute`() =
        runTest {
            repo.crear(aviso("a"))
            repo.posponer("a", 660)
            repo.posponer("a", 720)

            val a = repo.observeDe(20_000).first().single()
            assertEquals(2, a.pospuesto)
            assertEquals(720, a.minuto)
        }

    @Test
    fun `pending excludes done and cancelled and spans days`() =
        runTest {
            repo.crear(aviso("p1"))
            repo.crear(aviso("p2", dia = 19_999))
            repo.crear(aviso("h", EstadoAviso.HECHO))
            repo.crear(aviso("c", EstadoAviso.CANCELADO))
            repo.crear(aviso("l", EstadoAviso.LANZADO))

            assertEquals(listOf("p2", "p1"), repo.pendientes().map { it.id })
        }

    @Test
    fun `changing the state and emptying`() =
        runTest {
            repo.crear(aviso("a"))
            repo.cambiarEstado("a", EstadoAviso.HECHO)
            assertEquals(EstadoAviso.HECHO, repo.observeDe(20_000).first().single().estado)
            repo.vaciar()
            assertEquals(emptyList<AvisoAceptado>(), repo.observeDe(20_000).first())
        }
}
