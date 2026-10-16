package com.alvarotc.bito.ui.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alvarotc.bito.MainActivity
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.Accion
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.habi.renderHabiBitmap
import com.alvarotc.bito.ui.inicio.resolverTextoHabi
import com.alvarotc.bito.ui.sistema.BitoClaro
import java.util.concurrent.ConcurrentHashMap

/**
 * Posts or cancels Bito's local notifications from already-decided content — [ReminderUseCase],
 * [TrayRefresher], and [PerfectDayNotifier] are the callers that decide *whether* to notify;
 * this object renders what it's handed and manages stale notifications. Silently skips posting
 * when the user has disabled notifications (API 33+ reality): scheduling the next alarm never
 * depends on whether this actually reached the tray.
 */
object Notifier {
    const val REMINDER_ID = 1
    const val CELEBRATION_ID = 3
    const val TASKS_ID = 4
    const val FOCUS_ID = 5
    const val BREATHING_ID = 6

    /** El aviso de la noche ocupa el id que tenía el del repaso. */
    const val NOCHE_ID = 2

    /** El aviso de la mañana; el del aceptado lo sustituye con el mismo id. */
    const val MANANA_ID = 7

    /** El primer recordatorio aceptado del día; el segundo es el siguiente. */
    const val ACEPTADO_ID = 8

    /** Extra keys carried by [quickActionIntent] and read back in [QuickActionReceiver]. */
    const val EXTRA_HABIT_ID = "habitId"
    const val EXTRA_AMOUNT = "amount"
    const val EXTRA_NOTIFICATION_ID = "notificationId"

    /** Extra key carried by [contentIntent] and read back in `MainActivity.handleIntent` (T11). */
    const val EXTRA_OPEN_ROUTE = "openRoute"

    /**
     * The GLOBAL reminder: one or more habits still open, up to three quick-log actions. Speaks
     * in [personality]'s voice with the flavor [minutesOfDay] resolves to ([ReminderVoice]) —
     * the scheduled hour for a fired alarm, "now" for a tray refresh. The body carries the
     * useful part (pending count, up to three names, progress so far) under a BigTextStyle so
     * it can breathe when expanded.
     */
    fun showReminder(
        context: Context,
        payload: ReminderPayload,
        personality: Personality,
        userName: String,
        minutesOfDay: Int,
    ) {
        val flavor = ReminderVoice.flavorOf(minutesOfDay)
        val title =
            if (userName.isBlank()) {
                context.getString(ReminderVoice.titleSinNombreRes(personality, flavor))
            } else {
                context.getString(ReminderVoice.titleRes(personality, flavor), userName)
            }
        val pendingCount = payload.pendingNames.size
        val names = payload.pendingNames.take(ReminderVoice.MAX_NAMED_PENDING).joinToString(", ")
        val body =
            context.resources.getQuantityString(
                ReminderVoice.bodyRes(personality, flavor),
                pendingCount,
                pendingCount,
                names,
                payload.doneCount,
                payload.totalCount,
            )
        val builder =
            baseBuilder(context, NotificationChannels.REMINDERS)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setLargeIcon(iconoHabi(context, personality))
        payload.targets.take(MAX_ACTIONS).forEach { target ->
            builder.addAction(0, etiquetaAccion(context, target), quickActionIntent(context, target.habitId, target.amount, REMINDER_ID))
        }
        notify(context, REMINDER_ID, builder)
    }

    /**
     * The HABIT reminder: a single habit's own slot, still open and unfailed. Posted under its
     * own [habitId]-derived id so it can stack alongside other per-habit reminders and the
     * GLOBAL one instead of clobbering them. [target] is `null` for habit kinds with no honest
     * quick action (DURATION, ABSTINENCE) — the notification still fires, just without a
     * button; tapping it only opens the app.
     */
    fun showSingleHabit(
        context: Context,
        habitId: String,
        name: String,
        target: QuickTarget?,
    ) {
        val id = habitId.hashCode()
        val builder =
            baseBuilder(context, NotificationChannels.REMINDERS)
                .setContentTitle(context.getString(R.string.notif_reminder_single_title, name))
                .setContentText(context.getString(R.string.notif_reminder_single_body))
        if (target != null) {
            val label =
                if (target.isCheck) {
                    context.getString(R.string.notif_action_done)
                } else {
                    context.getString(R.string.notif_action_add, target.amount)
                }
            builder.addAction(0, label, quickActionIntent(context, target.habitId, target.amount, id))
        }
        notify(context, id, builder)
    }

    /**
     * The CELEBRATIONS nudge: [PerfectDayNotifier] already decided this write is worth
     * announcing outside the app — this just renders [body] under its own channel and id.
     * Tapping it opens Today bare, the same default [contentIntent] every other route falls
     * back to, where the in-app perfect-day sheet takes over from there.
     */
    fun showPerfectDay(
        context: Context,
        body: String,
        personality: Personality = Personality.NEUTRA,
    ) {
        val builder =
            baseBuilder(context, NotificationChannels.CELEBRATIONS)
                .setLargeIcon(iconoHabi(context, personality, mood = Mood.RADIANT))
                .setContentTitle(context.getString(R.string.notif_perfect_day_title))
                .setContentText(body)
        notify(context, CELEBRATION_ID, builder)
    }

    /** Clears a stale reminder notification (e.g. its habit got logged some other way). */
    fun cancelReminder(context: Context) = NotificationManagerCompat.from(context).cancel(REMINDER_ID)

    /** Quita el aviso de la noche (el día se acaba de sellar, o «Mañana» desde el propio aviso). */
    fun cancelNoche(context: Context) = NotificationManagerCompat.from(context).cancel(NOCHE_ID)

    /** Quita el aviso de la mañana (respondido desde la bandeja, o una acción de otro día). */
    fun cancelManana(context: Context) = NotificationManagerCompat.from(context).cancel(MANANA_ID)

    /** Quita el recordatorio aceptado número [indice] del día. */
    fun cancelAceptado(
        context: Context,
        indice: Int,
    ) = NotificationManagerCompat.from(context).cancel(aceptadoId(indice))

    /** El id del recordatorio del aviso aceptado número [indice] del día. */
    fun aceptadoId(indice: Int): Int = ACEPTADO_ID + indice

    /**
     * El aviso de la mañana (spec §6.1): [titulo] ya elegido, el cuerpo es el texto de [propuesta], la vigente de Hoy, y
     * sus acciones (como mucho [MAX_ACTIONS]). «Vale», «Hoy no» y «Luego» van a [AvisoHabiReceiver]; el resto abre la app.
     * Tocarlo abre Hoy, con la hoja de lo gordo si la vigente es elegirlo.
     */
    fun showManana(
        context: Context,
        propuesta: Propuesta,
        titulo: TextoHabi,
        personality: Personality,
        mood: Mood,
    ) {
        val cuerpo = resolverTextoHabi(propuesta.texto, context.resources)
        val ruta = if (propuesta.id.startsWith("${Situacion.ELEGIR_GORDO.name}:")) "gordo" else "today"
        val builder =
            baseBuilder(context, NotificationChannels.HABI)
                .setContentTitle(resolverTextoHabi(titulo, context.resources))
                .setContentText(cuerpo)
                .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
                .setLargeIcon(iconoHabi(context, personality, mood))
                .setContentIntent(contentIntent(context, ruta, PETICION_MANANA))
        propuesta.acciones.take(MAX_ACTIONS).forEachIndexed { i, accion ->
            builder.addAction(0, resolverTextoHabi(accion.etiqueta, context.resources), accionManana(context, propuesta, accion, i))
        }
        notify(context, MANANA_ID, builder)
    }

    /** El aviso aceptado: sustituye al de la mañana con su mismo id, sin volver a sonar (spec §6.1). */
    fun showAceptado(
        context: Context,
        titulo: TextoHabi,
        cuerpo: TextoHabi,
        personality: Personality,
    ) {
        val texto = resolverTextoHabi(cuerpo, context.resources)
        val builder =
            baseBuilder(context, NotificationChannels.HABI)
                .setContentTitle(resolverTextoHabi(titulo, context.resources))
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setLargeIcon(iconoHabi(context, personality, Mood.RADIANT))
                .setContentIntent(contentIntent(context, "today", PETICION_ACEPTADO))
        notify(context, MANANA_ID, builder)
    }

    /**
     * El recordatorio de un aviso aceptado, a su hora: el nombre del hábito, «Hecho» o «+1» por [QuickActionReceiver]
     * (como los recordatorios de siempre) y «Más tarde» mientras [masTarde] (spec §6.2, decisión 1 de §15).
     */
    fun showRecordatorioAceptado(
        context: Context,
        avisoId: String,
        indice: Int,
        nombre: String,
        cuerpo: TextoHabi,
        target: QuickTarget?,
        masTarde: Boolean,
        personality: Personality,
    ) {
        val id = aceptadoId(indice)
        val texto = resolverTextoHabi(cuerpo, context.resources)
        val builder =
            baseBuilder(context, NotificationChannels.HABI)
                .setContentTitle(nombre)
                .setContentText(texto)
                .setLargeIcon(iconoHabi(context, personality))
                .setContentIntent(contentIntent(context, "today", PETICION_RECORDATORIO + indice))
        if (target != null) {
            builder.addAction(0, etiquetaAccion(context, target), quickActionIntent(context, target.habitId, target.amount, id))
        }
        if (masTarde) {
            val intent =
                Intent(context, AvisoHabiReceiver::class.java)
                    .setAction(AvisoHabiReceiver.ACTION_MAS_TARDE)
                    .putExtra(AvisoHabiReceiver.EXTRA_AVISO_ID, avisoId)
            builder.addAction(0, context.getString(R.string.habla_mas_tarde), difusion(context, intent, id))
        }
        notify(context, id, builder)
    }

    /** El aviso de la noche: «Cerrar el día» abre el cierre y «Mañana» lo quita. Habi duerme con gorro (D9). */
    fun showNoche(
        context: Context,
        titulo: TextoHabi,
        cuerpo: TextoHabi,
        personality: Personality,
    ) {
        val texto = resolverTextoHabi(cuerpo, context.resources)
        val cerrar = contentIntent(context, "review", PETICION_CIERRE)
        val manana = Intent(context, AvisoHabiReceiver::class.java).setAction(AvisoHabiReceiver.ACTION_MANANA)
        val builder =
            baseBuilder(context, NotificationChannels.HABI)
                .setContentTitle(resolverTextoHabi(titulo, context.resources))
                .setContentText(texto)
                .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
                .setLargeIcon(iconoHabi(context, personality, dormido = true))
                .setContentIntent(cerrar)
                .addAction(0, context.getString(R.string.habla_cerrar_dia), cerrar)
                .addAction(0, context.getString(R.string.habla_manana_quitar), difusion(context, manana, NOCHE_ID))
        notify(context, NOCHE_ID, builder)
    }

    /**
     * El aviso de tareas de mediodia: una sola notificacion agrupada, con hasta tres titulos y el
     * recuento. SIN botones de accion — no hay accion honesta de un toque: marcar hecha desde la
     * bandeja una tarea que no has abierto es lo contrario de «empezar». Al tocarla abre la lista.
     *
     * El cuerpo son los titulos nombrados unidos por « · »; la coletilla «y N mas»
     * (`notif_tasks_more`) solo se añade cuando queda algun aviso sin nombrar — con [titles]
     * cubriendo todo [pendingCount], «A · B» ya lo dice todo y no hace falta un «y 0 mas».
     */
    fun showTasks(
        context: Context,
        titles: List<String>,
        pendingCount: Int,
        personality: Personality,
        userName: String,
    ) {
        val title =
            if (userName.isBlank()) {
                context.resources.getQuantityString(ReminderVoice.tasksTitleSinNombreRes(personality), pendingCount, "", pendingCount)
            } else {
                context.resources.getQuantityString(ReminderVoice.tasksTitleRes(personality), pendingCount, userName, pendingCount)
            }
        val named = titles.take(ReminderVoice.MAX_NAMED_PENDING)
        val remaining = pendingCount - named.size
        val body =
            if (remaining > 0) {
                val more = context.resources.getQuantityString(R.plurals.notif_tasks_more, remaining, remaining)
                "${named.joinToString(" · ")} $more"
            } else {
                named.joinToString(" · ")
            }
        val builder =
            baseBuilder(context, NotificationChannels.REMINDERS)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setLargeIcon(iconoHabi(context, personality))
                .setContentIntent(contentIntent(context, "tasks", 3))
        notify(context, TASKS_ID, builder)
    }

    /**
     * M11: el recordatorio diario de respirar, en la voz de [personality]. Canal REMINDERS, sin
     * canal propio: el control es el interruptor de Ajustes. Sin botones de accion. Al tocarla abre
     * el ejercicio EN REPOSO con el ultimo modo: el sonido y la vibracion no arrancan sin que el
     * usuario lo pida. requestCode 5, distinto de review (2), tasks (3) y focus (4): filterEquals
     * ignora los extras.
     */
    fun showBreathing(
        context: Context,
        personality: Personality,
        userName: String,
    ) {
        val title =
            if (userName.isBlank()) {
                context.getString(ReminderVoice.breathingTitleSinNombreRes(personality))
            } else {
                context.getString(ReminderVoice.breathingTitleRes(personality), userName)
            }
        val builder =
            baseBuilder(context, NotificationChannels.REMINDERS)
                .setContentTitle(title)
                .setContentText(context.getString(ReminderVoice.breathingBodyRes(personality)))
                .setLargeIcon(iconoHabi(context, personality))
                .setContentIntent(contentIntent(context, "breathing", 5))
        notify(context, BREATHING_ID, builder)
    }

    /**
     * El temporizador de foco, mientras dura. La cuenta atras la pinta SystemUI, no Bito: el
     * proceso puede morir y el reloj sigue bajando. setAutoCancel(false) es obligatorio — lo
     * hereda en true de baseBuilder, y una permanente que se borra al tocarla no es permanente.
     */
    fun showFocus(
        context: Context,
        taskTitle: String,
        endsAtMillis: Long,
    ) {
        val builder =
            baseBuilder(context, NotificationChannels.REMINDERS)
                .setContentTitle(taskTitle)
                .setContentText(context.getString(R.string.notif_focus_running))
                .setContentIntent(contentIntent(context, "focus", 4))
                .setAutoCancel(false)
                .setOngoing(true)
                .setSilent(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(endsAtMillis)
        notify(context, FOCUS_ID, builder)
    }

    /** «Se acabo el tiempo»: ya no es permanente y se va al tocarla. */
    fun showFocusOver(
        context: Context,
        taskTitle: String,
    ) {
        val builder =
            baseBuilder(context, NotificationChannels.REMINDERS)
                .setContentTitle(taskTitle)
                .setContentText(context.getString(R.string.notif_focus_over))
                .setContentIntent(contentIntent(context, "focus", 4))
                // setOnlyAlertOnce(false) es obligatorio — lo hereda en true de baseBuilder, y esta
                // notificacion postea bajo el mismo FOCUS_ID que ya ocupa la permanente; sin esto,
                // sustituye a showFocus en silencio (sin sonido, sin vibracion, sin heads-up), y un
                // aviso de fin que no avisa no es un aviso de fin.
                .setOnlyAlertOnce(false)
        notify(context, FOCUS_ID, builder)
    }

    /** Clears the focus tray. */
    fun cancelFocus(context: Context) = NotificationManagerCompat.from(context).cancel(FOCUS_ID)

    /**
     * El aviso fijo de la sesión (pieza 5): canal `sesion`, fijo, público, sin sonido ni botones. Con
     * [finParedMillis] la cuenta atrás la pinta Android; sin él (en pausa) la cifra queda quieta en [cuerpo].
     * Tocarla abre Hoy con la petición `focus`. La misma notificación es la del servicio de la sesión.
     */
    fun notificacionSesion(
        context: Context,
        titulo: String,
        cuerpo: String,
        finParedMillis: Long?,
        personality: Personality,
    ): Notification {
        val builder =
            baseBuilder(context, NotificationChannels.SESION)
                .setContentTitle(titulo)
                .setContentText(cuerpo)
                .setLargeIcon(iconoHabi(context, personality))
                .setContentIntent(contentIntent(context, "focus", 4))
                .setAutoCancel(false)
                .setOngoing(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (finParedMillis != null) {
            builder
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(finParedMillis)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    fun mostrarSesion(
        context: Context,
        titulo: String,
        cuerpo: String,
        finParedMillis: Long?,
        personality: Personality,
    ) = publicar(context, FOCUS_ID, notificacionSesion(context, titulo, cuerpo, finParedMillis, personality))

    /** «Se acabó el tiempo»: Habi radiante, canal `habi` (suena), en el mismo id; tocarla abre la sesión terminada. */
    fun mostrarSesionFin(
        context: Context,
        titulo: String,
        personality: Personality,
    ) {
        val builder =
            baseBuilder(context, NotificationChannels.HABI)
                .setContentTitle(titulo)
                .setContentText(context.getString(R.string.ses_notif_fin))
                .setLargeIcon(iconoHabi(context, personality, mood = Mood.RADIANT))
                .setContentIntent(contentIntent(context, "focus", 4))
                // Comparte id con el aviso fijo: sin esto sustituiría al fijo sin avisar.
                .setOnlyAlertOnce(false)
        notify(context, FOCUS_ID, builder)
    }

    fun cancelarSesion(context: Context) = NotificationManagerCompat.from(context).cancel(FOCUS_ID)

    @SuppressLint("MissingPermission")
    private fun publicar(
        context: Context,
        id: Int,
        notification: Notification,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.notify(id, notification)
    }

    /** Lado en píxeles del icono grande: Habi sin accesorios (spec pieza 2 §5.6). */
    private const val ICONO_PX = 192
    private const val MAX_ACTIONS = 3

    /**
     * Códigos de petición de las aperturas de la app desde los avisos de Habi, distintos de los de las rutas de
     * siempre (0, 2, 3, 4 y 5): [Intent.filterEquals] no mira los extras y `FLAG_UPDATE_CURRENT` cambiaría la ruta de
     * otra notificación viva. Uno por apertura: el toque de la mañana, el del aceptado, cada acción de la mañana que
     * abre la app (base más su índice) y el toque de cada recordatorio aceptado (base más su índice del día). El
     * cierre comparte el 2 porque su ruta es siempre la misma.
     */
    private const val PETICION_MANANA = 70
    private const val PETICION_ACEPTADO = 73
    private const val PETICION_AHORA = 90
    private const val PETICION_RECORDATORIO = 100
    private const val PETICION_CIERRE = 2

    /** Una acción de la vigente en el aviso de la mañana: al receptor si se responde ahí, a la app si hay que ir. */
    private fun accionManana(
        context: Context,
        propuesta: Propuesta,
        accion: Accion,
        indice: Int,
    ): PendingIntent =
        when (accion.respuesta) {
            Respuesta.VALE -> {
                val intent =
                    Intent(context, AvisoHabiReceiver::class.java)
                        .setAction(AvisoHabiReceiver.ACTION_VALE)
                        .putExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID, propuesta.id)
                        .putExtra(AvisoHabiReceiver.EXTRA_HORA, propuesta.hora ?: SIN_HORA)
                difusion(context, intent, MANANA_ID)
            }
            Respuesta.HOY_NO, Respuesta.LUEGO -> {
                // Comparten acción del receptor: el código de petición lleva el índice para no pisarse.
                val intent =
                    Intent(context, AvisoHabiReceiver::class.java)
                        .setAction(AvisoHabiReceiver.ACTION_HOY_NO)
                        .putExtra(AvisoHabiReceiver.EXTRA_PROPUESTA_ID, propuesta.id)
                        .putExtra(AvisoHabiReceiver.EXTRA_RESPUESTA, accion.respuesta.name)
                difusion(context, intent, MANANA_ID * 10 + indice)
            }
            Respuesta.CERRAR_DIA -> contentIntent(context, "review", PETICION_CIERRE)
            Respuesta.ELEGIR_GORDO -> contentIntent(context, "gordo", PETICION_AHORA + indice)
            else -> contentIntent(context, rutaDe(propuesta.destino), PETICION_AHORA + indice)
        }

    /** Adónde lleva «Ahora» desde la bandeja: solo rutas de la lista blanca de [com.alvarotc.bito.ui.NavRequests]. */
    private fun rutaDe(destino: Destino?): String =
        when (destino) {
            is Destino.Tarea -> "tasks"
            Destino.Respirar -> "breathing"
            Destino.Cierre -> "review"
            Destino.LoGordo -> "gordo"
            is Destino.Habito, null -> "today"
        }

    /** Sin hora en el «Vale» (día duro): el receptor lo lee como null. */
    const val SIN_HORA = -1

    private fun difusion(
        context: Context,
        intent: Intent,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Habi pintada una vez por voz y expresión; los receptores ya trabajan fuera del hilo principal. */
    private val iconos = ConcurrentHashMap<Triple<Personality, Mood, Boolean>, Bitmap>()

    private fun iconoHabi(
        context: Context,
        personality: Personality,
        mood: Mood = Mood.NORMAL,
        dormido: Boolean = false,
    ): Bitmap? {
        val clave = Triple(personality, if (dormido) Mood.NORMAL else mood, dormido)
        iconos[clave]?.let { return it }
        // Un icono que no se pinta nunca debe costar el aviso: sale sin icono grande.
        return runCatching {
            renderHabiBitmap(
                context.applicationContext,
                HabiSpec(
                    mood = if (dormido) Mood.NORMAL else mood,
                    personality = personality,
                    equipped = EquippedSet(),
                    closedEyes = dormido,
                    sleepCap = dormido,
                ),
                ICONO_PX,
            )
        }.getOrNull()?.also { iconos[clave] = it }
    }

    private fun baseBuilder(
        context: Context,
        channelId: String,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_habi)
            .setColor(BitoClaro.aguaHonda.toArgb())
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            // TrayRefresher re-posts the GLOBAL reminder on every in-app write while it's showing
            // (recomputed content, same id) — without this, each of those updates would re-alert
            // (sound/vibration) exactly like a brand-new notification. Only the very first post
            // should alert; every refresh after that is silent.
            .setOnlyAlertOnce(true)

    /**
     * [route], when given, is carried as [EXTRA_OPEN_ROUTE] for `MainActivity.handleIntent` to
     * hand to [com.alvarotc.bito.ui.NavRequests]. [requestCode] must differ per route — not just
     * be a constant — because [Intent.filterEquals] ignores extras: every route would otherwise
     * collide on the same [PendingIntent] and `FLAG_UPDATE_CURRENT` would silently overwrite the
     * other notification's extra with whichever posted last.
     */
    private fun contentIntent(
        context: Context,
        route: String? = null,
        requestCode: Int = 0,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                route?.let { putExtra(EXTRA_OPEN_ROUTE, it) }
            }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * [notificationId] is the tray id this action's notification was posted under, so the
     * receiver can clear it. The request code must be unique per (habit, notification) — not
     * just per habit — because [Intent.filterEquals] ignores extras: the same habit's GLOBAL
     * and HABIT actions would otherwise collide on the same [PendingIntent] and
     * `FLAG_UPDATE_CURRENT` would silently rewrite [notificationId] to whichever posted last.
     */
    private fun quickActionIntent(
        context: Context,
        habitId: String,
        amount: Int,
        notificationId: Int,
    ): PendingIntent {
        val intent =
            Intent(context, QuickActionReceiver::class.java)
                .putExtra(EXTRA_HABIT_ID, habitId)
                .putExtra(EXTRA_AMOUNT, amount)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        val requestCode = 31 * habitId.hashCode() + notificationId
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Posts [builder] under [id], skipping silently when notifications are disabled — including
     * on API 33+ where [NotificationManagerCompat.areNotificationsEnabled] already reflects a
     * missing POST_NOTIFICATIONS grant, so this never reaches the OS without it.
     */
    @SuppressLint("MissingPermission")
    private fun notify(
        context: Context,
        id: Int,
        builder: NotificationCompat.Builder,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.notify(id, builder.build())
    }
}

/** Texto del botón de una acción rápida: «Hecho: Leer» o «+1 vaso · 7/8» (sin progreso si no hay objetivo). */
internal fun etiquetaAccion(
    context: Context,
    target: QuickTarget,
): String =
    when {
        target.isCheck -> context.getString(R.string.notif_target_done, target.name)
        target.objetivo > 0 ->
            context.getString(R.string.notif_target_add, target.amount, target.name, target.hecho, target.objetivo)
        else -> context.getString(R.string.notif_action_add, target.amount) + " " + target.name
    }
