package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.ui.habi.HabiPart
import com.alvarotc.bito.ui.sistema.Curvas
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Muelles
import com.alvarotc.bito.ui.sistema.Tacto
import com.alvarotc.bito.ui.sistema.sonido.MotorSonido
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tienda.EscenaArmarioEstado
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// `prueba-cae`, `prueba-brota` y `movimiento.md` §4 (tienda-habi.js).
// La caída (`fall`) y el brote (`sprout`) están en píxeles del lienzo del arte: la pieza es hija del rig del mock,
// que se escala por alto/1404. Aquí son unidades del lienzo de Habi y se pasan a dp con [CambioArmario.dpPorUnidad].
internal const val CAIDA_LIENZO = 380f
internal const val BROTE_LIENZO = 260f

/** El suelo del recorte del brote: 156 unidades del lienzo por debajo de los pies (`G0 = 1860` del mock). */
internal const val SUELO_BAJO_PIES = 156f
private const val ALFA_INICIO = 0.2f
private const val REBOTE_DP = 3f
internal const val GRANOS_BROTE = GranosBrote.N
private const val FUNDIDO_MS = 180

// Cuánto se aplasta Habi (por ciento) y en cuánto va y vuelve.
private const val APLASTE_CAE = 0.07f
private const val APLASTE_BROTE = 0.04f
private const val APLASTE_FUNDE = 0.06f
private const val APLASTE_IDA_MS = 80
private const val APLASTE_VUELTA_MS = 240

/** Cómo llega una pieza a Habi al probarla: cae, brota de la tierra o se funde. */
internal enum class GestoPrueba { CAE, BROTA, FUNDE }

/** Cabeza, cara y cuello caen; los pies brotan; color, ojos y disfraz se funden. */
internal fun gestoDe(categoria: CustomizationCategory): GestoPrueba =
    when (categoria) {
        CustomizationCategory.HEAD, CustomizationCategory.FACE, CustomizationCategory.NECK -> GestoPrueba.CAE
        CustomizationCategory.FEET -> GestoPrueba.BROTA
        else -> GestoPrueba.FUNDE
    }

private fun GestoPrueba.aplaste(): Float =
    when (this) {
        GestoPrueba.CAE -> APLASTE_CAE
        GestoPrueba.BROTA -> APLASTE_BROTE
        GestoPrueba.FUNDE -> APLASTE_FUNDE
    }

/** La capa del atlas con la que se pinta la pieza suelta mientras cae o brota. */
internal fun parteDe(categoria: CustomizationCategory): HabiPart =
    when (categoria) {
        CustomizationCategory.FACE -> HabiPart.GLASSES
        CustomizationCategory.NECK -> HabiPart.NECK
        CustomizationCategory.FEET -> HabiPart.FEET
        else -> HabiPart.HEAD
    }

/** Lo que ha cambiado en lo que se ve de Habi entre dos estados de la tienda. */
internal sealed interface EventoArmario {
    data object Ninguno : EventoArmario

    /** Una pieza nueva a la vista: probada, o puesta porque ya era tuya. */
    data class Pone(val itemId: String, val categoria: CustomizationCategory) : EventoArmario

    /** Se va la prueba ([dePrueba]: ya suena en la pantalla) o se quita lo puesto (no suena en ningún otro sitio). */
    data class Quita(val dePrueba: Boolean) : EventoArmario
}

private fun EquippedSet.en(categoria: CustomizationCategory): String? =
    when (categoria) {
        CustomizationCategory.HEAD -> head
        CustomizationCategory.FACE -> face
        CustomizationCategory.NECK -> neck
        CustomizationCategory.FEET -> feet
        CustomizationCategory.BODY_COLOR -> bodyColor
        CustomizationCategory.EYE_COLOR -> eyeColor
        CustomizationCategory.COSTUME -> costume
        CustomizationCategory.GARDEN_OBJECT -> null
    }

private val EJES =
    listOf(
        CustomizationCategory.COSTUME,
        CustomizationCategory.HEAD,
        CustomizationCategory.FACE,
        CustomizationCategory.NECK,
        CustomizationCategory.FEET,
        CustomizationCategory.BODY_COLOR,
        CustomizationCategory.EYE_COLOR,
    )

/** Habi sin nada en la ranura de [categoria]: la pieza de antes se va en el acto y solo se mueve la nueva. */
private fun EquippedSet.sinRanura(categoria: CustomizationCategory): EquippedSet =
    when (categoria) {
        CustomizationCategory.HEAD -> copy(head = null)
        CustomizationCategory.FACE -> copy(face = null)
        CustomizationCategory.NECK -> copy(neck = null)
        CustomizationCategory.FEET -> copy(feet = null)
        else -> this
    }

/**
 * Qué ha pasado entre [previo] y [nuevo]. Si se iba una prueba ([habiaPrueba] y no [hayPrueba]) es quitar;
 * si algo nuevo aparece, es poner (aunque se vaya el disfraz de paso); si solo desaparece, es quitar.
 */
internal fun clasificarCambio(
    previo: EquippedSet,
    nuevo: EquippedSet,
    habiaPrueba: Boolean,
    hayPrueba: Boolean,
): EventoArmario {
    val distintos = EJES.filter { previo.en(it) != nuevo.en(it) }
    if (distintos.isEmpty()) return EventoArmario.Ninguno
    if (habiaPrueba && !hayPrueba) return EventoArmario.Quita(dePrueba = true)
    val ganada = distintos.firstOrNull { nuevo.en(it) != null }
    return if (ganada != null) EventoArmario.Pone(requireNotNull(nuevo.en(ganada)), ganada) else EventoArmario.Quita(dePrueba = false)
}

/**
 * Un cambio de lo que se ve en el armario y su animación. Se crea en composición, con sus animaciones a
 * cero, para que el primer fotograma ya sea el de salida (la pieza arriba, no puesta). [base] es lo que
 * pinta Habi mientras tanto; el resto lo pintan las capas encima.
 */
@Stable
internal class CambioArmario(
    val previo: EquippedSet,
    val nuevo: EquippedSet,
    val evento: EventoArmario,
    val reducido: Boolean,
) {
    var listo by mutableStateOf(evento is EventoArmario.Ninguno)
        private set

    private val categoria = (evento as? EventoArmario.Pone)?.categoria

    /** Con modo reducido todo se funde. */
    private val gestoPropio: GestoPrueba? =
        when {
            evento is EventoArmario.Ninguno -> null
            evento is EventoArmario.Quita || reducido -> GestoPrueba.FUNDE
            else -> gestoDe(requireNotNull(categoria))
        }

    val gesto: GestoPrueba? get() = if (listo) null else gestoPropio

    val itemId: String? = (evento as? EventoArmario.Pone)?.itemId

    /** El disfraz que se va al poner una pieza que cae o brota. */
    val disfrazSaliente: String? =
        if (gestoPropio == GestoPrueba.CAE || gestoPropio == GestoPrueba.BROTA) previo.costume.takeIf { nuevo.costume == null } else null

    private var disfrazVivo by mutableStateOf(disfrazSaliente != null)

    val disfrazSalienteVivo: String? get() = disfrazSaliente.takeIf { disfrazVivo && !listo }

    internal val progreso = Animatable(0f)
    internal val rebote = Animatable(0f)
    internal val granosAnim = Animatable(0f)
    internal val fundidoAnim = Animatable(if (evento is EventoArmario.Quita || disfrazSaliente != null) 1f else 0f)

    /** Cuántos dp mide una unidad del lienzo de Habi. Lo fija la escena al componer, con la caja de Habi. */
    var dpPorUnidad: Float = 0f

    val caidaDp: Float get() = if (gesto == GestoPrueba.CAE) CAIDA_LIENZO * dpPorUnidad * (1f - progreso.value) else 0f
    val alfaPieza: Float get() = if (gesto == GestoPrueba.CAE) ALFA_INICIO + (1f - ALFA_INICIO) * progreso.value.coerceIn(0f, 1f) else 1f
    val reboteDp: Float get() = rebote.value
    val brotarDp: Float get() = if (gesto == GestoPrueba.BROTA) BROTE_LIENZO * dpPorUnidad * (1f - progreso.value) else 0f
    val granos: Int get() = if (gesto == GestoPrueba.BROTA) GRANOS_BROTE else 0
    val progresoGranos: Float get() = granosAnim.value
    val fundido: Float get() = fundidoAnim.value

    /** Qué pinta Habi debajo de las capas. */
    val base: EquippedSet
        get() =
            when {
                listo -> nuevo
                gestoPropio == GestoPrueba.CAE || gestoPropio == GestoPrueba.BROTA -> nuevo.sinRanura(requireNotNull(categoria))
                evento is EventoArmario.Pone -> previo
                else -> nuevo
            }

    /** Qué Habi se funde encima: la nueva al entrar, la anterior al salir. Nulo si no hay. */
    val fundida: EquippedSet?
        get() =
            when {
                listo -> null
                evento is EventoArmario.Quita -> previo
                disfrazSalienteVivo != null -> previo
                gestoPropio == GestoPrueba.FUNDE -> nuevo
                else -> null
            }

    private val duracionFundido get() = if (reducido) Duraciones.REDUCIDO else FUNDIDO_MS

    private fun senal(
        sonido: MotorSonido,
        tacto: Tacto,
        snd: Snd?,
        h: Haptica,
    ) {
        snd?.let { sonido.play(it, 1f) }
        tacto.haptica(h)
    }

    /** Anima el cambio: sonido y háptica en el mismo instante que lo que se ve. */
    suspend fun reproducir(
        armario: EscenaArmarioEstado,
        sonido: MotorSonido,
        tacto: Tacto,
    ) {
        val gestoActual = gestoPropio ?: return
        try {
            coroutineScope {
                when {
                    evento is EventoArmario.Quita -> {
                        // Quitar la prueba ya suena en la pantalla; quitar lo puesto solo suena aquí.
                        if (!evento.dePrueba) senal(sonido, tacto, Snd.HUNDIR, Haptica.LOW)
                        fundidoAnim.animateTo(0f, tween(duracionFundido, easing = Curvas.out))
                        listo = true
                    }
                    gestoPropio == GestoPrueba.FUNDE -> {
                        val categoriaPropia = requireNotNull(categoria)
                        val real = gestoDe(categoriaPropia)
                        senal(sonido, tacto, if (real == GestoPrueba.CAE) Snd.POSAR else Snd.BROTE, Haptica.TICK)
                        launch { armario.aplastar(real.aplaste(), APLASTE_IDA_MS, APLASTE_VUELTA_MS) }
                        fundidoAnim.animateTo(1f, tween(duracionFundido, easing = Curvas.out))
                        listo = true
                        llegada(real, sonido, tacto)
                    }
                    gestoPropio == GestoPrueba.CAE -> {
                        senal(sonido, tacto, Snd.POSAR, Haptica.TICK)
                        saleElDisfraz()
                        progreso.animateTo(1f, tween(Duraciones.CAER, easing = Curvas.cae))
                        llegada(gestoActual, sonido, tacto)
                        launch { armario.aplastar(APLASTE_CAE, APLASTE_IDA_MS, APLASTE_VUELTA_MS) }
                        rebote.snapTo(-REBOTE_DP)
                        rebote.animateTo(0f, Muelles.caida<Float>())
                        listo = true
                    }
                    else -> {
                        senal(sonido, tacto, Snd.BROTE, Haptica.TICK)
                        saleElDisfraz()
                        launch { granosAnim.animateTo(1f, tween(GranosBrote.TOTAL_MS, easing = Curvas.lin)) }
                        launch { armario.aplastar(APLASTE_BROTE, APLASTE_IDA_MS, APLASTE_VUELTA_MS) }
                        progreso.animateTo(1f, Muelles.hoja<Float>())
                        llegada(gestoActual, sonido, tacto)
                        listo = true
                    }
                }
            }
        } finally {
            // Interrumpido: Habi no se queda aplastada a medias; vuelve desde donde estaba.
            if (armario.escalaY < 1f) withContext(NonCancellable) { armario.aplastar(1f - armario.escalaY, 0, APLASTE_VUELTA_MS) }
        }
    }

    /** El disfraz que tapaba la ranura se funde fuera a la vez. */
    private suspend fun saleElDisfraz() =
        coroutineScope {
            if (disfrazSaliente != null) {
                launch {
                    fundidoAnim.animateTo(0f, tween(FUNDIDO_MS, easing = Curvas.out))
                    disfrazVivo = false
                }
            }
        }

    /** Lo que suena y vibra al llegar la pieza: el golpe en el suelo. */
    private fun llegada(
        gesto: GestoPrueba,
        sonido: MotorSonido,
        tacto: Tacto,
    ) {
        when (gesto) {
            GestoPrueba.CAE -> senal(sonido, tacto, null, Haptica.LOW)
            GestoPrueba.BROTA -> senal(sonido, tacto, Snd.POSAR, Haptica.LOW)
            GestoPrueba.FUNDE -> Unit
        }
    }
}

/**
 * Lo que se ve del cambio en curso, para las pruebas. Apunta a lo mismo que pinta la escena; no guarda nada.
 */
@Stable
internal class AnimacionPrueba {
    internal var cambio by mutableStateOf<CambioArmario?>(null)

    val gesto: GestoPrueba? get() = cambio?.gesto
    val itemId: String? get() = cambio?.itemId
    val disfrazSaliente: String? get() = cambio?.disfrazSalienteVivo
    val caidaDp: Float get() = cambio?.caidaDp ?: 0f
    val alfaPieza: Float get() = cambio?.alfaPieza ?: 1f
    val reboteDp: Float get() = cambio?.reboteDp ?: 0f
    val brotarDp: Float get() = cambio?.brotarDp ?: 0f
    val granos: Int get() = cambio?.granos ?: 0
    val progresoGranos: Float get() = cambio?.progresoGranos ?: 0f
    val fundido: Float get() = cambio?.fundido ?: 0f
    val base: EquippedSet? get() = cambio?.base
}
