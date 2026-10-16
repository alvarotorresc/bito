package com.alvarotc.bito.data.habla

import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.Compliance
import com.alvarotc.bito.domain.ComplianceStatus
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.Sealing
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.Bolsa
import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.CatalogoHabla
import com.alvarotc.bito.domain.habla.ClaveTrozo
import com.alvarotc.bito.domain.habla.Contexto
import com.alvarotc.bito.domain.habla.Contextos
import com.alvarotc.bito.domain.habla.ElegirCandidata
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.domain.habla.EtiquetasAccion
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.FaseHabi
import com.alvarotc.bito.domain.habla.Franja
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.HechosCache
import com.alvarotc.bito.domain.habla.HoraPropuesta
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Motor
import com.alvarotc.bito.domain.habla.PropuestaVigente
import com.alvarotc.bito.domain.habla.Recuerdos
import com.alvarotc.bito.domain.habla.Redactor
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Ritmo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.cercaDeLaNoche
import com.alvarotc.bito.domain.habla.idPropuesta
import com.alvarotc.bito.domain.habla.reglas.Respuestas
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.notifications.AvisosHabi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** Lo que el sistema deja hacer a los avisos de Habi: notificaciones y alarmas exactas. */
data class PermisosAvisos(
    val notificaciones: Boolean,
    val alarmasExactas: Boolean,
)

/**
 * La fuente de lo que dice Habi (spec §4.2, §4.5). Por ámbito (y por hábito en la ficha) guarda la
 * propuesta vigente: mientras valga, cada emisión la devuelve con los números al día y sin redactar
 * otra; si deja de valer, el motor elige y el redactor escribe la siguiente. Responder escribe la
 * memoria y cambia la vigente. Todo lo que lee y escribe la vigente va bajo un cerrojo, para que la
 * tarjeta y el aviso de la mañana compartan la misma.
 */
class FuentePropuestasHabi(
    private val estado: Flow<DomainState>,
    private val ajustes: Flow<Settings>,
    private val recordatorios: Flow<Map<String, Int>>,
    private val memoria: MemoriaRepository,
    private val vigentes: PropuestaVigenteStore,
    private val gordo: LoGordoRepository,
    private val aceptados: AvisosAceptadosRepository,
    private val catalogo: () -> CatalogoHabla,
    private val avisos: AvisosHabi,
    private val permisos: () -> PermisosAvisos,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // «Hecho» de la vuelta; AppContainer lo cablea.
    private val registrar: suspend (habitId: String, cantidad: Int) -> Unit = { _, _ -> },
    // Los tests de m2 inyectan un falso.
    private val motor: ElegirCandidata = ElegirCandidata(Motor::elegir),
    // Ruling P24: los tests de m2 inyectan unos hechos falsos.
    private val hechos: (DomainState, Memoria, Contexto, Map<String, Int>) -> Hechos = HechosCache()::hechos,
) : FuentePropuestas {
    private val cerrojo = Mutex()

    private enum class Validez { VALE, HECHA, NO_VALE }

    override fun propuesta(
        ambito: Ambito,
        habitId: String?,
    ): Flow<Propuesta?> {
        if (ambito == Ambito.FICHA && habitId == null) return flowOf(null)
        val clave = if (ambito == Ambito.FICHA) habitId.orEmpty() else ""
        val dia = ajustes.map { diaDe(it) }.distinctUntilChanged()
        return combine(estado, ajustes, recordatorios, memoria.observe(dia)) { state, prefs, recs, m ->
            val ctx = contexto(state, prefs)
            val h = hechos(state, m, ctx, recs)
            enCerrojo { vigenteODeNuevo(ambito, clave, habitId, state, ctx, h, recs) }
        }.distinctUntilChanged()
    }

    /**
     * La vigente si vale (sin escribir nada, para no provocar otra emisión). Si lo propuesto ya está
     * hecho, se borra y se emite `null` una vez: la tarjeta se cierra sin texto y la emisión que
     * provoca el borrado trae lo que toque. Si no vale por otra cosa, se redacta otra con la memoria
     * releída dentro del cerrojo: la foto de la emisión puede no ver lo que otro ámbito o una respuesta
     * acaban de escribir. Una candidata que ya nace sin valer (caducada, hecha) no se redacta ni se
     * guarda: se emite `null` sin escribir nada, o cada emisión redactaría otra y gastaría el tope.
     */
    private suspend fun vigenteODeNuevo(
        ambito: Ambito,
        clave: String,
        habitId: String?,
        state: DomainState,
        ctx: Contexto,
        h: Hechos,
        recs: Map<String, Int>,
    ): Propuesta? {
        val v = vigentes.leer(ambito, clave)
        if (v != null) {
            when (validez(v, state, ctx)) {
                Validez.VALE -> return pintar(v, h, ctx)
                Validez.HECHA -> {
                    vigentes.borrar(ambito, clave)
                    // Lo propuesto se hizo fuera de la tarjeta: el aviso aceptado de ese hábito ya no tiene que sonar.
                    // `replanificar` no lee la fuente, así que se puede llamar con el cerrojo cogido.
                    avisos.replanificar()
                    return null
                }
                Validez.NO_VALE -> vigentes.borrar(ambito, clave)
            }
        }
        val m = memoria.observe(flowOf(ctx.hoy)).first()
        val hAlDia = hechos(state, m, ctx, recs)
        val c = motor.elegir(ambito, hAlDia, m, ctx, habitId) ?: return null
        if (validez(sinRedactar(c, ambito, clave, ctx), state, ctx) != Validez.VALE) return null
        return pintar(redactarYGuardar(c, ambito, clave, ctx, hAlDia, m), hAlDia, ctx)
    }

    // Validez (spec §4.5).

    private fun validez(
        v: PropuestaVigente,
        state: DomainState,
        ctx: Contexto,
    ): Validez =
        when {
            v.dia != ctx.hoy -> Validez.NO_VALE
            v.ambito == Ambito.HOY && !faseVale(v, ctx) -> Validez.NO_VALE
            caducada(v, ctx) -> Validez.NO_VALE
            !refExiste(v.ref, state) -> Validez.NO_VALE
            !trozosExisten(v) -> Validez.NO_VALE
            v.situacion in PROPONEN_HACER && v.ref != null && hecho(v.ref, state, ctx.hoy) -> Validez.HECHA
            else -> Validez.VALE
        }

    /** `NOCHE` pide el día sellado; el cierre, que no lo esté; el resto cae al sellar o al llegar la noche. */
    private fun faseVale(
        v: PropuestaVigente,
        ctx: Contexto,
    ): Boolean =
        when (v.situacion) {
            Situacion.NOCHE -> ctx.fase == FaseHabi.DURMIENDO
            Situacion.TODO_HECHO, Situacion.CERRAR_DIA -> ctx.fase != FaseHabi.DURMIENDO
            else -> ctx.fase != FaseHabi.DURMIENDO && !(deNoche(ctx.ahoraMillis, ctx) && !deNoche(v.creadoMillis, ctx))
        }

    /** La de [Contextos.de]: desde la hora de la noche, o pasada la medianoche antes del corte del día. */
    private fun deNoche(
        millis: Long,
        ctx: Contexto,
    ): Boolean {
        val reloj = Instant.ofEpochMilli(millis).atZone(ctx.zona).toLocalDateTime()
        return reloj.hour * 60 + reloj.minute >= ctx.horaNoche || reloj.toLocalDate() != ctx.fecha
    }

    /** La caducidad de cada situación (spec §5.1); el fin del día ya lo mira el día lógico. */
    private fun caducada(
        v: PropuestaVigente,
        ctx: Contexto,
    ): Boolean =
        when (v.situacion) {
            Situacion.ELEGIR_GORDO -> ctx.ahoraMin >= Ritmo.GORDO_HASTA_MIN
            // La misma cuenta con que las reglas dejan de proponerlas: una sola constante, ninguna nace caducada.
            Situacion.AYER_SE_ESCAPO, Situacion.RACHA_CERCA, Situacion.DIA_FLOJO -> ctx.cercaDeLaNoche()
            Situacion.HORA_HABITUAL -> {
                val hora = v.hora ?: (v.huecos[Hueco.HORA] as? ValorHueco.Hora)?.minuto
                hora != null && ctx.ahoraMin >= hora + Ritmo.HORA_HABITUAL_CADUCA_MIN
            }
            Situacion.SALUDO -> Franja.de(minutoDe(v.creadoMillis, ctx.zona)) != ctx.franja
            Situacion.PERMISO_AVISOS -> if (pideAlarmas(v)) ctx.alarmasExactas else ctx.permisoAvisos
            // Las que salen como respuesta duran la espera tras responder (§5.2): después vuelve a decidir el motor.
            Situacion.TRAS_HOY_NO, Situacion.GORDO_ELEGIDO, Situacion.CONFIRMA_VALE ->
                ctx.ahoraMillis - v.creadoMillis >= Ritmo.ESPERA_TRAS_RESPONDER_MIN * MILLIS_POR_MINUTO
            else -> false
        }

    /** Un hábito archivado o borrado, o una tarea borrada, tumba la vigente (Review Focus 5). */
    private fun refExiste(
        ref: String?,
        state: DomainState,
    ): Boolean {
        if (ref == null) return true
        val habito = state.habits.firstOrNull { it.id == ref }
        return (habito != null && habito.status != HabitStatus.ARCHIVED) || state.tasks.any { it.id == ref }
    }

    /**
     * Cada trozo sigue en su bolsa (Review Focus 3). Una bolsa sin trozos es un catálogo sin cargar: el
     * índice 0 que deja ahí el redactor vale, o la fuente redactaría en cada emisión.
     */
    private fun trozosExisten(v: PropuestaVigente): Boolean {
        val cat = catalogo()
        return v.trozos.all { t ->
            val n = cat.trozos(t.bolsa, t.voz).size
            t.indice < n || (n == 0 && t.indice == 0)
        }
    }

    /** Hecho hoy según los datos, no según los hechos (que en este carril aún son provisionales). */
    private fun hecho(
        ref: String,
        state: DomainState,
        hoy: Int,
    ): Boolean {
        val habito = state.habits.firstOrNull { it.id == ref }
        if (habito != null) {
            val clave = LogicalDays.periodKeyOf(hoy, habito.period)
            return Compliance.complianceOf(state, habito, clave, hoy) == ComplianceStatus.FULFILLED
        }
        return state.tasks.firstOrNull { it.id == ref }?.status == TaskStatus.DONE
    }

    private fun pideAlarmas(v: PropuestaVigente): Boolean = v.trozos.any { it.bolsa == Bolsa.NU_PERMISO_ALARMAS }

    // Pintar y redactar.

    /** La vigente con los números al día (spec §4.5): «Tu día», las filas del cierre, los días perfectos y el nombre. */
    private fun pintar(
        v: PropuestaVigente,
        h: Hechos,
        ctx: Contexto,
    ): Propuesta {
        val huecos = v.huecos.toMutableMap()

        fun poner(
            hueco: Hueco,
            valor: ValorHueco,
        ) {
            if (hueco in huecos) huecos[hueco] = valor
        }
        if (h.tuDiaTotal > 0) {
            poner(Hueco.HECHOS, ValorHueco.Numero(h.tuDiaHechos))
            poner(Hueco.TOTAL, ValorHueco.Numero(h.tuDiaTotal))
        }
        if (v.situacion == Situacion.CERRAR_DIA && h.pendientesCierre > 0) poner(Hueco.N, ValorHueco.Numero(h.pendientesCierre))
        if (v.situacion.ambito == Ambito.CAMINO && h.diasPerfectosMes > 0) poner(Hueco.N, ValorHueco.Numero(h.diasPerfectosMes))
        ctx.nombre?.let { poner(Hueco.NOMBRE, ValorHueco.Texto(it)) }
        return Propuesta(
            id = idDe(v),
            texto = TextoHabi.Frase(v.trozos, huecos),
            acciones = EtiquetasAccion.de(v.situacion, v.acciones, v.hora, huecos),
            destino = v.destino,
            hora = v.hora,
        )
    }

    /**
     * La vigente que sería [c] antes de redactarla, para mirar su validez. Lleva solo el núcleo, con el
     * índice 0: la validez lo necesita para distinguir variantes (el permiso de alarmas) y ese índice
     * siempre pasa por existente.
     */
    private fun sinRedactar(
        c: Candidata,
        ambito: Ambito,
        clave: String,
        ctx: Contexto,
    ) = PropuestaVigente(
        ambito = ambito,
        habitId = clave,
        dia = ctx.hoy,
        situacion = c.situacion,
        ref = c.ref,
        trozos = listOfNotNull(c.nucleo?.let { ClaveTrozo(it, ctx.voz, 0) }),
        huecos = c.huecos,
        acciones = c.acciones,
        destino = c.destino,
        hora = c.hora,
        creadoMillis = ctx.ahoraMillis,
    )

    /** Redacta [c], la guarda como vigente y anota los usos y el recuerdo mencionado. */
    private suspend fun redactarYGuardar(
        c: Candidata,
        ambito: Ambito,
        clave: String,
        ctx: Contexto,
        h: Hechos,
        m: Memoria,
    ): PropuestaVigente {
        val respuestasHoy = m.eventos.count { it.tipo == TipoMemoria.RESPUESTA && it.dia == ctx.hoy }
        val semilla = Redactor.semilla(ctx.hoy, c.situacion, c.ref, ctx.voz, respuestasHoy)
        val recuerdo = Recuerdos.elegible(h, m, ctx, enCierre = ambito == Ambito.CIERRE)
        val r = Redactor.redactar(c, ctx, catalogo(), m, Random(semilla), recuerdo = recuerdo)
        val v =
            PropuestaVigente(
                ambito = ambito,
                habitId = clave,
                dia = ctx.hoy,
                situacion = c.situacion,
                ref = c.ref,
                trozos = r.frase.trozos,
                huecos = r.frase.huecos,
                acciones = c.acciones,
                destino = c.destino,
                hora = c.hora,
                creadoMillis = ctx.ahoraMillis,
            )
        vigentes.guardar(v)
        if (r.usos.isNotEmpty()) memoria.anotarUsos(r.usos)
        // El día duro mostrado queda en la memoria con su hábito: Hoy deja ese fuera de «Esperan sin prisa» aunque
        // la tarjeta cambie sin respuesta y la app se reinicie (los usos guardan la situación, no la ref).
        if (c.situacion == Situacion.DIA_DURO) {
            val id = idDe(v)
            memoria.anotar(EventoMemoria("MOSTRADA:$id", TipoMemoria.MOSTRADA, ctx.hoy, id, null, ctx.ahoraMillis))
        }
        r.mencion?.let { evento ->
            memoria.anotar(EventoMemoria("MENCION:$evento", TipoMemoria.MENCION, ctx.hoy, evento, null, ctx.ahoraMillis))
        }
        // Releída: el destino guardado es el que reconstruye el almacén, igual que en las emisiones siguientes.
        return vigentes.leer(ambito, clave) ?: v
    }

    // Responder.

    override suspend fun responder(
        id: String,
        respuesta: Respuesta,
        hora: Int?,
    ): Efecto =
        enCerrojo {
            val (situacion, ref) = leerId(id) ?: return@enCerrojo Efecto.Ninguno
            val ambito = situacion.ambito
            val clave = if (ambito == Ambito.FICHA) ref.orEmpty() else ""
            val v = vigentes.leer(ambito, clave) ?: return@enCerrojo Efecto.Ninguno
            val state = estado.first()
            val ctx = contexto(state, ajustes.first())
            // Review Focus 4: un id de otro día u otra propuesta no hace nada.
            if (v.dia != ctx.hoy || idDe(v) != id) return@enCerrojo Efecto.Ninguno
            // «Otra hora» sin hora solo pide el selector: la respuesta se anota cuando llega la hora.
            if (respuesta != Respuesta.OTRA_HORA || hora != null) {
                memoria.anotar(
                    EventoMemoria(
                        "RESPUESTA:$id:${ctx.ahoraMillis}",
                        TipoMemoria.RESPUESTA,
                        ctx.hoy,
                        id,
                        respuesta.ordinal,
                        ctx.ahoraMillis,
                    ),
                )
            }
            val m = memoria.observe(flowOf(ctx.hoy)).first()
            val h = hechos(state, m, ctx, recordatorios.first())
            Respondiendo(v, clave, state, ctx, h, m).con(respuesta, hora)
        }

    /** Una respuesta a la vigente [v], con todo leído ya. */
    private inner class Respondiendo(
        val v: PropuestaVigente,
        val clave: String,
        val state: DomainState,
        val ctx: Contexto,
        val h: Hechos,
        val m: Memoria,
    ) {
        private val id = idDe(v)

        suspend fun con(
            respuesta: Respuesta,
            hora: Int?,
        ): Efecto =
            when (respuesta) {
                Respuesta.VALE -> vale(hora ?: v.hora)
                Respuesta.AHORA -> {
                    val destino = destinoAhora()
                    vigentes.borrar(v.ambito, clave)
                    destino?.let { Efecto.Sesion(it) } ?: Efecto.Ninguno
                }
                Respuesta.HECHO -> hecho()
                Respuesta.CERRAR_DIA -> Efecto.AbrirCierre
                Respuesta.ELEGIR_GORDO -> Efecto.AbrirLoGordo
                Respuesta.OTRA_HORA -> otraHora(hora)
                Respuesta.LUEGO, Respuesta.HOY_NO -> {
                    trasHoyNo()
                    Efecto.Ninguno
                }
            }

        private suspend fun vale(pedido: Int?): Efecto {
            // §5.2: con dos avisos aceptados hoy (contados con la memoria de ahora, bajo el cerrojo), «Vale» ya no
            // programa otro aunque la propuesta naciera con hora: queda como el «Vale» sin hora del día duro.
            val minuto = pedido?.takeUnless { Motor.dosAvisosHoy(m, ctx) }
            when {
                v.situacion == Situacion.PERMISO_AVISOS -> {
                    vigentes.borrar(v.ambito, clave)
                    return Efecto.PedirPermiso(alarmas = pideAlarmas(v))
                }
                v.situacion == Situacion.FI_OBJETIVO -> {
                    val habito = state.habits.firstOrNull { it.id == v.ref } ?: return Efecto.Ninguno
                    vigentes.borrar(v.ambito, clave)
                    return Efecto.EditarHabito(habito.id, maxOf(habito.step, habito.target / 2))
                }
            }
            if (minuto != null && v.ref != null) {
                aceptados.crear(aceptado(v.ref, minuto, id))
            }
            if (v.ambito == Ambito.HOY) {
                redactarYGuardar(Respuestas.confirmaVale(candidata(v), minuto, h), Ambito.HOY, "", ctx, h, m)
            } else {
                vigentes.borrar(v.ambito, clave)
            }
            if (minuto != null) avisos.replanificar()
            return Efecto.Ninguno
        }

        /** «Otra hora»: sin hora pide elegirla (spec §5.3); con hora, mueve el aceptado y confirma de nuevo. */
        private suspend fun otraHora(minuto: Int?): Efecto {
            if (minuto == null) {
                val ventana = HoraPropuesta.ventana(ctx) ?: return Efecto.Ninguno
                val actual = (v.hora ?: ventana.first).coerceIn(ventana)
                return Efecto.ElegirHora(ventana.first, ventana.last, actual)
            }
            val ref = v.ref ?: return Efecto.Ninguno
            val viejos = m.avisos.filter { it.habitId == ref && it.dia == ctx.hoy && it.estado == EstadoAviso.PENDIENTE }
            // §5.2: el nuevo sustituye a los que se cancelan; si sin ellos ya hay dos (uno lanzado, por ejemplo), no hay tercero.
            if (Motor.dosAvisosHoy(m.copy(avisos = m.avisos - viejos.toSet()), ctx)) return Efecto.Ninguno
            viejos.forEach { aceptados.cambiarEstado(it.id, EstadoAviso.CANCELADO) }
            if (viejos.isNotEmpty()) avisos.cancelarAceptados(viejos.map { it.id })
            aceptados.crear(aceptado(ref, minuto, viejos.firstOrNull()?.propuestaId ?: id))
            redactarYGuardar(Respuestas.confirmaVale(candidata(v), minuto, h), v.ambito, clave, ctx, h, m)
            avisos.replanificar()
            return Efecto.Ninguno
        }

        /** «Hecho» marca (CHECK uno, el resto un paso) y, en la vuelta, recuerda los días fuera (spec §5.6). */
        private suspend fun hecho(): Efecto {
            val habito = state.habits.firstOrNull { it.id == v.ref } ?: return Efecto.Ninguno
            registrar(habito.id, if (habito.metric == Metric.CHECK) 1 else habito.step)
            if (v.situacion == Situacion.VUELTA) {
                val dias = (v.huecos[Hueco.DIAS_FUERA] as? ValorHueco.Numero)?.v ?: h.diasSinActividad
                memoria.anotar(EventoMemoria("VUELTA:${ctx.hoy}", TipoMemoria.VUELTA, ctx.hoy, habito.id, dias, ctx.ahoraMillis))
            }
            vigentes.borrar(v.ambito, clave)
            return Efecto.Ninguno
        }

        /** En Hoy sube la frase corta; en la ficha la nota se va y el motor decide (el «No hace falta» lo filtra él). */
        private suspend fun trasHoyNo() {
            if (v.ambito == Ambito.HOY) {
                redactarYGuardar(Respuestas.trasHoyNo(h), Ambito.HOY, "", ctx, h, m)
            } else {
                vigentes.borrar(v.ambito, clave)
            }
        }

        /** En la confirmación y tras elegir lo gordo, «Sesión de 25 min» va a lo gordo pendiente. */
        private fun destinoAhora(): Destino? {
            if (v.situacion != Situacion.CONFIRMA_VALE && v.situacion != Situacion.GORDO_ELEGIDO) return v.destino
            val pendiente = m.gordo[ctx.hoy].orEmpty().firstOrNull { it !in h.gordoHecho }
            return pendiente?.let { if (it.tipo == TipoRefGordo.TAREA) Destino.Tarea(it.id) else Destino.Habito(it.id) } ?: v.destino
        }

        private fun aceptado(
            habitId: String,
            minuto: Int,
            propuestaId: String,
        ) = AvisoAceptado("$propuestaId:$minuto", ctx.hoy, habitId, minuto, 0, EstadoAviso.PENDIENTE, propuestaId, ctx.ahoraMillis)
    }

    override suspend fun guardarGordo(refs: List<RefGordo>) {
        enCerrojo {
            val state = estado.first()
            val ctx = contexto(state, ajustes.first())
            gordo.guardar(ctx.hoy, refs)
            val m = memoria.observe(flowOf(ctx.hoy)).first()
            val h = hechos(state, m, ctx, recordatorios.first())
            redactarYGuardar(Respuestas.gordoElegido(h), Ambito.HOY, "", ctx, h, m)
            avisos.replanificar()
        }
    }

    /** Leer y escribir la vigente, de una vez y sin cortarse a medias si quien escucha se va. */
    private suspend fun <T> enCerrojo(bloque: suspend () -> T): T = withContext(NonCancellable) { cerrojo.withLock { bloque() } }

    // Contexto.

    /** El contexto de ahora (ruling P8): sellado y exigibles salen del estado, nunca de los hechos. */
    private fun contexto(
        state: DomainState,
        prefs: Settings,
    ): Contexto {
        val ahora = now()
        val hoy = diaDe(prefs, ahora)
        val p = permisos()
        return Contextos.de(
            ahoraMillis = ahora,
            zona = zone(),
            corteDia = prefs.dayCutoffMinutes,
            horaManana = prefs.habiMorningMinutes,
            horaNoche = prefs.habiNightMinutes,
            dndActivo = prefs.dndEnabled,
            dndDesde = prefs.dndFromMinutes,
            dndHasta = prefs.dndToMinutes,
            voz = prefs.personality,
            nombre = prefs.userName,
            permisoAvisos = p.notificaciones,
            alarmasExactas = p.alarmasExactas,
            diaSellado = Sealing.isSealed(state, hoy),
            quedanExigibles = Hechos.quedanPendientes(state, hoy),
        )
    }

    private fun diaDe(
        prefs: Settings,
        ahora: Long = now(),
    ): Int = LogicalDays.logicalDayOf(ahora, prefs.dayCutoffMinutes, zone())

    private companion object {
        const val MILLIS_POR_MINUTO = 60_000L

        /** Las que proponen hacer su ref: si se hace desde la lista, el widget o un aviso, la tarjeta se cierra. */
        val PROPONEN_HACER =
            setOf(
                Situacion.VUELTA,
                Situacion.DIA_DURO,
                Situacion.AYER_SE_ESCAPO,
                Situacion.RACHA_CERCA,
                Situacion.DIA_FLOJO,
                Situacion.HORA_HABITUAL,
                Situacion.TAREA_VENCE,
                Situacion.GORDO_SIN_EMPEZAR,
                Situacion.CONFIRMA_VALE,
                Situacion.FI_HOY_FALTA,
            )

        fun idDe(v: PropuestaVigente): String = idPropuesta(v.situacion, v.ref, v.dia)

        /** «SITUACION:ref:día» → situación y ref, o null si no se entiende. */
        fun leerId(id: String): Pair<Situacion, String?>? {
            val partes = id.split(':')
            if (partes.size < 3) return null
            val situacion = Situacion.entries.firstOrNull { it.name == partes.first() } ?: return null
            val ref = partes.subList(1, partes.size - 1).joinToString(":").ifEmpty { null }
            return situacion to ref
        }

        fun candidata(v: PropuestaVigente) = Candidata(v.situacion, v.ref, v.huecos - Hueco.NOMBRE, v.acciones, v.destino, v.hora)

        fun minutoDe(
            millis: Long,
            zona: ZoneId,
        ): Int {
            val reloj = Instant.ofEpochMilli(millis).atZone(zona)
            return reloj.hour * 60 + reloj.minute
        }
    }
}
