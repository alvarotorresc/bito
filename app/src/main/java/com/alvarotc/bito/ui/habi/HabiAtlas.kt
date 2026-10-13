// Generado por tools/arte/exportar.py. No editar a mano.
package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.R

/** Las piezas de Habi con su rectángulo en coordenadas del cuerpo. */
internal object HabiAtlas {
    const val BODY_WIDTH = 1132.0f
    const val BODY_HEIGHT = 1404.0f

    /** Alto del lienzo a la resolución exportada: la referencia del submuestreo. */
    const val SOURCE_CANVAS_PX = 989.2f

    /**
     * El cuerpo ocupa (0, 0)-(BODY_WIDTH, BODY_HEIGHT); el resto es el margen calculado, simétrico en
     * horizontal para que centrar el lienzo centre a Habi.
     */
    val canvas = RigCanvas(left = -212.0f, top = -232.0f, width = 1556.0f, height = 1736.0f)

    val pieces: Map<String, AtlasPiece> =
        mapOf(
            "sombra" to AtlasPiece(R.drawable.habi_sombra, -28.0f, 1285.0f, 1189.0f, 202.0f),
            "cuerpo" to AtlasPiece(R.drawable.habi_cuerpo, 2.0f, 5.0f, 1124.0f, 1392.0f),
            "ojo-abierto-i" to
                AtlasPiece(
                    R.drawable.habi_ojo_abierto_i,
                    350.5f,
                    445.2f,
                    162.0f,
                    162.0f,
                    iris = R.drawable.habi_ojo_abierto_i_iris,
                ),
            "ojo-abierto-d" to
                AtlasPiece(
                    R.drawable.habi_ojo_abierto_d,
                    736.5f,
                    445.2f,
                    162.0f,
                    162.0f,
                    iris = R.drawable.habi_ojo_abierto_d_iris,
                ),
            "ojo-cerrado-i" to AtlasPiece(R.drawable.habi_ojo_cerrado_i, 352.8f, 494.8f, 159.8f, 69.8f),
            "ojo-cerrado-d" to AtlasPiece(R.drawable.habi_ojo_cerrado_d, 738.8f, 494.8f, 159.8f, 69.8f),
            "ojo-feliz-i" to AtlasPiece(R.drawable.habi_ojo_feliz_i, 352.8f, 490.2f, 157.5f, 78.8f),
            "ojo-feliz-d" to AtlasPiece(R.drawable.habi_ojo_feliz_d, 741.0f, 490.2f, 157.5f, 78.8f),
            "ojo-triste-i" to
                AtlasPiece(
                    R.drawable.habi_ojo_triste_i,
                    348.2f,
                    458.8f,
                    168.8f,
                    144.0f,
                    iris = R.drawable.habi_ojo_triste_i_iris,
                ),
            "ojo-triste-d" to
                AtlasPiece(
                    R.drawable.habi_ojo_triste_d,
                    736.5f,
                    456.5f,
                    168.8f,
                    146.2f,
                    iris = R.drawable.habi_ojo_triste_d_iris,
                ),
            "ojo-enfadado-i" to
                AtlasPiece(
                    R.drawable.habi_ojo_enfadado_i,
                    325.8f,
                    452.0f,
                    216.0f,
                    157.5f,
                    iris = R.drawable.habi_ojo_enfadado_i_iris,
                ),
            "ojo-enfadado-d" to
                AtlasPiece(
                    R.drawable.habi_ojo_enfadado_d,
                    711.8f,
                    452.0f,
                    213.8f,
                    157.5f,
                    iris = R.drawable.habi_ojo_enfadado_d_iris,
                ),
            "ojo-lloroso-i" to
                AtlasPiece(
                    R.drawable.habi_ojo_lloroso_i,
                    339.2f,
                    434.0f,
                    182.2f,
                    189.0f,
                    iris = R.drawable.habi_ojo_lloroso_i_iris,
                ),
            "ojo-lloroso-d" to
                AtlasPiece(
                    R.drawable.habi_ojo_lloroso_d,
                    725.2f,
                    434.0f,
                    182.2f,
                    186.8f,
                    iris = R.drawable.habi_ojo_lloroso_d_iris,
                ),
            "boca-sonrisa" to AtlasPiece(R.drawable.habi_boca_sonrisa, 556.2f, 664.5f, 173.4f, 51.0f),
            "boca-feliz" to AtlasPiece(R.drawable.habi_boca_feliz, 530.7f, 620.3f, 232.9f, 134.3f),
            "boca-neutra" to AtlasPiece(R.drawable.habi_boca_neutra, 549.4f, 676.4f, 195.5f, 20.4f),
            "boca-triste" to AtlasPiece(R.drawable.habi_boca_triste, 563.0f, 661.1f, 161.5f, 52.7f),
            "boca-o" to AtlasPiece(R.drawable.habi_boca_o, 603.8f, 637.3f, 83.3f, 102.0f),
            "boca-mueca" to AtlasPiece(R.drawable.habi_boca_mueca, 506.9f, 656.0f, 287.3f, 71.4f),
            "pintura-i-a" to AtlasPiece(R.drawable.habi_pintura_i_a, 267.0f, 594.5f, 148.2f, 94.9f),
            "pintura-i-b" to AtlasPiece(R.drawable.habi_pintura_i_b, 261.1f, 643.2f, 141.7f, 85.8f),
            "pintura-d-a" to AtlasPiece(R.drawable.habi_pintura_d_a, 849.6f, 594.5f, 156.0f, 94.9f),
            "pintura-d-b" to AtlasPiece(R.drawable.habi_pintura_d_b, 867.2f, 643.2f, 140.4f, 85.8f),
            "mofletes-i" to AtlasPiece(R.drawable.habi_mofletes_i, 272.8f, 605.2f, 126.0f, 126.0f),
            "mofletes-d" to AtlasPiece(R.drawable.habi_mofletes_d, 866.4f, 606.4f, 126.0f, 122.4f),
            "ceja-dura-i" to AtlasPiece(R.drawable.habi_ceja_dura_i, 360.4f, 374.6f, 148.7f, 99.1f),
            "ceja-dura-d" to AtlasPiece(R.drawable.habi_ceja_dura_d, 747.2f, 374.6f, 148.7f, 99.1f),
            "ceja-alegre-i" to AtlasPiece(R.drawable.habi_ceja_alegre_i, 368.5f, 392.0f, 132.0f, 64.5f),
            "ceja-alegre-d" to AtlasPiece(R.drawable.habi_ceja_alegre_d, 753.0f, 389.0f, 132.0f, 64.5f),
            "ceja-preocupada-i" to AtlasPiece(R.drawable.habi_ceja_preocupada_i, 361.0f, 378.5f, 144.0f, 87.0f),
            "ceja-preocupada-d" to AtlasPiece(R.drawable.habi_ceja_preocupada_d, 747.0f, 378.5f, 145.5f, 87.0f),
            "head-gorro-lana" to AtlasPiece(R.drawable.habi_head_gorro_lana, 2.0f, -98.0f, 1125.0f, 1496.0f),
            "head-boina" to AtlasPiece(R.drawable.habi_head_boina, 82.0f, -132.0f, 727.0f, 416.0f),
            "head-paja" to AtlasPiece(R.drawable.habi_head_paja, 176.0f, -150.0f, 692.0f, 363.0f),
            "head-auriculares" to AtlasPiece(R.drawable.habi_head_auriculares, -174.0f, -198.0f, 1389.0f, 1046.0f),
            "head-copa" to AtlasPiece(R.drawable.habi_head_copa, 326.0f, -232.0f, 411.0f, 329.0f),
            "head-flor" to AtlasPiece(R.drawable.habi_head_flor, 650.0f, -24.0f, 272.0f, 269.0f),
            "head-corona" to AtlasPiece(R.drawable.habi_head_corona, 282.0f, -226.0f, 494.0f, 322.0f),
            "face-gafas-redondas" to AtlasPiece(R.drawable.habi_face_gafas_redondas, 0.0f, 1.0f, 1127.0f, 1396.0f),
            "face-gafas-sol" to AtlasPiece(R.drawable.habi_face_gafas_sol, -8.0f, 432.0f, 1074.0f, 315.0f),
            "neck-bufanda" to AtlasPiece(R.drawable.habi_neck_bufanda, 0.0f, 1.0f, 1124.0f, 1394.0f),
            "neck-panuelo" to AtlasPiece(R.drawable.habi_neck_panuelo, -120.0f, 480.0f, 1203.0f, 547.0f),
            "neck-collar-flores" to AtlasPiece(R.drawable.habi_neck_collar_flores, -72.0f, 472.0f, 1212.0f, 514.0f),
            "neck-panuelo-brasa" to AtlasPiece(R.drawable.habi_neck_panuelo_brasa, -152.0f, 520.0f, 1242.0f, 587.0f),
            "feet-calcetines" to AtlasPiece(R.drawable.habi_feet_calcetines, 258.0f, 1270.0f, 604.0f, 209.0f),
            "feet-botas-agua" to AtlasPiece(R.drawable.habi_feet_botas_agua, 262.0f, 1296.0f, 642.0f, 208.0f),
            "feet-zapatillas" to AtlasPiece(R.drawable.habi_feet_zapatillas, 270.0f, 1280.0f, 638.0f, 224.0f),
            "costume-calabaza" to AtlasPiece(R.drawable.costume_calabaza, -1.0f, -131.0f, 1130.0f, 1525.0f),
            "costume-reno" to AtlasPiece(R.drawable.costume_reno, 0.0f, -219.0f, 1128.0f, 1613.0f),
            "costume-dinosaurio" to AtlasPiece(R.drawable.costume_dinosaurio, -212.0f, -117.0f, 1340.0f, 1512.0f),
        )

    /**
     * Con disfraz, la cara se desplaza (dx, dy) en coordenadas del cuerpo y se recorta con la
     * máscara del hueco, que no se mueve. Un disfraz sin entrada deja la cara como está.
     */
    val costumeFaces: Map<String, CostumeFace> =
        mapOf(
            "costume-calabaza" to
                CostumeFace(
                    -10.0f,
                    -14.0f,
                    AtlasPiece(R.drawable.costume_calabaza_hueco, 287.0f, 149.0f, 663.0f, 604.0f),
                ),
            "costume-reno" to
                CostumeFace(-19.0f, -71.0f, AtlasPiece(R.drawable.costume_reno_hueco, 282.0f, 90.0f, 659.0f, 612.0f)),
            "costume-dinosaurio" to
                CostumeFace(
                    9.0f,
                    13.0f,
                    AtlasPiece(R.drawable.costume_dinosaurio_hueco, 303.0f, 182.0f, 665.0f, 599.0f),
                ),
        )
}
