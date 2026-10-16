package com.alvarotc.bito.ui.sesion.respirar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.withResumed
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.R
import com.alvarotc.bito.data.habla.MemoriaRepository
import com.alvarotc.bito.data.repo.BreathingRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.BreathingStats
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.Sealing
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.CatalogoHabla
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Contextos
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.model.BreathingSession
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.sesion.Aire
import com.alvarotc.bito.sesion.FueraDeLaApp
import com.alvarotc.bito.ui.habla.FraseSuelta
import com.alvarotc.bito.ui.inicio.textoHabi
import com.alvarotc.bito.ui.premios.ClavesAncla
import com.alvarotc.bito.ui.premios.LocalAnclas
import com.alvarotc.bito.ui.sesion.AccionAtras
import com.alvarotc.bito.ui.sesion.CabeceraSesion
import com.alvarotc.bito.ui.sesion.Encuadre
import com.alvarotc.bito.ui.sesion.EstadoCamaraSesion
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.Globito
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

// `.ses-b`: 18 dp arriba. `.costura`: 6 dp de borde de arriba. Sin token: el recorte de la tierra.
private val ARRIBA_TIERRA = 18.dp
private val COSTURA = 6.dp

// Llegada a «¿Cómo estás?»: Habi se alegra 1,2 s a los 560 ms (100 reducido) y el globito dura 2,2 s.
private const val LLEGADA_MS = 560L
private const val LLEGADA_REDUCIDO_MS = 100L
private const val ALEGRIA_MS = 1_200L
private const val GLOBITO_MS = 2_200L
private const val TICK_MS = 100L

/**
 * La respiración suelta (spec §3.3.8, §8.5, §8.6): elegir, en marcha, «¿Cómo estás ahora?» y fin, con su cabecera y su
 * tierra, moviendo la [camara] por estado. Habi respira por [respiracion] (el Inicio la pasa a `GardenScene`) y sus
 * gestos salen por [gestos]. El tono y la vibración son del [aire]; [fuera] mantiene vivo el servicio con la pantalla
 * apagada (se pide al empezar, con la app delante, y se suelta al parar, al acabar y al salir).
 *
 * [estado] y [catalogo] por defecto salen del contenedor de la app. [alVolverAHoy] se llama tras lanzar la vuelta de
 * la cámara a Hoy (sonido y háptica de la subida salen ya).
 */
@Composable
fun RespirarSuelta(
    camara: EstadoCamaraSesion,
    aire: Aire,
    fuera: FueraDeLaApp,
    breathing: BreathingRepository,
    memoria: MemoriaRepository,
    settings: SettingsRepository,
    alVolverAHoy: () -> Unit,
    respiracion: androidx.compose.runtime.MutableState<(() -> Float)?>,
    modifier: Modifier = Modifier,
    gestos: GestosHabi = remember { GestosHabi() },
    entorno: EntornoSuelta = EntornoSuelta(),
    estado: Flow<DomainState>? = null,
    catalogo: CatalogoHabla? = null,
) {
    val contexto = LocalContext.current
    val flujoEstado = estado ?: remember(contexto) { (contexto.applicationContext as BitoApp).container.domainState.observe() }
    val catalogoHabla = catalogo ?: remember(contexto) { (contexto.applicationContext as BitoApp).container.catalogoHabla }
    val ajustes by produceState<Settings?>(null, settings) { settings.settings.collect { value = it } }
    val dominio by produceState<DomainState?>(null, flujoEstado) { flujoEstado.collect { value = it } }
    val reglas = remember { RespiracionSuelta(entorno.elapsed, entorno.pared) }
    var listo by remember { mutableStateOf(false) }
    LaunchedEffect(ajustes != null) {
        val a = ajustes ?: return@LaunchedEffect
        if (!listo) {
            reglas.modo = a.breathingLastMode
            reglas.musica = a.breathingMusicEnabled
            listo = true
        }
    }
    val a = ajustes
    if (!listo || a == null) {
        Box(modifier.fillMaxSize())
        return
    }

    val gesto = rememberGesto()
    val reducido = BitoTema.reducido
    val alcance = rememberCoroutineScope()
    val activity = remember(contexto) { contexto.buscarActivity() }
    val energia = remember(contexto) { contexto.getSystemService(PowerManager::class.java) }
    val marcha = reglas.etapa == EtapaSuelta.EN_MARCHA

    // El último tick (cada 100 ms) y, aparte, el cuadro que lee Habi al dibujar.
    val tick = remember { mutableLongStateOf(0L) }
    val cuadro = remember { mutableLongStateOf(0L) }
    val punto by remember {
        derivedStateOf { reglas.punto(tick.longValue).let { it.copy(fill = 0f, remainingMillis = 0L) } }
    }
    val restaSegundos by remember { derivedStateOf { reglas.punto(tick.longValue).remainingSeconds } }

    var pendiente by remember { mutableStateOf<BreathingSession?>(null) }
    var tamAlGuardar by remember { mutableIntStateOf(0) }
    var contextoDia by remember { mutableStateOf<Contexto?>(null) }
    var fraseYaEsta by remember { mutableStateOf<TextoHabi.Frase?>(null) }
    var fraseRespuesta by remember { mutableStateOf<TextoHabi.Frase?>(null) }
    var modoCerrado by remember { mutableStateOf(reglas.modo) }
    var globo by remember { mutableStateOf(false) }
    var ronda by remember { mutableIntStateOf(0) }

    fun hoy(): Int = LogicalDays.logicalDayOf(entorno.pared(), a.dayCutoffMinutes, entorno.zona())

    fun frase(
        bolsa: Bolsa,
        c: Contexto?,
    ): TextoHabi.Frase =
        FraseSuelta.elegir(
            bolsa,
            a.personality,
            a.userName.trim().ifEmpty { null },
            entorno.semilla(),
            catalogoHabla,
            contexto = c,
        )

    fun comenzar() {
        gestos.alegre = false
        globo = false
        fraseRespuesta = null
        ronda++
        reglas.empezar()
        tick.longValue = entorno.elapsed()
        cuadro.longValue = tick.longValue
        aire.respirar(reglas.modo, reglas.ancla, reglas.ciclos, reglas.musica)
        // Con la app delante: desde segundo plano, Android 12+ no deja arrancar el servicio.
        fuera.respirando(true, reglas.finElapsed)
        if (a.breathingLastMode != reglas.modo) {
            val modo = reglas.modo
            alcance.launch { settings.update { it.copy(breathingLastMode = modo) } }
        }
    }

    fun cerrarEjercicio() {
        if (reglas.etapa != EtapaSuelta.EN_MARCHA) return
        val modo = reglas.modo
        aire.parar()
        fuera.respirando(false, null)
        when (val d = reglas.cerrar()) {
            Desenlace.Descartada -> Unit
            is Desenlace.Guardar -> {
                val c = d.cierre
                modoCerrado = modo
                tamAlGuardar = dominio?.breathingSessions?.size ?: 0
                pendiente = BreathingSession("pending", c.modo, c.inicioPared, c.segundos, c.completa)
                // La fila tiene que llegar a Room aunque la pantalla se vaya en este fotograma.
                alcance.launch(NonCancellable) { breathing.save(c.modo, c.inicioPared, c.segundos, c.completa) }
            }
        }
    }

    fun volverAHoy() {
        alcance.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { camara.ir(Encuadre.HOY, gesto) }
        alVolverAHoy()
    }

    fun saltarAnimo() {
        if (reglas.etapa != EtapaSuelta.COMO_ESTAS) return
        fraseRespuesta = null
        reglas.etapa = EtapaSuelta.FIN
    }

    fun atras() {
        when (reglas.etapa) {
            EtapaSuelta.ELEGIR, EtapaSuelta.FIN -> volverAHoy()
            EtapaSuelta.EN_MARCHA -> cerrarEjercicio()
            EtapaSuelta.COMO_ESTAS -> saltarAnimo()
        }
    }

    // Cámara por estado.
    LaunchedEffect(reglas.etapa) {
        camara.ir(
            when (reglas.etapa) {
                EtapaSuelta.ELEGIR -> Encuadre.SUELTA_ELEGIR
                EtapaSuelta.EN_MARCHA -> Encuadre.SUELTA_MARCHA
                EtapaSuelta.COMO_ESTAS -> Encuadre.COMO_ESTAS
                EtapaSuelta.FIN -> Encuadre.SUELTA_FIN
            },
            gesto,
        )
    }

    // El reloj de la respiración: cada 100 ms desde el ancla, y al llegar al final cierra solo.
    LaunchedEffect(marcha, ronda) {
        if (!marcha) return@LaunchedEffect
        while (isActive) {
            tick.longValue = entorno.elapsed()
            if (!entorno.animada) cuadro.longValue = tick.longValue
            if (reglas.punto(tick.longValue).finished) {
                cerrarEjercicio()
                break
            }
            delay(TICK_MS)
        }
    }
    // Habi se estira cada fotograma (solo con la pantalla delante).
    LaunchedEffect(marcha, ronda, entorno.animada) {
        if (!marcha || !entorno.animada) return@LaunchedEffect
        while (isActive) androidx.compose.runtime.withFrameNanos { cuadro.longValue = entorno.elapsed() }
    }
    DisposableEffect(marcha, ronda) {
        respiracion.value = if (marcha) ({ reglas.punto(cuadro.longValue).fill }) else null
        onDispose { respiracion.value = null }
    }
    // Pantalla encendida mientras se respira.
    DisposableEffect(marcha, activity) {
        val ventana = activity?.window
        if (marcha) ventana?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (marcha) ventana?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    // Al salir de la pantalla en plena respiración: se para y se guarda como «Parar».
    DisposableEffect(Unit) {
        onDispose {
            if (reglas.etapa == EtapaSuelta.EN_MARCHA) cerrarEjercicio()
            fuera.respirando(false, null)
        }
    }
    // Otra app (ON_STOP con la pantalla encendida) para y guarda; con la pantalla apagada sigue.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (marcha && activity?.isChangingConfigurations != true && energia?.isInteractive != false) cerrarEjercicio()
    }
    BackHandler { atras() }

    // Llegada a «¿Cómo estás ahora?»: la frase se elige ya (así el fin la tiene aunque se salte enseguida), suena la
    // campanilla, y a los 560 ms Habi se alegra y lo dice. Esto último sale de `alcance` porque el mock lo lanza con
    // un `setTimeout` que sigue vivo aunque se salte la pregunta; solo lo corta empezar otra respiración.
    val ciclo = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reglas.etapa == EtapaSuelta.COMO_ESTAS, ronda, pendiente) {
        if (reglas.etapa != EtapaSuelta.COMO_ESTAS) return@LaunchedEffect
        // El estado ya está cargado desde que se abrió la pantalla; solo si no, se espera a él.
        val c = contextoDe(a, dominio ?: flujoEstado.first(), entorno)
        contextoDia = c
        fraseYaEsta = frase(Bolsa.RE_FIN_RESPIRAR, c)
        val esta = ronda
        alcance.launch {
            delay(if (reducido) LLEGADA_REDUCIDO_MS else LLEGADA_MS)
            if (ronda != esta) return@launch
            gestos.alegre = true
            globo = true
            launch {
                delay(ALEGRIA_MS)
                if (ronda == esta) gestos.alegre = false
            }
            delay(GLOBITO_MS)
            if (ronda == esta) globo = false
        }
        ciclo.withResumed { }
        gesto(Snd.CAMPANILLA, Haptica.CLICK)
    }

    val sesiones =
        dominio?.breathingSessions?.let { s ->
            pendiente?.takeIf { s.size <= tamAlGuardar }?.let { s + it } ?: s
        }
    val semana = sesiones?.let { BreathingStats.thisWeek(it, hoy(), a.dayCutoffMinutes, entorno.zona()) }
    val total = sesiones?.let { BreathingStats.allTime(it) }

    var raiz by remember { mutableStateOf(Offset.Zero) }
    var altoContenido by remember { mutableIntStateOf(0) }
    val costra = BitoTema.colores.costra
    val habi = LocalAnclas.current?.posiciones?.get(ClavesAncla.HABI)

    Box(modifier.fillMaxSize().onGloballyPositioned { raiz = it.positionInRoot() }) {
        // La tierra (`#ses-tierra`): estrato `t-alta` con su costura, que sube y baja con el suelo de la cámara.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = camara.sueloEn(size.height, altoContenido.toFloat())
                    alpha = camara.alfa
                }.background(BitoTema.colores.tAlta)
                .drawBehind { drawRect(costra, Offset.Zero, Size(size.width, COSTURA.toPx())) },
        ) {
            AnimatedContent(
                targetState = reglas.etapa,
                transitionSpec = {
                    if (reducido) {
                        ContentTransform(fadeIn(tween(Duraciones.REDUCIDO)), fadeOut(tween(Duraciones.REDUCIDO)))
                    } else {
                        // Cada hijo se anima por su cuenta (`escalon`); el bloque solo tiene que seguir en pantalla.
                        ContentTransform(EnterTransition.None, ExitTransition.None)
                    }
                },
                label = "bloqueSuelta",
                modifier = Modifier.fillMaxWidth(),
            ) { etapa ->
                CompositionLocalProvider(LocalEscalonado provides if (reducido) null else transition) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .onSizeChanged { altoContenido = it.height }
                            .padding(top = COSTURA + ARRIBA_TIERRA, start = Medidas.margen, end = Medidas.margen),
                    ) {
                        when (etapa) {
                            EtapaSuelta.ELEGIR ->
                                ElegirSuelta(
                                    modo = reglas.modo,
                                    alModo = { reglas.modo = it },
                                    musica = reglas.musica,
                                    alMusica = { on ->
                                        reglas.musica = on
                                        alcance.launch { settings.update { it.copy(breathingMusicEnabled = on) } }
                                    },
                                    semana = semana,
                                    alEmpezar = ::comenzar,
                                )
                            EtapaSuelta.EN_MARCHA ->
                                EnMarchaSuelta(punto = punto, restaSegundos = restaSegundos, alParar = ::cerrarEjercicio)
                            EtapaSuelta.COMO_ESTAS ->
                                ComoEstas(
                                    alElegir = { animo ->
                                        val c = contextoDia
                                        fraseRespuesta =
                                            frase(
                                                when (animo) {
                                                    AnimoSuelta.MEJOR -> Bolsa.CE_MEJOR
                                                    AnimoSuelta.IGUAL -> Bolsa.CE_IGUAL
                                                    AnimoSuelta.CUESTA -> Bolsa.CE_CUESTA
                                                },
                                                c,
                                            )
                                        when (animo) {
                                            AnimoSuelta.MEJOR -> gestos.saltos++
                                            AnimoSuelta.CUESTA -> {
                                                gestos.alegre = false
                                                gestos.caras++
                                            }
                                            AnimoSuelta.IGUAL -> Unit
                                        }
                                        val recuerdo =
                                            EventoMemoria(
                                                id = UUID.randomUUID().toString(),
                                                tipo = TipoMemoria.COMO_ESTAS,
                                                dia = hoy(),
                                                ref = modoCerrado.name,
                                                valor = animo.valor,
                                                creadoMillis = entorno.pared(),
                                            )
                                        alcance.launch(NonCancellable) { memoria.anotar(recuerdo) }
                                    },
                                    alSeguir = { reglas.etapa = EtapaSuelta.FIN },
                                    alSaltar = ::saltarAnimo,
                                )
                            EtapaSuelta.FIN -> {
                                val f = fraseRespuesta ?: fraseYaEsta
                                FinSuelta(
                                    frase = f?.let { textoHabi(it) }.orEmpty(),
                                    semana = semana,
                                    total = total,
                                    alOtraVez = ::comenzar,
                                    alListo = ::volverAHoy,
                                )
                            }
                        }
                    }
                }
            }
        }
        CabeceraSesion(
            atras = AccionAtras(stringResource(R.string.back), ::atras),
            musica = if (marcha) reglas.musica else null,
            alMusica = { on ->
                reglas.musica = on
                aire.musica(on)
                alcance.launch { settings.update { it.copy(breathingMusicEnabled = on) } }
            },
            saldo = null,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        // El globito de Habi, sobre ella (o, sin ancla, arriba al centro).
        val texto = fraseYaEsta?.let { textoHabi(it) }.orEmpty()
        val ancla =
            if (habi != null) {
                IntOffset((habi.center.x - raiz.x).roundToInt(), (habi.top - raiz.y).roundToInt())
            } else {
                null
            }
        Box(
            (if (ancla != null) Modifier.offset { ancla } else Modifier.align(Alignment.TopCenter).padding(top = 240.dp))
                .size(1.dp),
        ) {
            Globito(visible = globo && texto.isNotEmpty(), texto = texto, autoOcultar = false)
        }
    }
}

private fun contextoDe(
    a: Settings,
    d: DomainState,
    e: EntornoSuelta,
): Contexto {
    val ahora = e.pared()
    val zona = e.zona()
    val hoy = LogicalDays.logicalDayOf(ahora, a.dayCutoffMinutes, zona)
    return Contextos.de(
        ahoraMillis = ahora,
        zona = zona,
        corteDia = a.dayCutoffMinutes,
        horaManana = a.habiMorningMinutes,
        horaNoche = a.habiNightMinutes,
        dndActivo = a.dndEnabled,
        dndDesde = a.dndFromMinutes,
        dndHasta = a.dndToMinutes,
        voz = a.personality,
        nombre = a.userName,
        // Ninguna etiqueta (`cumple`) mira los permisos.
        permisoAvisos = false,
        alarmasExactas = false,
        diaSellado = Sealing.isSealed(d, hoy),
        quedanExigibles = Hechos.quedanPendientes(d, hoy),
    )
}

private fun Context.buscarActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
