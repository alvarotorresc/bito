package com.alvarotc.bito.data.habla

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VueltaAlRegistrarTest {
    private lateinit var db: BitoDatabase
    private lateinit var memoria: MemoriaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        memoria = MemoriaRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `si el calculo de la vuelta falla, la entrada se guarda igual`() =
        runTest {
            val vuelta = VueltaAlRegistrar(estado = { error("estado roto") }, memoria = memoria, corteDia = { 0 })
            var escrita = false
            vuelta.registrando(entryEntity()) { escrita = true }
            assertEquals(true, escrita)
        }

    @Test
    fun `si falla el corte del dia, la entrada se guarda igual`() =
        runTest {
            val vuelta = VueltaAlRegistrar(estado = { error("no se llega") }, memoria = memoria, corteDia = { error("corte roto") })
            var escrita = false
            vuelta.registrando(entryEntity()) { escrita = true }
            assertEquals(true, escrita)
        }

    @Test
    fun `la cancelacion no se traga`() {
        val vuelta = VueltaAlRegistrar(estado = { throw CancellationException("cancelada") }, memoria = memoria, corteDia = { 0 })
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { vuelta.registrando(entryEntity()) { } }
        }
    }
}
