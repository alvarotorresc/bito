package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.model.Personality
import java.time.DayOfWeek

/** Los huecos con nombre que una frase rellena. */
enum class Hueco(
    val clave: String,
) {
    NOMBRE("nombre"),
    HABITO("habito"),
    TAREA("tarea"),
    GORDO("gordo"),
    PRIMER_PASO("primer_paso"),
    HORA("hora"),
    MINUTOS("minutos"),
    N("n"),
    RACHA("racha"),
    FALTAN("faltan"),
    BITOS("bitos"),
    DIA_SEMANA("dia_semana"),
    DIAS_FUERA("dias_fuera"),
    HECHOS("hechos"),
    TOTAL("total"),
    MEJOR("mejor"),
    PCT("pct"),
    OBJETIVO("objetivo"),
    FECHA("fecha"),
    LISTA("lista"),
    MODO("modo"),
}

/** Tipos de trozo con los que se compone una frase. */
enum class TipoTrozo { APERTURA, NUCLEO, CIERRE, RECUERDO, SUELTA }

/**
 * Las bolsas de trozos de Habi (spec §8.1), una por fila y en el mismo orden. La fuente única es
 * `tools/habla/bolsas.json`; `BolsasJsonTest` comprueba que coinciden. [clave] es el nombre de la
 * bolsa y el de sus `string-array` (`habla_<clave>_<voz>`); [huecos], los que admiten sus items;
 * [minimo], los items que pide cada voz.
 */
enum class Bolsa(
    val clave: String,
    val tipo: TipoTrozo,
    val huecos: Set<Hueco>,
    val minimo: Int,
) {
    AP_MANANA("ap_manana", TipoTrozo.APERTURA, setOf(Hueco.NOMBRE), 10),
    AP_NOCHE("ap_noche", TipoTrozo.APERTURA, setOf(Hueco.NOMBRE), 10),
    AP_MEDIODIA("ap_mediodia", TipoTrozo.APERTURA, setOf(Hueco.NOMBRE), 8),
    AP_TARDE("ap_tarde", TipoTrozo.APERTURA, setOf(Hueco.NOMBRE), 8),
    CI_EMPUJE("ci_empuje", TipoTrozo.CIERRE, emptySet(), 10),
    CI_CALMA("ci_calma", TipoTrozo.CIERRE, emptySet(), 10),
    RE_VUELTA("re_vuelta", TipoTrozo.RECUERDO, setOf(Hueco.HABITO, Hueco.N, Hueco.RACHA, Hueco.HORA, Hueco.TAREA), 5),
    RE_DURO_PASADO("re_duro_pasado", TipoTrozo.RECUERDO, setOf(Hueco.HABITO, Hueco.N, Hueco.RACHA, Hueco.HORA, Hueco.TAREA), 5),
    RE_RACHA_RECUPERADA("re_racha_recuperada", TipoTrozo.RECUERDO, setOf(Hueco.HABITO, Hueco.N, Hueco.RACHA, Hueco.HORA, Hueco.TAREA), 5),
    RE_CUMPLISTE_HORA("re_cumpliste_hora", TipoTrozo.RECUERDO, setOf(Hueco.HABITO, Hueco.N, Hueco.RACHA, Hueco.HORA, Hueco.TAREA), 5),
    RE_GORDO_HECHO("re_gordo_hecho", TipoTrozo.RECUERDO, setOf(Hueco.HABITO, Hueco.N, Hueco.RACHA, Hueco.HORA, Hueco.TAREA), 5),
    RE_BIEN_AYER("re_bien_ayer", TipoTrozo.RECUERDO, setOf(Hueco.HABITO, Hueco.N, Hueco.RACHA, Hueco.HORA, Hueco.TAREA), 5),
    RE_PATRON_ANIMO("re_patron_animo", TipoTrozo.RECUERDO, setOf(Hueco.DIA_SEMANA), 4),
    NU_AYER_ESCAPO("nu_ayer_escapo", TipoTrozo.NUCLEO, setOf(Hueco.HABITO, Hueco.MINUTOS, Hueco.HORA), 12),
    NU_ELEGIR_GORDO("nu_elegir_gordo", TipoTrozo.NUCLEO, setOf(Hueco.TAREA, Hueco.GORDO, Hueco.PRIMER_PASO, Hueco.HABITO, Hueco.HORA), 10),
    NU_GORDO_SIN_EMPEZAR(
        "nu_gordo_sin_empezar",
        TipoTrozo.NUCLEO,
        setOf(Hueco.TAREA, Hueco.GORDO, Hueco.PRIMER_PASO, Hueco.HABITO, Hueco.HORA),
        10,
    ),
    NU_HORA_HABITUAL(
        "nu_hora_habitual",
        TipoTrozo.NUCLEO,
        setOf(Hueco.TAREA, Hueco.GORDO, Hueco.PRIMER_PASO, Hueco.HABITO, Hueco.HORA),
        10,
    ),
    NU_VUELTA(
        "nu_vuelta",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.DIAS_FUERA, Hueco.RACHA, Hueco.FALTAN, Hueco.BITOS, Hueco.DIA_SEMANA, Hueco.TAREA),
        8,
    ),
    NU_DIA_DURO(
        "nu_dia_duro",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.DIAS_FUERA, Hueco.RACHA, Hueco.FALTAN, Hueco.BITOS, Hueco.DIA_SEMANA, Hueco.TAREA),
        8,
    ),
    NU_RACHA_CERCA(
        "nu_racha_cerca",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.DIAS_FUERA, Hueco.RACHA, Hueco.FALTAN, Hueco.BITOS, Hueco.DIA_SEMANA, Hueco.TAREA),
        8,
    ),
    NU_DIA_FLOJO(
        "nu_dia_flojo",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.DIAS_FUERA, Hueco.RACHA, Hueco.FALTAN, Hueco.BITOS, Hueco.DIA_SEMANA, Hueco.TAREA),
        8,
    ),
    NU_TAREA_VENCE(
        "nu_tarea_vence",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.DIAS_FUERA, Hueco.RACHA, Hueco.FALTAN, Hueco.BITOS, Hueco.DIA_SEMANA, Hueco.TAREA),
        8,
    ),
    NU_REGULAR("nu_regular", TipoTrozo.NUCLEO, setOf(Hueco.HABITO), 6),
    NU_PERMISO_AVISOS("nu_permiso_avisos", TipoTrozo.NUCLEO, emptySet(), 3),
    NU_PERMISO_ALARMAS("nu_permiso_alarmas", TipoTrozo.NUCLEO, emptySet(), 3),
    NU_SALUDO("nu_saludo", TipoTrozo.NUCLEO, setOf(Hueco.NOMBRE, Hueco.HECHOS, Hueco.TOTAL), 12),
    NU_TODO_HECHO("nu_todo_hecho", TipoTrozo.NUCLEO, setOf(Hueco.N, Hueco.TAREA), 10),
    NU_CERRAR_DIA("nu_cerrar_dia", TipoTrozo.NUCLEO, setOf(Hueco.N, Hueco.TAREA), 10),
    NU_NOCHE("nu_noche", TipoTrozo.NUCLEO, setOf(Hueco.N, Hueco.TAREA), 10),
    NU_TRAS_HOY_NO("nu_tras_hoy_no", TipoTrozo.NUCLEO, setOf(Hueco.N, Hueco.TAREA), 10),
    NU_CONFIRMA_VALE("nu_confirma_vale", TipoTrozo.NUCLEO, setOf(Hueco.HORA, Hueco.GORDO), 8),
    NU_CONFIRMA_VALE_GORDO("nu_confirma_vale_gordo", TipoTrozo.NUCLEO, setOf(Hueco.HORA, Hueco.GORDO), 8),
    NU_GORDO_ELEGIDO("nu_gordo_elegido", TipoTrozo.NUCLEO, setOf(Hueco.GORDO, Hueco.PRIMER_PASO), 6),
    FI_HOY_FALTA(
        "fi_hoy_falta",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.RACHA, Hueco.MEJOR, Hueco.PCT, Hueco.DIA_SEMANA, Hueco.N, Hueco.HORA, Hueco.OBJETIVO),
        6,
    ),
    FI_OBJETIVO(
        "fi_objetivo",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.RACHA, Hueco.MEJOR, Hueco.PCT, Hueco.DIA_SEMANA, Hueco.N, Hueco.HORA, Hueco.OBJETIVO),
        6,
    ),
    FI_RACHA_VS_MEJOR(
        "fi_racha_vs_mejor",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.RACHA, Hueco.MEJOR, Hueco.PCT, Hueco.DIA_SEMANA, Hueco.N, Hueco.HORA, Hueco.OBJETIVO),
        6,
    ),
    FI_MEJOR_DIA(
        "fi_mejor_dia",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.RACHA, Hueco.MEJOR, Hueco.PCT, Hueco.DIA_SEMANA, Hueco.N, Hueco.HORA, Hueco.OBJETIVO),
        6,
    ),
    FI_CONSTANCIA(
        "fi_constancia",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.RACHA, Hueco.MEJOR, Hueco.PCT, Hueco.DIA_SEMANA, Hueco.N, Hueco.HORA, Hueco.OBJETIVO),
        6,
    ),
    FI_NUEVO(
        "fi_nuevo",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.RACHA, Hueco.MEJOR, Hueco.PCT, Hueco.DIA_SEMANA, Hueco.N, Hueco.HORA, Hueco.OBJETIVO),
        6,
    ),
    FI_LIMPIO(
        "fi_limpio",
        TipoTrozo.NUCLEO,
        setOf(Hueco.HABITO, Hueco.RACHA, Hueco.MEJOR, Hueco.PCT, Hueco.DIA_SEMANA, Hueco.N, Hueco.HORA, Hueco.OBJETIVO),
        6,
    ),
    FI_PAUSA("fi_pausa", TipoTrozo.NUCLEO, setOf(Hueco.FECHA), 4),
    CA_RADIANTE("ca_radiante", TipoTrozo.NUCLEO, setOf(Hueco.N, Hueco.PCT), 6),
    CA_NORMAL("ca_normal", TipoTrozo.NUCLEO, setOf(Hueco.N, Hueco.PCT), 6),
    CA_MUSTIO("ca_mustio", TipoTrozo.NUCLEO, setOf(Hueco.N, Hueco.PCT), 6),
    CA_AUSENCIA("ca_ausencia", TipoTrozo.NUCLEO, setOf(Hueco.N, Hueco.PCT), 6),
    CIE_GORDO_HECHO("cie_gordo_hecho", TipoTrozo.NUCLEO, setOf(Hueco.NOMBRE, Hueco.HABITO, Hueco.RACHA, Hueco.GORDO), 6),
    CIE_LOGRO("cie_logro", TipoTrozo.NUCLEO, setOf(Hueco.NOMBRE, Hueco.HABITO, Hueco.RACHA, Hueco.GORDO), 6),
    CIE_RACHA_AVANZA("cie_racha_avanza", TipoTrozo.NUCLEO, setOf(Hueco.NOMBRE, Hueco.HABITO, Hueco.RACHA, Hueco.GORDO), 6),
    CIE_SENCILLO("cie_sencillo", TipoTrozo.NUCLEO, setOf(Hueco.NOMBRE, Hueco.HABITO, Hueco.RACHA, Hueco.GORDO), 6),
    CIE_NADA_PENDIENTE("cie_nada_pendiente", TipoTrozo.NUCLEO, setOf(Hueco.NOMBRE, Hueco.HABITO, Hueco.RACHA, Hueco.GORDO), 6),
    CIE_ANIMO_BIEN("cie_animo_bien", TipoTrozo.SUELTA, emptySet(), 6),
    CIE_ANIMO_REGULAR("cie_animo_regular", TipoTrozo.SUELTA, emptySet(), 6),
    CIE_ANIMO_DURO("cie_animo_duro", TipoTrozo.SUELTA, emptySet(), 6),
    CIE_SELLO("cie_sello", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE), 8),
    CIE_SELLO_PERFECTO("cie_sello_perfecto", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE), 8),
    CIE_TAREAS_MANANA("cie_tareas_manana", TipoTrozo.SUELTA, setOf(Hueco.LISTA, Hueco.N), 3),
    AV_MANANA_TITULO("av_manana_titulo", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE, Hueco.GORDO, Hueco.HECHOS, Hueco.TOTAL, Hueco.TAREA), 10),
    AV_MANANA_TITULO_GORDO(
        "av_manana_titulo_gordo",
        TipoTrozo.SUELTA,
        setOf(Hueco.NOMBRE, Hueco.GORDO, Hueco.HECHOS, Hueco.TOTAL, Hueco.TAREA),
        10,
    ),
    AV_RECORDATORIO("av_recordatorio", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE, Hueco.GORDO, Hueco.HECHOS, Hueco.TOTAL, Hueco.TAREA), 10),
    AV_NOCHE_TITULO("av_noche_titulo", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE, Hueco.GORDO, Hueco.HECHOS, Hueco.TOTAL, Hueco.TAREA), 10),
    AV_NOCHE_CUERPO("av_noche_cuerpo", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE, Hueco.GORDO, Hueco.HECHOS, Hueco.TOTAL, Hueco.TAREA), 10),
    AV_ACEPTADO_TITULO("av_aceptado_titulo", TipoTrozo.SUELTA, setOf(Hueco.HORA, Hueco.GORDO, Hueco.NOMBRE), 6),
    AV_ACEPTADO_CUERPO("av_aceptado_cuerpo", TipoTrozo.SUELTA, setOf(Hueco.HORA, Hueco.GORDO, Hueco.NOMBRE), 6),
    AV_AUSENCIA("av_ausencia", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE, Hueco.HORA, Hueco.GORDO), 6),
    SE_ENTRAR("se_entrar", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE, Hueco.TAREA, Hueco.HABITO, Hueco.MINUTOS), 10),
    DE_HABITO("de_habito", TipoTrozo.SUELTA, setOf(Hueco.HABITO, Hueco.MINUTOS), 10),
    DE_HABITO_LARGO("de_habito_largo", TipoTrozo.SUELTA, setOf(Hueco.HABITO, Hueco.MINUTOS), 6),
    DE_SIN_PENDIENTES("de_sin_pendientes", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE), 10),
    SE_HECHA_TAREA("se_hecha_tarea", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE), 8),
    SE_HECHA_HABITO("se_hecha_habito", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE), 8),
    CE_MEJOR("ce_mejor", TipoTrozo.SUELTA, emptySet(), 6),
    CE_IGUAL("ce_igual", TipoTrozo.SUELTA, emptySet(), 6),
    CE_CUESTA("ce_cuesta", TipoTrozo.SUELTA, emptySet(), 6),
    RE_FIN_RESPIRAR("re_fin_respirar", TipoTrozo.SUELTA, setOf(Hueco.NOMBRE), 8),
    RE_RESPIRAR_MEJOR("re_respirar_mejor", TipoTrozo.RECUERDO, setOf(Hueco.MODO), 5),
}

/** Etiquetas de contexto de un item («[lunes,manana] Texto», spec §5.13). */
enum class Etiqueta(
    val clave: String,
) {
    LUNES("lunes"),
    MARTES("martes"),
    MIERCOLES("miercoles"),
    JUEVES("jueves"),
    VIERNES("viernes"),
    SABADO("sabado"),
    DOMINGO("domingo"),
    FINDE("finde"),
    PRIMAVERA("primavera"),
    VERANO("verano"),
    OTONO("otono"),
    INVIERNO("invierno"),
    MADRUGADA("madrugada"),
    MANANA("manana"),
    MEDIODIA("mediodia"),
    TARDE("tarde"),
    NOCHE("noche"),
    DESPIERTA("despierta"),
    ESPERANDO("esperando"),
    DURMIENDO("durmiendo"),
    ;

    companion object {
        private val porClave = entries.associateBy { it.clave }

        /** La etiqueta de esa clave, o null si no existe. */
        fun de(clave: String): Etiqueta? = porClave[clave]
    }
}

/** Si el contexto cumple la etiqueta: día de la semana, fin de semana, estación, franja o fase (spec §5.13). */
fun Contexto.cumple(e: Etiqueta): Boolean =
    when (e) {
        Etiqueta.LUNES -> diaSemana == DayOfWeek.MONDAY
        Etiqueta.MARTES -> diaSemana == DayOfWeek.TUESDAY
        Etiqueta.MIERCOLES -> diaSemana == DayOfWeek.WEDNESDAY
        Etiqueta.JUEVES -> diaSemana == DayOfWeek.THURSDAY
        Etiqueta.VIERNES -> diaSemana == DayOfWeek.FRIDAY
        Etiqueta.SABADO -> diaSemana == DayOfWeek.SATURDAY
        Etiqueta.DOMINGO -> diaSemana == DayOfWeek.SUNDAY
        Etiqueta.FINDE -> diaSemana == DayOfWeek.SATURDAY || diaSemana == DayOfWeek.SUNDAY
        Etiqueta.PRIMAVERA -> estacion == Estacion.PRIMAVERA
        Etiqueta.VERANO -> estacion == Estacion.VERANO
        Etiqueta.OTONO -> estacion == Estacion.OTONO
        Etiqueta.INVIERNO -> estacion == Estacion.INVIERNO
        Etiqueta.MADRUGADA -> franja == Franja.MADRUGADA
        Etiqueta.MANANA -> franja == Franja.MANANA
        Etiqueta.MEDIODIA -> franja == Franja.MEDIODIA
        Etiqueta.TARDE -> franja == Franja.TARDE
        Etiqueta.NOCHE -> franja == Franja.NOCHE
        Etiqueta.DESPIERTA -> fase == FaseHabi.DESPIERTA
        Etiqueta.ESPERANDO -> fase == FaseHabi.ESPERANDO
        Etiqueta.DURMIENDO -> fase == FaseHabi.DURMIENDO
    }

sealed interface ValorHueco {
    data class Texto(
        val v: String,
    ) : ValorHueco

    data class Numero(
        val v: Int,
    ) : ValorHueco

    data class Hora(
        val minuto: Int,
    ) : ValorHueco

    data class Lista(
        val v: List<String>,
    ) : ValorHueco

    data class DiaSemana(
        val v: DayOfWeek,
    ) : ValorHueco

    data class Fecha(
        val epochDay: Long,
    ) : ValorHueco

    /** El modo de respiración; la pantalla lo escribe con su nombre en el idioma activo. */
    data class Modo(
        val modo: BreathingMode,
    ) : ValorHueco
}

/** Se guarda como «BOLSA:VOZ:índice». */
data class ClaveTrozo(
    val bolsa: Bolsa,
    val voz: Personality,
    val indice: Int,
)

/** Un item de una bolsa: su índice, sus etiquetas y los huecos que pide (spec §4.4). */
data class TrozoInfo(
    val indice: Int,
    val etiquetas: Set<Etiqueta>,
    val huecos: Set<Hueco>,
)

/** Lo que hay en cada bolsa para cada voz. La app lo lee de los recursos; los tests, de un falso. */
interface CatalogoHabla {
    fun trozos(
        bolsa: Bolsa,
        voz: Personality,
    ): List<TrozoInfo>
}

/** Un catálogo sin trozos: lo que se pasa hasta que los recursos estén cableados. */
object CatalogoHablaVacio : CatalogoHabla {
    override fun trozos(
        bolsa: Bolsa,
        voz: Personality,
    ) = emptyList<TrozoInfo>()
}
