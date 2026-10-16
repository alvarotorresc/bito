package com.alvarotc.bito.data.habla

import com.alvarotc.bito.data.db.AvisoAceptadoEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.HabiMemoriaEntity
import com.alvarotc.bito.data.db.HablaUsadaEntity
import com.alvarotc.bito.data.db.LoGordoEntity
import com.alvarotc.bito.data.db.PropuestaVigenteEntity
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.PropuestaVigente
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.UsoTrozo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.DayOfWeek

/*
 * Entidad <-> dominio de los datos de Habi. Lo que se lee de disco puede venir de una version
 * anterior del catalogo (una bolsa borrada, un valor desconocido): se descarta la fila o el
 * campo, nunca se lanza.
 */

private const val SEPARADOR = ";"

/** «BOLSA:VOZ:índice», con el nombre del enum (no la clave de la bolsa). */
fun ClaveTrozo.aTexto(): String = "${bolsa.name}:${voz.name}:$indice"

/** `null` si la bolsa ya no existe, la voz no se conoce o el texto no tiene la forma. */
fun claveDeTexto(texto: String): ClaveTrozo? {
    val partes = texto.split(":")
    if (partes.size != 3) return null
    val bolsa = Bolsa.entries.firstOrNull { it.name == partes[0] } ?: return null
    val voz = Personality.entries.firstOrNull { it.name == partes[1] } ?: return null
    val indice = partes[2].toIntOrNull() ?: return null
    return ClaveTrozo(bolsa, voz, indice)
}

/** Los huecos como JSON plano: `{"habito":{"t":"texto","v":"Agua"},...}`. */
fun Map<Hueco, ValorHueco>.aJson(): String =
    JsonObject(
        mapKeys { it.key.clave }.mapValues { (_, valor) -> valor.aJson() },
    ).toString()

private fun ValorHueco.aJson(): JsonObject =
    when (this) {
        is ValorHueco.Texto -> JsonObject(mapOf("t" to JsonPrimitive("texto"), "v" to JsonPrimitive(v)))
        is ValorHueco.Numero -> JsonObject(mapOf("t" to JsonPrimitive("numero"), "v" to JsonPrimitive(v)))
        is ValorHueco.Hora -> JsonObject(mapOf("t" to JsonPrimitive("hora"), "v" to JsonPrimitive(minuto)))
        is ValorHueco.Lista ->
            JsonObject(mapOf("t" to JsonPrimitive("lista"), "v" to JsonArray(v.map { JsonPrimitive(it) })))
        is ValorHueco.DiaSemana -> JsonObject(mapOf("t" to JsonPrimitive("dia"), "v" to JsonPrimitive(v.name)))
        is ValorHueco.Fecha -> JsonObject(mapOf("t" to JsonPrimitive("fecha"), "v" to JsonPrimitive(epochDay)))
    }

/** Un hueco desconocido o un valor roto se salta; un JSON ilegible da un mapa vacio. */
fun huecosDeJson(json: String): Map<Hueco, ValorHueco> {
    val raiz = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return emptyMap()
    val salida = LinkedHashMap<Hueco, ValorHueco>()
    for ((clave, elemento) in raiz) {
        val hueco = Hueco.entries.firstOrNull { it.clave == clave } ?: continue
        val valor = runCatching { valorDeJson(elemento.jsonObject) }.getOrNull() ?: continue
        salida[hueco] = valor
    }
    return salida
}

private fun valorDeJson(o: JsonObject): ValorHueco? {
    val v = o["v"] ?: return null
    return when (o["t"]?.jsonPrimitive?.contentOrNull) {
        "texto" -> ValorHueco.Texto(v.jsonPrimitive.content)
        "numero" -> ValorHueco.Numero(v.jsonPrimitive.int)
        "hora" -> ValorHueco.Hora(v.jsonPrimitive.int)
        "lista" -> ValorHueco.Lista(v.jsonArray.map { it.jsonPrimitive.content })
        "dia" -> ValorHueco.DiaSemana(DayOfWeek.valueOf(v.jsonPrimitive.content))
        "fecha" -> ValorHueco.Fecha(v.jsonPrimitive.long)
        else -> null
    }
}

// Propuesta vigente.

/**
 * La fila no guarda el destino (el esquema no tiene columna): se reconstruye con [destinoDe].
 * Por eso lo que vuelve de [aDominio] puede diferir en `destino` del objeto que se guardo si
 * este no seguia esa regla.
 */
fun PropuestaVigente.aEntidad(): PropuestaVigenteEntity =
    PropuestaVigenteEntity(
        ambito = ambito.name,
        habitId = habitId,
        dia = dia,
        situacion = situacion.name,
        ref = ref,
        trozos = trozos.joinToString(SEPARADOR) { it.aTexto() },
        huecos = huecos.aJson(),
        acciones = acciones.joinToString(SEPARADOR) { it.name },
        hora = hora,
        creadoMillis = creadoMillis,
    )

/**
 * `null` si la fila habla de algo que ya no existe (bolsa, situacion, ambito o respuesta).
 * [refEsTarea] solo importa en las situaciones cuyo `ref` puede ser un habito o una tarea
 * ([refAmbiguo]); el almacen lo resuelve mirando la tabla de tareas.
 */
fun PropuestaVigenteEntity.aDominio(refEsTarea: Boolean = false): PropuestaVigente? {
    val ambitoLeido = Ambito.entries.firstOrNull { it.name == ambito } ?: return null
    val situacionLeida = Situacion.entries.firstOrNull { it.name == situacion } ?: return null
    val claves = if (trozos.isEmpty()) emptyList() else trozos.split(SEPARADOR).map { claveDeTexto(it) ?: return null }
    val respuestas =
        if (acciones.isEmpty()) {
            emptyList()
        } else {
            acciones.split(SEPARADOR).map { nombre -> Respuesta.entries.firstOrNull { it.name == nombre } ?: return null }
        }
    return PropuestaVigente(
        ambito = ambitoLeido,
        habitId = habitId,
        dia = dia,
        situacion = situacionLeida,
        ref = ref,
        trozos = claves,
        huecos = huecosDeJson(huecos),
        acciones = respuestas,
        destino = destinoDe(situacionLeida, ref, refEsTarea),
        hora = hora,
        creadoMillis = creadoMillis,
    )
}

/**
 * Unico sitio que resuelve el ref ambiguo: mira la tabla de tareas. Lo usan [PropuestaVigenteStore]
 * y `MemoriaRepository.observe`, para que ambos den el mismo destino.
 */
suspend fun PropuestaVigenteEntity.aDominio(db: BitoDatabase): PropuestaVigente? {
    val lectura = Situacion.entries.firstOrNull { it.name == situacion }
    val esTarea = ref != null && lectura != null && refAmbiguo(lectura) && db.taskDao().byId(ref) != null
    return aDominio(refEsTarea = esTarea)
}

/** Situaciones cuyo `ref` puede ser una tarea o un habito (lo gordo mezcla los dos). */
fun refAmbiguo(situacion: Situacion): Boolean =
    situacion == Situacion.GORDO_SIN_EMPEZAR ||
        situacion == Situacion.GORDO_ELEGIDO ||
        situacion == Situacion.CONFIRMA_VALE

private val SITUACIONES_DE_HABITO =
    setOf(
        Situacion.VUELTA,
        Situacion.DIA_DURO,
        Situacion.AYER_SE_ESCAPO,
        Situacion.RACHA_CERCA,
        Situacion.DIA_FLOJO,
        Situacion.HORA_HABITUAL,
        Situacion.FI_HOY_FALTA,
        Situacion.FI_OBJETIVO,
    )

/**
 * A donde lleva «Ahora» o «Hecho» en cada situacion. Ninguna situacion de la spec lleva a
 * `Destino.Respirar`, asi que no se reconstruye.
 */
fun destinoDe(
    situacion: Situacion,
    ref: String?,
    refEsTarea: Boolean = false,
): Destino? =
    when {
        situacion == Situacion.CERRAR_DIA || situacion == Situacion.TODO_HECHO -> Destino.Cierre
        situacion == Situacion.ELEGIR_GORDO -> Destino.LoGordo
        ref == null -> null
        situacion == Situacion.TAREA_VENCE -> Destino.Tarea(ref)
        situacion in SITUACIONES_DE_HABITO -> Destino.Habito(ref)
        refAmbiguo(situacion) -> if (refEsTarea) Destino.Tarea(ref) else Destino.Habito(ref)
        else -> null
    }

// Memoria.

fun EventoMemoria.aEntidad() = HabiMemoriaEntity(id, tipo.name, dia, ref, valor, creadoMillis)

fun HabiMemoriaEntity.aDominio(): EventoMemoria? {
    val tipoLeido = TipoMemoria.entries.firstOrNull { it.name == tipo } ?: return null
    return EventoMemoria(id, tipoLeido, dia, ref, valor, creadoMillis)
}

fun UsoTrozo.aEntidad() = HablaUsadaEntity(0, voz.name, bolsa.name, indice, combinacion, dia, usadoMillis, situacion?.name)

fun HablaUsadaEntity.aDominio(): UsoTrozo? {
    val vozLeida = Personality.entries.firstOrNull { it.name == voz } ?: return null
    val bolsaLeida = Bolsa.entries.firstOrNull { it.name == bolsa } ?: return null
    return UsoTrozo(
        voz = vozLeida,
        bolsa = bolsaLeida,
        indice = indice,
        combinacion = combinacion,
        dia = dia,
        usadoMillis = usadoMillis,
        situacion = situacion?.let { nombre -> Situacion.entries.firstOrNull { it.name == nombre } },
    )
}

// Lo gordo y avisos.

fun RefGordo.aEntidad(
    dia: Int,
    posicion: Int,
    creadoMillis: Long,
) = LoGordoEntity(dia, posicion, tipo.name, id, creadoMillis)

fun LoGordoEntity.aDominio(): RefGordo? {
    val tipoLeido = TipoRefGordo.entries.firstOrNull { it.name == tipo } ?: return null
    return RefGordo(tipoLeido, refId)
}

fun AvisoAceptado.aEntidad() = AvisoAceptadoEntity(id, dia, habitId, minuto, pospuesto, estado.name, propuestaId, creadoMillis)

fun AvisoAceptadoEntity.aDominio(): AvisoAceptado? {
    val estadoLeido = EstadoAviso.entries.firstOrNull { it.name == estado } ?: return null
    return AvisoAceptado(id, dia, habitId, minuto, pospuesto, estadoLeido, propuestaId, creadoMillis)
}
