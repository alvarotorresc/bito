package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.BreathingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Idioma de una frase de Habi. */
enum class Idioma { ES, EN }

/**
 * Las frases por huecos (spec §8.2). Puro: sin Android. Un solo recorrido del texto de entrada; lo
 * insertado nunca se vuelve a leer, así que un nombre con «{», «%» o «/» sale tal cual.
 */
object Plantilla {
    private val reHueco = Regex("""\{([^{}]*)\}""")
    private val reEtiquetas = Regex("""^\s*\[([^\]]*)]\s*""")
    private val porClave = Hueco.entries.associateBy { it.clave }

    /** «[lunes,manana] Texto» → (`{LUNES, MANANA}`, «Texto»). Una etiqueta desconocida lanza. */
    fun separarEtiquetas(item: String): Pair<Set<Etiqueta>, String> {
        val m = reEtiquetas.find(item) ?: return emptySet<Etiqueta>() to item
        val etiquetas =
            m.groupValues[1]
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { requireNotNull(Etiqueta.de(it)) { "Etiqueta desconocida: $it" } }
                .toSet()
        return etiquetas to item.substring(m.range.last + 1)
    }

    /** Los huecos que usa un texto; `{n|día|días}` cuenta como `n`. Un hueco desconocido lanza. */
    fun huecosDe(texto: String): Set<Hueco> = reHueco.findAll(texto).map { hueco(it.groupValues[1].split('|')[0]) }.toSet()

    fun rellenar(
        texto: String,
        huecos: Map<Hueco, ValorHueco>,
        idioma: Idioma,
        locale: Locale,
    ): String {
        val cuerpo = separarEtiquetas(texto).second
        return reHueco.replace(cuerpo) { m ->
            val partes = m.groupValues[1].split('|')
            val h = hueco(partes[0])
            val v = requireNotNull(huecos[h]) { "Falta el valor del hueco {${h.clave}}" }
            when (partes.size) {
                1 -> valor(v, idioma, locale)
                3 -> {
                    require(v is ValorHueco.Numero) { "El plural de {${h.clave}} pide un número" }
                    "${v.v} ${if (v.v == 1) partes[1] else partes[2]}"
                }
                else -> throw IllegalArgumentException("Hueco mal formado: {${m.groupValues[1]}}")
            }
        }
    }

    /** Un único valor tal como se pinta en una frase. */
    fun valor(
        v: ValorHueco,
        idioma: Idioma,
        locale: Locale,
    ): String =
        when (v) {
            is ValorHueco.Texto -> v.v
            is ValorHueco.Numero -> v.v.toString()
            is ValorHueco.Hora -> String.format(Locale.ROOT, "%d:%02d", v.minuto / 60, v.minuto % 60)
            is ValorHueco.Lista -> lista(v.v, idioma)
            is ValorHueco.DiaSemana -> dia(v.v, idioma, locale)
            is ValorHueco.Fecha -> fecha(v.epochDay, idioma, locale)
            // La pantalla suele cambiarlo por un Texto con los recursos; si llega aquí, nunca sale el identificador.
            is ValorHueco.Modo -> modo(v.modo, idioma)
        }

    /** Une los trozos con un espacio, sin vacíos ni espacios dobles. */
    fun unir(trozos: List<String>): String =
        trozos
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    private fun modo(
        m: BreathingMode,
        idioma: Idioma,
    ): String =
        when (m) {
            BreathingMode.CALM -> if (idioma == Idioma.ES) "Calmarme" else "Calm down"
            BreathingMode.SLEEP -> if (idioma == Idioma.ES) "Dormir" else "Sleep"
            BreathingMode.FOCUS -> if (idioma == Idioma.ES) "Centrarme" else "Focus"
        }

    private fun hueco(clave: String): Hueco = requireNotNull(porClave[clave]) { "Hueco desconocido: {$clave}" }

    private fun lista(
        v: List<String>,
        idioma: Idioma,
    ): String {
        if (v.size <= 1) return v.joinToString("")
        val y = if (idioma == Idioma.ES) " y " else " and "
        return v.dropLast(1).joinToString(", ") + y + v.last()
    }

    private fun dia(
        d: DayOfWeek,
        idioma: Idioma,
        locale: Locale,
    ): String {
        val nombre = d.getDisplayName(TextStyle.FULL, locale)
        return if (idioma == Idioma.ES) nombre.lowercase(locale) else nombre
    }

    private fun fecha(
        epochDay: Long,
        idioma: Idioma,
        locale: Locale,
    ): String {
        val patron = if (idioma == Idioma.ES) "d 'de' MMMM" else "MMMM d"
        return DateTimeFormatter.ofPattern(patron, locale).format(LocalDate.ofEpochDay(epochDay))
    }
}
