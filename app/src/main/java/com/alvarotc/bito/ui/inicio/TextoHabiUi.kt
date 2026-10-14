package com.alvarotc.bito.ui.inicio

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.Idioma
import com.alvarotc.bito.domain.habla.Plantilla
import com.alvarotc.bito.domain.habla.TipoTrozo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habla.BolsasRecursos
import java.util.Locale

/**
 * Resuelve un [TextoHabi] con el idioma activo de la pantalla; los argumentos que son otro
 * [TextoHabi] se resuelven antes. Es [resolverTextoHabi] con los recursos de la configuración actual.
 */
@Composable
fun textoHabi(t: TextoHabi): String {
    val contexto = LocalContext.current
    val config = LocalConfiguration.current
    val resources = remember(contexto, config) { contexto.createConfigurationContext(config).resources }
    return resolverTextoHabi(t, resources)
}

/**
 * Resuelve un [TextoHabi] sin Compose: la misma salida que [textoHabi], para lo que se pinta fuera de
 * una pantalla (avisos). El idioma sale de [resources]. Un argumento [ValorHueco] se formatea con
 * [Plantilla.valor] y uno [TextoHabi] se resuelve antes.
 */
fun resolverTextoHabi(
    t: TextoHabi,
    resources: Resources,
): String =
    when (t) {
        is TextoHabi.Recurso -> {
            val locale = locale(resources)
            val idioma = idioma(locale)
            val args =
                t.args.map {
                    when (it) {
                        is TextoHabi -> resolverTextoHabi(it, resources)
                        is ValorHueco -> Plantilla.valor(it, idioma, locale)
                        else -> it
                    }
                }
            resources.getString(t.res, *args.toTypedArray())
        }
        is TextoHabi.Frase -> frase(t, resources)
    }

private fun locale(resources: Resources): Locale = resources.configuration.locales.get(0) ?: Locale.getDefault()

private fun idioma(locale: Locale): Idioma = if (locale.language == "es") Idioma.ES else Idioma.EN

private fun frase(
    f: TextoHabi.Frase,
    resources: Resources,
): String {
    val locale = locale(resources)
    val idioma = idioma(locale)
    return Plantilla.unir(f.trozos.map { trozo(it, f, resources, idioma, locale) })
}

/**
 * Un trozo: el item por índice. Si falta o no se puede rellenar (le falta un hueco), se omite; el
 * núcleo prueba además los items en orden desde el primero, y si ninguno se rellena queda vacío.
 */
private fun trozo(
    clave: ClaveTrozo,
    f: TextoHabi.Frase,
    resources: Resources,
    idioma: Idioma,
    locale: Locale,
): String {
    val items = resources.getStringArray(BolsasRecursos.array(clave.bolsa, clave.voz))
    val candidatos =
        if (clave.bolsa.tipo == TipoTrozo.NUCLEO) {
            listOfNotNull(items.getOrNull(clave.indice)) + items.toList()
        } else {
            listOfNotNull(items.getOrNull(clave.indice))
        }
    return candidatos.firstNotNullOfOrNull { rellenar(it, f, idioma, locale) }.orEmpty()
}

private fun rellenar(
    item: String,
    f: TextoHabi.Frase,
    idioma: Idioma,
    locale: Locale,
): String? =
    try {
        val (_, texto) = Plantilla.separarEtiquetas(item)
        Plantilla.rellenar(texto, f.huecos, idioma, locale)
    } catch (_: IllegalArgumentException) {
        null
    }
