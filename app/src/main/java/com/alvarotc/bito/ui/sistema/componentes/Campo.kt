package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/** Campo de texto (`campo`, radio 16). [error] lo pone en rojo y enseña el texto; [temblorClave] tiembla al cambiar. */
@Composable
fun Campo(
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    etiqueta: String? = null,
    pista: String? = null,
    error: String? = null,
    oculto: Boolean = false,
    teclado: KeyboardOptions = KeyboardOptions.Default,
    alHecho: (() -> Unit)? = null,
    temblorClave: Any? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        modifier = modifier,
        label = etiqueta?.let { { Text(it) } },
        placeholder = pista?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (oculto) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = teclado,
    )
}

/** El numérico de 72 dp de la hoja de número (3.6). */
@Composable
fun CampoGrande(
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    unidad: String? = null,
    error: String? = null,
    temblorClave: Any? = null,
) = Campo(
    valor,
    alCambiar,
    modifier,
    etiqueta = unidad,
    error = error,
    teclado = KeyboardOptions(keyboardType = KeyboardType.Number),
    temblorClave = temblorClave,
)
