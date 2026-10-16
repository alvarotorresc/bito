package com.alvarotc.bito.ui.inicio

import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.ui.sesion.MotorSesionFalso
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntradaSesionTest {
    private val motor = MotorSesionFalso()
    private val entradas = mutableListOf<Boolean>()
    private val rutas = mutableListOf<String>()
    private val inicio = AbrirSesion(motor, { entradas += it }, { rutas += it })

    @After
    fun tearDown() = EntradaSesion.consumir()

    @Test
    fun `the card's Now opens prepare with the task`() =
        runTest {
            EntradaSesion.abrir(Destino.Tarea("t1"), inicio)

            assertEquals(listOf("preparar:Tarea(id=t1)"), motor.llamadas)
            assertEquals(listOf(true), entradas)
            assertEquals(emptyList<String>(), rutas)
        }

    @Test
    fun `plus focus opens prepare with the big thing or free`() =
        runTest {
            EntradaSesion.abrir(null, inicio)
            EntradaSesion.abrir(Destino.LoGordo, inicio)

            assertEquals(listOf("preparar:null", "preparar:LoGordo"), motor.llamadas)
            assertEquals(listOf(true, true), entradas)
        }

    @Test
    fun `a habit goes through prepare and a destination with nothing to prepare enters nothing`() =
        runTest {
            EntradaSesion.abrir(Destino.Habito("h1"), inicio)
            motor.planAlPreparar = null
            motor.borrador.value = null
            EntradaSesion.abrir(Destino.Cierre, inicio)

            assertEquals(listOf("preparar:Habito(id=h1)", "preparar:Cierre"), motor.llamadas)
            assertEquals(listOf(true), entradas)
        }

    @Test
    fun `with a live session no destination leads to its state and a task to prepare`() =
        runTest {
            motor.viva(planSesion(entrada = Entrada.DIRECTO))

            EntradaSesion.abrir(null, inicio)
            assertEquals(emptyList<String>(), motor.llamadas)
            assertEquals(listOf(false), entradas)

            EntradaSesion.abrir(Destino.Tarea("t2"), inicio)
            assertEquals(listOf("preparar:Tarea(id=t2)"), motor.llamadas)
            assertEquals(listOf(false, true), entradas)
        }

    @Test
    fun `breathing keeps its route until the loose breathing is integrated`() =
        runTest {
            EntradaSesion.abrir(Destino.Respirar, inicio)

            assertEquals(listOf("breathing"), rutas)
            assertEquals(emptyList<String>(), motor.llamadas)
            assertEquals(emptyList<Boolean>(), entradas)
        }

    @Test
    fun `a request from another screen is delivered once`() {
        EntradaSesion.pedir(Destino.Tarea("t1"))
        assertEquals(Destino.Tarea("t1"), EntradaSesion.pendiente.value?.destino)

        EntradaSesion.consumir()
        assertNull(EntradaSesion.pendiente.value)

        // Dos peticiones iguales seguidas no se funden en una: cada una trae su número.
        EntradaSesion.pedir(null)
        val primera = EntradaSesion.pendiente.value!!.numero
        EntradaSesion.pedir(null)
        assertTrue(EntradaSesion.pendiente.value!!.numero > primera)
    }
}
