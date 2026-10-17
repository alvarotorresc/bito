package com.alvarotc.bito.ui.tienda.congeladores

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.ui.sistema.BitoColores
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.componentes.AvisoEstado
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import com.alvarotc.bito.ui.tienda.CapaMoneda
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.EscenaArmarioEstado
import com.alvarotc.bito.ui.tienda.RelojTienda
import com.alvarotc.bito.ui.tienda.SaldoConRodillo
import com.alvarotc.bito.ui.tienda.TiendaViewModel
import com.alvarotc.bito.ui.tienda.VueloMoneda
import com.alvarotc.bito.ui.tienda.origenMoneda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs

/** Recursos de la app en [idioma] ("es" o "en"). */
internal fun recursos(idioma: String): Resources {
    val base = ApplicationProvider.getApplicationContext<Context>()
    val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(idioma)) }
    return base.createConfigurationContext(cfg).resources
}

/** Cómo se pinta un componente de congeladores en un test: 412 dp de ancho, fondo blanco, en español. */
internal class Banco(
    private val rule: ComposeContentTestRule,
) {
    val sonido = SonidoGrabado()
    val tacto = TactoGrabado()
    lateinit var vista: View
    lateinit var colores: BitoColores

    fun colocar(
        idioma: String = "es",
        ancho: Int = 412,
        contenido: @Composable () -> Unit,
    ) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(idioma)) }
        val ctx = base.createConfigurationContext(cfg)
        rule.setContent {
            CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides cfg) {
                ConSistema(sonido = sonido, tacto = tacto) {
                    vista = LocalView.current
                    colores = BitoTema.colores
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        Box(Modifier.width(ancho.dp)) { contenido() }
                    }
                }
            }
        }
    }

    fun fotograma(): Bitmap {
        rule.waitForIdle()
        val foto = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(foto))
        return foto
    }

    /** El píxel a ([xDp], [yDp]) desde la esquina de arriba a la izquierda de la caja de [etiqueta]. */
    fun pixel(
        foto: Bitmap,
        etiqueta: String,
        xDp: Float,
        yDp: Float,
        indice: Int = 0,
    ): Int {
        val caja = rule.onAllNodesWithTag(etiqueta, useUnmergedTree = true)[indice].fetchSemanticsNode().boundsInRoot
        return foto.getPixel((caja.left + xDp * rule.density.density).toInt(), (caja.top + yDp * rule.density.density).toInt())
    }
}

internal fun cerca(
    a: Int,
    b: Int,
    margen: Int = 14,
) = abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) < margen &&
    abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) < margen &&
    abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) < margen

/**
 * La pestaña Congeladores con el modelo real de la tienda sobre Room en memoria, a escala de pantalla: la cabecera
 * con el saldo (origen de la moneda), el catálogo, la capa de monedas y el aviso. Room, el repositorio y los ajustes
 * son los reales; todo corre por un despachador de prueba que este banco mueve a la vez que el reloj de Compose,
 * así que cada escritura es asíncrona como en la app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TiendaReal(
    val rule: ComposeContentTestRule,
    private val carpeta: File,
) {
    val sonido = SonidoGrabado()
    val tacto = TactoGrabado()
    val vuelo = VueloMoneda()
    val avisos = AvisoEstado()
    val armario = EscenaArmarioEstado()
    private val planificador = TestCoroutineScheduler()
    private val despachador = StandardTestDispatcher(planificador)
    private val alcance = CoroutineScope(despachador)
    private val utc = ZoneId.of("UTC")
    private val ahora = ZonedDateTime.of(2025, 8, 15, 14, 0, 0, 0, utc).toInstant().toEpochMilli()
    private val hoy = LogicalDays.logicalDayOf(ahora, 0, utc)

    lateinit var db: BitoDatabase
    lateinit var rewards: RewardsRepository
    lateinit var vm: TiendaViewModel
    lateinit var vista: View
    lateinit var colores: BitoColores
    var panelVisible by mutableStateOf(true)

    fun avanzar(ms: Int) {
        var resto = ms
        while (resto > 0) {
            val paso = minOf(16, resto)
            planificador.advanceTimeBy(paso.toLong())
            planificador.runCurrent()
            rule.mainClock.advanceTimeBy(paso.toLong())
            ShadowLooper.idleMainLooper()
            resto -= paso
        }
    }

    /** Avanza de 16 en 16 ms hasta que se cumple [condicion]; devuelve los ms que pasaron. */
    fun avanzarHasta(
        tope: Int = 3000,
        condicion: () -> Boolean,
    ): Int {
        var ms = 0
        while (!condicion()) {
            check(ms <= tope) { "la condición no llegó en $tope ms" }
            avanzar(16)
            ms += 16
        }
        return ms
    }

    /** Corre [bloque] en el despachador de la base y espera su resultado. */
    fun <T> ejecutar(bloque: suspend () -> T): T {
        val resultado = alcance.async { bloque() }
        avanzarHasta { resultado.isCompleted }
        return resultado.getCompleted()
    }

    fun abrir(
        saldo: Int,
        congeladores: Int = 0,
        reducido: Boolean = false,
    ) {
        Dispatchers.setMain(despachador)
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        val cfg = Configuration(contexto.resources.configuration).apply { setLocale(Locale.forLanguageTag("es")) }
        val contextoEs = contexto.createConfigurationContext(cfg)
        db =
            Room
                .inMemoryDatabaseBuilder(contexto, BitoDatabase::class.java)
                .setQueryExecutor(despachador.asExecutor())
                .setTransactionExecutor(despachador.asExecutor())
                .allowMainThreadQueries()
                .build()
        rewards = RewardsRepository(db)
        val ajustes =
            SettingsRepository(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher(planificador) + Job())) {
                    File(carpeta, "congeladores.preferences_pb")
                },
            )
        vm =
            TiendaViewModel(
                domainState = DomainStateRepository(db),
                rewards = rewards,
                settings = ajustes,
                reloj = RelojTienda(ahora = { ahora }, zona = { utc }),
                recursos = { contextoEs.resources },
                saved = SavedStateHandle(),
                defaultDispatcher = despachador,
            )
        ejecutar {
            db.pointsLedgerDao().insert(pointsLedgerEntity(id = "saldo", delta = saldo, logicalDay = hoy))
            repeat(congeladores) {
                db.pointsLedgerDao().insert(
                    pointsLedgerEntity(
                        id = "cong-$it",
                        delta = 0,
                        reason = PointsReason.BUY_FREEZER,
                        refId = "semilla-$it",
                        logicalDay = hoy,
                    ),
                )
            }
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalContext provides contextoEs, LocalConfiguration provides cfg) {
                ConSistema(sonido = sonido, tacto = tacto, reducido = reducido) {
                    vista = LocalView.current
                    colores = BitoTema.colores
                    val estado by vm.uiState.collectAsState()
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        if (estado.cargado) {
                            val ctx =
                                ContextoPanel(estado, vm, vm.eventos, { 0f }, vuelo, armario, avisos, sonido, tacto)
                            Column(Modifier.width(412.dp)) {
                                Box(Modifier.height(64.dp)) { SaldoConRodillo(estado.saldo, Modifier.origenMoneda(vuelo)) }
                                if (panelVisible) PanelCongeladores.Catalogo(ctx, Modifier.fillMaxWidth().height(700.dp))
                            }
                        }
                        AvisoHost(avisos, Modifier.align(Alignment.BottomCenter))
                        CapaMoneda(vuelo)
                    }
                }
            }
        }
        avanzarHasta { vm.uiState.value.cargado }
        avanzar(400)
        sonido.sonados.clear()
        tacto.hapticas.clear()
    }

    fun cerrar() {
        alcance.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    fun comprarDeUnToque() {
        rule.onNodeWithTag("tc-buy").performClick()
    }

    fun copos() = rule.onAllNodesWithTag("tc-copo", useUnmergedTree = true)

    fun textos(texto: String) = rule.onAllNodesWithText(texto, useUnmergedTree = true)

    fun compradas() = ejecutar { db.pointsLedgerDao().all().count { it.reason == PointsReason.BUY_FREEZER } }

    fun saldoEnLibro() = ejecutar { db.pointsLedgerDao().all().sumOf { it.delta } }

    fun fotograma(): Bitmap {
        rule.waitForIdle()
        val foto = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(foto))
        return foto
    }

    fun caja(
        etiqueta: String,
        indice: Int = 0,
    ) = rule.onAllNodesWithTag(
        etiqueta,
        useUnmergedTree = true,
    )[indice].fetchSemanticsNode().boundsInRoot

    fun pixel(
        foto: Bitmap,
        etiqueta: String,
        xDp: Float,
        yDp: Float,
        indice: Int = 0,
    ): Int {
        val c = caja(etiqueta, indice)
        return foto.getPixel((c.left + xDp * rule.density.density).toInt(), (c.top + yDp * rule.density.density).toInt())
    }
}
