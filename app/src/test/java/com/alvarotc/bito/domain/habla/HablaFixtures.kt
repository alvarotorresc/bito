package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.millisOn
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.testZone
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/*
 * Constructores de prueba del motor de Habi. Todo lleva valores por defecto para que cada test
 * diga solo lo que le importa. `TODAY` es el de DomainFixtures: viernes 2026-08-14.
 * Ningún carril lo modifica: si necesita otro constructor, lo crea en su propio archivo de test.
 */

private val eventoSeq = AtomicInteger(0)

/** Fecha, día de la semana, estación y franja salen de [hoy] y [ahoraMin]. */
internal fun ctx(
    ahoraMin: Int = 10 * 60,
    hoy: LogicalDay = TODAY,
    voz: Personality = Personality.NEUTRA,
    nombre: String? = "Álvaro",
    horaManana: Int = 8 * 60 + 30,
    horaNoche: Int = 21 * 60 + 30,
    corteDia: Int = 0,
    silencio: Silencio? = null,
    permisoAvisos: Boolean = true,
    alarmasExactas: Boolean = true,
    fase: FaseHabi = FaseHabi.DESPIERTA,
    zona: ZoneId = testZone,
): Contexto {
    val fecha = LocalDate.ofEpochDay(hoy.toLong())
    return Contexto(
        hoy = hoy,
        ahoraMin = ahoraMin,
        ahoraMillis = millisOn(hoy, ahoraMin / 60, ahoraMin % 60),
        fecha = fecha,
        diaSemana = fecha.dayOfWeek,
        estacion = Estacion.de(fecha, zona),
        franja = Franja.de(ahoraMin),
        fase = fase,
        voz = voz,
        nombre = nombre,
        horaManana = horaManana,
        horaNoche = horaNoche,
        corteDia = corteDia,
        silencio = silencio,
        permisoAvisos = permisoAvisos,
        alarmasExactas = alarmasExactas,
        zona = zona,
    )
}

/** El resto de campos con valores neutros. */
internal fun habito(
    id: String = "h1",
    nombre: String = "Leer",
    tipo: TipoHabito = TipoHabito.CHECK,
    diario: Boolean = true,
    exigibleHoy: Boolean = true,
    hechoHoy: Boolean = false,
    objetivo: Int = 1,
    paso: Int = 1,
    racha: Int = 0,
    mejorRacha: Int = 0,
    pct30: Int? = 80,
    juzgados30: Int = 30,
    falladoAyer: Boolean = false,
    cumplidosTresAnteriores: Int = 3,
    semanasHistoria: Int = 10,
    pctHoySemana: Int? = 80,
    muestrasHoySemana: Int = 8,
    horaHabitual: Int? = null,
    recordatorio: Int? = null,
    hitoCerca: Int? = null,
    faltanHito: Int? = null,
    enPausa: Boolean = false,
    creadoHaceDias: Int = 100,
    diasLimpios: Int = 0,
): HabitoHoy =
    HabitoHoy(
        id = id,
        nombre = nombre,
        tipo = tipo,
        diario = diario,
        exigibleHoy = exigibleHoy,
        hechoHoy = hechoHoy,
        falladoHoy = false,
        enPausa = enPausa,
        pausadoDesde = null,
        progreso = if (hechoHoy) objetivo else 0,
        objetivo = objetivo,
        paso = paso,
        unidad = null,
        racha = racha,
        mejorRacha = mejorRacha,
        diasLimpios = diasLimpios,
        creadoHaceDias = creadoHaceDias,
        semanasHistoria = semanasHistoria,
        falladoAyer = falladoAyer,
        cumplidosTresAnteriores = cumplidosTresAnteriores,
        pct30 = pct30,
        juzgados30 = juzgados30,
        pctHoySemana = pctHoySemana,
        muestrasHoySemana = muestrasHoySemana,
        mejorDiaSemana = null,
        pctMejorDiaSemana = null,
        horaHabitual = horaHabitual,
        recordatorio = recordatorio,
        hitoCerca = hitoCerca,
        faltanHito = faltanHito,
        bitosHito = null,
        rachaHoyRedonda = false,
        rachaRotaRecuperada = false,
    )

internal fun tarea(
    id: String = "t1",
    titulo: String = "El informe",
    vencidaDias: Int? = null,
    venceHoy: Boolean = false,
    hecha: Boolean = false,
    empezada: Boolean = false,
    primerPaso: String? = null,
): TareaHoy = TareaHoy(id, titulo, primerPaso, vencidaDias, venceHoy, hecha, empezada)

/** `tuDia*` y `tareasPendientesHoy` se calculan de [habitos] y [tareas]; lo gordo, al final, por si hace falta. */
internal fun hechos(
    habitos: List<HabitoHoy> = emptyList(),
    tareas: List<TareaHoy> = emptyList(),
    gordo: List<RefGordo> = emptyList(),
    diasSinActividad: Int = 0,
    primerDiaTrasAusencia: Boolean = false,
    animoAyer: Int? = null,
    diaSellado: Boolean = false,
    pendientesCierre: Int = 0,
    gordoAyerSinHacer: List<RefGordo> = emptyList(),
    candidatosGordo: Int = 0,
    sugerenciaGordo: RefGordo? = null,
    gordoHecho: List<RefGordo> = emptyList(),
): Hechos =
    Hechos(
        hoy = TODAY,
        habitos = habitos,
        tareas = tareas,
        gordo = gordo,
        gordoAyerSinHacer = gordoAyerSinHacer,
        diasSinActividad = diasSinActividad,
        primerDiaTrasAusencia = primerDiaTrasAusencia,
        animoAyer = animoAyer,
        diaDuro = animoAyer == 2,
        diaSellado = diaSellado,
        tuDiaHechos = habitos.count { it.exigibleHoy && it.hechoHoy },
        tuDiaTotal = habitos.count { it.exigibleHoy },
        pendientesCierre = pendientesCierre,
        tareasPendientesHoy = tareas.filterNot { it.hecha }.map { it.titulo },
        animoSemana = Mood.NORMAL,
        diasPerfectosMes = 0,
        deltaSemanaPct = null,
        candidatosGordo = candidatosGordo,
        sugerenciaGordo = sugerenciaGordo,
        gordoHecho = gordoHecho,
    )

/** Un `RESPUESTA` como lo escribe la fuente: `ref` = id de la propuesta, `valor` = ordinal de la respuesta. */
internal fun respuesta(
    situacion: Situacion,
    ref: String?,
    r: Respuesta,
    millis: Long,
    dia: LogicalDay = TODAY,
): EventoMemoria =
    EventoMemoria(
        id = "evento-${eventoSeq.incrementAndGet()}",
        tipo = TipoMemoria.RESPUESTA,
        dia = dia,
        ref = idPropuesta(situacion, ref, dia),
        valor = r.ordinal,
        creadoMillis = millis,
    )

internal fun mostrada(
    situacion: Situacion,
    millis: Long,
    ref: String? = null,
): Mostrada = Mostrada(situacion, ref, millis)

/** Un catálogo con esas bolsas, igual para cualquier voz; el resto, vacías. */
internal fun catalogoFalso(vararg bolsas: Pair<Bolsa, List<TrozoInfo>>): CatalogoHabla {
    val porBolsa = bolsas.toMap()
    return object : CatalogoHabla {
        override fun trozos(
            bolsa: Bolsa,
            voz: Personality,
        ): List<TrozoInfo> = porBolsa[bolsa].orEmpty()
    }
}
