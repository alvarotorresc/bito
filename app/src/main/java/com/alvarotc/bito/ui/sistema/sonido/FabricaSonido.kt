package com.alvarotc.bito.ui.sistema.sonido

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** La única forma de crear el motor (la llama `AppContainer`). */
fun crearMotorSonido(
    context: Context,
    settings: SettingsRepository,
): MotorSonido {
    val pool =
        SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()
    val listos = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    pool.setOnLoadCompleteListener { _, id, estado -> if (estado == 0) listos += id }
    val reproductor =
        object : Reproductor {
            override fun cargar(recurso: Int) = pool.load(context, recurso, 1)

            override fun listo(id: Int) = id in listos

            override fun play(
                id: Int,
                volumen: Float,
                bucle: Boolean,
            ) = pool.play(id, volumen, volumen, 1, if (bucle) -1 else 0, 1f)

            override fun volumen(
                stream: Int,
                volumen: Float,
            ) = pool.setVolume(stream, volumen, volumen)

            override fun parar(stream: Int) = pool.stop(stream)
        }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val actuales = AtomicReference(AjustesSonido.de(Settings()))
    scope.launch { settings.settings.collect { actuales.set(AjustesSonido.de(it)) } }
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val recursos = Snd.entries.associateWith(::recursoRaw)
    return MotorSonidoAndroid(
        reproductor,
        recursoDe = { recursos.getValue(it) },
        ajustes = { actuales.get() },
        timbreNormal = { audio.ringerMode == AudioManager.RINGER_MODE_NORMAL },
        scope = scope,
        precargaEn = Dispatchers.IO,
    )
}

/** `when` exhaustivo, sin `else`: si falta un OGG no compila, y R8 ve cada recurso usado. */
internal fun recursoRaw(snd: Snd): Int =
    when (snd) {
        Snd.GORGOTEO -> R.raw.snd_gorgoteo
        Snd.LLEGADA -> R.raw.snd_llegada
        Snd.VACIAR -> R.raw.snd_vaciar
        Snd.BROTE -> R.raw.snd_brote
        Snd.HUNDIR -> R.raw.snd_hundir
        Snd.ASENTAR -> R.raw.snd_asentar
        Snd.POSAR -> R.raw.snd_posar
        Snd.CAMPANILLA -> R.raw.snd_campanilla
        Snd.GASTAR -> R.raw.snd_gastar
        Snd.HABI -> R.raw.snd_habi
        Snd.PLANTA -> R.raw.snd_planta
        Snd.INTERRUPTOR_ON -> R.raw.snd_interruptor_on
        Snd.INTERRUPTOR_OFF -> R.raw.snd_interruptor_off
        Snd.ANOCHECER -> R.raw.snd_anochecer
        Snd.DESCENSO -> R.raw.snd_descenso
        Snd.ASCENSO -> R.raw.snd_ascenso
        Snd.ZOOM -> R.raw.snd_zoom
        Snd.ALEJAR -> R.raw.snd_alejar
        Snd.ACERCAR -> R.raw.snd_acercar
        Snd.ABRIR -> R.raw.snd_abrir
        Snd.CERRAR -> R.raw.snd_cerrar
        Snd.TIC -> R.raw.snd_tic
        Snd.PASO_MES_ATRAS -> R.raw.snd_pasomes_atras
        Snd.PASO_MES_DELANTE -> R.raw.snd_pasomes_delante
        Snd.LADO_IZQ -> R.raw.snd_lado_izq
        Snd.LADO_DER -> R.raw.snd_lado_der
        Snd.AMBIENTE -> R.raw.snd_ambiente
        Snd.AVISO -> R.raw.snd_aviso
        Snd.MONEDA -> R.raw.snd_moneda
        Snd.DIA_COMPLETO -> R.raw.snd_diacompleto
        Snd.RACHA -> R.raw.snd_racha
        Snd.LOGRO -> R.raw.snd_logro
        Snd.FONDO_LLUVIA -> R.raw.snd_fondo_lluvia
        Snd.FONDO_MARRON -> R.raw.snd_fondo_marron
        Snd.FONDO_CAFE -> R.raw.snd_fondo_cafe
    }
