package com.alvarotc.bito.ui.inicio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.ui.camino.CaminoContenido
import com.alvarotc.bito.ui.camino.CaminoViewModel
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.premios.CelebracionesViewModel
import com.alvarotc.bito.ui.premios.HojaSaldo
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.premios.PremiosEnInicio
import com.alvarotc.bito.ui.premios.RegistroAnclas
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import com.alvarotc.bito.ui.sistema.componentes.rememberAvisoEstado
import com.alvarotc.bito.ui.tasks.CreateChoiceSheet
import com.alvarotc.bito.ui.today.HoyLista
import com.alvarotc.bito.ui.today.TodayViewModel

/** Hoy y Camino en una sola pantalla. Esqueleto: lo sustituye entero el carril c1. */
@Composable
fun InicioScreen(
    hoy: TodayViewModel,
    camino: CaminoViewModel,
    celebraciones: CelebracionesViewModel,
    navegar: NavegacionInicio,
    creado: String? = null,
    alCreadoVisto: () -> Unit = {},
    enPrimerPlano: Boolean = true,
) {
    val estado by hoy.uiState.collectAsStateWithLifecycle()
    val estadoCamino by camino.uiState.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableFloatStateOf(0f) }
    var crear by remember { mutableStateOf(false) }
    var saldoAbierto by remember { mutableStateOf(false) }
    val registro = remember { RegistroAnclas() }
    val avisos = rememberAvisoEstado()
    BackHandler(enabled = q > 0f) { q = 0f }
    LaunchedEffect(estado.garden) { celebraciones.jardin(estado.garden) }
    DisposableEffect(enPrimerPlano) {
        celebraciones.enHoy(enPrimerPlano && q == 0f)
        onDispose { celebraciones.enHoy(false) }
    }
    CompositionLocalProvider(LocalAnclas provides registro) {
        Box(Modifier.fillMaxSize().testTag("inicio")) {
            Column {
                CabeceraHoy(estado.today, estado.saldo, alSaldo = { saldoAbierto = true }, alAjustes = navegar.abrirAjustes)
                JardinCompartido(
                    estado.garden,
                    estado.spec,
                    q = { q },
                    encogido = { 0f },
                    animado = enPrimerPlano,
                    alTocarFondo = { q = 1f },
                )
                if (q < 0.5f) {
                    // inicio-hoy es de InicioScreen; inicio-camino lo pone CaminoContenido
                    Box(Modifier.weight(1f).testTag("inicio-hoy")) {
                        HoyLista(
                            hoy,
                            navegar,
                            rememberLazyListState(),
                            avisos,
                            encabezado = {},
                            creado = creado,
                            alCreadoVisto = alCreadoVisto,
                        )
                    }
                } else {
                    CaminoContenido(estadoCamino, rememberScrollState(), alVerEnJardin = {}, modifier = Modifier.weight(1f))
                }
            }
            Pildora(q = { q }, alHoy = { q = 0f }, alCamino = { q = 1f }, modifier = Modifier.align(Alignment.BottomCenter))
            BotonCrearStub(Modifier.align(Alignment.BottomEnd).testTag("boton-crear")) { crear = true }
            PremiosEnInicio(celebraciones, registro, costuraY = { 0f }, alVerEnJardin = { q = 1f }, alJardin = {
                    _,
                    _,
                ->
            }, alAsomarCabecera = {})
            AvisoHost(avisos, Modifier.align(Alignment.BottomCenter))
        }
    }
    HojaSaldo(
        saldoAbierto,
        estado.saldo,
        estado.movimientos,
        estado.economia,
        alIrTienda = {
            saldoAbierto = false
            navegar.abrirTienda()
        },
        alCerrar = { saldoAbierto = false },
    )
    if (crear) {
        CreateChoiceSheet(
            onHabit = {
                crear = false
                navegar.crearHabito()
            },
            onTask = {
                crear = false
                navegar.crearTarea()
            },
            onFocus = {
                crear = false
                navegar.abrirRuta("focus")
            },
            onBreathing = {
                crear = false
                navegar.abrirRuta("breathing")
            },
            onDismiss = { crear = false },
        )
    }
}

@Composable
private fun BotonCrearStub(
    modifier: Modifier,
    alPulsar: () -> Unit,
) {
    Icon(
        IconosSistema.Plus,
        contentDescription = stringResource(R.string.nav_new_habit),
        modifier = modifier.padding(20.dp).size(48.dp).clickable(onClick = alPulsar),
    )
}
