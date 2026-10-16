package com.alvarotc.bito.domain.sesion

/** «{m} min», «{h} h» o «{h} h {m} min». */
enum class TextoMinutos { MIN, H, H_MIN }

/** El subtítulo de los bloques en Preparar: uno, dos o tres, cuatro o más (spec §3.3.2). */
enum class TextoBloques { UNO, POCOS, MUCHOS }

/** Qué texto toca y con qué números, en el orden de sus marcadores. La UI lo pasa a `R.string.ses_*`. */
data class Texto<T : Enum<T>>(val clave: T, val args: List<Int>)

/** Formatos de la sesión. Puro: devuelve claves y números, nunca recursos. */
object FormatoSesion {
    private const val MINUTOS_POR_HORA = 60
    private const val MILLIS_POR_SEGUNDO = 1_000L
    private const val SEGUNDOS_POR_MINUTO = 60L
    private const val MILLIS_POR_MINUTO = 60_000L
    private const val POCOS_HASTA = 3

    fun minutos(minutos: Int): Texto<TextoMinutos> {
        val h = minutos / MINUTOS_POR_HORA
        val m = minutos % MINUTOS_POR_HORA
        return when {
            h == 0 -> Texto(TextoMinutos.MIN, listOf(m))
            m == 0 -> Texto(TextoMinutos.H, listOf(h))
            else -> Texto(TextoMinutos.H_MIN, listOf(h, m))
        }
    }

    /** La cifra de Terminada: el foco real redondeado al minuto, como mínimo 1 (spec §7.6). */
    fun minutosDeFoco(focoMillis: Long): Int =
        ((focoMillis.coerceAtLeast(0L) + MILLIS_POR_MINUTO / 2) / MILLIS_POR_MINUTO).toInt().coerceAtLeast(1)

    /** `m:ss`, con los segundos redondeados hacia arriba: con la sesión viva nunca dice 0:00. */
    fun cuenta(millis: Long): String {
        val segundos = (millis.coerceAtLeast(0L) + MILLIS_POR_SEGUNDO - 1) / MILLIS_POR_SEGUNDO
        val m = segundos / SEGUNDOS_POR_MINUTO
        val s = segundos % SEGUNDOS_POR_MINUTO
        return "$m:" + s.toString().padStart(2, '0')
    }

    /**
     * 1 → «Un bloque, sin descanso»; 2 o 3 → «{n} de {min} min, {corto} de descanso entre ellos»;
     * 4 o más → «{n} de {min} min: {corto} de descanso entre ellos y {largo} al cerrar cada {rondas}».
     */
    fun bloques(
        bloques: Int,
        minutosFoco: Int,
        corto: Int,
        largo: Int,
        rondas: Int,
    ): Texto<TextoBloques> =
        when {
            bloques <= 1 -> Texto(TextoBloques.UNO, emptyList())
            bloques <= POCOS_HASTA -> Texto(TextoBloques.POCOS, listOf(bloques, minutosFoco, corto))
            else -> Texto(TextoBloques.MUCHOS, listOf(bloques, minutosFoco, corto, largo, rondas))
        }
}
