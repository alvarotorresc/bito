package com.alvarotc.bito.ui.sesion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.FormatoSesion
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.PlanSesion
import com.alvarotc.bito.domain.sesion.TextoBloques
import com.alvarotc.bito.sesion.Conflicto
import com.alvarotc.bito.sesion.FondoMudo
import com.alvarotc.bito.sesion.MotorSesion
import com.alvarotc.bito.sesion.NoMolestar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonTexto
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.Fila
import com.alvarotc.bito.ui.sistema.componentes.FilaInterruptor
import com.alvarotc.bito.ui.sistema.componentes.FilaValor
import com.alvarotc.bito.ui.sistema.componentes.Segmentado
import com.alvarotc.bito.ui.sistema.componentes.Stepper
import com.alvarotc.bito.ui.sistema.fuerte
import kotlinx.coroutines.launch
import kotlin.math.ceil

// `.ses-h`: 19 sp / 700 con interlineado 1,25.
private val TITULO_LINEA = 23.75.sp

// `.ses-tarea` (sin token): 64 dp de alto mínimo, 10 dp arriba, relleno 18 / 8 / 6 / 8 y radio 32 dp.
private val TAREA_ALTO = 64.dp
private val TAREA_ARRIBA = 10.dp
private val TAREA_INICIO = 18.dp
private val TAREA_FINAL = 6.dp
private val TAREA_VERTICAL = 8.dp
private val TAREA_FORMA = RoundedCornerShape(32.dp)

// `.ses-lab`: 14 dp arriba y 6 abajo.
private val ETIQUETA_ARRIBA = 14.dp
private val ETIQUETA_ABAJO = 6.dp

// `.seg.ses-ancho`: 40 dp de alto, relleno 3 dp y 8 dp a los lados de cada botón.
private val SEG_ALTO = 40.dp
private val SEG_RELLENO = 3.dp
private val SEG_LADOS = 8.dp

// `.ses-filas`: 8 dp arriba; cada fila 54 dp de alto mínimo con 6 dp arriba y abajo (el sistema usa 56 y 8).
private val FILAS_ARRIBA = 8.dp
private val FILA_ALTO = 54.dp
private val FILA_RELLENO = 6.dp
private val STEPPER_BOTON = 40.dp

// `.btnrow.llena`: 12 dp arriba y el botón a 48 dp.
private val EMPEZAR_ARRIBA = 12.dp

private val MINUTOS_FIJOS = listOf(15, 25, 45)
private const val PESTANA_OTRO = 3
private const val BLOQUES_MIN = 1
private const val BLOQUES_MAX = 8
private const val MILIS_POR_MINUTO = 60_000.0

private enum class HojaPreparar { NINGUNA, TAREA, MINUTOS, FONDO, OCUPADO }

/** Otra sesión en marcha: su nombre y cuánto le queda, para la hoja «Ya tienes algo en marcha». */
private class Ocupada(val nombre: String, val quedan: Int, val deMin: Int)

@Composable
fun rememberNoMolestar(): NoMolestar {
    val contexto = LocalContext.current
    return remember(contexto) { NoMolestar(contexto) }
}

/**
 * Preparar dentro de la tierra: lee el motor y su borrador y monta [PrepararContenido] con lo que el Inicio sabe
 * ([LocalSesionInicio]: tareas de hoy, fondo de la sesión, No molestar, nombre del objetivo) y la voz de Habi.
 */
@Composable
internal fun PrepararDeSesion() {
    val motor = LocalMotorSesion.current ?: return
    val inicio = LocalSesionInicio.current
    val plan by motor.borrador.collectAsStateWithLifecycle()
    val actual = plan ?: return
    val salida = LocalSalidaSesion.current
    val tareas by produceState(emptyList<OpcionTarea>(), inicio) { value = inicio?.tareasDeHoy().orEmpty() }
    val voz by produceState<Personality?>(null, inicio) { value = inicio?.contexto()?.voz }
    val datos =
        DatosPreparar(
            tareas = tareas,
            spec = HabiSpec(Mood.NORMAL, voz ?: Personality.NEUTRA, EquippedSet()),
            fondo = inicio?.fondo ?: FondoMudo,
            aviso = salida.avisar,
            objetivo = { o -> inicio?.objetivo(o)?.let { ObjetivoUi(it.nombre, it.primerPaso) } },
        )
    PrepararContenido(actual, motor, datos)
}

/**
 * Preparar (spec §3.3.2, inventario 5.1): ocho piezas que son hijos directos de la tierra para que entren
 * escalonadas, y las hojas que cuelgan de ellas. No guarda nada: cada cambio es un [MotorSesion.cambiarBorrador].
 */
@Composable
fun PrepararContenido(
    plan: PlanSesion,
    motor: MotorSesion,
    datos: DatosPreparar,
    noMolestar: NoMolestar = datos.noMolestar ?: rememberNoMolestar(),
) {
    val alcance = rememberCoroutineScope()
    val registro = LocalRegistroHoja.current
    var hoja by remember { mutableStateOf(HojaPreparar.NINGUNA) }
    var ocupada by remember { mutableStateOf<Ocupada?>(null) }
    var sinPermiso by remember { mutableStateOf(false) }
    val flujo = rememberFlujoNoMolestar()
    var enOtro by remember { mutableStateOf(plan.minutosFoco !in MINUTOS_FIJOS) }
    var otroValor by remember { mutableIntStateOf(if (plan.minutosFoco !in MINUTOS_FIJOS) plan.minutosFoco else 0) }

    // Atrás cierra la hoja que haya antes que nada.
    val hayHoja = hoja != HojaPreparar.NINGUNA || flujo.paso != PasoPermiso.NINGUNO
    DisposableEffect(hayHoja) {
        if (hayHoja) {
            registro.abrir {
                hoja = HojaPreparar.NINGUNA
                flujo.paso = PasoPermiso.NINGUNO
            }
        }
        onDispose { if (hayHoja) registro.soltar() }
    }

    val objetivo = nombreDeObjetivo(plan.objetivo, datos)

    // 1. Título
    Text(
        stringResource(R.string.ses_preparar_titulo),
        style = BitoTema.tipos.t19.fuerte().copy(lineHeight = TITULO_LINEA),
        color = BitoTema.colores.tinta,
    )
    // 2. Lo que vas a hacer
    FilaTarea(objetivo, plan.objetivo is Objetivo.Libre) { hoja = HojaPreparar.TAREA }
    // 3 y 4. Tiempo
    Etiqueta(stringResource(R.string.ses_tiempo))
    val textoMin = stringResource(R.string.ses_min)
    val pestanas =
        MINUTOS_FIJOS.map {
            textoMin.format(it)
        } + (if (otroValor > 0) textoMin.format(otroValor) else stringResource(R.string.ses_otro))
    Segmentado(
        opciones = pestanas,
        elegida = if (enOtro) PESTANA_OTRO else MINUTOS_FIJOS.indexOf(plan.minutosFoco).coerceAtLeast(0),
        alElegir = { i ->
            if (i == PESTANA_OTRO) {
                enOtro = true
                hoja = HojaPreparar.MINUTOS
            } else {
                enOtro = false
                motor.cambiarBorrador(plan.copy(minutosFoco = MINUTOS_FIJOS[i]))
            }
        },
        modifier = Modifier.testTag("p-min"),
        anchoCompleto = true,
        alto = SEG_ALTO,
        relleno = SEG_RELLENO,
        ladosOpcion = SEG_LADOS,
        alVolverAElegir = { i -> if (i == PESTANA_OTRO) hoja = HojaPreparar.MINUTOS },
    )
    // 5 y 6. Para entrar
    Etiqueta(stringResource(R.string.ses_para_entrar))
    Segmentado(
        opciones = listOf(stringResource(R.string.ses_respirar_1), stringResource(R.string.ses_directo)),
        elegida = if (plan.entrada == Entrada.RESPIRAR) 0 else 1,
        alElegir = { i -> motor.cambiarBorrador(plan.copy(entrada = if (i == 0) Entrada.RESPIRAR else Entrada.DIRECTO)) },
        modifier = Modifier.testTag("p-entrar"),
        anchoCompleto = true,
        alto = SEG_ALTO,
        relleno = SEG_RELLENO,
        ladosOpcion = SEG_LADOS,
    )
    // 7. Bloques, fondo y No molestar
    Column(Modifier.padding(top = FILAS_ARRIBA)) {
        val etiquetaBloques = stringResource(R.string.ses_bloques)
        Fila(
            etiqueta = etiquetaBloques,
            subtitulo = subtituloBloques(plan),
            alto = FILA_ALTO,
            relleno = FILA_RELLENO,
        ) {
            Stepper(
                valor = plan.bloques,
                alCambiar = { motor.cambiarBorrador(plan.copy(bloques = it)) },
                min = BLOQUES_MIN,
                max = BLOQUES_MAX,
                descripcion = etiquetaBloques,
                modifier = Modifier.testTag("p-blq"),
                tamano = STEPPER_BOTON,
                etiquetaMenos = stringResource(R.string.ses_un_bloque_menos),
                etiquetaMas = stringResource(R.string.ses_un_bloque_mas),
            )
        }
        FilaValor(
            etiqueta = stringResource(R.string.ses_fondo),
            valor = nombreFondo(plan.fondo),
            alPulsar = { hoja = HojaPreparar.FONDO },
            modifier = Modifier.testTag("p-fondo"),
            alto = FILA_ALTO,
            relleno = FILA_RELLENO,
            valorFuerte = true,
        )
        FilaInterruptor(
            etiqueta = stringResource(R.string.ses_no_molestar),
            activo = plan.noMolestar,
            alCambiar = { motor.cambiarBorrador(plan.copy(noMolestar = it)) },
            modifier = Modifier.testTag("p-dnd"),
            subtitulo = stringResource(if (sinPermiso) R.string.ses_dnd_sin_permiso else R.string.ses_dnd_sub),
            alto = FILA_ALTO,
            relleno = FILA_RELLENO,
            // Sin permiso el interruptor no se mueve: sale la hoja que lo pide. Se mira al tocar, no al componer.
            bloqueada = { nuevo -> nuevo && !noMolestar.permitido() },
            alBloqueada = { flujo.pedir() },
        )
    }
    // 8. Empezar (el motor pone el brote y el confirm)
    val libre = stringResource(R.string.ses_solo_foco)
    val recursos = LocalContext.current.resources
    Boton(
        texto = stringResource(R.string.ses_empezar),
        alPulsar = {
            alcance.launch {
                val otra = motor.empezar() as? Conflicto.OtraEnMarcha ?: return@launch
                val nombre = nombreFuera(otra.objetivo, datos, libre)
                ocupada = Ocupada(nombre, ceil(otra.restaMillis / MILIS_POR_MINUTO).toInt(), otra.minutosPlan)
                hoja = HojaPreparar.OCUPADO
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = EMPEZAR_ARRIBA).testTag("p-empezar"),
        estilo = EstiloBoton.PRINCIPAL,
        alto = Medidas.botonHoja,
        llena = true,
        pildoraMock = true,
    )

    // Las hojas no son piezas: viven en su propia ventana.
    HojaTarea(
        abierta = hoja == HojaPreparar.TAREA,
        tareas = datos.tareas,
        elegida = plan.objetivo,
        alElegir = { motor.cambiarBorrador(plan.copy(objetivo = it)) },
        alCerrar = { hoja = HojaPreparar.NINGUNA },
    )
    HojaMinutos(
        abierta = hoja == HojaPreparar.MINUTOS,
        alGuardar = { minutos ->
            otroValor = minutos
            enOtro = true
            motor.cambiarBorrador(plan.copy(minutosFoco = minutos))
        },
        alCerrar = { hoja = HojaPreparar.NINGUNA },
    )
    HojaFondoSesion(
        abierta = hoja == HojaPreparar.FONDO,
        elegido = plan.fondo,
        alElegir = { motor.cambiarBorrador(plan.copy(fondo = it)) },
        fondo = datos.fondo,
        alCerrar = { hoja = HojaPreparar.NINGUNA },
    )
    val otra = ocupada
    HojaOcupado(
        abierta = hoja == HojaPreparar.OCUPADO && otra != null,
        nombre = otra?.nombre.orEmpty(),
        quedan = otra?.quedan ?: 0,
        deMin = otra?.deMin ?: 0,
        alSeguir = { alcance.launch { motor.seguirConLaViva() } },
        alDejar = {
            alcance.launch {
                val resultado = motor.dejarlaYEmpezar()
                resultado.aviso?.let { datos.aviso(it.texto(recursos)) }
            }
        },
        alCerrar = { hoja = HojaPreparar.NINGUNA },
    )
    HojasNoMolestar(
        flujo = flujo,
        noMolestar = noMolestar,
        spec = datos.spec,
        alActivar = { motor.cambiarBorrador(plan.copy(noMolestar = true)) },
        alAhoraNo = { sinPermiso = true },
        aviso = datos.aviso,
    )
}

/** La tarea elegida (`.ses-tarea`): su nombre, su primer paso y «Cambiar». */
@Composable
private fun FilaTarea(
    objetivo: ObjetivoUi,
    libre: Boolean,
    alCambiar: () -> Unit,
) {
    val colores = BitoTema.colores
    Row(
        Modifier
            .padding(top = TAREA_ARRIBA)
            .fillMaxWidth()
            .heightIn(min = TAREA_ALTO)
            .background(colores.piedraClara, TAREA_FORMA)
            .border(BorderStroke(Medidas.linea, colores.contorno), TAREA_FORMA)
            .padding(start = TAREA_INICIO, end = TAREA_FINAL, top = TAREA_VERTICAL, bottom = TAREA_VERTICAL),
        horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                objetivo.nombre,
                modifier = Modifier.testTag("p-nom"),
                style = BitoTema.tipos.t16.fuerte(),
                color = colores.tinta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!libre) {
                Text(
                    objetivo.primerPaso?.let { stringResource(R.string.ses_primer_paso, it.lowercase()) }
                        ?: stringResource(R.string.ses_sin_primer_paso),
                    modifier = Modifier.testTag("p-paso"),
                    style = BitoTema.tipos.t14,
                    color = colores.tintaSuave,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BotonTexto(stringResource(R.string.ses_cambiar), alCambiar, Modifier.testTag("p-cambiar"))
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Text(
        texto,
        modifier = Modifier.padding(top = ETIQUETA_ARRIBA, bottom = ETIQUETA_ABAJO),
        style = BitoTema.tipos.t14.fuerte(),
        color = BitoTema.colores.tintaSuave,
    )
}

@Composable
private fun nombreFondo(fondo: SessionBackground): String =
    stringResource(
        when (fondo) {
            SessionBackground.RAIN -> R.string.ses_fondo_lluvia
            SessionBackground.BROWN -> R.string.ses_fondo_marron
            SessionBackground.CAFE -> R.string.ses_fondo_cafe
            SessionBackground.NONE -> R.string.ses_fondo_ninguno
        },
    )

@Composable
private fun subtituloBloques(plan: PlanSesion): String {
    val texto = FormatoSesion.bloques(plan.bloques, plan.minutosFoco, plan.descansoCorto, plan.descansoLargo, plan.rondas)
    val a = texto.args
    return when (texto.clave) {
        TextoBloques.UNO -> stringResource(R.string.ses_bloques_uno)
        TextoBloques.POCOS -> stringResource(R.string.ses_bloques_pocos, a[0], a[1], a[2])
        TextoBloques.MUCHOS -> stringResource(R.string.ses_bloques_muchos, a[0], a[1], a[2], a[3], a[4])
    }
}

/**
 * Nombre y primer paso de [objetivo]: «Solo foco» si es libre; si no, el de las tareas de hoy. Si no está entre ellas
 * (un hábito, una tarea que no toca hoy), lo que diga [DatosPreparar.objetivo].
 */
@Composable
private fun nombreDeObjetivo(
    objetivo: Objetivo,
    datos: DatosPreparar,
): ObjetivoUi {
    val libre = stringResource(R.string.ses_solo_foco)
    val deHoy = (objetivo as? Objetivo.Tarea)?.let { t -> datos.tareas.firstOrNull { it.id == t.id } }
    val buscado by produceState<ObjetivoUi?>(null, objetivo, deHoy) {
        value = if (deHoy == null && objetivo !is Objetivo.Libre) datos.objetivo(objetivo) else null
    }
    return when {
        objetivo is Objetivo.Libre -> ObjetivoUi(libre, null)
        deHoy != null -> ObjetivoUi(deHoy.nombre, deHoy.primerPaso)
        else -> buscado ?: ObjetivoUi("", null)
    }
}

/** Lo mismo que [nombreDeObjetivo], para cuando ya no se está componiendo (el nombre de la otra sesión). */
private suspend fun nombreFuera(
    objetivo: Objetivo,
    datos: DatosPreparar,
    libre: String,
): String =
    when (objetivo) {
        is Objetivo.Libre -> libre
        is Objetivo.Tarea -> datos.tareas.firstOrNull { it.id == objetivo.id }?.nombre ?: datos.objetivo(objetivo)?.nombre.orEmpty()
        is Objetivo.Habito -> datos.objetivo(objetivo)?.nombre.orEmpty()
    }
