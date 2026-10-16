// Generado por tools/arte/exportar.py. No editar a mano.
package com.alvarotc.bito.ui.garden

import com.alvarotc.bito.R

/** Recursos y posiciones del jardín. Los sitios van en % de la escena, anclados abajo en el centro. */
internal object GardenArt {
    const val SCENE_ASPECT = 1.3389f

    val background: Int = R.drawable.garden_fondo

    /** Tierra de cada parcela, en orden de apertura. */
    val plots: List<GardenPiece> =
        listOf(
            GardenPiece(R.drawable.garden_parcela_1, aspect = 2.2266f, heightRatio = 0.2550f),
            GardenPiece(R.drawable.garden_parcela_2, aspect = 2.1327f, heightRatio = 0.2550f),
            GardenPiece(R.drawable.garden_parcela_3, aspect = 2.1472f, heightRatio = 0.2550f),
            GardenPiece(R.drawable.garden_parcela_4, aspect = 2.2871f, heightRatio = 0.2550f),
            GardenPiece(R.drawable.garden_parcela_5, aspect = 2.2356f, heightRatio = 0.2550f),
            GardenPiece(R.drawable.garden_parcela_6, aspect = 2.4118f, heightRatio = 0.2550f),
        )

    /** Clave de cada especie de [plants], en orden: debe coincidir con GardenSpecies. */
    val plantKeys: List<String> = listOf("hojas-redondas", "girasoles", "lavanda", "manzano", "helechos", "cerezo")

    /** [especie][etapa], en el orden de GardenSpecies y GrowthStage. */
    val plants: List<List<GardenPiece>> =
        listOf(
            listOf(
                GardenPiece(R.drawable.garden_hojas_redondas_1, aspect = 2.7609f, heightRatio = 0.1533f),
                GardenPiece(R.drawable.garden_hojas_redondas_2, aspect = 1.3908f, heightRatio = 0.2907f),
                GardenPiece(R.drawable.garden_hojas_redondas_3, aspect = 0.7791f, heightRatio = 0.5427f),
                GardenPiece(R.drawable.garden_hojas_redondas_4, aspect = 0.9218f, heightRatio = 0.5973f),
                GardenPiece(R.drawable.garden_hojas_redondas_5, aspect = 0.6233f, heightRatio = 1.0000f),
            ),
            listOf(
                GardenPiece(R.drawable.garden_girasoles_1, aspect = 2.2281f, heightRatio = 0.1725f),
                GardenPiece(R.drawable.garden_girasoles_2, aspect = 1.3829f, heightRatio = 0.2661f),
                GardenPiece(R.drawable.garden_girasoles_3, aspect = 0.6580f, heightRatio = 0.5857f),
                GardenPiece(R.drawable.garden_girasoles_4, aspect = 0.6016f, heightRatio = 0.7776f),
                GardenPiece(R.drawable.garden_girasoles_5, aspect = 0.5988f, heightRatio = 1.0000f),
            ),
            listOf(
                GardenPiece(R.drawable.garden_lavanda_1, aspect = 2.7609f, heightRatio = 0.1716f),
                GardenPiece(R.drawable.garden_lavanda_2, aspect = 1.3011f, heightRatio = 0.3463f),
                GardenPiece(R.drawable.garden_lavanda_3, aspect = 0.8141f, heightRatio = 0.5821f),
                GardenPiece(R.drawable.garden_lavanda_4, aspect = 0.6388f, heightRatio = 0.8463f),
                GardenPiece(R.drawable.garden_lavanda_5, aspect = 0.7463f, heightRatio = 1.0000f),
            ),
            listOf(
                GardenPiece(R.drawable.garden_manzano_1, aspect = 2.7609f, heightRatio = 0.1531f),
                GardenPiece(R.drawable.garden_manzano_2, aspect = 1.3908f, heightRatio = 0.2903f),
                GardenPiece(R.drawable.garden_manzano_3, aspect = 0.7791f, heightRatio = 0.5419f),
                GardenPiece(R.drawable.garden_manzano_4, aspect = 0.9388f, heightRatio = 0.6258f),
                GardenPiece(R.drawable.garden_manzano_5, aspect = 0.6190f, heightRatio = 1.0000f),
            ),
            listOf(
                GardenPiece(R.drawable.garden_helechos_1, aspect = 2.7609f, heightRatio = 0.1434f),
                GardenPiece(R.drawable.garden_helechos_2, aspect = 1.2804f, heightRatio = 0.2943f),
                GardenPiece(R.drawable.garden_helechos_3, aspect = 0.7674f, heightRatio = 0.5162f),
                GardenPiece(R.drawable.garden_helechos_4, aspect = 0.9342f, heightRatio = 0.6160f),
                GardenPiece(R.drawable.garden_helechos_5, aspect = 0.7352f, heightRatio = 1.0000f),
            ),
            listOf(
                GardenPiece(R.drawable.garden_cerezo_1, aspect = 2.7609f, heightRatio = 0.1531f),
                GardenPiece(R.drawable.garden_cerezo_2, aspect = 1.3908f, heightRatio = 0.2903f),
                GardenPiece(R.drawable.garden_cerezo_3, aspect = 0.7791f, heightRatio = 0.5419f),
                GardenPiece(R.drawable.garden_cerezo_4, aspect = 0.6458f, heightRatio = 0.7710f),
                GardenPiece(R.drawable.garden_cerezo_5, aspect = 0.6190f, heightRatio = 1.0000f),
            ),
        )

    val specials: Map<String, GardenPiece> =
        mapOf(
            "farolillo" to GardenPiece(R.drawable.garden_farolillo, aspect = 0.6009f, heightRatio = 1.0000f),
            "caminito" to GardenPiece(R.drawable.garden_caminito, aspect = 0.7464f, heightRatio = 1.0000f),
            "estanque" to GardenPiece(R.drawable.garden_estanque, aspect = 1.6528f, heightRatio = 1.0000f),
            "setas" to GardenPiece(R.drawable.garden_setas, aspect = 0.9981f, heightRatio = 1.0000f),
            "cerezo-grande" to GardenPiece(R.drawable.garden_cerezo_grande, aspect = 0.5696f, heightRatio = 1.0000f),
            "arbol-dorado" to GardenPiece(R.drawable.garden_arbol_dorado, aspect = 0.4943f, heightRatio = 1.0000f),
        )

    /** Objetos de jardín de la tienda (banco, regadera): se compran y se colocan. */
    val objectPieces: Map<String, GardenPiece> = emptyMap()

    val habiSpot = GardenSpot(50.0f, 88.0f, 40.0f)

    val plotSpots: List<GardenSpot> =
        listOf(
            GardenSpot(14.0f, 70.0f, 28.0f),
            GardenSpot(84.0f, 68.0f, 29.7f),
            GardenSpot(27.0f, 55.0f, 20.9f),
            GardenSpot(72.0f, 53.0f, 19.9f),
            GardenSpot(19.0f, 88.0f, 39.2f),
            GardenSpot(83.0f, 87.0f, 36.2f),
        )

    val specialSpots: Map<String, GardenSpot> =
        mapOf(
            "farolillo" to GardenSpot(36.0f, 58.0f, 21.7f),
            "caminito" to GardenSpot(34.0f, 99.0f, 22.0f),
            "estanque" to GardenSpot(68.0f, 84.0f, 12.0f),
            "setas" to GardenSpot(86.0f, 95.0f, 13.2f),
            "cerezo-grande" to GardenSpot(60.0f, 43.0f, 21.0f),
            "arbol-dorado" to GardenSpot(44.0f, 41.0f, 20.0f),
        )

    val objectSpots: Map<String, GardenSpot> = emptyMap()
}
