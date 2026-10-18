package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido

/**
 * [ConSistema] con las miniaturas de la tira sustituidas por una caja vacía: las pruebas de geometría y textos no
 * decodifican ~38 webp en el hilo principal. Las miniaturas reales se prueban aparte (`MiniaturasRealesTest`).
 */
@Composable
internal fun ConSistemaLigero(
    sonido: MotorSonido = SonidoGrabado(),
    tacto: Tacto = TactoGrabado(),
    reducido: Boolean = false,
    content: @Composable () -> Unit,
) = ConSistema(sonido, tacto, reducido) {
    CompositionLocalProvider(LocalMiniaturaFalsa provides { m -> Box(m) }, content = content)
}
