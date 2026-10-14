package com.alvarotc.bito.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.ui.breathing.AndroidBreathingMusic
import com.alvarotc.bito.ui.breathing.BreathingScreen
import com.alvarotc.bito.ui.breathing.BreathingViewModel
import com.alvarotc.bito.ui.camino.CaminoViewModel
import com.alvarotc.bito.ui.detail.DetailScreen
import com.alvarotc.bito.ui.detail.DetailViewModel
import com.alvarotc.bito.ui.habi.HabiScreen
import com.alvarotc.bito.ui.habi.HabiViewModel
import com.alvarotc.bito.ui.habitform.HabitFormScreen
import com.alvarotc.bito.ui.habitform.HabitFormViewModel
import com.alvarotc.bito.ui.inicio.InicioScreen
import com.alvarotc.bito.ui.inicio.LocalAnimacionRuta
import com.alvarotc.bito.ui.inicio.LocalTransicionCompartida
import com.alvarotc.bito.ui.inicio.NavegacionInicio
import com.alvarotc.bito.ui.notifications.NotificationPermissionPrompt
import com.alvarotc.bito.ui.onboarding.OnboardingReconciler
import com.alvarotc.bito.ui.onboarding.OnboardingScreen
import com.alvarotc.bito.ui.onboarding.OnboardingViewModel
import com.alvarotc.bito.ui.premios.CelebracionesViewModel
import com.alvarotc.bito.ui.review.ReviewScreen
import com.alvarotc.bito.ui.review.ReviewViewModel
import com.alvarotc.bito.ui.settings.AjustesScreen
import com.alvarotc.bito.ui.settings.ArchivedScreen
import com.alvarotc.bito.ui.settings.BackupViewModel
import com.alvarotc.bito.ui.settings.SettingsViewModel
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.LocalReducido
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tasks.FocusScreen
import com.alvarotc.bito.ui.tasks.FocusViewModel
import com.alvarotc.bito.ui.tasks.TaskFormScreen
import com.alvarotc.bito.ui.tasks.TaskFormState
import com.alvarotc.bito.ui.tasks.TasksScreen
import com.alvarotc.bito.ui.tasks.TasksViewModel
import com.alvarotc.bito.ui.tasks.resolvedDueDay
import com.alvarotc.bito.ui.tasks.toFormState
import com.alvarotc.bito.ui.theme.Papel
import com.alvarotc.bito.ui.today.TodayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BitoNavHost(
    container: AppContainer,
    // Los tests inyectan el suyo para leer la pila; la app usa el de siempre.
    navController: NavHostController = rememberNavController(),
) {
    // OnboardingReconciler.reconcile runs to completion FIRST, gating the rest of this composable
    // behind it — a v1/v2 restore's seeded `onboardingDone = true` (a habits-but-no-onboardingDone
    // restore, see OnboardingReconciler's own KDoc) is committed to DataStore before the settings
    // gate below ever subscribes and reads it. That makes the startDestination decision further
    // down deterministic: no more race against a subscriber that could arrive before the
    // reconciling write commits. `reconciled` only ever flips false -> true, once per [container] —
    // keyed on it (not bare `remember {}`) so a container swap re-closes this gate instead of
    // leaving it open while `LaunchedEffect` below restarts and races the settings subscription
    // further down against the new container's own reconcile, the exact race this fix removes.
    var reconciled by remember(container) { mutableStateOf(false) }
    LaunchedEffect(container) {
        // Main.immediate: no hace nada en la app (el efecto ya corre en el hilo principal), pero
        // fija que la escritura de `reconciled` vuelva al principal tras la espera de Room y
        // DataStore. El dispatcher de efectos de los tests de Compose reanuda en el hilo que
        // termino la espera (arch_disk_io) si cae fuera de un fotograma, y desde ahi recomponia
        // (CalledFromWrongThreadException en BitoNavHostTest).
        withContext(Dispatchers.Main.immediate) {
            OnboardingReconciler.reconcile(container.settings, container.habits)
            reconciled = true
        }
    }
    if (!reconciled) {
        Box(Modifier.fillMaxSize().background(Papel).testTag("app-loading"))
        return
    }

    // The very first Settings emission decides where the app opens (today vs onboarding) — a
    // hardcoded "today" start would flash before onboardingDone is known, so nothing but the app
    // background renders until that first value lands. Same "state == null means still loading"
    // gate AjustesScreen already uses for its own DataStore-backed cards. Reading it only starts
    // once reconciled above, so this is the reconciled value for the "onboardingDone" case above,
    // not a fresh race against it.
    val settingsState by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val loadedSettings = settingsState
    if (loadedSettings == null) {
        Box(Modifier.fillMaxSize().background(Papel).testTag("app-loading"))
        return
    }
    // Decided once, from that first resolved value: NavHost keys its graph on startDestination,
    // so re-evaluating this on every recomposition would rebuild the whole nav graph out from
    // under an in-flight finish()-triggered navigate("today") the moment onboardingDone flips.
    val startDestination = remember { if (loadedSettings.onboardingDone) "today" else "onboarding" }

    val nav = navController
    val reducido = LocalReducido.current
    val gesto = rememberGesto()
    DisposableEffect(nav, gesto) {
        var anterior: String? = nav.currentDestination?.route
        val oyente =
            NavController.OnDestinationChangedListener { controller, destino, _ ->
                val nueva = destino.route
                val haciaDelante = controller.previousBackStackEntry?.destination?.route == anterior
                when (sonidoDeCambio(anterior, nueva, haciaDelante)) {
                    CambioSonoro.DESCENSO -> gesto(Snd.DESCENSO, Haptica.TICK)
                    CambioSonoro.ASCENSO -> gesto(Snd.ASCENSO, Haptica.LOW)
                    CambioSonoro.ALEJAR -> gesto(Snd.ALEJAR, Haptica.TICK)
                    CambioSonoro.ACERCAR -> gesto(Snd.ACERCAR, Haptica.LOW)
                    null -> Unit
                }
                anterior = nueva
            }
        nav.addOnDestinationChangedListener(oyente)
        onDispose { nav.removeOnDestinationChangedListener(oyente) }
    }
    val context = LocalContext.current
    // Activity-scoped (como habiViewModel): Hoy, Camino y las celebraciones vuelven al instante.
    val habiViewModel: HabiViewModel = viewModel(factory = HabiViewModel.factory(container))
    val caminoViewModel: CaminoViewModel = viewModel(factory = CaminoViewModel.factory(container))
    val celebracionesViewModel: CelebracionesViewModel = viewModel(factory = CelebracionesViewModel.factory(container))
    // Activity-scoped (this composable's own store owner), not the "task?id={id}" route's own —
    // on purpose: TasksViewModel.create/edit/delete are fire-and-forget inside viewModelScope, and
    // both the create and the edit path below call nav.popBackStack() right after starting one.
    // A route-scoped instance would have its own ViewModelStore cleared by that very pop, which
    // cancels its viewModelScope and any write still in flight — same reasoning
    // HabitFormViewModel.save() documents for calling onSaved() from INSIDE its own coroutine.
    // "tasks" screen below builds its own separate instance from this same factory function, not
    // this one — a NavBackStackEntry-scoped VM, by design, same as every other route's own.
    val tasksViewModel: TasksViewModel = viewModel(factory = TasksViewModel.factory(container))
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route

    // Lo recién creado se deja en la entrada `today` (no en la anterior): Hoy lo recibe lo cree donde lo cree.
    fun dejarCreado(id: String) {
        runCatching { nav.getBackStackEntry("today") }.getOrNull()?.savedStateHandle?.set("creado", id)
    }

    val camara = Muelles.camara<IntOffset>(umbral = IntOffset.VisibilityThreshold)
    val globalEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = { signo ->
        fadeIn(tween(150, easing = LinearOutSlowInEasing)) +
            slideInHorizontally(tween(150, easing = LinearOutSlowInEasing)) { signo * it / 12 }
    }
    val globalExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(150, easing = LinearOutSlowInEasing))
    }

    Scaffold(
        containerColor = Papel,
    ) { padding ->
        SharedTransitionLayout {
            CompositionLocalProvider(LocalTransicionCompartida provides this) {
                NavHost(
                    nav,
                    startDestination = startDestination,
                    // Consume the outer Scaffold's insets, not just offset for them — otherwise Today's
                    // and Settings' own Scaffolds (default contentWindowInsets = systemBars) re-apply
                    // the status bar gap on top of this padding under edge-to-edge.
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                    // Quiet global motion (motion spec §8: 150-250ms, ease-out) — every route change,
                    // bottom-bar tab switches included, gets a subtle fade + 1/12-width slide instead of
                    // the default hard cut. Every tween below carries LinearOutSlowInEasing explicitly —
                    // tween()'s own default (FastOutSlowInEasing) is NOT ease-out, so leaving it implicit
                    // on any of the eight calls here would quietly violate the spec on that one. Onboarding's
                    // own internal pager motion lives inside OnboardingScreen and is untouched by this;
                    // only entering/leaving its route uses this. The juicy celebration sheets are
                    // overlays, not routes, so they never see this.
                    enterTransition = {
                        entra(
                            transicionDe(Momento.ENTRA, initialState.destination.route, targetState.destination.route, reducido),
                            camara,
                        ) { globalEnter(1) }
                    },
                    exitTransition = {
                        sale(
                            transicionDe(Momento.SALE, initialState.destination.route, targetState.destination.route, reducido),
                            camara,
                        ) { globalExit() }
                    },
                    popEnterTransition = {
                        entra(
                            transicionDe(Momento.VUELVE_ENTRA, initialState.destination.route, targetState.destination.route, reducido),
                            camara,
                        ) {
                            globalEnter(-1)
                        }
                    },
                    popExitTransition = {
                        sale(
                            transicionDe(Momento.VUELVE_SALE, initialState.destination.route, targetState.destination.route, reducido),
                            camara,
                        ) { globalExit() }
                    },
                ) {
                    // No bottom nav: a fresh install's first-run flow, not a bar destination — same
                    // reasoning as "review" below, and not deep-link allowlisted either. T6 builds the
                    // welcome + story screens (7a-7d) against OnboardingViewModel's state/callbacks; T7-T8
                    // own the rest of the flow's content. This composable's own job stays just the spine:
                    // instantiate the VM and leave once it reports done.
                    composable("onboarding") {
                        val onboardingVm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(container))
                        val onboardingState by onboardingVm.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(onboardingState.done) {
                            if (onboardingState.done) {
                                nav.navigate("today") { popUpTo("onboarding") { inclusive = true } }
                            }
                        }
                        Box(Modifier.fillMaxSize().background(Papel).testTag("onboarding-screen")) {
                            OnboardingScreen(onboardingVm)
                        }
                    }
                    composable("today") { entry ->
                        val creado by entry.savedStateHandle.getStateFlow<String?>("creado", null).collectAsStateWithLifecycle()
                        val ciclo by entry.lifecycle.currentStateAsState()
                        CompositionLocalProvider(LocalAnimacionRuta provides this) {
                            InicioScreen(
                                hoy = viewModel(factory = TodayViewModel.factory(container)),
                                camino = caminoViewModel,
                                celebraciones = celebracionesViewModel,
                                navegar =
                                    NavegacionInicio(
                                        abrirFicha = { nav.navigate("detail/$it") },
                                        abrirAjustes = { nav.navigate("settings") },
                                        abrirTienda = { nav.navigate("habi") { launchSingleTop = true } },
                                        abrirRepaso = { nav.navigate("review") },
                                        abrirTareas = { nav.navigate("tasks") },
                                        crearHabito = { nav.navigate("habit") },
                                        crearTarea = { nav.navigate("task") },
                                        abrirRuta = { nav.navigate(it) { launchSingleTop = true } },
                                    ),
                                creado = creado,
                                alCreadoVisto = { entry.savedStateHandle["creado"] = null },
                                enPrimerPlano = ciclo.isAtLeast(Lifecycle.State.RESUMED),
                            )
                        }
                    }
                    composable(
                        "habit?id={id}",
                        arguments =
                            listOf(
                                navArgument("id") {
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                    ) { entry ->
                        HabitFormScreen(
                            viewModel = viewModel(factory = HabitFormViewModel.factory(container, entry.arguments?.getString("id"))),
                            onBack = { nav.popBackStack() },
                            alCrear = { id -> dejarCreado(id) },
                        )
                    }
                    composable(
                        RUTA_FICHA,
                        arguments =
                            listOf(
                                navArgument("lectura") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                },
                            ),
                    ) { entry ->
                        val habitId = entry.arguments?.getString("habitId") ?: return@composable
                        val lectura = entry.arguments?.getBoolean("lectura") ?: false
                        CompositionLocalProvider(LocalAnimacionRuta provides this) {
                            DetailScreen(
                                viewModel = viewModel(factory = DetailViewModel.factory(container, habitId, lectura)),
                                onBack = { nav.popBackStack() },
                                onEdit = { nav.navigate("habit?id=$it") },
                                onOpenHabi = { nav.navigate("habi") { launchSingleTop = true } },
                            )
                        }
                    }
                    composable("settings") {
                        AjustesScreen(
                            backupViewModel = viewModel(factory = BackupViewModel.factory(container)),
                            settingsViewModel = viewModel(factory = SettingsViewModel.factory(container)),
                            habiViewModel = habiViewModel,
                            onBack = { nav.popBackStack() },
                            onOpenArchived = { nav.navigate("archived") },
                            onOpenIntro = { nav.navigate("onboarding_replay") },
                            entrada = this,
                        )
                    }
                    // Replay de la introducción desde Ajustes: mismos 7 pasos, nada se persiste — and
                    // `persist = false` is what actually makes that true. The flow's language and
                    // personality steps write to settings the moment they're tapped, not at the end, so
                    // the closing callback alone never stopped a replay from editing real data.
                    composable("onboarding_replay") {
                        OnboardingScreen(
                            viewModel = viewModel(factory = OnboardingViewModel.factory(container, persist = false)),
                            replayOnClose = { nav.popBackStack() },
                        )
                    }
                    composable("archived") {
                        ArchivedScreen(
                            viewModel = viewModel(factory = SettingsViewModel.factory(container)),
                            onBack = { nav.popBackStack() },
                            onOpenHabit = { nav.navigate("detail/$it?lectura=true") },
                        )
                    }
                    composable("habi") {
                        HabiScreen(viewModel = habiViewModel)
                    }
                    // No bottom nav: the review is its own flow, not a bar destination (see the
                    // bottomBar route set above, which deliberately omits "review").
                    composable("review") {
                        ReviewScreen(
                            viewModel = viewModel(factory = ReviewViewModel.factory(container)),
                            onClose = { nav.popBackStack() },
                        )
                    }
                    // D12 revocado: crear y editar una tarea son ahora una pantalla completa, calcada de
                    // la ruta "habit?id={id}" de arriba en el mismo sentido — un solo composable, id nulo
                    // para crear — sin barra inferior, mismo patron de navegacion (push simple, sin
                    // popUpTo ni launchSingleTop: esos dos solo aparecen en las pestanas de la barra
                    // inferior mas abajo, nunca en las rutas de formulario). El "+" navega aqui sin id; el
                    // menu de fila de "tasks" navega con id.
                    composable(
                        "task?id={id}",
                        arguments =
                            listOf(
                                navArgument("id") {
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                    ) { entry ->
                        // El mismo calculo que el sheet global tenia (comentario que se fue con el): today
                        // sale de loadedSettings, probadamente no nulo en este punto, nunca de
                        // tasksViewModel.uiState.today, cuyo combine puede seguir a medias (today = 0 por
                        // defecto) cuando esta ruta compone por primera vez.
                        val today =
                            remember {
                                LogicalDays.logicalDayOf(
                                    System.currentTimeMillis(),
                                    loadedSettings.dayCutoffMinutes,
                                    ZoneId.systemDefault(),
                                )
                            }
                        val id = entry.arguments?.getString("id")
                        if (id == null) {
                            TaskFormScreen(
                                initial = TaskFormState(),
                                today = today,
                                onSave = { saved ->
                                    val id = tasksViewModel.create(saved.title, saved.firstStep, saved.dueKind, saved.resolvedDueDay(today))
                                    dejarCreado(id)
                                    nav.popBackStack()
                                },
                                onBack = { nav.popBackStack() },
                            )
                        } else {
                            // A un solo elemento, el mismo que HabitFormViewModel.init{} hace para
                            // "habit?id={id}" — a diferencia del viejo sheet de TasksScreen, esta ruta
                            // puede componer sin que la lista haya corrido nunca (un enlace directo, un
                            // reinicio a media edicion), asi que el estado inicial no puede salir de una
                            // fila ya en memoria: hay que cargarlo. Un fotograma en blanco (mismo idioma
                            // que las puertas de reconciled/settings de arriba) cubre ese hueco de una
                            // consulta en vez de mostrar el formulario vacio y saltar luego a los campos
                            // reales.
                            var loaded by remember(id) { mutableStateOf<TaskFormState?>(null) }
                            LaunchedEffect(id) {
                                // La tarea pudo desaparecer entre el menu de la fila y que esta ruta
                                // cargue (borrado desde otra pantalla, reinicio a media edicion): abrir
                                // un formulario en blanco para ese id dejaria "Guardar" sin escribir nada
                                // (TasksRepository.update sale por su propio `?: return@withTransaction`),
                                // asi que se vuelve atras sin enseñar nada en vez de fingir que hay algo que editar.
                                // Main.immediate por lo mismo que el `reconciled` de arriba: popBackStack
                                // toca el LifecycleRegistry de la entrada y exige el hilo principal.
                                withContext(Dispatchers.Main.immediate) {
                                    val entity = container.tasks.task(id)
                                    if (entity == null) {
                                        nav.popBackStack()
                                    } else {
                                        loaded = entity.toFormState()
                                    }
                                }
                            }
                            val initial = loaded
                            if (initial == null) {
                                Box(Modifier.fillMaxSize().background(Papel).testTag("task-form-loading"))
                            } else {
                                TaskFormScreen(
                                    initial = initial,
                                    today = today,
                                    onSave = { saved ->
                                        tasksViewModel.edit(id, saved.title, saved.firstStep, saved.dueKind, saved.resolvedDueDay(today))
                                        nav.popBackStack()
                                    },
                                    onBack = { nav.popBackStack() },
                                    onDelete = {
                                        tasksViewModel.delete(id)
                                        nav.popBackStack()
                                    },
                                )
                            }
                        }
                    }
                    // Sin barra inferior: la lista es un destino secundario colgado de Hoy, como review.
                    composable("tasks") {
                        TasksScreen(
                            viewModel = viewModel(factory = TasksViewModel.factory(container)),
                            onBack = { nav.popBackStack() },
                            onStartFocus = { nav.navigate("focus?taskId=$it") },
                            onEditTask = { nav.navigate("task?id=$it") },
                        )
                    }
                    // El argumento es opcional a proposito: sin defaultValue = null, navegar a "focus" a
                    // secas (la notificacion permanente, y el sheet de conflicto de esta misma pantalla)
                    // no casaria con este patron y navigate() lanzaria IllegalArgumentException. Sin barra
                    // inferior, como "review" y "tasks" arriba — una sesion en marcha no es una pestana.
                    // "focus" gana la allowlist de NavRequests (arriba en este fichero) precisamente para
                    // que esa notificacion pueda pedirla.
                    composable(
                        "focus?taskId={taskId}",
                        arguments =
                            listOf(
                                navArgument("taskId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                    ) { entry ->
                        FocusScreen(
                            viewModel =
                                viewModel(factory = FocusViewModel.factory(container, context, entry.arguments?.getString("taskId"))),
                            onClose = { nav.popBackStack() },
                            // keepOther() no escribe nada — el conflicto se resuelve solo a favor de la
                            // sesion viva, que este estado no lleva id (solo su titulo en busyWith). Volver
                            // a la ruta desnuda es el mismo camino que ya usa la notificacion permanente
                            // para resolver contra la sesion viva, sin anadir un id a FocusUiState. popUpTo
                            // inclusive reemplaza esta entrada en vez de apilar una segunda "focus": sin
                            // esto, ATRAS desde la sesion viva volvia a la pantalla del conflicto (con
                            // busyWith todavia no nulo), que reabria la misma hoja y dejaba al usuario sin
                            // salida mientras la otra sesion siguiera en marcha.
                            onKeepOther = {
                                nav.navigate("focus") {
                                    popUpTo("focus?taskId={taskId}") { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                    // M11: sin argumentos — el modo inicial sale de breathingLastMode. Fuera de la barra
                    // inferior como review, tasks y focus: respirar no es una pestana. En la lista blanca de
                    // NavRequests para que la notificacion del recordatorio la abra (en reposo: el sonido
                    // y la vibracion no arrancan sin que el usuario lo pida). ViewModel por entrada: su
                    // guardado no depende de este scope (va en NonCancellable) y onClose solo llega cuando
                    // la escritura ya termino.
                    composable("breathing") {
                        BreathingScreen(
                            viewModel = viewModel(factory = BreathingViewModel.factory(container) { AndroidBreathingMusic(context) }),
                            onClose = { nav.popBackStack() },
                        )
                    }
                }
            }
        }

        // T11 (+ M9.5 T6): the one bridge from an Intent (a notification tap) to this NavHost.
        // Declared alongside NavHost, not as a sibling of the outer Scaffold — Scaffold subcomposes
        // its content (this whole block) lazily during measurement, so an effect placed outside it
        // would fire before NavHost has set the nav graph and crash navigating anywhere. A route
        // already pending when this composes (MainActivity decoded it before setContent) fires
        // on the very first LaunchedEffect run, same as one that arrives later via onNewIntent.
        //
        // While `onboarding` is the current route, a request must not navigate OVER the flow —
        // same philosophy as the celebration guard below (a mid-flow user shouldn't get sandwiched
        // into e.g. "review"), and genuinely the same MECHANISM this time too: just don't consume.
        // NavRequests.pending is a process-wide MutableStateFlow, the one source of truth for "a
        // route is waiting" — it already survives an Activity recreation on its own, so the fix is
        // to leave it alone (return@LaunchedEffect without calling NavRequests.consume()) rather
        // than copy it into ephemeral remember-scoped state, which an earlier version of this guard
        // did and which reset to nothing on rotation. Once onboarding's own `done` effect above
        // navigates to "today", `currentRoute` changes, this effect re-runs (keyed on it), and the
        // still-pending route fires through the branch below like any other request — no extra
        // state, no ordering argument between two navigate() calls to get right. `currentRoute ==
        // null` gets the same non-consuming treatment for the same reason: NavHost hasn't
        // necessarily set its first back-stack entry the very first time this composes, and a
        // request landing in that narrow window must wait for a REAL route rather than being
        // dropped.
        val pendingRoute by NavRequests.pending.collectAsStateWithLifecycle()
        LaunchedEffect(pendingRoute, currentRoute) {
            val route = pendingRoute ?: return@LaunchedEffect
            if (currentRoute == null || currentRoute == "onboarding") return@LaunchedEffect
            nav.navigate(route) { launchSingleTop = true }
            NavRequests.consume()
        }

        // POST_NOTIFICATIONS, asked once per install the first time a real route is on screen —
        // hosted here, above the graph, so the single ask covers finishing onboarding, restoring
        // a backup (which never enters onboarding) and updating an older install alike. Renders
        // nothing; see its own KDoc for why this is the moment.
        NotificationPermissionPrompt(container.settings, currentRoute)
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.entra(
    t: TipoTransicion,
    camara: FiniteAnimationSpec<IntOffset>,
    global: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
): EnterTransition =
    when (t) {
        TipoTransicion.BAJA_ENTRA -> slideInVertically(camara) { it }
        TipoTransicion.SUBE_ENTRA -> slideInVertically(camara) { -it / 4 }
        TipoTransicion.FUNDIDO -> fadeIn(tween(Duraciones.REDUCIDO))
        TipoTransicion.FICHA_FUNDIDO -> fadeIn(tween(220, easing = Curvas.out))
        TipoTransicion.CAMARA_ENTRA -> slideInVertically(camara) { it * 24 / 100 } + fadeIn(tween(Duraciones.ENTRAR))
        TipoTransicion.CAMARA_VUELVE_ENTRA -> fadeIn(tween(Duraciones.ENTRAR))
        else -> global()
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.sale(
    t: TipoTransicion,
    camara: FiniteAnimationSpec<IntOffset>,
    global: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
): ExitTransition =
    when (t) {
        TipoTransicion.BAJA_SALE -> slideOutVertically(camara) { -it / 4 }
        TipoTransicion.SUBE_SALE -> slideOutVertically(camara) { it }
        TipoTransicion.FUNDIDO -> fadeOut(tween(Duraciones.REDUCIDO))
        TipoTransicion.FICHA_FUNDIDO -> fadeOut(tween(160, easing = Curvas.out))
        TipoTransicion.CAMARA_SALE -> fadeOut(tween(Duraciones.ENTRAR))
        TipoTransicion.CAMARA_VUELVE_SALE -> slideOutVertically(camara) { it * 24 / 100 } + fadeOut(tween(Duraciones.SALIR))
        else -> global()
    }
