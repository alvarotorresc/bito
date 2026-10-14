package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta

/** Qué recuerda Habi (spec §4.6, §5.11). `RESPUESTA`: `ref` = [idPropuesta], `valor` = ordinal de la [Respuesta]. */
enum class TipoMemoria { ANIMO, RESPUESTA, VUELTA, GORDO_HECHO, ACEPTADO_CUMPLIDO, MENCION, COMO_ESTAS }

/** Un recuerdo de un día (spec §4.6). */
data class EventoMemoria(
    val id: String,
    val tipo: TipoMemoria,
    val dia: LogicalDay,
    val ref: String?,
    val valor: Int?,
    val creadoMillis: Long,
)

enum class TipoRefGordo { HABITO, TAREA }

/** Una cosa que el usuario ha puesto como «lo gordo» del día. */
data class RefGordo(
    val tipo: TipoRefGordo,
    val id: String,
)

enum class EstadoAviso { PENDIENTE, LANZADO, HECHO, CANCELADO }

/** Un «Vale, a las…» aceptado (spec §6.1): el aviso de ese hábito a ese minuto. */
data class AvisoAceptado(
    val id: String,
    val dia: LogicalDay,
    val habitId: String,
    val minuto: Int,
    val pospuesto: Int,
    val estado: EstadoAviso,
    val propuestaId: String,
    val creadoMillis: Long,
)

/** La propuesta que se está mostrando en un ámbito (spec §4.5). [habitId] es "" fuera de la ficha. */
data class PropuestaVigente(
    val ambito: Ambito,
    val habitId: String,
    val dia: LogicalDay,
    val situacion: Situacion,
    val ref: String?,
    val trozos: List<ClaveTrozo>,
    val huecos: Map<Hueco, ValorHueco>,
    val acciones: List<Respuesta>,
    val destino: Destino?,
    val hora: Int?,
    val creadoMillis: Long,
)

/** Una propuesta que se llegó a mostrar hoy, para el tope de §5.2. */
data class Mostrada(
    val situacion: Situacion,
    val ref: String?,
    val millis: Long,
)

/** Un trozo que se usó, para la anti-repetición (spec §5.12). */
data class UsoTrozo(
    val voz: Personality,
    val bolsa: Bolsa,
    val indice: Int,
    val combinacion: String,
    val dia: LogicalDay,
    val usadoMillis: Long,
    val situacion: Situacion? = null,
)

/** Un recuerdo que se puede mencionar (spec §5.11): la bolsa `re_*`, el evento del que sale y sus huecos. */
data class Recuerdo(
    val bolsa: Bolsa,
    val eventoId: String,
    val huecos: Map<Hueco, ValorHueco>,
)

/** Lo que Habi recuerda (spec §4.6). */
data class Memoria(
    /** Últimos 400 días. */
    val eventos: List<EventoMemoria> = emptyList(),
    /** Hoy y ayer. */
    val gordo: Map<LogicalDay, List<RefGordo>> = emptyMap(),
    /** Cuándo se guardó lo gordo de hoy. */
    val gordoElegidoMillis: Long? = null,
    /** De hoy. */
    val avisos: List<AvisoAceptado> = emptyList(),
    val vigentes: List<PropuestaVigente> = emptyList(),
    /** Tope de §5.2. */
    val mostradasHoy: List<Mostrada> = emptyList(),
    /** Últimos 60 días, para el redactor. */
    val usos: List<UsoTrozo> = emptyList(),
)
