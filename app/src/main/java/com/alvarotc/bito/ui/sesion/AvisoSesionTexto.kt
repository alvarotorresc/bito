package com.alvarotc.bito.ui.sesion

import android.content.res.Resources
import com.alvarotc.bito.R
import com.alvarotc.bito.sesion.AvisoSesion

/** El texto del aviso G9 que acompaña a la vuelta a Hoy (spec §16.1). */
fun AvisoSesion.texto(recursos: Resources): String =
    when (this) {
        is AvisoSesion.SigueEnMarcha -> recursos.getString(R.string.ses_aviso_sigue, resta)
        is AvisoSesion.Hecho -> recursos.getString(R.string.ses_aviso_hecho, nombre)
        is AvisoSesion.LoDejamos ->
            if (minutos > 0) {
                recursos.getString(
                    R.string.ses_aviso_lo_dejamos,
                    minutos,
                )
            } else {
                recursos.getString(R.string.ses_aviso_otra_vez_sera)
            }
        AvisoSesion.QuedaParaLuego -> recursos.getString(R.string.ses_aviso_luego)
        is AvisoSesion.ComoIntento -> recursos.getString(R.string.ses_aviso_intento, nombre)
    }
