package com.alvarotc.bito.ui.tienda

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.garden.GardenSpot
import com.alvarotc.bito.ui.garden.SceneRect
import com.alvarotc.bito.ui.garden.habiRect
import com.alvarotc.bito.ui.habi.HabiAtlas
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.SondaSalto
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.delay

/** Las medidas del armario (`#tscene`, `#tnicho` de tienda-habi.css y la colocación de Habi en `tienda-habi.js`). */
internal object MedidasArmario {
    val escenaAlto = 300.dp
    val nichoArriba = 58.dp
    val nichoLados = 40.dp
    val radioArco = 200.dp
    val radioBase = 24.dp

    /** Habi: pies al 90 % de la hornacina, centrada, con el cuerpo al 70 % de su alto (165 dp en 236). */
    val sitioHabi = GardenSpot(xPct = 50f, yPct = 90f, heightPct = 70f)

    fun habiCuerpo(altoNicho: Float): Float = altoNicho * sitioHabi.heightPct / 100f

    /** La caja del lienzo de Habi dentro de una hornacina de [ancho] x [alto]. */
    fun cajaHabi(
        ancho: Float,
        alto: Float,
    ): SceneRect = habiRect(ancho, alto, sitioHabi)
}

// Saltito de Habi al tocarla: `jump()` del sistema en el mock (amplitud 0,38).
private const val SALTO_TOQUE = 0.38f

// Con una amplitud mayor de esta, la alegría dura más (`cheer` del mock).
private const val SALTO_ALTO = 0.2f
private const val ALEGRIA_ALTO_MS = 1200L
private const val ALEGRIA_BAJO_MS = 800L

// `squash(a)` del mock estira un 80 % de lo que aplasta.
private const val ENSANCHA = 0.8f

/**
 * Los canales de Habi en el armario que no son suyos: el salto que pide la tienda y el aplastamiento al
 * posar algo. [saltar] y [aplastar] se interrumpen entre sí; en modo reducido no hacen nada.
 */
@Stable
class EscenaArmarioEstado {
    internal var cuenta by mutableIntStateOf(0)
    internal var amplitud by mutableStateOf<Float?>(null)
    internal var reducido = false
    internal var sonda: SondaSalto? = null
    private val aplaste = Animatable(0f)

    /** Cuánto se ve aplastada Habi en vertical: 1 en reposo. Se lee en la fase de dibujo. */
    val escalaY: Float get() = 1f - aplaste.value

    internal val escalaX: Float get() = 1f + ENSANCHA * aplaste.value

    /** Habi salta con [amplitud] (fracción de su alto, como el salto del sistema) y se alegra un rato. */
    fun saltar(amplitud: Float) {
        if (reducido) return
        this.amplitud = amplitud
        cuenta++
    }

    /** Aplasta a Habi un [pct] desde los pies en [ida] ms y la suelta en [vuelta] ms. Nunca baja de cero. */
    suspend fun aplastar(
        pct: Float,
        ida: Int,
        vuelta: Int,
    ) {
        if (reducido) return
        aplaste.animateTo(pct.coerceIn(0f, MAX_APLASTE), tween(ida.coerceAtLeast(0), easing = Curvas.lin))
        aplaste.animateTo(0f, tween(vuelta.coerceAtLeast(0), easing = Curvas.out))
    }

    private companion object {
        const val MAX_APLASTE = 0.95f
    }
}

/**
 * El armario de la tienda: una hornacina de 332 x 236 dp con Habi de pie (165 dp), la luz multiplicada solo
 * sobre ella y una costra de 6 dp debajo. En total 306 dp de alto: los 300 de la escena más la costra.
 * [luz] (0 a 1) se lee en la fase de dibujo, nunca en composición. [capaSuperior] se pinta dentro del recorte,
 * encima de Habi y bajo la luz.
 */
@Composable
fun EscenaArmario(
    look: EquippedSet,
    luz: () -> Float,
    estado: EscenaArmarioEstado,
    alegre: Boolean = false,
    capaSuperior: @Composable BoxScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colores = BitoTema.colores
    val reducido = BitoTema.reducido
    val gesto = rememberGesto()
    estado.reducido = reducido
    var alegria by remember { mutableStateOf(false) }
    LaunchedEffect(estado.cuenta) {
        if (estado.cuenta == 0) return@LaunchedEffect
        alegria = true
        delay(if ((estado.amplitud ?: 0f) > SALTO_ALTO) ALEGRIA_ALTO_MS else ALEGRIA_BAJO_MS)
        alegria = false
    }
    val forma =
        RoundedCornerShape(
            topStart = MedidasArmario.radioArco,
            topEnd = MedidasArmario.radioArco,
            bottomEnd = MedidasArmario.radioBase,
            bottomStart = MedidasArmario.radioBase,
        )

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .testTag("tscene")
                .fillMaxWidth()
                .height(MedidasArmario.escenaAlto)
                .background(colores.tAlta)
                .padding(
                    start = MedidasArmario.nichoLados,
                    end = MedidasArmario.nichoLados,
                    top = MedidasArmario.nichoArriba,
                    bottom = Medidas.costra,
                ),
        ) {
            Box(
                Modifier
                    .testTag("tnicho")
                    .fillMaxWidth()
                    .height(Medidas.hornacinaArmario)
                    // Una capa propia para que la luz multiplique solo lo de dentro de la hornacina.
                    .graphicsLayer {
                        shape = forma
                        clip = true
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .background(colores.hornacina)
                    .drawWithContent {
                        drawContent()
                        val v = luz()
                        drawRect(
                            brush = Brush.verticalGradient(listOf(LuzArmario.arriba(v), LuzArmario.abajo(v))),
                            blendMode = BlendMode.Multiply,
                        )
                    },
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth().height(Medidas.hornacinaArmario)) {
                    val caja = MedidasArmario.cajaHabi(maxWidth.value, maxHeight.value)
                    // Anclado en los pies (el cuerpo termina al 90 %): el aplastamiento nace del suelo.
                    val pies = (HabiAtlas.BODY_HEIGHT - HabiAtlas.canvas.top) / HabiAtlas.canvas.height
                    Box(
                        Modifier
                            .offset(caja.left.dp, caja.top.dp)
                            .size(caja.width.dp, caja.height.dp)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, pies)
                                scaleY = estado.escalaY
                                scaleX = estado.escalaX
                            }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                estado.saltar(SALTO_TOQUE)
                                gesto(Snd.HABI, Haptica.TICK)
                            },
                    ) {
                        HabiAvatar(
                            spec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, look),
                            modifier = Modifier.fillMaxSize(),
                            delighted = alegre || alegria,
                            nudge = estado.cuenta,
                            nudgeSalto = estado.amplitud,
                            sondaSalto = estado.sonda,
                        )
                    }
                }
                capaSuperior()
            }
        }
        Box(Modifier.testTag("tcostra").fillMaxWidth().height(Medidas.costra).background(colores.costra))
    }
}
