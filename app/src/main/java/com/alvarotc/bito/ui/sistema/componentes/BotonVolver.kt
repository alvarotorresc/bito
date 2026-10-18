package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas

/**
 * La flecha de volver de las pantallas que se abren encima de Hoy. [alVolver] ha de ser el mismo «atrás» que el gesto del
 * sistema (la tienda, por ejemplo, quita primero la prueba). Objetivo táctil de [Medidas.toqueMinimo]; la pantalla la
 * coloca por debajo de la barra de estado.
 */
@Composable
fun BotonVolver(
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .testTag("volver")
            .defaultMinSize(Medidas.toqueMinimo, Medidas.toqueMinimo)
            .clickable(role = Role.Button, onClick = alVolver),
        contentAlignment = Alignment.Center,
    ) {
        Icon(IconosSistema.CaretLeft, contentDescription = stringResource(R.string.back), tint = BitoTema.colores.tinta)
    }
}
