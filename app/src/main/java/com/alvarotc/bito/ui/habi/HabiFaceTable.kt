// Generado por tools/arte/exportar.py. No editar a mano.
package com.alvarotc.bito.ui.habi

/** La tabla de caras aprobada (spec §5.2), desde tools/arte/caras.json. */
internal object HabiFaceTable {
    /**
     * Ojos, boca y cejas por nombre de Mood y de Personality. Son claves de pieza sin el lado
     * (-i, -d); sin cejas, null.
     */
    val faces: Map<String, Map<String, Triple<String, String, String?>>> =
        mapOf(
            "RADIANT" to
                mapOf(
                    "NEUTRA" to Triple("ojo-feliz", "boca-feliz", null),
                    "CHEERLEADER" to Triple("ojo-feliz", "boca-feliz", "ceja-alegre"),
                    "SARGENTO" to Triple("ojo-feliz", "boca-sonrisa", "ceja-dura"),
                ),
            "NORMAL" to
                mapOf(
                    "NEUTRA" to Triple("ojo-abierto", "boca-sonrisa", null),
                    "CHEERLEADER" to Triple("ojo-abierto", "boca-sonrisa", "ceja-alegre"),
                    "SARGENTO" to Triple("ojo-abierto", "boca-neutra", "ceja-dura"),
                ),
            "WILTED" to
                mapOf(
                    "NEUTRA" to Triple("ojo-triste", "boca-triste", null),
                    "CHEERLEADER" to Triple("ojo-triste", "boca-triste", "ceja-preocupada"),
                    "SARGENTO" to Triple("ojo-abierto", "boca-triste", "ceja-preocupada"),
                ),
            "DRAMATIC" to
                mapOf(
                    "NEUTRA" to Triple("ojo-lloroso", "boca-mueca", null),
                    "CHEERLEADER" to Triple("ojo-lloroso", "boca-o", null),
                    "SARGENTO" to Triple("ojo-enfadado", "boca-mueca", null),
                ),
        )

    /** Las marcas que lleva siempre cada personalidad, por nombre de Personality. */
    val marks: Map<String, List<String>> =
        mapOf(
            "NEUTRA" to emptyList(),
            "CHEERLEADER" to listOf("mofletes-i", "mofletes-d"),
            "SARGENTO" to listOf("pintura-i-a", "pintura-i-b", "pintura-d-a", "pintura-d-b"),
        )
}
