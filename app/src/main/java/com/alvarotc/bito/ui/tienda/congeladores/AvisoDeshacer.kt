package com.alvarotc.bito.ui.tienda.congeladores

import com.alvarotc.bito.ui.sistema.componentes.AvisoEstado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * El aviso «Congelador comprado · Deshacer» (J13). Solo hay uno: uno nuevo sustituye al anterior, y [retirar] lo
 * quita sin esperar a sus 4 s. Vive en el ámbito del catálogo: si se sale de la pestaña o de la tienda, el aviso
 * se va con él y la compra queda firme.
 */
internal class AvisoDeshacer(
    private val avisos: AvisoEstado,
    private val ambito: CoroutineScope,
) {
    private var trabajo: Job? = null

    fun mostrar(
        texto: String,
        accion: String,
        alDeshacer: () -> Unit,
    ) {
        retirar()
        trabajo = ambito.launch { avisos.mostrar(texto, accion, alDeshacer) }
    }

    fun retirar() {
        trabajo?.cancel()
        trabajo = null
    }
}
