package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.habla.reglas.ReglaAyerSeEscapo
import com.alvarotc.bito.domain.habla.reglas.ReglaCerrarDia
import com.alvarotc.bito.domain.habla.reglas.ReglaDiaDuro
import com.alvarotc.bito.domain.habla.reglas.ReglaDiaFlojo
import com.alvarotc.bito.domain.habla.reglas.ReglaElegirGordo
import com.alvarotc.bito.domain.habla.reglas.ReglaGordoSinEmpezar
import com.alvarotc.bito.domain.habla.reglas.ReglaHoraHabitual
import com.alvarotc.bito.domain.habla.reglas.ReglaPermisoAvisos
import com.alvarotc.bito.domain.habla.reglas.ReglaRachaCerca
import com.alvarotc.bito.domain.habla.reglas.ReglaSaludo
import com.alvarotc.bito.domain.habla.reglas.ReglaTareaVence
import com.alvarotc.bito.domain.habla.reglas.ReglaTodoHecho
import com.alvarotc.bito.domain.habla.reglas.ReglaVuelta
import com.alvarotc.bito.domain.habla.reglas.ReglasCamino
import com.alvarotc.bito.domain.habla.reglas.ReglasCierre
import com.alvarotc.bito.domain.habla.reglas.ReglasFicha
import com.alvarotc.bito.domain.habla.reglas.Respuestas
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.task
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * El contrato de huecos (`tools/habla/HUECOS.md` y `obligatorios` en `tools/habla/bolsas.json`): todo lo
 * que el dominio puede producir (reglas, respuestas, recuerdos y los retoques del motor) pasa al menos los
 * huecos obligatorios de su bolsa, también cuando falta un dato opcional. Los avisos y el cierre arman sus
 * huecos en la capa de Android y no entran aquí.
 */
class ContratoHuecosTest {
    private val obligatorios: Map<String, Set<String>> =
        Json
            .parseToJsonElement(java.io.File("../tools/habla/bolsas.json").readText())
            .jsonArray
            .map { it.jsonObject }
            .associate { o ->
                o.getValue("bolsa").jsonPrimitive.content to
                    o.getValue("obligatorios").jsonArray.map { it.jsonPrimitive.content }.toSet()
            }

    /** Lo producido en un escenario: la bolsa y los huecos que lleva. */
    private data class Producido(
        val escenario: String,
        val bolsa: Bolsa,
        val huecos: Set<Hueco>,
    )

    private val tareaInforme = RefGordo(TipoRefGordo.TAREA, "t1")
    private val tareaBanco = RefGordo(TipoRefGordo.TAREA, "t2")
    private val habitoGordo = RefGordo(TipoRefGordo.HABITO, "h1")
    private val tareas =
        listOf(tarea("t1", titulo = "El informe", primerPaso = "Abrir el documento"), tarea("t2", titulo = "Llamar al banco"))

    private fun aviso(id: String) = AvisoAceptado(id, TODAY, "h1", 15 * 60, 0, EstadoAviso.PENDIENTE, "p-$id", 1L)

    private fun ev(
        id: String,
        tipo: TipoMemoria,
        dia: Int,
        valor: Int? = null,
        ref: String? = null,
    ) = EventoMemoria(id, tipo, dia, ref, valor, 0L)

    private fun candidatas(
        escenario: String,
        cs: List<Candidata?>,
    ): List<Producido> =
        cs.filterNotNull().map { c -> Producido(escenario, checkNotNull(c.nucleo) { escenario }, c.huecos.keys) }.also {
            assertTrue("el escenario «$escenario» no produjo nada", it.isNotEmpty())
        }

    private fun recuerdo(
        escenario: String,
        h: Hechos,
        m: Memoria,
        enCierre: Boolean = false,
    ): List<Producido> = listOfNotNull(Recuerdos.elegible(h, m, ctx(), enCierre)).map { Producido(escenario, it.bolsa, it.huecos.keys) }

    private fun hoy(): List<Producido> {
        val leer = habito()
        val duracion = habito(id = "h3", nombre = "Meditar", tipo = TipoHabito.DURACION, objetivo = 10)
        val escapado = leer.copy(falladoAyer = true)
        return buildList {
            addAll(candidatas("sin permiso de avisos", ReglaPermisoAvisos.candidatas(hechos(), Memoria(), ctx(permisoAvisos = false))))
            addAll(
                candidatas(
                    "sin alarmas exactas",
                    ReglaPermisoAvisos.candidatas(hechos(), Memoria(avisos = listOf(aviso("a1"))), ctx(alarmasExactas = false)),
                ),
            )
            addAll(
                candidatas(
                    "vuelta",
                    ReglaVuelta.candidatas(hechos(listOf(leer), primerDiaTrasAusencia = true, diasSinActividad = 4), Memoria(), ctx()),
                ),
            )
            addAll(candidatas("día duro", ReglaDiaDuro.candidatas(hechos(listOf(leer), animoAyer = 2), Memoria(), ctx())))
            addAll(
                candidatas(
                    "cerrar el día",
                    ReglaCerrarDia.candidatas(hechos(listOf(leer), pendientesCierre = 2), Memoria(), ctx(ahoraMin = 22 * 60)),
                ),
            )
            addAll(candidatas("todo hecho", ReglaTodoHecho.candidatas(hechos(listOf(habito(hechoHoy = true))), Memoria(), ctx())))
            addAll(
                candidatas(
                    "elegir lo gordo con sugerencia",
                    ReglaElegirGordo.candidatas(
                        hechos(tareas = tareas, candidatosGordo = 2, sugerenciaGordo = tareaInforme),
                        Memoria(),
                        ctx(),
                    ),
                ),
            )
            addAll(
                candidatas(
                    "elegir lo gordo sin sugerencia",
                    ReglaElegirGordo.candidatas(hechos(tareas = tareas, candidatosGordo = 2), Memoria(), ctx()),
                ),
            )
            addAll(
                candidatas(
                    "lo gordo sin empezar, con primer paso",
                    ReglaGordoSinEmpezar.candidatas(
                        hechos(tareas = tareas, gordo = listOf(tareaInforme)),
                        Memoria(),
                        ctx(ahoraMin = 13 * 60),
                    ),
                ),
            )
            addAll(
                candidatas(
                    "lo gordo sin empezar, solo un hábito",
                    ReglaGordoSinEmpezar.candidatas(hechos(listOf(leer), gordo = listOf(habitoGordo)), Memoria(), ctx(ahoraMin = 13 * 60)),
                ),
            )
            addAll(candidatas("ayer se escapó, de marcar", ReglaAyerSeEscapo.candidatas(hechos(listOf(escapado)), Memoria(), ctx())))
            addAll(
                candidatas(
                    "ayer se escapó, sin hora posible",
                    ReglaAyerSeEscapo.candidatas(hechos(listOf(escapado)), Memoria(), ctx(ahoraMin = 20 * 60 + 58)),
                ),
            )
            addAll(
                candidatas(
                    "ayer se escapó, de minutos",
                    ReglaAyerSeEscapo.candidatas(hechos(listOf(duracion.copy(falladoAyer = true))), Memoria(), ctx()),
                ),
            )
            val cerca = habito(racha = 5, hitoCerca = 7, faltanHito = 2).copy(bitosHito = 5)
            addAll(candidatas("racha cerca", ReglaRachaCerca.candidatas(hechos(listOf(cerca)), Memoria(), ctx())))
            val flojo = habito(pct30 = 90, pctHoySemana = 30)
            addAll(candidatas("día flojo", ReglaDiaFlojo.candidatas(hechos(listOf(flojo)), Memoria(), ctx())))
            addAll(
                candidatas(
                    "hora habitual",
                    ReglaHoraHabitual.candidatas(hechos(listOf(habito(horaHabitual = 10 * 60 + 30))), Memoria(), ctx()),
                ),
            )
            addAll(
                candidatas(
                    "tarea que vence",
                    ReglaTareaVence.candidatas(hechos(tareas = listOf(tarea(venceHoy = true))), Memoria(), ctx()),
                ),
            )
            addAll(candidatas("saludo sin nombre ni hábitos", ReglaSaludo.candidatas(hechos(), Memoria(), ctx(nombre = null))))
        }
    }

    private fun respuestas(): List<Producido> {
        val vale = ReglaDiaDuro.candidatas(hechos(listOf(habito()), animoAyer = 2), Memoria(), ctx()).single()
        val conGordo = hechos(tareas = tareas, gordo = listOf(tareaInforme, tareaBanco))
        // Lo gordo apunta a una tarea que ya no existe: no hay nombre que dar.
        val gordoSinNombre = hechos(gordo = listOf(RefGordo(TipoRefGordo.TAREA, "borrada")))
        return buildList {
            addAll(candidatas("confirmación sin lo gordo", listOf(Respuestas.confirmaVale(vale, 900, hechos()))))
            addAll(candidatas("confirmación con lo gordo", listOf(Respuestas.confirmaVale(vale, 900, conGordo))))
            addAll(candidatas("confirmación con lo gordo sin nombre", listOf(Respuestas.confirmaVale(vale, 900, gordoSinNombre))))
            addAll(candidatas("confirmación sin hora", listOf(Respuestas.confirmaVale(vale, null, hechos()))))
            addAll(candidatas("confirmación sin hora con lo gordo", listOf(Respuestas.confirmaVale(vale, null, conGordo))))
            addAll(candidatas("lo gordo elegido, tareas", listOf(Respuestas.gordoElegido(conGordo))))
            addAll(
                candidatas(
                    "lo gordo elegido, un hábito",
                    listOf(Respuestas.gordoElegido(hechos(listOf(habito()), gordo = listOf(habitoGordo)))),
                ),
            )
            addAll(candidatas("tras hoy no", listOf(Respuestas.trasHoyNo(conGordo))))
            addAll(candidatas("noche con arrastre", listOf(Respuestas.noche(conGordo))))
            addAll(candidatas("noche sin arrastre", listOf(Respuestas.noche(hechos()))))
        }
    }

    /** Los retoques del motor: ayer regular (`nu_regular`) y el tope de dos avisos. */
    private fun motor(): List<Producido> {
        val escapado = habito(falladoAyer = true)
        val dosAvisos = Memoria(avisos = listOf(aviso("a1"), aviso("a2")))
        return buildList {
            add(
                Motor.elegirHoy(hechos(listOf(escapado), animoAyer = 1), Memoria(), ctx()).let {
                    Producido("ayer regular, ayer se escapó", it.nucleo!!, it.huecos.keys)
                },
            )
            add(
                Motor
                    .elegirHoy(hechos(listOf(habito()), gordo = listOf(habitoGordo), animoAyer = 1), Memoria(), ctx(ahoraMin = 13 * 60))
                    .let { Producido("ayer regular, lo gordo es un hábito", it.nucleo!!, it.huecos.keys) },
            )
            add(
                Motor.elegirHoy(hechos(listOf(escapado)), dosAvisos, ctx()).let {
                    Producido("dos avisos aceptados, ayer se escapó", it.nucleo!!, it.huecos.keys)
                },
            )
        }
    }

    private fun ficha(): List<Producido> {
        fun de(
            escenario: String,
            h: HabitoHoy,
        ) = candidatas(escenario, ReglasFicha.candidatas(hechos(listOf(h)), Memoria(), ctx(), h.id))
        return buildList {
            addAll(de("en pausa sin fecha", habito(enPausa = true)))
            addAll(de("hoy falta", habito()))
            addAll(de("objetivo", habito(tipo = TipoHabito.CONTADOR, objetivo = 8, pct30 = 20, juzgados30 = 20)))
            addAll(de("nuevo", habito(creadoHaceDias = 3)))
            addAll(de("racha y mejor", habito(racha = 5, mejorRacha = 5)))
            addAll(de("limpio", habito(tipo = TipoHabito.DEJAR, diasLimpios = 3)))
            addAll(de("mejor día", habito(pct30 = 60).copy(mejorDiaSemana = DayOfWeek.MONDAY, pctMejorDiaSemana = 95)))
            addAll(de("constancia sin días juzgados", habito(pct30 = null)))
        }
    }

    private fun camino(): List<Producido> =
        Mood.entries.flatMap { animo ->
            candidatas("Camino $animo sin días perfectos", ReglasCamino.candidatas(hechos().copy(animoSemana = animo), Memoria(), ctx()))
        }

    private fun cierre(): List<Producido> {
        val logro = habito(falladoAyer = true, hechoHoy = true, racha = 2)
        val redonda = habito(id = "h2", nombre = "Correr", hechoHoy = true, racha = 10).copy(rachaHoyRedonda = true)
        val h =
            hechos(listOf(logro, redonda), tareas = tareas.map { it.copy(hecha = true) }, gordo = listOf(tareaInforme))
                .copy(gordoHecho = listOf(tareaInforme))
        return candidatas("el cierre", ReglasCierre.candidatas(h, Memoria(), ctx(nombre = null)))
    }

    private fun recuerdos(): List<Producido> {
        val enRacha = habito(id = "h2", nombre = "Correr", racha = 9)
        return buildList {
            addAll(recuerdo("racha recuperada", hechos(listOf(habito().copy(racha = 8, rachaRotaRecuperada = true))), Memoria()))
            addAll(recuerdo("vuelta", hechos(listOf(enRacha)), Memoria(eventos = listOf(ev("v", TipoMemoria.VUELTA, TODAY - 20, 4)))))
            addAll(
                recuerdo(
                    "día duro pasado",
                    hechos(),
                    Memoria(eventos = listOf(ev("d", TipoMemoria.ANIMO, TODAY - 5, 2), ev("b", TipoMemoria.ANIMO, TODAY - 1, 0))),
                ),
            )
            // Lo gordo de ayer, entero, con sus tareas hechas ayer: los hechos salen del estado, como en la app.
            val ayer =
                Memoria(
                    eventos = listOf(ev("g", TipoMemoria.GORDO_HECHO, TODAY - 1, 11)),
                    gordo = mapOf(TODAY - 1 to listOf(tareaInforme)),
                )
            val estado = domainState(tasks = listOf(task(id = "t1", title = "El informe", status = TaskStatus.DONE, doneOnDay = TODAY - 1)))
            addAll(recuerdo("lo gordo hecho ayer", Hechos.de(estado, ayer, ctx(), emptyMap()), ayer))
            addAll(
                recuerdo(
                    "cumpliste a la hora",
                    hechos(listOf(habito())),
                    Memoria(eventos = listOf(ev("c", TipoMemoria.ACEPTADO_CUMPLIDO, TODAY - 1, 15 * 60, ref = "h1"))),
                ),
            )
            addAll(
                recuerdo(
                    "cumpliste a la hora, el hábito ya no está",
                    hechos(),
                    Memoria(eventos = listOf(ev("c", TipoMemoria.ACEPTADO_CUMPLIDO, TODAY - 1, 15 * 60, ref = "h1"))),
                ),
            )
            addAll(recuerdo("bien ayer", hechos(), Memoria(eventos = listOf(ev("a", TipoMemoria.ANIMO, TODAY - 1, 0)))))
            val duros = (1..3).map { ev("p$it", TipoMemoria.ANIMO, TODAY - 7 * it, 2) }
            addAll(recuerdo("patrón de ánimo", hechos(), Memoria(eventos = duros), enCierre = true))
        }
    }

    private fun todo(): List<Producido> = hoy() + respuestas() + motor() + ficha() + camino() + cierre() + recuerdos()

    @Test
    fun `everything the domain produces passes the obligatory holes of its bag`() {
        val faltas =
            todo().mapNotNull { p ->
                val faltan = obligatorios.getValue(p.bolsa.clave) - p.huecos.map { it.clave }.toSet()
                if (faltan.isEmpty()) null else "${p.escenario} (${p.bolsa.clave}): faltan $faltan"
            }
        assertEquals(emptyList<String>(), faltas)
    }

    @Test
    fun `the scenarios cover every bag the domain can produce`() {
        val esperadas =
            Situacion.entries.mapNotNull { it.nucleo }.toSet() +
                setOf(Bolsa.NU_REGULAR, Bolsa.NU_PERMISO_ALARMAS, Bolsa.NU_CONFIRMA_VALE_GORDO) +
                Bolsa.entries.filter { it.tipo == TipoTrozo.RECUERDO }
        assertEquals(esperadas, todo().map { it.bolsa }.toSet())
    }

    @Test
    fun `the obligatory holes are holes of the bag`() =
        Bolsa.entries.forEach { b ->
            assertTrue(b.clave, obligatorios.getValue(b.clave).all { o -> b.huecos.any { it.clave == o } })
        }
}
