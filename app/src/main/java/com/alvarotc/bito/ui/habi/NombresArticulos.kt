package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.R

/** Nombres por id, solo en ui/ (docs/05 §2). */
fun itemNameRes(id: String): Int =
    when (id) {
        "body-salvia" -> R.string.item_body_salvia
        "body-vainilla" -> R.string.item_body_vainilla
        "body-melocoton" -> R.string.item_body_melocoton
        "body-cielo" -> R.string.item_body_cielo
        "body-lavanda" -> R.string.item_body_lavanda
        "body-rosa" -> R.string.item_body_rosa
        "body-terracota" -> R.string.item_body_terracota
        "body-carbon" -> R.string.item_body_carbon
        "body-dorado" -> R.string.item_body_dorado
        "eyes-tinta" -> R.string.item_eyes_tinta
        "eyes-avellana" -> R.string.item_eyes_avellana
        "eyes-verde" -> R.string.item_eyes_verde
        "eyes-azul" -> R.string.item_eyes_azul
        "eyes-ambar" -> R.string.item_eyes_ambar
        "eyes-violeta" -> R.string.item_eyes_violeta
        "head-gorro-lana" -> R.string.item_head_gorro_lana
        "head-boina" -> R.string.item_head_boina
        "head-paja" -> R.string.item_head_paja
        "head-auriculares" -> R.string.item_head_auriculares
        "head-copa" -> R.string.item_head_copa
        "head-flor" -> R.string.item_head_flor
        "head-corona" -> R.string.item_head_corona
        "face-gafas-redondas" -> R.string.item_face_gafas_redondas
        "face-gafas-sol" -> R.string.item_face_gafas_sol
        "neck-bufanda" -> R.string.item_neck_bufanda
        "neck-panuelo" -> R.string.item_neck_panuelo
        "neck-collar-flores" -> R.string.item_neck_collar_flores
        "neck-panuelo-brasa" -> R.string.item_neck_panuelo_brasa
        "feet-calcetines" -> R.string.item_feet_calcetines
        "feet-botas-agua" -> R.string.item_feet_botas_agua
        "feet-zapatillas" -> R.string.item_feet_zapatillas
        "costume-calabaza" -> R.string.item_costume_calabaza
        "costume-reno" -> R.string.item_costume_reno
        "costume-dinosaurio" -> R.string.item_costume_dinosaurio
        "garden-banco" -> R.string.store_garden_bench
        "garden-regadera" -> R.string.store_garden_can
        // Inalcanzable para un artículo real: cae a un texto que existe en vez de romper con un id corrupto.
        else -> R.string.store_title
    }
