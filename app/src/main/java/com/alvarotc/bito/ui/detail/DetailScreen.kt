package com.alvarotc.bito.ui.detail

import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.DayDot
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.icons.IconosSistema
import com.alvarotc.bito.ui.inicio.LocalAnimacionRuta
import com.alvarotc.bito.ui.inicio.cantoCompartido
import com.alvarotc.bito.ui.inicio.textoHabi
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.CalendarioGotas
import com.alvarotc.bito.ui.sistema.componentes.EstiloBoton
import com.alvarotc.bito.ui.sistema.componentes.pulsar
import com.alvarotc.bito.ui.sistema.fuerte
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.today.CardKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private const val ALTO_CANTO = 76
private const val RADIO_CANTO_FILA = 28
private const val RADIO_CANTO = 38
private const val NOMBRE_FILA = 16f
private const val NOMBRE_CABECERA = 24f
private const val LLEGADA_SIN_GOTA_MS = 300L
private const val CANTO_MS = 440

/**
 * La ficha de un hábito (pieza 3, §8): el canto con el círculo de hoy, la lámina de herbario con la planta y sus
 * tres datos, el calendario de gotas, la nota de Habi y el pie con lo que la ficha aprobada no dibuja (cumplimiento,
 * congeladores, recaída, pausar y archivar). En modo lectura no escribe: sin círculo, días que no responden y solo «Reactivar».
 */
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenHabi: () -> Unit,
    // Editar con el objetivo que sugiere Habi (Tarea 29); sin él, editar a secas.
    alEditarConObjetivo: (String, Int) -> Unit = { id, _ -> onEdit(id) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val propuesta by viewModel.propuesta.collectAsStateWithLifecycle()
    // «Editar con el objetivo que sugiere Habi»: el efecto llega por el flujo, no por el estado.
    LaunchedEffect(viewModel) {
        viewModel.efectos.collect { efecto ->
            if (efecto is Efecto.EditarHabito) alEditarConObjetivo(efecto.habitId, efecto.objetivoSugerido)
        }
    }
    // Al volver de Editar, la nota espera al estado nuevo antes de pintarse.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.alVolver() }
    // `uiState` arranca en `null` antes de su primera emisión Y cuando el hábito no existe: por tipo no se distinguen.
    // `hasLoaded` separa el único caso que importa: un hábito borrado (desde Ajustes o al importar) con su ficha
    // abierta vuelve atrás una vez visto; un `null` de arranque en frío nunca llama a onBack. `remember` a secas, no
    // `rememberSaveable`: tras morir el proceso el VM recreado también parte de `null`.
    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onBack()
        }
    }
    val current = state ?: return

    val colores = BitoTema.colores
    val gesto = rememberGesto()
    val scope = rememberCoroutineScope()
    var daySheetFor by remember { mutableStateOf<LogicalDay?>(null) }
    val archivado = current.status == HabitStatus.ARCHIVED
    val interactivo = !current.lectura && !archivado
    val mesActual = remember(current.today) { YearMonth.from(LocalDate.ofEpochDay(current.today.toLong())) }

    // El círculo sigue a lo de hoy aunque el calendario enseñe otro mes.
    val hechoHoy = current.hechoHoy

    // Marcar hoy (G1). La abstinencia no se marca.
    fun marcarHoy() {
        if (!interactivo || current.kind == CardKind.ABSTINENCE) return
        // El sonido es el del catálogo, como en la fila de Hoy: gorgoteo al marcar, vaciar al desmarcar y tic al sumar.
        when {
            current.kind != CardKind.CHECK -> gesto(Snd.TIC, Haptica.TICK)
            hechoHoy -> gesto(Snd.VACIAR, Haptica.LOW)
            else -> gesto(Snd.GORGOTEO, Haptica.TICKS)
        }
        // Sin gota que vuele (el jardín no se ve), la llegada suena 300 ms después.
        if (current.kind != CardKind.CHECK || !hechoHoy) {
            scope.launch {
                delay(LLEGADA_SIN_GOTA_MS)
                gesto(Snd.LLEGADA, Haptica.CLICK)
            }
        }
        viewModel.marcarHoy()
    }

    Column(Modifier.fillMaxSize().background(colores.tAlta2).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(colores.cielo).statusBarsPadding()) {
            BarraSuperior(onBack = onBack, onEdit = { onEdit(current.habitId) })
            CantoHabito(
                current,
                hechoHoy,
                circulo = interactivo && current.kind != CardKind.ABSTINENCE,
                activo = interactivo,
                alMarcar = ::marcarHoy,
            )
            if (!current.hasAnyEntry) {
                Text(
                    stringResource(R.string.detail_empty_hint),
                    Modifier.padding(start = Medidas.e24, end = Medidas.e24, top = Medidas.e12),
                    style = BitoTema.tipos.t14,
                    color = colores.tintaSuave,
                )
            }
            Spacer(Modifier.height(Medidas.e8))
        }
        Lamina(current.planta, current.bestStreak, current.pct30, current.currentStreak, current.period, current.kind)
        Column(Modifier.padding(start = Medidas.e12, end = Medidas.e12, top = 22.dp)) {
            CalendarioGotas(
                mes = current.month,
                dias = current.heatmap,
                primerMes = current.primerMes,
                ultimoMes = mesActual,
                alMesAnterior = { viewModel.previousMonth() },
                alMesSiguiente = { viewModel.nextMonth() },
                // En abstinencia hoy no se marca (la recaída va por el pie): tocarlo no hace nada.
                alTocarDia = { dia ->
                    if (dia == current.today) marcarHoy() else daySheetFor = dia
                },
                interactivo = interactivo,
            )
            Text(
                stringResource(R.string.ficha_cal_nota),
                Modifier.padding(start = 6.dp, end = 6.dp, top = Medidas.e8),
                style = BitoTema.tipos.t14,
                color = colores.tintaSobreTAlta2,
            )
        }
        NotaDeHabi(propuesta, current, viewModel::responder)
        PieFicha(
            estado = current,
            acciones =
                AccionesPie(
                    pausar = { viewModel.pause(it) },
                    reanudar = { viewModel.resume() },
                    archivar = { viewModel.archive() },
                    reactivar = {
                        viewModel.unarchive()
                        if (current.lectura) onBack()
                    },
                    recaer = { viewModel.logRelapseOn(current.today) },
                    irTienda = onOpenHabi,
                ),
            modifier = Modifier.padding(start = Medidas.margen, end = Medidas.margen, top = 22.dp),
        )
        Spacer(Modifier.height(40.dp).navigationBarsPadding())
    }

    daySheetFor?.let { day ->
        val heatmapDay = current.heatmap.find { it.day == day }
        // Equivale a FreezerEngine.eligibilityOf(...) == ELIGIBLE: un punto FAILED ya implica periodo DAY, cumplimiento
        // fallado y sin proteger; `!isToday` casa con day >= today -> FUTURE_DAY (un congelador solo protege un día pasado).
        val freezerOffered = heatmapDay?.dot == DayDot.FAILED && !heatmapDay.isToday && current.freezersOwned > 0
        DaySheet(
            day = day,
            kind = current.kind,
            currentValue = current.dayValues[day] ?: 0,
            freezerOffered = freezerOffered,
            freezersOwned = current.freezersOwned,
            actions =
                DaySheetActions(
                    onMarkDone = { viewModel.markDayDone(day) },
                    onMarkNotDone = { viewModel.clearDay(day) },
                    onSetValue = { viewModel.setDayValue(day, it) },
                    onRelapse = { viewModel.logRelapseOn(day) },
                    onStayedClean = { viewModel.markCleanAndSeal(day) },
                    onUseFreezer = { viewModel.applyFreezer(day) },
                ),
            onDismiss = { daySheetFor = null },
            unit = current.unit,
        )
    }
}

@Composable
private fun BarraSuperior(
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val colores = BitoTema.colores
    val atras = stringResource(R.string.back)
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Medidas.e8), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(Medidas.toqueMinimo)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .testTag("atras")
                .semantics { contentDescription = atras },
            contentAlignment = Alignment.Center,
        ) {
            Icon(IconosSistema.ArrowLeft, null, Modifier.size(24.dp), tint = colores.tinta)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .height(Medidas.toqueMinimo)
                .clip(BitoFormas.canto)
                .clickable(role = Role.Button, onClick = onEdit)
                .padding(horizontal = Medidas.e12)
                .testTag("editar"),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.ficha_editar), style = BitoTema.tipos.t16.fuerte(), color = colores.tinta)
        }
    }
}

/**
 * El canto del hábito (elemento compartido con su fila de Hoy): el círculo de marcar hoy, el nombre y a qué planta
 * alimenta. Con la transición compartida, el radio pasa de 28 a 38 y el nombre de 16 a 24 sp en 440 ms `drawer`.
 */
@Composable
private fun CantoHabito(
    estado: DetailUiState,
    hecho: Boolean,
    circulo: Boolean,
    activo: Boolean,
    alMarcar: () -> Unit,
) {
    val colores = BitoTema.colores
    val ruta = LocalAnimacionRuta.current
    val radio: Dp
    val nombre: Float
    if (ruta != null && !BitoTema.reducido) {
        val spec = tween<Dp>(CANTO_MS, easing = Curvas.drawer)
        radio =
            ruta.transition.animateDp({
                spec
            }, label = "canto-radio") { if (it == EnterExitState.Visible) RADIO_CANTO.dp else RADIO_CANTO_FILA.dp }.value
        nombre =
            ruta.transition.animateFloat({ tween(CANTO_MS, easing = Curvas.drawer) }, label = "canto-nombre") {
                if (it == EnterExitState.Visible) NOMBRE_CABECERA else NOMBRE_FILA
            }.value
    } else {
        radio = RADIO_CANTO.dp
        nombre = NOMBRE_CABECERA
    }
    val forma = RoundedCornerShape(radio)
    val marcarCd = stringResource(R.string.ficha_marcar_hoy, estado.name)
    Row(
        Modifier
            .padding(horizontal = Medidas.margen)
            .cantoCompartido(estado.habitId)
            .fillMaxWidth()
            .height(ALTO_CANTO.dp)
            .clip(forma)
            .background(colores.canto)
            .border(Medidas.linea, colores.contorno, forma)
            .padding(start = 6.dp, end = 22.dp)
            .testTag("canto"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (circulo) {
            CirculoHoy(hecho, activo, marcarCd, alMarcar)
        } else {
            Spacer(Modifier.size(Medidas.e16))
        }
        Column(Modifier.weight(1f)) {
            Text(
                estado.name,
                style = BitoTema.tipos.t24.fuerte().copy(fontSize = nombre.sp, lineHeight = (nombre * 1.1f).sp),
                color = colores.tinta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(alimentaRes(estado.planta.especie)),
                style = BitoTema.tipos.t14,
                color = colores.tintaSuave,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun alimentaRes(especie: GardenSpecies): Int =
    when (especie) {
        GardenSpecies.ROUND_LEAVES -> R.string.base_alimenta_hojas_redondas
        GardenSpecies.SUNFLOWERS -> R.string.base_alimenta_girasoles
        GardenSpecies.LAVENDER -> R.string.base_alimenta_lavanda
        GardenSpecies.APPLE_TREE -> R.string.base_alimenta_manzano
        GardenSpecies.FERNS -> R.string.base_alimenta_helechos
        GardenSpecies.CHERRY_TREE -> R.string.base_alimenta_cerezo
    }

/** El círculo de marcar hoy (G1): el agua sube con el muelle `marcar` y sale la marca. */
@Composable
private fun CirculoHoy(
    hecho: Boolean,
    activo: Boolean,
    descripcion: String,
    alPulsar: () -> Unit,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val nivel = remember { Animatable(if (hecho) 1f else 0f) }
    LaunchedEffect(hecho, reducido) {
        nivel.animateTo(if (hecho) 1f else 0f, if (reducido) tween(Duraciones.REDUCIDO) else Muelles.marcar())
    }
    val fuente = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(52.dp)
            .pulsar(fuente, redondo = true)
            .clickable(enabled = activo, interactionSource = fuente, indication = null, role = Role.Button, onClick = alPulsar)
            .testTag("marcar-hoy")
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .drawBehind {
                    val alto = size.height * nivel.value
                    drawRect(colores.agua, Offset(0f, size.height - alto), Size(size.width, alto))
                }.border(Medidas.linea, colores.contorno, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                IconosSistema.Check,
                null,
                Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        val v = nivel.value
                        alpha = ((v - 0.45f) * 2f).coerceIn(0f, 1f)
                        scaleX = 0.7f + 0.3f * v.coerceAtMost(1f)
                        scaleY = scaleX
                    },
                tint = colores.tinta,
            )
        }
    }
}

/** La nota de Habi sobre este hábito: la propuesta con su mini Habi; «Vale» y «No hace falta» solo si trae acciones. */
@Composable
private fun NotaDeHabi(
    propuesta: Propuesta?,
    estado: DetailUiState,
    alResponder: (Respuesta) -> Unit,
) {
    if (propuesta == null) return
    val colores = BitoTema.colores
    Row(
        Modifier
            .padding(start = Medidas.margen, end = Medidas.margen, top = 22.dp)
            .fillMaxWidth()
            .background(colores.canto, BitoFormas.tarjeta)
            .border(Medidas.linea, colores.contorno, BitoFormas.tarjeta)
            .padding(start = Medidas.e8, top = Medidas.e12, end = Medidas.e16, bottom = 14.dp)
            .testTag("nota-habi"),
        horizontalArrangement = Arrangement.spacedBy(Medidas.e8),
    ) {
        HabiAvatar(
            HabiSpec(estado.mood, estado.personality, estado.equipped),
            Modifier.size(76.dp, 84.dp),
            animated = !BitoTema.reducido,
        )
        // El relevo (G33): la frase y sus botones se funden juntos cuando la propuesta cambia, sin que quede un botón huérfano.
        val fundido = if (BitoTema.reducido) Duraciones.REDUCIDO else Duraciones.RELEVO_ENTRA
        Crossfade(propuesta, Modifier.weight(1f), animationSpec = tween(fundido), label = "nota-habi") { p ->
            Column {
                Text(textoHabi(p.texto), style = BitoTema.tipos.t16.copy(lineHeight = 22.sp), color = colores.tinta)
                if (p.acciones.isNotEmpty()) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(Medidas.e8)) {
                        p.acciones.forEach { accion ->
                            Boton(
                                textoHabi(accion.etiqueta),
                                { alResponder(accion.respuesta) },
                                Modifier.testTag("habi-${accion.respuesta.name.lowercase()}"),
                                estilo = if (accion.principal) EstiloBoton.PRINCIPAL else EstiloBoton.FANTASMA,
                            )
                        }
                    }
                }
            }
        }
    }
}
