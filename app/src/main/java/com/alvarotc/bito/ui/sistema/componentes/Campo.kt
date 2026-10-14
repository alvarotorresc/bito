package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.cifra
import com.alvarotc.bito.ui.sistema.fuerte

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
    val colores = BitoTema.colores
    var visible by remember { mutableStateOf(false) }
    val escondido = oculto && !visible
    val opciones =
        if (oculto && teclado.keyboardType == KeyboardType.Text) teclado.copy(keyboardType = KeyboardType.Password) else teclado
    Column(modifier.temblor(temblorClave), verticalArrangement = Arrangement.spacedBy(Medidas.e4)) {
        if (etiqueta != null) Text(etiqueta, style = BitoTema.tipos.t14.fuerte(), color = colores.tinta)
        CajaCampo(
            valor = valor,
            alCambiar = alCambiar,
            estilo = BitoTema.tipos.t16.copy(color = colores.tinta),
            mensajeError = error,
            transformacion = if (escondido) PasswordVisualTransformation() else VisualTransformation.None,
            teclado = opciones,
            alHecho = alHecho,
            pista = pista,
            alto = Medidas.toqueMinimo,
            final = {
                if (oculto) {
                    val descripcion = stringResource(if (visible) R.string.sistema_ocultar else R.string.sistema_mostrar)
                    Box(
                        Modifier
                            .size(Medidas.toqueMinimo)
                            .clickable(role = Role.Button) { visible = !visible }
                            .semantics { contentDescription = descripcion },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (visible) IconosSistema.EyeSlash else IconosSistema.Eye,
                            contentDescription = null,
                            tint = colores.tintaSuave,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
        )
        if (error != null) Text(error, style = BitoTema.tipos.t14, color = colores.peligroTexto)
    }
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
) {
    val colores = BitoTema.colores
    Column(modifier.temblor(temblorClave), verticalArrangement = Arrangement.spacedBy(Medidas.e4)) {
        CajaCampo(
            valor = valor,
            alCambiar = alCambiar,
            estilo = BitoTema.tipos.t48.cifra().copy(color = colores.tinta, textAlign = TextAlign.Center),
            mensajeError = error,
            transformacion = VisualTransformation.None,
            teclado = KeyboardOptions(keyboardType = KeyboardType.Number),
            alHecho = null,
            pista = null,
            alto = 72.dp,
            final = {
                if (unidad != null) {
                    Text(unidad, style = BitoTema.tipos.t19, color = colores.tintaSuave, modifier = Modifier.padding(end = Medidas.e16))
                }
            },
        )
        if (error != null) Text(error, style = BitoTema.tipos.t14, color = colores.peligroTexto)
    }
}

@Composable
private fun CajaCampo(
    valor: String,
    alCambiar: (String) -> Unit,
    estilo: TextStyle,
    mensajeError: String?,
    transformacion: VisualTransformation,
    teclado: KeyboardOptions,
    alHecho: (() -> Unit)?,
    pista: String?,
    alto: Dp,
    final: @Composable () -> Unit,
) {
    val colores = BitoTema.colores
    val contorno = if (mensajeError != null) colores.peligro else colores.contorno
    BasicTextField(
        value = valor,
        onValueChange = alCambiar,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { if (mensajeError != null) error(mensajeError) },
        textStyle = estilo,
        singleLine = true,
        cursorBrush = SolidColor(colores.tinta),
        visualTransformation = transformacion,
        keyboardOptions = if (alHecho != null) teclado.copy(imeAction = ImeAction.Done) else teclado,
        keyboardActions = KeyboardActions(onDone = { alHecho?.invoke() }),
        decorationBox = { campo ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = alto)
                    .background(colores.campo, BitoFormas.campo)
                    .border(Medidas.linea, contorno, BitoFormas.campo)
                    .padding(start = Medidas.e16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.weight(1f),
                    contentAlignment = if (estilo.textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart,
                ) {
                    if (valor.isEmpty() && pista != null) Text(pista, style = estilo, color = colores.tintaSuave)
                    campo()
                }
                final()
            }
        },
    )
}
