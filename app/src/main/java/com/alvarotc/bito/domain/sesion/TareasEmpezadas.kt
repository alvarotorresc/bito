package com.alvarotc.bito.domain.sesion

import com.alvarotc.bito.data.sesion.SesionCerrada

/**
 * Las tareas que tienen hoy una sesión: las de las filas de `sesiones` de hoy (se escriben al cerrar) y la de la sesión
 * viva, que aún no ha escrito la suya. Con ellas, Habi no dice «lo gordo sin empezar» de algo en lo que ya se está.
 */
object TareasEmpezadas {
    fun de(
        cerradasHoy: List<SesionCerrada>,
        viva: RegistroSesion?,
    ): Set<String> =
        buildSet {
            cerradasHoy.forEach { (it.objetivo as? Objetivo.Tarea)?.let { t -> add(t.id) } }
            (viva?.plan?.objetivo as? Objetivo.Tarea)?.let { add(it.id) }
        }
}
