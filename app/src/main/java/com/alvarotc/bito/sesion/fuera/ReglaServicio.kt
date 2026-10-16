package com.alvarotc.bito.sesion.fuera

import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.sesion.Ahora
import com.alvarotc.bito.domain.sesion.Instante
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.Tramo

/**
 * Cuándo existe el servicio `mediaPlayback` de la sesión (spec pieza 5 §11.2, Review Focus 5), en un solo sitio. La
 * usan lo de fuera con la app delante (avisado por el motor), lo de fuera con la app fuera cuando cambia el audio y el
 * receptor en cada frontera, así que no pueden decidir distinto.
 *
 * Sigue mientras suena algo de la sesión (aire o fondo), mientras el fondo está pedido y aún sin confirmar (cruzar a un
 * foco no lo para y vuelve a arrancar) y durante un descanso si la sesión tiene fondo (ahí se calla a propósito y
 * vuelve en el foco siguiente). Fuera de eso se suelta. Solo arranca cuando algo suena y con la app delante.
 */
internal object ReglaServicio {
    fun sigue(
        actual: Tramo?,
        fondo: SessionBackground,
        suena: Boolean,
        fondoPendiente: Boolean,
    ): Boolean = actual != null && (suena || fondoPendiente || (actual is Tramo.Descanso && fondo != SessionBackground.NONE))

    fun arranca(
        actual: Tramo?,
        suena: Boolean,
    ): Boolean = actual != null && suena

    /**
     * Pone el servicio como pide la regla: lo suelta si no debe seguir (eso se puede desde segundo plano) y lo arranca
     * solo si algo suena, la app está delante y quien llama puede arrancarlo ([puedeArrancar]: el receptor y el camino de
     * la app fuera nunca, aunque la app vuelva justo mientras deciden). Devuelve si lo ha soltado: su aviso se va con él.
     */
    suspend fun aplicar(
        e: EntornoFuera,
        r: RegistroSesion,
        instante: Instante,
        ahora: Ahora,
        suena: Boolean,
        fondoPendiente: Boolean,
        puedeArrancar: Boolean = true,
    ): Boolean {
        val servicio = e.servicio
        if (servicio.pedido()) {
            if (sigue(instante.actual, r.plan.fondo, suena, fondoPendiente)) return false
            servicio.parar()
            return true
        }
        if (puedeArrancar && arranca(instante.actual, suena) && e.visible()) {
            val aviso = PresenciaSesion.construir(e.context, r, ahora, e.titulo(r.plan.objetivo), e.voz())
            if (aviso != null) servicio.arrancar(aviso, null)
        }
        return false
    }
}
