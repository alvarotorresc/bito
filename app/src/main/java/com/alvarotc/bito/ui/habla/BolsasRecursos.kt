package com.alvarotc.bito.ui.habla

import androidx.annotation.ArrayRes
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.model.Personality

/**
 * Tabla explícita bolsa × voz → `string-array` (spec §4.4). Sin `getIdentifier`, para que la reducción de
 * recursos no borre los arrays. `keep_habla.xml` los conserva igualmente. Los nombres siguen
 * `habla_<bolsa>_<voz>` y viven en `strings_habla_<archivo>.xml`.
 */
object BolsasRecursos {
    @ArrayRes
    fun array(
        bolsa: Bolsa,
        voz: Personality,
    ): Int =
        when (bolsa) {
            Bolsa.AP_MANANA ->
                trio(voz, R.array.habla_ap_manana_sargento, R.array.habla_ap_manana_cheerleader, R.array.habla_ap_manana_neutra)
            Bolsa.AP_NOCHE ->
                trio(voz, R.array.habla_ap_noche_sargento, R.array.habla_ap_noche_cheerleader, R.array.habla_ap_noche_neutra)
            Bolsa.AP_MEDIODIA ->
                trio(voz, R.array.habla_ap_mediodia_sargento, R.array.habla_ap_mediodia_cheerleader, R.array.habla_ap_mediodia_neutra)
            Bolsa.AP_TARDE ->
                trio(voz, R.array.habla_ap_tarde_sargento, R.array.habla_ap_tarde_cheerleader, R.array.habla_ap_tarde_neutra)
            Bolsa.CI_EMPUJE ->
                trio(voz, R.array.habla_ci_empuje_sargento, R.array.habla_ci_empuje_cheerleader, R.array.habla_ci_empuje_neutra)
            Bolsa.CI_CALMA ->
                trio(voz, R.array.habla_ci_calma_sargento, R.array.habla_ci_calma_cheerleader, R.array.habla_ci_calma_neutra)
            Bolsa.RE_VUELTA ->
                trio(voz, R.array.habla_re_vuelta_sargento, R.array.habla_re_vuelta_cheerleader, R.array.habla_re_vuelta_neutra)
            Bolsa.RE_DURO_PASADO ->
                trio(
                    voz,
                    R.array.habla_re_duro_pasado_sargento,
                    R.array.habla_re_duro_pasado_cheerleader,
                    R.array.habla_re_duro_pasado_neutra,
                )
            Bolsa.RE_RACHA_RECUPERADA ->
                trio(
                    voz,
                    R.array.habla_re_racha_recuperada_sargento,
                    R.array.habla_re_racha_recuperada_cheerleader,
                    R.array.habla_re_racha_recuperada_neutra,
                )
            Bolsa.RE_CUMPLISTE_HORA ->
                trio(
                    voz,
                    R.array.habla_re_cumpliste_hora_sargento,
                    R.array.habla_re_cumpliste_hora_cheerleader,
                    R.array.habla_re_cumpliste_hora_neutra,
                )
            Bolsa.RE_GORDO_HECHO ->
                trio(
                    voz,
                    R.array.habla_re_gordo_hecho_sargento,
                    R.array.habla_re_gordo_hecho_cheerleader,
                    R.array.habla_re_gordo_hecho_neutra,
                )
            Bolsa.RE_BIEN_AYER ->
                trio(voz, R.array.habla_re_bien_ayer_sargento, R.array.habla_re_bien_ayer_cheerleader, R.array.habla_re_bien_ayer_neutra)
            Bolsa.RE_PATRON_ANIMO ->
                trio(
                    voz,
                    R.array.habla_re_patron_animo_sargento,
                    R.array.habla_re_patron_animo_cheerleader,
                    R.array.habla_re_patron_animo_neutra,
                )
            Bolsa.NU_AYER_ESCAPO ->
                trio(
                    voz,
                    R.array.habla_nu_ayer_escapo_sargento,
                    R.array.habla_nu_ayer_escapo_cheerleader,
                    R.array.habla_nu_ayer_escapo_neutra,
                )
            Bolsa.NU_ELEGIR_GORDO ->
                trio(
                    voz,
                    R.array.habla_nu_elegir_gordo_sargento,
                    R.array.habla_nu_elegir_gordo_cheerleader,
                    R.array.habla_nu_elegir_gordo_neutra,
                )
            Bolsa.NU_GORDO_SIN_EMPEZAR ->
                trio(
                    voz,
                    R.array.habla_nu_gordo_sin_empezar_sargento,
                    R.array.habla_nu_gordo_sin_empezar_cheerleader,
                    R.array.habla_nu_gordo_sin_empezar_neutra,
                )
            Bolsa.NU_HORA_HABITUAL ->
                trio(
                    voz,
                    R.array.habla_nu_hora_habitual_sargento,
                    R.array.habla_nu_hora_habitual_cheerleader,
                    R.array.habla_nu_hora_habitual_neutra,
                )
            Bolsa.NU_VUELTA ->
                trio(voz, R.array.habla_nu_vuelta_sargento, R.array.habla_nu_vuelta_cheerleader, R.array.habla_nu_vuelta_neutra)
            Bolsa.NU_DIA_DURO ->
                trio(voz, R.array.habla_nu_dia_duro_sargento, R.array.habla_nu_dia_duro_cheerleader, R.array.habla_nu_dia_duro_neutra)
            Bolsa.NU_RACHA_CERCA ->
                trio(
                    voz,
                    R.array.habla_nu_racha_cerca_sargento,
                    R.array.habla_nu_racha_cerca_cheerleader,
                    R.array.habla_nu_racha_cerca_neutra,
                )
            Bolsa.NU_DIA_FLOJO ->
                trio(voz, R.array.habla_nu_dia_flojo_sargento, R.array.habla_nu_dia_flojo_cheerleader, R.array.habla_nu_dia_flojo_neutra)
            Bolsa.NU_TAREA_VENCE ->
                trio(
                    voz,
                    R.array.habla_nu_tarea_vence_sargento,
                    R.array.habla_nu_tarea_vence_cheerleader,
                    R.array.habla_nu_tarea_vence_neutra,
                )
            Bolsa.NU_REGULAR ->
                trio(voz, R.array.habla_nu_regular_sargento, R.array.habla_nu_regular_cheerleader, R.array.habla_nu_regular_neutra)
            Bolsa.NU_PERMISO_AVISOS ->
                trio(
                    voz,
                    R.array.habla_nu_permiso_avisos_sargento,
                    R.array.habla_nu_permiso_avisos_cheerleader,
                    R.array.habla_nu_permiso_avisos_neutra,
                )
            Bolsa.NU_PERMISO_ALARMAS ->
                trio(
                    voz,
                    R.array.habla_nu_permiso_alarmas_sargento,
                    R.array.habla_nu_permiso_alarmas_cheerleader,
                    R.array.habla_nu_permiso_alarmas_neutra,
                )
            Bolsa.NU_SALUDO ->
                trio(voz, R.array.habla_nu_saludo_sargento, R.array.habla_nu_saludo_cheerleader, R.array.habla_nu_saludo_neutra)
            Bolsa.NU_TODO_HECHO ->
                trio(voz, R.array.habla_nu_todo_hecho_sargento, R.array.habla_nu_todo_hecho_cheerleader, R.array.habla_nu_todo_hecho_neutra)
            Bolsa.NU_CERRAR_DIA ->
                trio(voz, R.array.habla_nu_cerrar_dia_sargento, R.array.habla_nu_cerrar_dia_cheerleader, R.array.habla_nu_cerrar_dia_neutra)
            Bolsa.NU_NOCHE ->
                trio(voz, R.array.habla_nu_noche_sargento, R.array.habla_nu_noche_cheerleader, R.array.habla_nu_noche_neutra)
            Bolsa.NU_TRAS_HOY_NO ->
                trio(
                    voz,
                    R.array.habla_nu_tras_hoy_no_sargento,
                    R.array.habla_nu_tras_hoy_no_cheerleader,
                    R.array.habla_nu_tras_hoy_no_neutra,
                )
            Bolsa.NU_CONFIRMA_VALE ->
                trio(
                    voz,
                    R.array.habla_nu_confirma_vale_sargento,
                    R.array.habla_nu_confirma_vale_cheerleader,
                    R.array.habla_nu_confirma_vale_neutra,
                )
            Bolsa.NU_CONFIRMA_VALE_GORDO ->
                trio(
                    voz,
                    R.array.habla_nu_confirma_vale_gordo_sargento,
                    R.array.habla_nu_confirma_vale_gordo_cheerleader,
                    R.array.habla_nu_confirma_vale_gordo_neutra,
                )
            Bolsa.NU_GORDO_ELEGIDO ->
                trio(
                    voz,
                    R.array.habla_nu_gordo_elegido_sargento,
                    R.array.habla_nu_gordo_elegido_cheerleader,
                    R.array.habla_nu_gordo_elegido_neutra,
                )
            Bolsa.FI_HOY_FALTA ->
                trio(voz, R.array.habla_fi_hoy_falta_sargento, R.array.habla_fi_hoy_falta_cheerleader, R.array.habla_fi_hoy_falta_neutra)
            Bolsa.FI_OBJETIVO ->
                trio(voz, R.array.habla_fi_objetivo_sargento, R.array.habla_fi_objetivo_cheerleader, R.array.habla_fi_objetivo_neutra)
            Bolsa.FI_RACHA_VS_MEJOR ->
                trio(
                    voz,
                    R.array.habla_fi_racha_vs_mejor_sargento,
                    R.array.habla_fi_racha_vs_mejor_cheerleader,
                    R.array.habla_fi_racha_vs_mejor_neutra,
                )
            Bolsa.FI_MEJOR_DIA ->
                trio(voz, R.array.habla_fi_mejor_dia_sargento, R.array.habla_fi_mejor_dia_cheerleader, R.array.habla_fi_mejor_dia_neutra)
            Bolsa.FI_CONSTANCIA ->
                trio(voz, R.array.habla_fi_constancia_sargento, R.array.habla_fi_constancia_cheerleader, R.array.habla_fi_constancia_neutra)
            Bolsa.FI_NUEVO ->
                trio(voz, R.array.habla_fi_nuevo_sargento, R.array.habla_fi_nuevo_cheerleader, R.array.habla_fi_nuevo_neutra)
            Bolsa.FI_LIMPIO ->
                trio(voz, R.array.habla_fi_limpio_sargento, R.array.habla_fi_limpio_cheerleader, R.array.habla_fi_limpio_neutra)
            Bolsa.FI_PAUSA ->
                trio(voz, R.array.habla_fi_pausa_sargento, R.array.habla_fi_pausa_cheerleader, R.array.habla_fi_pausa_neutra)
            Bolsa.CA_RADIANTE ->
                trio(voz, R.array.habla_ca_radiante_sargento, R.array.habla_ca_radiante_cheerleader, R.array.habla_ca_radiante_neutra)
            Bolsa.CA_NORMAL ->
                trio(voz, R.array.habla_ca_normal_sargento, R.array.habla_ca_normal_cheerleader, R.array.habla_ca_normal_neutra)
            Bolsa.CA_MUSTIO ->
                trio(voz, R.array.habla_ca_mustio_sargento, R.array.habla_ca_mustio_cheerleader, R.array.habla_ca_mustio_neutra)
            Bolsa.CA_AUSENCIA ->
                trio(voz, R.array.habla_ca_ausencia_sargento, R.array.habla_ca_ausencia_cheerleader, R.array.habla_ca_ausencia_neutra)
            Bolsa.CIE_GORDO_HECHO ->
                trio(
                    voz,
                    R.array.habla_cie_gordo_hecho_sargento,
                    R.array.habla_cie_gordo_hecho_cheerleader,
                    R.array.habla_cie_gordo_hecho_neutra,
                )
            Bolsa.CIE_LOGRO ->
                trio(voz, R.array.habla_cie_logro_sargento, R.array.habla_cie_logro_cheerleader, R.array.habla_cie_logro_neutra)
            Bolsa.CIE_RACHA_AVANZA ->
                trio(
                    voz,
                    R.array.habla_cie_racha_avanza_sargento,
                    R.array.habla_cie_racha_avanza_cheerleader,
                    R.array.habla_cie_racha_avanza_neutra,
                )
            Bolsa.CIE_SENCILLO ->
                trio(voz, R.array.habla_cie_sencillo_sargento, R.array.habla_cie_sencillo_cheerleader, R.array.habla_cie_sencillo_neutra)
            Bolsa.CIE_NADA_PENDIENTE ->
                trio(
                    voz,
                    R.array.habla_cie_nada_pendiente_sargento,
                    R.array.habla_cie_nada_pendiente_cheerleader,
                    R.array.habla_cie_nada_pendiente_neutra,
                )
            Bolsa.CIE_ANIMO_BIEN ->
                trio(
                    voz,
                    R.array.habla_cie_animo_bien_sargento,
                    R.array.habla_cie_animo_bien_cheerleader,
                    R.array.habla_cie_animo_bien_neutra,
                )
            Bolsa.CIE_ANIMO_REGULAR ->
                trio(
                    voz,
                    R.array.habla_cie_animo_regular_sargento,
                    R.array.habla_cie_animo_regular_cheerleader,
                    R.array.habla_cie_animo_regular_neutra,
                )
            Bolsa.CIE_ANIMO_DURO ->
                trio(
                    voz,
                    R.array.habla_cie_animo_duro_sargento,
                    R.array.habla_cie_animo_duro_cheerleader,
                    R.array.habla_cie_animo_duro_neutra,
                )
            Bolsa.CIE_SELLO ->
                trio(voz, R.array.habla_cie_sello_sargento, R.array.habla_cie_sello_cheerleader, R.array.habla_cie_sello_neutra)
            Bolsa.CIE_SELLO_PERFECTO ->
                trio(
                    voz,
                    R.array.habla_cie_sello_perfecto_sargento,
                    R.array.habla_cie_sello_perfecto_cheerleader,
                    R.array.habla_cie_sello_perfecto_neutra,
                )
            Bolsa.CIE_TAREAS_MANANA ->
                trio(
                    voz,
                    R.array.habla_cie_tareas_manana_sargento,
                    R.array.habla_cie_tareas_manana_cheerleader,
                    R.array.habla_cie_tareas_manana_neutra,
                )
            Bolsa.AV_MANANA_TITULO ->
                trio(
                    voz,
                    R.array.habla_av_manana_titulo_sargento,
                    R.array.habla_av_manana_titulo_cheerleader,
                    R.array.habla_av_manana_titulo_neutra,
                )
            Bolsa.AV_MANANA_TITULO_GORDO ->
                trio(
                    voz,
                    R.array.habla_av_manana_titulo_gordo_sargento,
                    R.array.habla_av_manana_titulo_gordo_cheerleader,
                    R.array.habla_av_manana_titulo_gordo_neutra,
                )
            Bolsa.AV_RECORDATORIO ->
                trio(
                    voz,
                    R.array.habla_av_recordatorio_sargento,
                    R.array.habla_av_recordatorio_cheerleader,
                    R.array.habla_av_recordatorio_neutra,
                )
            Bolsa.AV_NOCHE_TITULO ->
                trio(
                    voz,
                    R.array.habla_av_noche_titulo_sargento,
                    R.array.habla_av_noche_titulo_cheerleader,
                    R.array.habla_av_noche_titulo_neutra,
                )
            Bolsa.AV_NOCHE_CUERPO ->
                trio(
                    voz,
                    R.array.habla_av_noche_cuerpo_sargento,
                    R.array.habla_av_noche_cuerpo_cheerleader,
                    R.array.habla_av_noche_cuerpo_neutra,
                )
            Bolsa.AV_ACEPTADO_TITULO ->
                trio(
                    voz,
                    R.array.habla_av_aceptado_titulo_sargento,
                    R.array.habla_av_aceptado_titulo_cheerleader,
                    R.array.habla_av_aceptado_titulo_neutra,
                )
            Bolsa.AV_ACEPTADO_CUERPO ->
                trio(
                    voz,
                    R.array.habla_av_aceptado_cuerpo_sargento,
                    R.array.habla_av_aceptado_cuerpo_cheerleader,
                    R.array.habla_av_aceptado_cuerpo_neutra,
                )
            Bolsa.AV_AUSENCIA ->
                trio(voz, R.array.habla_av_ausencia_sargento, R.array.habla_av_ausencia_cheerleader, R.array.habla_av_ausencia_neutra)
            Bolsa.SE_ENTRAR ->
                trio(
                    voz,
                    R.array.habla_se_entrar_sargento,
                    R.array.habla_se_entrar_cheerleader,
                    R.array.habla_se_entrar_neutra,
                )
            Bolsa.DE_HABITO ->
                trio(
                    voz,
                    R.array.habla_de_habito_sargento,
                    R.array.habla_de_habito_cheerleader,
                    R.array.habla_de_habito_neutra,
                )
            Bolsa.DE_HABITO_LARGO ->
                trio(
                    voz,
                    R.array.habla_de_habito_largo_sargento,
                    R.array.habla_de_habito_largo_cheerleader,
                    R.array.habla_de_habito_largo_neutra,
                )
            Bolsa.DE_SIN_PENDIENTES ->
                trio(
                    voz,
                    R.array.habla_de_sin_pendientes_sargento,
                    R.array.habla_de_sin_pendientes_cheerleader,
                    R.array.habla_de_sin_pendientes_neutra,
                )
            Bolsa.SE_HECHA_TAREA ->
                trio(
                    voz,
                    R.array.habla_se_hecha_tarea_sargento,
                    R.array.habla_se_hecha_tarea_cheerleader,
                    R.array.habla_se_hecha_tarea_neutra,
                )
            Bolsa.SE_HECHA_HABITO ->
                trio(
                    voz,
                    R.array.habla_se_hecha_habito_sargento,
                    R.array.habla_se_hecha_habito_cheerleader,
                    R.array.habla_se_hecha_habito_neutra,
                )
            Bolsa.CE_MEJOR ->
                trio(
                    voz,
                    R.array.habla_ce_mejor_sargento,
                    R.array.habla_ce_mejor_cheerleader,
                    R.array.habla_ce_mejor_neutra,
                )
            Bolsa.CE_IGUAL ->
                trio(
                    voz,
                    R.array.habla_ce_igual_sargento,
                    R.array.habla_ce_igual_cheerleader,
                    R.array.habla_ce_igual_neutra,
                )
            Bolsa.CE_CUESTA ->
                trio(
                    voz,
                    R.array.habla_ce_cuesta_sargento,
                    R.array.habla_ce_cuesta_cheerleader,
                    R.array.habla_ce_cuesta_neutra,
                )
            Bolsa.RE_FIN_RESPIRAR ->
                trio(
                    voz,
                    R.array.habla_re_fin_respirar_sargento,
                    R.array.habla_re_fin_respirar_cheerleader,
                    R.array.habla_re_fin_respirar_neutra,
                )
            Bolsa.RE_RESPIRAR_MEJOR ->
                trio(
                    voz,
                    R.array.habla_re_respirar_mejor_sargento,
                    R.array.habla_re_respirar_mejor_cheerleader,
                    R.array.habla_re_respirar_mejor_neutra,
                )
        }

    @ArrayRes
    private fun trio(
        voz: Personality,
        sargento: Int,
        cheerleader: Int,
        neutra: Int,
    ): Int =
        when (voz) {
            Personality.SARGENTO -> sargento
            Personality.CHEERLEADER -> cheerleader
            Personality.NEUTRA -> neutra
        }
}
