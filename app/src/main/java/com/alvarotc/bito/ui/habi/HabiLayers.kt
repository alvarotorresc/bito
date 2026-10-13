package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import kotlin.math.min

/** Las capas de Habi en el orden en que se pintan (spec §3.2). IRIS va pegado a EYES; el cuello va sobre la cara. */
enum class HabiPart { SHADOW, BODY, FEET, EYES, IRIS, MOUTH, MARKS, BROWS, NECK, GLASSES, HEAD }

/** Las partes de la expresión: cambian con ánimo y personalidad y se funden entre caras (§3.5). */
val FACE_PARTS: Set<HabiPart> = setOf(HabiPart.EYES, HabiPart.IRIS, HabiPart.MOUTH, HabiPart.MARKS, HabiPart.BROWS)

/** Desplazamiento máximo de la mirada en coordenadas del cuerpo (§3.5; el prototipo usa 22 y 12). */
const val GAZE_MAX_X = 22f
const val GAZE_MAX_Y = 12f

/**
 * Con disfraz la mirada mueve la cara la mitad: el hueco no se mueve, y a mirada entera las cejas
 * duras se salían por el borde (hasta un 4,7 % de la pieza con el dinosaurio); a la mitad, nada visible.
 */
const val COSTUME_GAZE_SCALE = 0.5f

/** Por debajo de este alto se decodifica con submuestreo (§3.3). */
internal const val SUBSAMPLE_BELOW_DP = 100f

/**
 * Una capa: la pieza [key] del atlas, desplazada [dx], [dy] en coordenadas del cuerpo. [iris] pinta
 * su máscara de iris. [masked] pide al pintor recortarla con la máscara del hueco del disfraz
 * (`HabiAtlas.costumeFaces`), que se queda fija en el marco del disfraz aunque la cara se mueva.
 */
data class HabiLayer(
    val key: String,
    val part: HabiPart,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val tint: ColorTint? = null,
    val alpha: Float = 1f,
    val iris: Boolean = false,
    val masked: Boolean = false,
)

/** Lo que cambia fotograma a fotograma, fuera de [HabiSpec]. */
data class HabiLook(
    val delighted: Boolean = false,
    /** 0: los ojos de la expresión; 1: la pieza de ojos cerrados; entre medias, fundido. */
    val eyeClosure: Float = 0f,
    /** Mirada de -1 a 1 en cada eje. */
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
)

enum class Eyes(val key: String) {
    OPEN("ojo-abierto"),
    CLOSED("ojo-cerrado"),
    HAPPY("ojo-feliz"),
    SAD("ojo-triste"),
    ANGRY("ojo-enfadado"),
    TEARY("ojo-lloroso"),
}

enum class Mouth(val key: String) {
    SMILE("boca-sonrisa"),
    HAPPY("boca-feliz"),
    NEUTRAL("boca-neutra"),
    SAD("boca-triste"),
    O("boca-o"),
    GRIMACE("boca-mueca"),
}

enum class Brows(val key: String) {
    HARD("ceja-dura"),
    CHEERFUL("ceja-alegre"),
    WORRIED("ceja-preocupada"),
}

data class HabiFace(
    val eyes: Eyes,
    val mouth: Mouth,
    val brows: Brows?,
)

private val SIDES = listOf("i", "d")

/**
 * La cara de la personalidad por ánimo (§5.2), leída de la tabla generada [HabiFaceTable] (la misma
 * que pinta la galería). `delighted` fuerza ojos y boca felices y conserva las cejas del ánimo;
 * `closedEyes` pone los ojos cerrados y quita las cejas.
 */
fun faceOf(
    mood: Mood,
    personality: Personality,
    delighted: Boolean = false,
    closedEyes: Boolean = false,
): HabiFace {
    val (eyes, mouth, brows) = requireNotNull(HabiFaceTable.faces[mood.name]?.get(personality.name)) { "$mood/$personality" }
    val base =
        HabiFace(
            eyes = Eyes.entries.first { it.key == eyes },
            mouth = Mouth.entries.first { it.key == mouth },
            brows = brows?.let { key -> Brows.entries.first { it.key == key } },
        )
    val cheered = if (delighted) base.copy(eyes = Eyes.HAPPY, mouth = Mouth.HAPPY) else base
    return if (closedEyes) cheered.copy(eyes = Eyes.CLOSED, brows = null) else cheered
}

/** Las marcas que lleva siempre cada personalidad (§5.2), de la tabla generada. */
fun marksOf(personality: Personality): List<String> = HabiFaceTable.marks.getValue(personality.name)

/**
 * Habi como lista de capas en orden de pintado (§3.2). Con disfraz, la pieza del disfraz sustituye
 * al cuerpo sin tinte y se omiten pies, cuello, gafas y cabeza; el color de ojos se mantiene. La
 * cara se desplaza el [CostumeFace] del disfraz (la mirada, a [COSTUME_GAZE_SCALE]) y sus capas van `masked`.
 * Las claves que no estén en el atlas se las salta el pintor; un disfraz sin pieza se ignora entero,
 * para que Habi no se quede sin cuerpo.
 */
internal fun habiLayers(
    spec: HabiSpec,
    look: HabiLook = HabiLook(),
): List<HabiLayer> {
    val equipped = spec.equipped
    val costume = equipped.costume?.takeIf { it in HabiAtlas.pieces }
    val costumeFace = costume?.let { HabiAtlas.costumeFaces[it] }
    val masked = costumeFace != null
    val face = faceOf(spec.mood, spec.personality, look.delighted, spec.closedEyes)
    val gaze = if (costumeFace != null) COSTUME_GAZE_SCALE else 1f
    val dx = look.gazeX.coerceIn(-1f, 1f) * GAZE_MAX_X * gaze + (costumeFace?.dx ?: 0f)
    val dy = look.gazeY.coerceIn(-1f, 1f) * GAZE_MAX_Y * gaze + (costumeFace?.dy ?: 0f)
    val eyeTint = HabiTintTable.eyes[equipped.eyeColor]
    val closure = if (face.eyes == Eyes.CLOSED) 0f else look.eyeClosure.coerceIn(0f, 1f)
    val layers = mutableListOf(HabiLayer("sombra", HabiPart.SHADOW))

    if (costume != null) {
        layers += HabiLayer(costume, HabiPart.BODY)
    } else {
        layers += HabiLayer("cuerpo", HabiPart.BODY, tint = spec.bodyToneOverride ?: HabiTintTable.body[equipped.bodyColor])
        equipped.feet?.let { layers += HabiLayer(it, HabiPart.FEET) }
    }

    fun eyes(
        kind: Eyes,
        alpha: Float,
    ) {
        if (alpha <= 0f) return
        SIDES.forEach { layers += HabiLayer("${kind.key}-$it", HabiPart.EYES, dx, dy, alpha = alpha, masked = masked) }
        if (eyeTint != null && kind != Eyes.CLOSED) {
            SIDES.forEach { layers += HabiLayer("${kind.key}-$it", HabiPart.IRIS, dx, dy, eyeTint, alpha, iris = true, masked = masked) }
        }
    }
    eyes(face.eyes, 1f - closure)
    eyes(Eyes.CLOSED, closure)
    layers += HabiLayer(face.mouth.key, HabiPart.MOUTH, dx, dy, masked = masked)
    marksOf(spec.personality).forEach { layers += HabiLayer(it, HabiPart.MARKS, dx, dy, masked = masked) }
    face.brows?.let { brows -> SIDES.forEach { layers += HabiLayer("${brows.key}-$it", HabiPart.BROWS, dx, dy, masked = masked) } }

    if (costume == null) {
        // El cuello va sobre boca, marcas y cejas: la bufanda tapa lo que asome por debajo.
        equipped.neck?.let { layers += HabiLayer(it, HabiPart.NECK) }
        // Las gafas siguen la mirada como la cara, pero no van enmascaradas (sin disfraz no hay máscara).
        equipped.face?.let { layers += HabiLayer(it, HabiPart.GLASSES, dx, dy) }
        equipped.head?.let { layers += HabiLayer(it, HabiPart.HEAD) }
    }
    return layers
}

/** El fundido de expresión (§3.5): el cuerpo y lo puesto de [to]; la cara de [from] saliendo y la de [to] entrando. */
internal fun crossfadeFace(
    from: List<HabiLayer>,
    to: List<HabiLayer>,
    progress: Float,
): List<HabiLayer> {
    if (progress >= 1f) return to
    val p = progress.coerceIn(0f, 1f)
    val before = to.filter { it.part.ordinal < HabiPart.EYES.ordinal }
    val after = to.filter { it.part.ordinal > HabiPart.BROWS.ordinal }
    val outgoing = from.filter { it.part in FACE_PARTS }.map { it.copy(alpha = it.alpha * (1f - p)) }
    val incoming = to.filter { it.part in FACE_PARTS }.map { it.copy(alpha = it.alpha * p) }
    return before + outgoing + incoming + after
}

/** Dónde cae el origen del cuerpo y a qué escala, para que el lienzo del rig quepa centrado y apoyado abajo. */
data class RigFit(
    val scale: Float,
    val originX: Float,
    val originY: Float,
)

/**
 * El lienzo exportado ya es simétrico en horizontal respecto al cuerpo (C11), así que centrar el
 * lienzo centra a Habi.
 */
internal fun rigFit(
    width: Float,
    height: Float,
    canvas: RigCanvas = HabiAtlas.canvas,
): RigFit {
    val scale = min(width / canvas.width, height / canvas.height)
    val left = (width - canvas.width * scale) / 2f
    val top = height - canvas.height * scale
    return RigFit(scale, left - canvas.left * scale, top - canvas.top * scale)
}

/**
 * El submuestreo (1, 2, 4…) con el que decodificar para pintar a [drawHeightPx]: siempre 1 a partir
 * de [SUBSAMPLE_BELOW_DP]; por debajo, la mayor potencia de dos que no baja del alto que se pinta.
 */
internal fun sampleSizeFor(
    drawHeightPx: Float,
    drawHeightDp: Float,
    sourceHeightPx: Float = HabiAtlas.SOURCE_CANVAS_PX,
): Int {
    if (drawHeightDp >= SUBSAMPLE_BELOW_DP) return 1
    var sample = 1
    while (sourceHeightPx / (sample * 2) >= drawHeightPx) sample *= 2
    return sample
}
