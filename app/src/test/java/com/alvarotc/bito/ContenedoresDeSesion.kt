package com.alvarotc.bito

import org.junit.rules.ExternalResource

/**
 * Al acabar el test cancela el alcance de sesión de cada [AppContainer] que se haya registrado: el tic del motor y los
 * colectores de ajustes del audio no sobreviven al test ni leen archivos de contenedores que ya no existen (revisión
 * final, I3). Se registra todo contenedor que monte un test que llegue al motor, al aire o al fondo.
 */
class ContenedoresDeSesion : ExternalResource() {
    private val contenedores = mutableListOf<AppContainer>()

    fun <T : AppContainer> registrar(contenedor: T): T = contenedor.also { contenedores += it }

    override fun after() {
        contenedores.forEach { it.cerrarSesion() }
        contenedores.clear()
    }
}
