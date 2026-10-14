package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Fila
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.HojaPasos
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.delay

private const val SIN_HORA = -1
private const val RETRASO_BROTE = 120
private const val DURACION_BROTE = 240

/** Recordatorios (G8, G34): la lista y, en la misma hoja, el paso del reloj para añadir o cambiar una hora. */
@Composable
fun HojaRecordatorios(
    abierta: Boolean,
    horas: List<Int>,
    alAnadir: (Int) -> Unit,
    alCambiar: (antes: Int, despues: Int) -> Unit,
    alQuitar: (Int) -> Unit,
    alCerrar: () -> Unit,
) {
    var paso by rememberSaveable { mutableIntStateOf(0) }
    var editando by rememberSaveable { mutableIntStateOf(SIN_HORA) }
    var inicial by rememberSaveable { mutableIntStateOf(17 * 60) }
    var fresca by rememberSaveable { mutableIntStateOf(SIN_HORA) }
    val gesto = rememberGesto()

    Hoja(
        abierta = abierta,
        alCerrar = {
            // Cerrar con atrás o con el velo no pasa por Guardar: la próxima vez debe abrirse en la lista.
            paso = 0
            alCerrar()
        },
        seccion = BitoTema.colores.estratos.avisos,
        etiqueta = stringResource(R.string.hojas_estrato_avisos),
        titulo = stringResource(R.string.hojas_rec_titulo),
    ) {
        HojaPasos(paso) { p ->
            if (p == 0) {
                ListaHoras(
                    horas = horas,
                    fresca = fresca,
                    alAnadir = {
                        var m = 17 * 60
                        var intentos = 0
                        while (m in horas && intentos++ < 24) m = (m + 60) % (24 * 60)
                        editando = SIN_HORA
                        inicial = m
                        fresca = SIN_HORA
                        paso = 1
                    },
                    alEditar = {
                        editando = it
                        inicial = it
                        fresca = SIN_HORA
                        paso = 1
                    },
                    alQuitar = {
                        gesto(Snd.HUNDIR, Haptica.FALL)
                        alQuitar(it)
                    },
                    alHecho = { cerrar() },
                )
            } else {
                PasoReloj(
                    titulo =
                        stringResource(
                            if (editando == SIN_HORA) R.string.hojas_rec_nuevo else R.string.hojas_rec_uno,
                        ),
                    descripcion = stringResource(R.string.hojas_rec_reloj),
                    minutos = inicial,
                    guardar = stringResource(R.string.sistema_guardar),
                    alGuardar = { m ->
                        gesto(Snd.BROTE, Haptica.CONFIRM)
                        if (editando == SIN_HORA) alAnadir(m) else alCambiar(editando, m)
                        fresca = m
                        paso = 0
                    },
                    suave = stringResource(R.string.hojas_atras),
                    alSuave = { paso = 0 },
                    paso = 5,
                )
            }
        }
    }
}

@Composable
private fun ListaHoras(
    horas: List<Int>,
    fresca: Int,
    alAnadir: () -> Unit,
    alEditar: (Int) -> Unit,
    alQuitar: (Int) -> Unit,
    alHecho: () -> Unit,
) {
    val colores = BitoTema.colores
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Medidas.e8)) {
        Text(stringResource(R.string.hojas_rec_desc), style = BitoTema.tipos.t14, color = colores.tintaSuave)
        if (horas.isEmpty()) {
            Text(
                stringResource(R.string.hojas_rec_vacio),
                Modifier.padding(vertical = Medidas.e12),
                style = BitoTema.tipos.t16,
                color = colores.tinta,
            )
        } else {
            Column {
                horas.sorted().forEach { h ->
                    androidx.compose.runtime.key(h) { FilaHora(h, h == fresca, alEditar, alQuitar) }
                }
            }
        }
        BotonesHoja(
            stringResource(R.string.hojas_rec_hecho),
            alHecho,
            suave = stringResource(R.string.hojas_rec_anadir),
            alSuave = alAnadir,
        )
    }
}

/** La fila nueva brota (120 ms de retraso) y la quitada se pliega en 200 ms antes de avisar. */
@Composable
private fun FilaHora(
    minutos: Int,
    nueva: Boolean,
    alEditar: (Int) -> Unit,
    alQuitar: (Int) -> Unit,
) {
    val reducido = BitoTema.reducido
    val visible = remember { MutableTransitionState(!nueva).apply { targetState = true } }
    var quitando by remember { mutableStateOf(false) }
    LaunchedEffect(quitando) {
        if (quitando) {
            visible.targetState = false
            delay((if (reducido) Duraciones.REDUCIDO else Duraciones.SALIR + 20).toLong())
            alQuitar(minutos)
        }
    }
    val texto = hora(minutos)
    val descEditar = stringResource(R.string.hojas_rec_editar_desc, texto)
    val descQuitar = stringResource(R.string.hojas_rec_quitar_desc, texto)
    val entra =
        if (reducido) {
            fadeIn(tween(Duraciones.REDUCIDO))
        } else {
            fadeIn(tween(DURACION_BROTE, RETRASO_BROTE, Curvas.out)) +
                expandVertically(tween(DURACION_BROTE, RETRASO_BROTE, Curvas.out))
        }
    val sale =
        if (reducido) {
            fadeOut(tween(Duraciones.REDUCIDO))
        } else {
            fadeOut(tween(Duraciones.SALIR, easing = Curvas.out)) +
                shrinkVertically(tween(Duraciones.SALIR, easing = Curvas.out))
        }
    AnimatedVisibility(visibleState = visible, enter = entra, exit = sale) {
        Fila(etiqueta = texto) {
            Row {
                BotonTexto(
                    stringResource(R.string.hojas_rec_editar),
                    { alEditar(minutos) },
                    Modifier.semantics { contentDescription = descEditar },
                )
                BotonTexto(
                    stringResource(R.string.hojas_rec_quitar),
                    { if (!quitando) quitando = true },
                    Modifier.semantics { contentDescription = descQuitar },
                )
            }
        }
    }
}
