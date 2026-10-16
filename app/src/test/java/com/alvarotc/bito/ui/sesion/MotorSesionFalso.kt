package com.alvarotc.bito.ui.sesion

import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.ANCLA_ELAPSED
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.INICIO_PARED
import com.alvarotc.bito.domain.sesion.Instante
import com.alvarotc.bito.domain.sesion.PlanSesion
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.RelojSesion
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.domain.sesion.registroSesion
import com.alvarotc.bito.sesion.Conflicto
import com.alvarotc.bito.sesion.MotorSesion
import com.alvarotc.bito.sesion.ResultadoAccion
import kotlinx.coroutines.flow.MutableStateFlow

private val SIN_EFECTO = ResultadoAccion(bitos = 0, celebraciones = false, aviso = null)

/** Un motor a mano para probar la pantalla: apunta lo que se le pide y deja poner a mano lo que publica. */
class MotorSesionFalso : MotorSesion {
    override val instante = MutableStateFlow<Instante?>(null)
    override val registro = MutableStateFlow<RegistroSesion?>(null)
    override val borrador = MutableStateFlow<PlanSesion?>(null)

    /** Lo que `preparar` deja en el borrador. */
    var planAlPreparar: PlanSesion? = planSesion()
    val llamadas = mutableListOf<String>()

    override suspend fun preparar(desde: Destino?) {
        llamadas += "preparar:$desde"
        borrador.value = planAlPreparar
    }

    override fun cambiarBorrador(plan: PlanSesion) {
        borrador.value = plan
    }

    override suspend fun empezar(): Conflicto? = null.also { llamadas += "empezar" }

    override suspend fun seguirConLaViva() {
        llamadas += "seguirConLaViva"
    }

    override suspend fun dejarlaYEmpezar(): ResultadoAccion = SIN_EFECTO.also { llamadas += "dejarlaYEmpezar" }

    override suspend fun pausar() {
        llamadas += "pausar"
    }

    override suspend fun seguir() {
        llamadas += "seguir"
    }

    override suspend fun sumar(minutos: Int) {
        llamadas += "sumar:$minutos"
    }

    override suspend fun saltar() {
        llamadas += "saltar"
    }

    override suspend fun empezarDescanso() {
        llamadas += "empezarDescanso"
    }

    override suspend fun heTerminado(): ResultadoAccion =
        SIN_EFECTO.also {
            llamadas += "heTerminado"
            cerrar()
        }

    override suspend fun loDejo(): ResultadoAccion =
        SIN_EFECTO.also {
            llamadas += "loDejo"
            cerrar()
        }

    override suspend fun siTerminado(): ResultadoAccion =
        SIN_EFECTO.also {
            llamadas += "siTerminado"
            cerrar()
        }

    override suspend fun sigoLuego(): ResultadoAccion =
        SIN_EFECTO.also {
            llamadas += "sigoLuego"
            cerrar()
        }

    /** Como el motor real al cerrar: ya no hay registro ni instante, aunque la pantalla siga un rato. */
    private fun cerrar() {
        registro.value = null
        instante.value = null
    }

    override suspend fun otraDeCinco() {
        llamadas += "otraDeCinco"
    }

    override suspend fun volverAPreparar() {
        llamadas += "volverAPreparar"
        borrador.value = registro.value?.plan
        registro.value = null
        instante.value = null
    }

    override suspend fun recuperar() {
        llamadas += "recuperar"
    }

    /** Pone una sesión viva del [plan] en el ms [msSesion] de la sesión, calculada con el reloj de verdad. */
    fun viva(
        plan: PlanSesion = planSesion(),
        msSesion: Long = 0L,
    ): Instante {
        val r = registroSesion(plan)
        val i = RelojSesion.en(r, Ahora(INICIO_PARED + msSesion, ANCLA_ELAPSED + msSesion))
        registro.value = r
        instante.value = i
        return i
    }
}
