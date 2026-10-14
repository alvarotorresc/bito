package com.alvarotc.bito.ui.sistema.sonido

/** Los tres canales con volumen propio (D3). */
enum class Canal { HABI, EFECTOS, AMBIENTE }

/**
 * Los sonidos del catálogo §1.7 menos `respirar` (pieza 5). [recurso] es el nombre del OGG en `res/raw`
 * (lo produce `tools/sonido/renderizar.sh`); [registro] = lo calla «Sonido al registrar»; [bucle] = se
 * reproduce con `MotorSonido.bucle`.
 */
enum class Snd(
    val recurso: String,
    val canal: Canal,
    val registro: Boolean = false,
    val bucle: Boolean = false,
) {
    GORGOTEO("snd_gorgoteo", Canal.EFECTOS, registro = true),
    LLEGADA("snd_llegada", Canal.EFECTOS, registro = true),
    VACIAR("snd_vaciar", Canal.EFECTOS, registro = true),
    BROTE("snd_brote", Canal.EFECTOS),
    HUNDIR("snd_hundir", Canal.EFECTOS),
    ASENTAR("snd_asentar", Canal.EFECTOS),
    POSAR("snd_posar", Canal.EFECTOS),
    CAMPANILLA("snd_campanilla", Canal.EFECTOS),
    GASTAR("snd_gastar", Canal.EFECTOS),
    HABI("snd_habi", Canal.HABI),
    PLANTA("snd_planta", Canal.EFECTOS),
    INTERRUPTOR_ON("snd_interruptor_on", Canal.EFECTOS),
    INTERRUPTOR_OFF("snd_interruptor_off", Canal.EFECTOS),
    ANOCHECER("snd_anochecer", Canal.AMBIENTE),
    DESCENSO("snd_descenso", Canal.EFECTOS),
    ASCENSO("snd_ascenso", Canal.EFECTOS),
    ZOOM("snd_zoom", Canal.AMBIENTE),
    ALEJAR("snd_alejar", Canal.EFECTOS),
    ACERCAR("snd_acercar", Canal.EFECTOS),
    ABRIR("snd_abrir", Canal.EFECTOS),
    CERRAR("snd_cerrar", Canal.EFECTOS),
    TIC("snd_tic", Canal.EFECTOS),
    PASO_MES_ATRAS("snd_pasomes_atras", Canal.EFECTOS),
    PASO_MES_DELANTE("snd_pasomes_delante", Canal.EFECTOS),
    LADO_IZQ("snd_lado_izq", Canal.EFECTOS),
    LADO_DER("snd_lado_der", Canal.EFECTOS),
    AMBIENTE("snd_ambiente", Canal.AMBIENTE),
    AVISO("snd_aviso", Canal.EFECTOS),
    MONEDA("snd_moneda", Canal.EFECTOS),
    DIA_COMPLETO("snd_diacompleto", Canal.EFECTOS),
    RACHA("snd_racha", Canal.EFECTOS),
    LOGRO("snd_logro", Canal.EFECTOS),
    FONDO_LLUVIA("snd_fondo_lluvia", Canal.AMBIENTE, bucle = true),
    FONDO_MARRON("snd_fondo_marron", Canal.AMBIENTE, bucle = true),
    FONDO_CAFE("snd_fondo_cafe", Canal.AMBIENTE, bucle = true),
}
