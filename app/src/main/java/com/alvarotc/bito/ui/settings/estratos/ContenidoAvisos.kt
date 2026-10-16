package com.alvarotc.bito.ui.settings.estratos

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.ui.settings.SettingsViewModel
import com.alvarotc.bito.ui.settings.hojas.HojaAvisosHabi
import com.alvarotc.bito.ui.settings.hojas.HojaNoMolestar
import com.alvarotc.bito.ui.settings.hojas.HojaPomodoro
import com.alvarotc.bito.ui.settings.hojas.HojaRecordatorios
import com.alvarotc.bito.ui.settings.hojas.Pomodoro
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.LocalEstrato
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.Fila
import com.alvarotc.bito.ui.sistema.componentes.FilaInterruptor
import com.alvarotc.bito.ui.sistema.componentes.FilaValor
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.RelojCielo
import com.alvarotc.bito.ui.sistema.fuerte
import android.provider.Settings as AndroidSettings

/** La línea del estrato cerrado: «12:00 y 19:00 · Habi 8:30 y 21:30». */
internal fun resumenAvisos(
    settings: Settings,
    resources: Resources,
): String {
    val recordatorios =
        if (settings.globalReminderMinutes.isEmpty()) {
            resources.getString(R.string.ajustes_resumen_sin_recordatorios)
        } else {
            unirHoras(settings.globalReminderMinutes, resources)
        }
    val habi = unirHoras(listOf(settings.habiMorningMinutes, settings.habiNightMinutes), resources)
    return resources.getString(R.string.ajustes_resumen_avisos, recordatorios, habi)
}

private enum class HojaAvisos { RECORDATORIOS, HABI, NO_MOLESTAR, POMODORO, RESPIRACION }

private fun alarmasExactasBloqueadas(alarmManager: AlarmManager?): Boolean =
    Build.VERSION.SDK_INT >= 31 && alarmManager?.canScheduleExactAlarms() == false

/** No depende de la versión: lee el interruptor general de avisos, que existe desde el minSdk. */
private fun avisosActivados(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

/** Ajustes de avisos de la app: el paquete va como extra, no como `package:`, o abre una pantalla genérica. */
private fun abrirAjustesDeAvisos(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
}

/** El cuerpo del estrato «Avisos y respiración» (spec §5.1.3). */
@Composable
fun ContenidoAvisos(
    settings: Settings,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hoja by remember { mutableStateOf<HojaAvisos?>(null) }

    var avisosBloqueados by remember { mutableStateOf(!avisosActivados(context)) }
    val lanzadorAvisos =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            avisosBloqueados = !concedido
        }
    val alarmManager = remember { context.getSystemService(AlarmManager::class.java) }
    var alarmasBloqueadas by remember { mutableStateOf(alarmasExactasBloqueadas(alarmManager)) }
    // Los permisos se conceden en la app de Ajustes del sistema, que no mata este proceso: se vuelven a leer al volver.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        alarmasBloqueadas = alarmasExactasBloqueadas(alarmManager)
        avisosBloqueados = !avisosActivados(context)
    }

    Column(modifier) {
        if (avisosBloqueados) {
            Fila(
                etiqueta = stringResource(R.string.notif_permission_hint),
                alPulsar = { abrirAjustesDeAvisos(context) },
            ) {
                Text(stringResource(R.string.ajustes_permitir), style = BitoTema.tipos.t16.fuerte())
            }
        }
        if (alarmasBloqueadas) {
            Fila(
                etiqueta = stringResource(R.string.ajustes_alarmas_exactas),
                alPulsar = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.ajustes_activar), style = BitoTema.tipos.t16.fuerte())
            }
        }
        FilaValor(
            etiqueta = stringResource(R.string.reminders_section_title),
            subtitulo = stringResource(R.string.ajustes_recordatorios_sub),
            valor =
                if (settings.globalReminderMinutes.isEmpty()) {
                    stringResource(R.string.ajustes_ninguno)
                } else {
                    unirHoras(settings.globalReminderMinutes, context.resources)
                },
            alPulsar = { hoja = HojaAvisos.RECORDATORIOS },
        )
        FilaValor(
            etiqueta = stringResource(R.string.ajustes_avisos_habi),
            subtitulo = stringResource(R.string.ajustes_avisos_habi_sub),
            valor = unirHoras(listOf(settings.habiMorningMinutes, settings.habiNightMinutes), context.resources),
            alPulsar = { hoja = HojaAvisos.HABI },
        )
        FilaValor(
            etiqueta = stringResource(R.string.ajustes_no_molestar),
            subtitulo = stringResource(R.string.ajustes_no_molestar_sub),
            valor =
                if (settings.dndEnabled) {
                    stringResource(R.string.ajustes_rango_horas, horaCorta(settings.dndFromMinutes), horaCorta(settings.dndToMinutes))
                } else {
                    stringResource(R.string.sistema_apagado)
                },
            alPulsar = { hoja = HojaAvisos.NO_MOLESTAR },
        )
        FilaInterruptor(
            etiqueta = stringResource(R.string.settings_celebration_label),
            activo = settings.perfectDayCelebration,
            alCambiar = settingsViewModel::setPerfectDayCelebration,
            modifier = Modifier.testTag("celebration-row"),
        )
        FilaInterruptor(
            etiqueta = stringResource(R.string.settings_task_notices_label),
            subtitulo = stringResource(R.string.settings_task_notices_hint),
            activo = settings.taskNoticesEnabled,
            alCambiar = settingsViewModel::setTaskNotices,
            modifier = Modifier.testTag("task-notices-row"),
        )
        Subtitulo(stringResource(R.string.ajustes_sesion_foco))
        FilaValor(
            etiqueta = stringResource(R.string.ajustes_pomodoro),
            subtitulo = stringResource(R.string.ajustes_pomodoro_sub),
            valor = stringResource(R.string.ajustes_pomodoro_valor, settings.pomoFocus, settings.pomoShort),
            alPulsar = { hoja = HojaAvisos.POMODORO },
        )
        Subtitulo(stringResource(R.string.settings_breathing_section))
        FilaInterruptor(
            etiqueta = stringResource(R.string.settings_breathing_reminder),
            subtitulo = stringResource(R.string.settings_breathing_reminder_hint),
            activo = settings.breathingReminderEnabled,
            alCambiar = settingsViewModel::setBreathingReminder,
            modifier = Modifier.testTag("breathing-reminder"),
        )
        if (settings.breathingReminderEnabled) {
            FilaValor(
                etiqueta = stringResource(R.string.settings_breathing_time),
                valor = horaCorta(settings.breathingReminderTimeMinutes),
                alPulsar = { hoja = HojaAvisos.RESPIRACION },
                modifier = Modifier.testTag("breathing-time-row"),
            )
            if (avisosBloqueados) {
                Fila(
                    etiqueta = stringResource(R.string.notif_permission_hint),
                    alPulsar = { abrirAjustesDeAvisos(context) },
                ) {
                    Text(stringResource(R.string.ajustes_permitir), style = BitoTema.tipos.t16.fuerte())
                }
            }
        }
    }

    HojaRecordatorios(
        abierta = hoja == HojaAvisos.RECORDATORIOS,
        horas = settings.globalReminderMinutes,
        alAnadir = { minutos ->
            settingsViewModel.addReminder(minutos)
            // Se pide porque FALTA el permiso, no porque sea la primera hora: las horas se siembran al instalar.
            if (Build.VERSION.SDK_INT >= 33 && !avisosActivados(context)) {
                lanzadorAvisos.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        alCambiar = { antes, despues ->
            settingsViewModel.removeReminder(antes)
            settingsViewModel.addReminder(despues)
        },
        alQuitar = settingsViewModel::removeReminder,
        alCerrar = { hoja = null },
    )
    HojaAvisosHabi(
        abierta = hoja == HojaAvisos.HABI,
        manana = settings.habiMorningMinutes,
        noche = settings.habiNightMinutes,
        alGuardarManana = settingsViewModel::setHabiMorning,
        alGuardarNoche = settingsViewModel::setHabiNight,
        alCerrar = { hoja = null },
    )
    HojaNoMolestar(
        abierta = hoja == HojaAvisos.NO_MOLESTAR,
        activo = settings.dndEnabled,
        desde = settings.dndFromMinutes,
        hasta = settings.dndToMinutes,
        permisoDenegado = avisosBloqueados,
        alCambiarActivo = settingsViewModel::setDndEnabled,
        alCambiarDesde = settingsViewModel::setDndFrom,
        alCambiarHasta = settingsViewModel::setDndTo,
        alPedirPermiso = { abrirAjustesDeAvisos(context) },
        alCerrar = { hoja = null },
    )
    HojaPomodoro(
        abierta = hoja == HojaAvisos.POMODORO,
        valores = Pomodoro(settings.pomoFocus, settings.pomoShort, settings.pomoLong, settings.pomoRounds, settings.pomoAutoBreak),
        alCambiar = { settingsViewModel.setPomodoro(it.foco, it.corto, it.largo, it.rondas, it.descansoSolo) },
        alCerrar = { hoja = null },
    )
    HojaHoraRespiracion(
        abierta = hoja == HojaAvisos.RESPIRACION,
        minutos = settings.breathingReminderTimeMinutes,
        alGuardar = settingsViewModel::setBreathingReminderTime,
        alCerrar = { hoja = null },
    )
}

@Composable
internal fun Subtitulo(texto: String) {
    Text(
        texto,
        style = BitoTema.tipos.t14.fuerte(),
        color = LocalEstrato.current?.tinta2 ?: BitoTema.colores.tintaSuave,
        modifier = Modifier.fillMaxWidth().padding(top = Medidas.e16, bottom = Medidas.e4),
    )
}

/** La hora del recordatorio de respirar: reloj de cielo a paso de 5 minutos y «Guardar». */
@Composable
private fun HojaHoraRespiracion(
    abierta: Boolean,
    minutos: Int,
    alGuardar: (Int) -> Unit,
    alCerrar: () -> Unit,
) {
    var elegido by remember(abierta, minutos) { mutableIntStateOf(minutos) }
    val descripcion = stringResource(R.string.ajustes_hora_respiracion)
    Hoja(
        abierta = abierta,
        alCerrar = alCerrar,
        seccion = BitoTema.colores.estratos.avisos,
        etiqueta = stringResource(R.string.ajustes_estrato_avisos),
        titulo = stringResource(R.string.settings_breathing_time),
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = Medidas.e12), contentAlignment = Alignment.Center) {
            RelojCielo(elegido, { elegido = it }, Modifier.size(260.dp), paso = 5, descripcion = descripcion)
        }
        BotonesHoja(
            principal = stringResource(R.string.sistema_guardar),
            alPrincipal = {
                alGuardar(elegido)
                cerrar()
            },
        )
    }
}
