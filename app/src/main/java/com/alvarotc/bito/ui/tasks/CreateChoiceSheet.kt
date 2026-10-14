package com.alvarotc.bito.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.HojaPasos
import com.alvarotc.bito.ui.sistema.componentes.OpcionIcono
import com.alvarotc.bito.ui.sistema.componentes.OpcionMenu
import com.alvarotc.bito.ui.sistema.componentes.TonoOpcion
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd

/**
 * La hoja del «+» (G7): «¿Qué añadimos?» con Hábito, Tarea y Sesión ahora. «Sesión ahora» es un
 * segundo paso de la misma hoja (G8) con Foco y Respirar. Elegir cierra la hoja y, 160 ms después
 * de empezar a cerrarse, lanza la acción.
 */
@Composable
fun CreateChoiceSheet(
    onHabit: () -> Unit,
    onTask: () -> Unit,
    onDismiss: () -> Unit,
    onFocus: () -> Unit = {},
    onBreathing: () -> Unit = {},
) {
    val gesto = rememberGesto()
    var paso by remember { mutableIntStateOf(0) }
    Hoja(
        abierta = true,
        alCerrar = onDismiss,
        titulo = stringResource(if (paso == 0) R.string.create_choice_title else R.string.tasks2_sesion_titulo),
        modifier = Modifier.testTag("create-choice-sheet"),
    ) {
        HojaPasos(paso) { p ->
            if (p == 0) {
                val habito = OpcionMenu(stringResource(R.string.tasks2_crear_habito), IconosSistema.Plant, TonoOpcion.AGUA) { onHabit() }
                val tarea =
                    OpcionMenu(stringResource(R.string.tasks2_crear_tarea), IconosSistema.ListChecks, TonoOpcion.PIEDRA) { onTask() }
                val sesion = OpcionMenu(stringResource(R.string.tasks2_sesion_titulo), IconosSistema.Timer, TonoOpcion.AGUA) {}
                Row(Modifier.fillMaxWidth().padding(top = Medidas.e12), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OpcionIcono(habito, alPulsar = { cerrarY(habito.alElegir) }, modifier = Modifier.testTag("create-habit"))
                    OpcionIcono(tarea, alPulsar = { cerrarY(tarea.alElegir) }, modifier = Modifier.testTag("create-task"))
                    OpcionIcono(
                        sesion,
                        alPulsar = {
                            gesto(Snd.BROTE, Haptica.TICK)
                            paso = 1
                        },
                        modifier = Modifier.testTag("create-session"),
                    )
                }
            } else {
                val foco = OpcionMenu(stringResource(R.string.focus_title), IconosSistema.Timer, TonoOpcion.AGUA) { onFocus() }
                val respirar = OpcionMenu(stringResource(R.string.breathing_title), IconosSistema.Wind, TonoOpcion.AGUA) { onBreathing() }
                Row(Modifier.fillMaxWidth().padding(top = Medidas.e12), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OpcionIcono(foco, alPulsar = { cerrarY(foco.alElegir) }, modifier = Modifier.testTag("create-focus"))
                    OpcionIcono(respirar, alPulsar = { cerrarY(respirar.alElegir) }, modifier = Modifier.testTag("create-breathing"))
                }
                BotonTexto(
                    stringResource(R.string.back),
                    alPulsar = {
                        gesto(Snd.HUNDIR, Haptica.LOW)
                        paso = 0
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
