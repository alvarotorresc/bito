package com.alvarotc.bito.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.CampoGrande
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.rememberGesto

/** Cifras que caben en el campo grande (cabe un Int: 999999). El tope de verdad lo pone [validar]. */
private const val MAX_CIFRAS = 6

/**
 * Direct numeric entry for any target/stepper value across the app, in a [Hoja] with the big
 * numeric field. [extraContent], when given, renders below the Save button inside the same
 * [Column] (the freezer row of [com.alvarotc.bito.ui.detail.DaySheet]'s COUNTER/DURATION day),
 * leaving every other caller's plain input-then-save layout untouched.
 *
 * [validar] decides whether a typed number is acceptable: if it returns a text, the sheet does not
 * confirm, shows that text as the field's error and shakes it. The default accepts everything.
 */
@Composable
fun NumberInputSheet(
    title: String,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    extraContent: @Composable (ColumnScope.() -> Unit)? = null,
    validar: (Int) -> String? = { null },
) {
    var texto by remember { mutableStateOf(initial.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var temblor by remember { mutableIntStateOf(0) }
    val gesto = rememberGesto()
    val valor = texto.toIntOrNull()

    fun confirmar(cerrar: (() -> Unit) -> Unit) {
        val n = valor ?: return
        val aviso = validar(n)
        if (aviso != null) {
            error = aviso
            temblor++
            gesto(h = Haptica.REJECT)
        } else {
            gesto(h = Haptica.CONFIRM)
            cerrar { onConfirm(n) }
        }
    }

    Hoja(abierta = true, alCerrar = onDismiss, titulo = title) {
        Column {
            CampoGrande(
                valor = texto,
                alCambiar = {
                    texto = it.filter(Char::isDigit).take(MAX_CIFRAS)
                    error = null
                },
                modifier = Modifier.testTag("target-input"),
                error = error,
                temblorClave = temblor,
            )
            Spacer(Modifier.height(16.dp))
            BotonesHoja(
                principal = stringResource(R.string.save),
                alPrincipal = { confirmar { accion -> cerrarY(accion) } },
                suave = stringResource(R.string.cancel),
                alSuave = ::cerrar,
                apagado = valor == null,
            )
            extraContent?.invoke(this)
        }
    }
}
