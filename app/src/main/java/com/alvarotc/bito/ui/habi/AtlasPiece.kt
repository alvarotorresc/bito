package com.alvarotc.bito.ui.habi

/**
 * Una pieza pintada de Habi: su recurso y su rectángulo en coordenadas del cuerpo (el cuerpo mide
 * HabiAtlas.BODY_WIDTH x BODY_HEIGHT con origen en su esquina superior izquierda). [iris] es la
 * máscara del iris de los ojos que la tienen: el color de ojos solo tiñe esa máscara.
 * Lo genera tools/arte/exportar.py.
 */
data class AtlasPiece(
    val res: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val iris: Int? = null,
)

/** El rectángulo que cubre todas las piezas, en coordenadas del cuerpo. El margen lo calcula exportar.py. */
data class RigCanvas(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * La cara con un disfraz puesto: toda ella (ojos, boca, marcas y cejas) se desplaza ([dx], [dy]) en
 * coordenadas del cuerpo y se recorta con [mask], el hueco del disfraz, que no se mueve con la cara.
 * Lo genera tools/arte/exportar.py.
 */
data class CostumeFace(
    val dx: Float,
    val dy: Float,
    val mask: AtlasPiece,
)
