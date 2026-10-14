package com.alvarotc.bito.ui.inicio

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.domain.propuestas.TextoHabi

/** Resuelve un [TextoHabi] con el idioma activo; los argumentos que son otro [TextoHabi] se resuelven antes. */
@Composable
fun textoHabi(t: TextoHabi): String {
    val args = t.args.map { if (it is TextoHabi) textoHabi(it) else it }
    return stringResource(t.res, *args.toTypedArray())
}
