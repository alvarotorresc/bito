package com.alvarotc.bito.ui.today

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.ui.inicio.textoHabi

/** La propuesta de Habi en Hoy. Provisional: la rehace el carril c2. */
@Composable
fun TarjetaHabi(
    propuesta: Propuesta?,
    alResponder: (Respuesta) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (propuesta != null) Text(textoHabi(propuesta.texto), modifier.testTag("tarjeta-habi"))
}
