package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Medidas
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AvisoDatos(
    val texto: String,
    val accion: String? = null,
    val alAccion: (() -> Unit)? = null,
)

/** Estado del aviso con Deshacer (G9). Uno cada vez: el siguiente espera su turno. */
@Stable
class AvisoEstado {
    var actual: AvisoDatos? by mutableStateOf(null)
        internal set

    /** Lo que esquiva el aviso (el +) sube esto mientras se ve: 64 dp. Lo anima `AvisoHost`. */
    var desplazamiento: Dp by mutableStateOf(0.dp)
        internal set

    private val turno = Mutex()

    /** Enseña [texto] 4 s. Vuelve cuando se ha ido. */
    suspend fun mostrar(
        texto: String,
        accion: String? = null,
        alAccion: (() -> Unit)? = null,
    ) = turno.withLock {
        actual = AvisoDatos(texto, accion, alAccion)
        delay(Duraciones.AVISO.toLong())
        actual = null
    }
}

@Composable
fun rememberAvisoEstado(): AvisoEstado = remember { AvisoEstado() }

/** Va en la raíz de cada pantalla que enseña avisos, abajo. */
@Composable
fun AvisoHost(
    estado: AvisoEstado,
    modifier: Modifier = Modifier,
) {
    val aviso = estado.actual ?: return
    Row(modifier.background(BitoTema.colores.roca).padding(Medidas.margen)) {
        Text(aviso.texto, color = BitoTema.colores.tintaClara)
        if (aviso.accion != null) TextButton(onClick = { aviso.alAccion?.invoke() }) { Text(aviso.accion) }
    }
}
