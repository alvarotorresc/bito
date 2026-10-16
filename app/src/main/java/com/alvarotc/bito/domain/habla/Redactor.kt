package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.TextoHabi
import kotlin.random.Random

/** Una frase redactada, los trozos que gastó y el recuerdo que mencionó (id del evento), si alguno. */
data class Redaccion(
    val frase: TextoHabi.Frase,
    val usos: List<UsoTrozo>,
    val mencion: String?,
)

/**
 * Convierte una [Candidata] en una frase `[apertura] + núcleo + [cierre o recuerdo]` (spec §4.4,
 * §5.12, §5.13). Elegible es el trozo cuyos huecos llegan y cuyas etiquetas se cumplen; con
 * etiqueta pesa el doble. Puro: todo el azar sale de `azar`, así que la misma semilla da la misma
 * frase.
 */
object Redactor {
    /**
     * [forzarApertura]: los títulos de aviso siempre abren, sea cual sea el núcleo, salvo delante de un
     * saludo. [recuerdo]: el que la fuente eligió con `Recuerdos.elegible`; los avisos pasan null. Sin
     * ningún núcleo elegible, el índice 0 de su bolsa, solo y sin uso anotado.
     */
    fun redactar(
        c: Candidata,
        ctx: Contexto,
        catalogo: CatalogoHabla,
        m: Memoria,
        azar: Random,
        forzarApertura: Boolean = false,
        recuerdo: Recuerdo? = null,
    ): Redaccion {
        val huecos = if (ctx.nombre != null) c.huecos + (Hueco.NOMBRE to ValorHueco.Texto(ctx.nombre)) else c.huecos - Hueco.NOMBRE
        val bolsaNucleo = c.nucleo ?: return Redaccion(TextoHabi.Frase(emptyList(), huecos), emptyList(), null)
        val voz = ctx.voz
        val usos = m.usos.filter { it.voz == voz }
        val nucleos = elegibles(catalogo.trozos(bolsaNucleo, voz), huecos.keys, ctx)
        if (nucleos.isEmpty()) {
            return Redaccion(TextoHabi.Frase(listOf(ClaveTrozo(bolsaNucleo, voz, 0)), huecos), emptyList(), null)
        }

        // Apertura antes que núcleo: el nombre se prefiere en la apertura.
        val bolsaApertura = if (llevaApertura(c, bolsaNucleo, forzarApertura, azar)) aperturaDe(ctx.franja) else null
        val aperturas = bolsaApertura?.let { elegibles(catalogo.trozos(it, voz), huecos.keys, ctx) }.orEmpty()
        var apertura = elegirRotando(aperturas, usos.de(bolsaApertura), azar)
        if (apertura != null && Hueco.NOMBRE in apertura.huecos && nucleos.none { Hueco.NOMBRE !in it.huecos }) {
            apertura = elegirRotando(aperturas.sinNombre(), usos.de(bolsaApertura), azar)
        }
        // §5.13: con apertura, el nombre solo puede ir en ella; sin apertura, en el núcleo.
        val nucleosPosibles = if (apertura != null) nucleos.sinNombre().ifEmpty { nucleos } else nucleos
        val nucleo = elegirNucleo(nucleosPosibles, usos.de(bolsaNucleo), ctx.hoy, azar)

        // Cierre o recuerdo.
        val admitido = recuerdo?.takeIf { Recuerdos.admite(c.situacion, bolsaNucleo) }
        val recuerdos = admitido?.let { trozosRecuerdo(it, c, catalogo, voz, ctx) }.orEmpty()
        var cola: Bolsa? = null
        var final: TrozoInfo? = null
        var huecosFrase = huecos
        var mencion: String? = null
        if (recuerdos.isNotEmpty() && recuerdo != null) {
            cola = recuerdo.bolsa
            final = elegirRotando(recuerdos, usos.de(cola), azar)
            huecosFrase = huecos + recuerdo.huecos.filterKeys { it !in huecos }
            mencion = recuerdo.eventoId
        } else if (bolsaNucleo.tipo == TipoTrozo.NUCLEO && azar.nextDouble() < Ritmo.PROB_CIERRE) {
            cola = if (c.situacion.conBotones) Bolsa.CI_EMPUJE else Bolsa.CI_CALMA
            final = elegirRotando(elegibles(catalogo.trozos(cola, voz), huecos.keys, ctx), usos.de(cola), azar)
        }

        // Frase entera: sin repetir en 30 días; se cambia el cierre y, si no basta, la apertura.
        val recientes =
            usos
                .filter { it.bolsa == bolsaNucleo && ctx.hoy - it.dia < Ritmo.FRASE_DIAS }
                .map { it.combinacion }
                .toSet()
        if (combinacion(apertura, nucleo, final) in recientes) {
            val cierreNuevo =
                if (cola == null || mencion != null) {
                    null
                } else {
                    val otros =
                        elegibles(catalogo.trozos(cola, voz), huecos.keys, ctx)
                            .filter { combinacion(apertura, nucleo, it) !in recientes }
                    elegirRotando(otros, usos.de(cola), azar)
                }
            if (cierreNuevo != null) {
                final = cierreNuevo
            } else if (apertura != null) {
                val otras =
                    (if (Hueco.NOMBRE in nucleo.huecos) aperturas.sinNombre() else aperturas)
                        .filter { combinacion(it, nucleo, final) !in recientes }
                elegirRotando(otras, usos.de(bolsaApertura), azar)?.let { apertura = it }
            }
        }

        val clave = combinacion(apertura, nucleo, final)
        val elegidos =
            listOfNotNull(
                apertura?.let { bolsaApertura!! to it },
                bolsaNucleo to nucleo,
                final?.let { cola!! to it },
            )
        val frase = TextoHabi.Frase(elegidos.map { (b, t) -> ClaveTrozo(b, voz, t.indice) }, huecosFrase)
        val nuevos =
            elegidos.map { (b, t) ->
                UsoTrozo(voz, b, t.indice, clave, ctx.hoy, ctx.ahoraMillis, if (b == bolsaNucleo) c.situacion else null)
            }
        return Redaccion(frase, nuevos, mencion)
    }

    /** La semilla del azar de una redacción: la misma propuesta del mismo día dice lo mismo. */
    fun semilla(
        dia: LogicalDay,
        situacion: Situacion,
        ref: String?,
        voz: Personality,
        respuestasHoy: Int,
    ): Long {
        var s = dia.toLong()
        s = s * PRIMO + situacion.name.hashCode()
        s = s * PRIMO + ref.orEmpty().hashCode()
        s = s * PRIMO + voz.name.hashCode()
        s = s * PRIMO + respuestasHoy
        return s
    }

    private const val PRIMO = 31L

    /**
     * Nunca delante de un saludo (que ya saluda), ni forzada. Si no, solo en Hoy y con núcleo de tipo
     * NUCLEO; los títulos de aviso la fuerzan.
     */
    private fun llevaApertura(
        c: Candidata,
        bolsaNucleo: Bolsa,
        forzar: Boolean,
        azar: Random,
    ): Boolean =
        bolsaNucleo != Bolsa.NU_SALUDO &&
            c.situacion != Situacion.SALUDO &&
            (
                forzar ||
                    (
                        c.situacion.ambito == Ambito.HOY &&
                            bolsaNucleo.tipo == TipoTrozo.NUCLEO &&
                            azar.nextDouble() < Ritmo.PROB_APERTURA_TARJETA
                    )
            )

    private fun aperturaDe(franja: Franja): Bolsa =
        when (franja) {
            Franja.MADRUGADA, Franja.MANANA -> Bolsa.AP_MANANA
            Franja.MEDIODIA -> Bolsa.AP_MEDIODIA
            Franja.TARDE -> Bolsa.AP_TARDE
            Franja.NOCHE -> Bolsa.AP_NOCHE
        }

    /**
     * Los trozos del recuerdo que se pueden decir con sus huecos y los de la candidata. Un hueco que
     * los dos traen con valor distinto lo descarta: la frase lleva un solo valor por hueco.
     */
    private fun trozosRecuerdo(
        r: Recuerdo,
        c: Candidata,
        catalogo: CatalogoHabla,
        voz: Personality,
        ctx: Contexto,
    ): List<TrozoInfo> {
        val choques = r.huecos.filter { (h, v) -> c.huecos[h]?.let { it != v } ?: false }.keys
        val disponibles = c.huecos.keys + r.huecos.keys - choques
        return elegibles(catalogo.trozos(r.bolsa, voz), disponibles, ctx).filter { it.huecos.none { h -> h in choques } }
    }

    /** En el orden del catálogo: todos sus huecos llegan y todas sus etiquetas se cumplen. */
    private fun elegibles(
        trozos: List<TrozoInfo>,
        disponibles: Set<Hueco>,
        ctx: Contexto,
    ): List<TrozoInfo> = trozos.filter { t -> disponibles.containsAll(t.huecos) && t.etiquetas.all { ctx.cumple(it) } }

    /** Sin los usados en los últimos 7 días lógicos; si no queda ninguno, el usado hace más tiempo. */
    private fun elegirNucleo(
        nucleos: List<TrozoInfo>,
        usos: List<UsoTrozo>,
        hoy: LogicalDay,
        azar: Random,
    ): TrozoInfo {
        val recientes = usos.filter { hoy - it.dia < Ritmo.NUCLEO_DIAS }.map { it.indice }.toSet()
        val libres = nucleos.filter { it.indice !in recientes }
        return if (libres.isNotEmpty()) sortear(libres, azar) else masAntiguo(nucleos, usos)
    }

    /** Aperturas, cierres y recuerdos: sin los de los 5 últimos usos; si no queda ninguno, el usado hace más tiempo. */
    private fun elegirRotando(
        trozos: List<TrozoInfo>,
        usos: List<UsoTrozo>,
        azar: Random,
    ): TrozoInfo? {
        if (trozos.isEmpty()) return null
        val ultimos =
            usos
                .sortedByDescending { it.usadoMillis }
                .take(Ritmo.APERTURA_CIERRE_USOS)
                .map { it.indice }
                .toSet()
        val libres = trozos.filter { it.indice !in ultimos }
        return if (libres.isNotEmpty()) sortear(libres, azar) else masAntiguo(trozos, usos)
    }

    private fun masAntiguo(
        trozos: List<TrozoInfo>,
        usos: List<UsoTrozo>,
    ): TrozoInfo {
        val ultimoUso = usos.groupBy { it.indice }.mapValues { (_, us) -> us.maxOf { it.usadoMillis } }
        return trozos.minBy { ultimoUso[it.indice] ?: Long.MIN_VALUE }
    }

    /** Sorteo ponderado: con etiqueta (que ya se cumple) pesa 2; sin etiqueta, 1. */
    private fun sortear(
        trozos: List<TrozoInfo>,
        azar: Random,
    ): TrozoInfo {
        val pesos = trozos.map { if (it.etiquetas.isEmpty()) 1 else 2 }
        var r = azar.nextInt(pesos.sum())
        trozos.forEachIndexed { i, t ->
            r -= pesos[i]
            if (r < 0) return t
        }
        return trozos.last()
    }

    private fun combinacion(
        apertura: TrozoInfo?,
        nucleo: TrozoInfo,
        cierre: TrozoInfo?,
    ): String = "${apertura?.indice ?: "-"}|${nucleo.indice}|${cierre?.indice ?: "-"}"

    private fun List<TrozoInfo>.sinNombre() = filter { Hueco.NOMBRE !in it.huecos }

    private fun List<UsoTrozo>.de(bolsa: Bolsa?) = if (bolsa == null) emptyList() else filter { it.bolsa == bolsa }
}
