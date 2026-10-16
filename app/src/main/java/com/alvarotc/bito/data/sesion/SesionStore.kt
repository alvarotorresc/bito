package com.alvarotc.bito.data.sesion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Evento
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.PlanSesion
import com.alvarotc.bito.domain.sesion.RegistroSesion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * La sesión en curso en `focus.preferences_pb` (spec §14.2): el [RegistroSesion] entero como una
 * sola cadena JSON con versión. Un JSON ilegible o de otra versión es «no hay sesión» y se borra
 * al leerlo. [borrar] quita solo su clave: en el mismo archivo viven otras.
 */
class SesionStore(private val dataStore: DataStore<Preferences>) {
    val registro: Flow<RegistroSesion?> = dataStore.data.map { prefs -> prefs[CLAVE]?.let(::decodificar) }

    suspend fun leer(): RegistroSesion? {
        val texto = dataStore.data.first()[CLAVE] ?: return null
        val registro = decodificar(texto)
        if (registro == null) {
            dataStore.edit { prefs -> if (prefs[CLAVE] == texto) prefs.remove(CLAVE) }
        }
        return registro
    }

    suspend fun guardar(r: RegistroSesion) {
        val texto = json.encodeToString(RegistroDto.serializer(), RegistroDto.de(r))
        dataStore.edit { prefs ->
            CLAVES_VIEJAS.forEach { prefs.remove(it) }
            prefs[CLAVE] = texto
        }
    }

    suspend fun borrar() {
        dataStore.edit { prefs -> prefs.remove(CLAVE) }
    }

    private fun decodificar(texto: String): RegistroSesion? =
        try {
            json.decodeFromString(RegistroDto.serializer(), texto).takeIf { it.v == VERSION_REGISTRO }?.aDominio()
        } catch (e: IllegalArgumentException) {
            // SerializationException incluida: JSON roto, campos que faltan o un enum desconocido.
            null
        }

    private companion object {
        val CLAVE = stringPreferencesKey("sesion_v1")

        /** Las de `FocusSession`, que esta sesión sustituye. */
        val CLAVES_VIEJAS =
            listOf(
                stringPreferencesKey("task_id"),
                longPreferencesKey("started_at_millis"),
                longPreferencesKey("ends_at_millis"),
                longPreferencesKey("ends_at_elapsed"),
                longPreferencesKey("boot_millis"),
            )

        val json =
            Json {
                classDiscriminator = "tipo"
                encodeDefaults = true
            }
    }
}

private const val VERSION_REGISTRO = 1

// Forma en disco, separada del dominio para que un cambio de modelo no cambie el archivo sin querer.

@Serializable
private data class RegistroDto(
    val v: Int,
    val id: String,
    val plan: PlanDto,
    val eventos: List<EventoDto>,
    val inicioPared: Long,
    val anclaElapsed: Long,
    val arranque: Long,
    val filtroPrevio: Int?,
    val inicioDia: Int,
) {
    fun aDominio() =
        RegistroSesion(
            id = id,
            plan = plan.aDominio(),
            eventos = eventos.map { it.aDominio() },
            inicioPared = inicioPared,
            anclaElapsed = anclaElapsed,
            arranque = arranque,
            filtroPrevio = filtroPrevio,
            inicioDia = inicioDia,
        )

    companion object {
        fun de(r: RegistroSesion) =
            RegistroDto(
                v = VERSION_REGISTRO,
                id = r.id,
                plan = PlanDto.de(r.plan),
                eventos = r.eventos.map { EventoDto.de(it) },
                inicioPared = r.inicioPared,
                anclaElapsed = r.anclaElapsed,
                arranque = r.arranque,
                filtroPrevio = r.filtroPrevio,
                inicioDia = r.inicioDia,
            )
    }
}

@Serializable
private data class PlanDto(
    val objetivo: ObjetivoDto,
    val minutosFoco: Int,
    val entrada: Entrada,
    val bloques: Int,
    val descansoCorto: Int,
    val descansoLargo: Int,
    val rondas: Int,
    val descansoSolo: Boolean,
    val fondo: SessionBackground,
    val noMolestar: Boolean,
) {
    fun aDominio() =
        PlanSesion(
            objetivo = objetivo.aDominio(),
            minutosFoco = minutosFoco,
            entrada = entrada,
            bloques = bloques,
            descansoCorto = descansoCorto,
            descansoLargo = descansoLargo,
            rondas = rondas,
            descansoSolo = descansoSolo,
            fondo = fondo,
            noMolestar = noMolestar,
        )

    companion object {
        fun de(p: PlanSesion) =
            PlanDto(
                objetivo = ObjetivoDto.de(p.objetivo),
                minutosFoco = p.minutosFoco,
                entrada = p.entrada,
                bloques = p.bloques,
                descansoCorto = p.descansoCorto,
                descansoLargo = p.descansoLargo,
                rondas = p.rondas,
                descansoSolo = p.descansoSolo,
                fondo = p.fondo,
                noMolestar = p.noMolestar,
            )
    }
}

@Serializable
private sealed interface ObjetivoDto {
    fun aDominio(): Objetivo

    @Serializable
    @SerialName("tarea")
    data class Tarea(val id: String) : ObjetivoDto {
        override fun aDominio() = Objetivo.Tarea(id)
    }

    @Serializable
    @SerialName("habito")
    data class Habito(val id: String) : ObjetivoDto {
        override fun aDominio() = Objetivo.Habito(id)
    }

    @Serializable
    @SerialName("libre")
    data object Libre : ObjetivoDto {
        override fun aDominio() = Objetivo.Libre
    }

    companion object {
        fun de(o: Objetivo): ObjetivoDto =
            when (o) {
                is Objetivo.Tarea -> Tarea(o.id)
                is Objetivo.Habito -> Habito(o.id)
                Objetivo.Libre -> Libre
            }
    }
}

@Serializable
private sealed interface EventoDto {
    fun aDominio(): Evento

    @Serializable
    @SerialName("pausa")
    data class Pausa(val en: Long) : EventoDto {
        override fun aDominio() = Evento.Pausa(en)
    }

    @Serializable
    @SerialName("seguir")
    data class Seguir(val en: Long) : EventoDto {
        override fun aDominio() = Evento.Seguir(en)
    }

    @Serializable
    @SerialName("sumar")
    data class Sumar(val en: Long, val minutos: Int) : EventoDto {
        override fun aDominio() = Evento.Sumar(en, minutos)
    }

    @Serializable
    @SerialName("saltar")
    data class Saltar(val en: Long) : EventoDto {
        override fun aDominio() = Evento.Saltar(en)
    }

    @Serializable
    @SerialName("empezar_descanso")
    data class EmpezarDescanso(val en: Long) : EventoDto {
        override fun aDominio() = Evento.EmpezarDescanso(en)
    }

    companion object {
        fun de(e: Evento): EventoDto =
            when (e) {
                is Evento.Pausa -> Pausa(e.enMillis)
                is Evento.Seguir -> Seguir(e.enMillis)
                is Evento.Sumar -> Sumar(e.enMillis, e.minutos)
                is Evento.Saltar -> Saltar(e.enMillis)
                is Evento.EmpezarDescanso -> EmpezarDescanso(e.enMillis)
            }
    }
}
