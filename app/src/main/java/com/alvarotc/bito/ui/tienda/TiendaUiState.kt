package com.alvarotc.bito.ui.tienda

/** Las tres pestañas de la tienda, en el orden en que se ven. */
enum class PestanaTienda { HABI, JARDIN, CONGELADORES }

/** Las tres paradas de la luz; [parada] es su sitio en el surco, de 0 a 1. */
enum class LuzTienda(val parada: Float) {
    DIA(0f),
    TARDE(0.5f),
    NOCHE(1f),
}
