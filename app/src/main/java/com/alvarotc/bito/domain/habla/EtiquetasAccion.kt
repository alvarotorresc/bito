package com.alvarotc.bito.domain.habla

import androidx.annotation.StringRes
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.propuestas.Accion
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi

/** La etiqueta de cada botón según la situación (spec §5.1, §5.7, §7.1). */
object EtiquetasAccion {
    /**
     * Las acciones con su etiqueta (recurso `habla_*` de strings_habla_fijos.xml). La primera,
     * principal, salvo «Otra hora» en la confirmación sin lo gordo, que va como texto. Los
     * argumentos son [ValorHueco]: la hora y el hábito se formatean en la UI.
     */
    fun de(
        situacion: Situacion,
        acciones: List<Respuesta>,
        hora: Int?,
        huecos: Map<Hueco, ValorHueco>,
    ): List<Accion> =
        acciones.mapIndexed { i, r ->
            val comoTexto = situacion == Situacion.CONFIRMA_VALE && r == Respuesta.OTRA_HORA
            Accion(r, etiqueta(situacion, r, hora, huecos), principal = i == 0 && !comoTexto)
        }

    private fun etiqueta(
        situacion: Situacion,
        r: Respuesta,
        hora: Int?,
        huecos: Map<Hueco, ValorHueco>,
    ): TextoHabi =
        when (r) {
            Respuesta.VALE -> vale(situacion, hora, huecos)
            Respuesta.AHORA -> recurso(ahora(situacion))
            Respuesta.HECHO -> recurso(R.string.habla_hecho)
            Respuesta.CERRAR_DIA -> recurso(R.string.habla_cerrar_dia)
            Respuesta.ELEGIR_GORDO -> recurso(R.string.habla_elegir)
            Respuesta.OTRA_HORA -> recurso(R.string.habla_otra_hora)
            Respuesta.LUEGO -> recurso(luego(situacion))
            Respuesta.HOY_NO -> recurso(hoyNo(situacion))
        }

    private fun vale(
        situacion: Situacion,
        hora: Int?,
        huecos: Map<Hueco, ValorHueco>,
    ): TextoHabi {
        val habito = huecos[Hueco.HABITO]
        return when {
            situacion == Situacion.PERMISO_AVISOS -> recurso(R.string.habla_permitir)
            hora != null -> TextoHabi.Recurso(R.string.habla_vale_a_las, listOf(ValorHueco.Hora(hora)))
            situacion == Situacion.DIA_DURO && habito != null -> TextoHabi.Recurso(R.string.habla_vale_habito, listOf(habito))
            else -> recurso(R.string.habla_vale)
        }
    }

    @StringRes
    private fun ahora(situacion: Situacion): Int =
        when (situacion) {
            Situacion.GORDO_SIN_EMPEZAR -> R.string.habla_empezar_25
            Situacion.CONFIRMA_VALE, Situacion.GORDO_ELEGIDO -> R.string.habla_sesion_25
            Situacion.TAREA_VENCE -> R.string.habla_empezar
            else -> R.string.habla_ahora
        }

    @StringRes
    private fun luego(situacion: Situacion): Int =
        when (situacion) {
            Situacion.CERRAR_DIA, Situacion.TODO_HECHO -> R.string.habla_todavia_no
            else -> R.string.habla_luego
        }

    @StringRes
    private fun hoyNo(situacion: Situacion): Int =
        when {
            situacion == Situacion.PERMISO_AVISOS || situacion == Situacion.VUELTA || situacion == Situacion.RACHA_CERCA ->
                R.string.habla_ahora_no
            situacion.ambito == Ambito.FICHA -> R.string.habla_no_hace_falta
            else -> R.string.task_not_today
        }

    private fun recurso(
        @StringRes res: Int,
    ): TextoHabi = TextoHabi.Recurso(res)
}
