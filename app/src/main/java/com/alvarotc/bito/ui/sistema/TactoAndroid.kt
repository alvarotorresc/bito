package com.alvarotc.bito.ui.sistema

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View

/**
 * El tacto sobre el vibrador de verdad. [ajustes] devuelve (vibración encendida, fuerza 0–10) con
 * los valores ya guardados; [respuestaSistema] dice si la respuesta táctil del sistema está activa
 * (de ella depende `performHapticFeedback`).
 */
class TactoAndroid(
    private val vibrador: Vibrator,
    private val ajustes: () -> Pair<Boolean, Int>,
    private val sdk: Int = Build.VERSION.SDK_INT,
    private val vista: View? = null,
    private val respuestaSistema: () -> Boolean,
) : Tacto {
    override fun haptica(h: Haptica) {
        val (vibracion, fuerza) = ajustes()
        ejecutar(h, fuerza, vibracion)
    }

    override fun probar(nivel: Int) = ejecutar(Haptica.TICK, nivel, true)

    override fun conVista(vista: View): Tacto = TactoAndroid(vibrador, ajustes, sdk, vista, respuestaSistema)

    // `PlanHaptico` solo devuelve Predefinido (API 29+) y Primitivas (API 30+) con el `sdk` que lo permite; lint no ve ese `sdk` inyectado.
    @SuppressLint("NewApi")
    private fun ejecutar(
        h: Haptica,
        fuerza: Int,
        vibracion: Boolean,
    ) {
        val plan =
            PlanHaptico.de(
                h = h,
                sdk = sdk,
                primitivaOk = { primitivaSoportada(it) },
                fuerza = fuerza,
                vibracion = vibracion,
                vistaUsable = vista != null && respuestaSistema(),
            )
        when (plan) {
            PlanTacto.Nada -> Unit
            is PlanTacto.Constante -> vista?.performHapticFeedback(plan.constante)
            is PlanTacto.Predefinido -> vibrador.vibrate(VibrationEffect.createPredefined(plan.efecto))
            is PlanTacto.Pulso -> {
                val amplitud = if (vibrador.hasAmplitudeControl()) plan.amplitud.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
                vibrador.vibrate(VibrationEffect.createOneShot(plan.ms, amplitud))
            }
            is PlanTacto.Primitivas -> {
                val composicion = VibrationEffect.startComposition()
                plan.pasos.forEach { composicion.addPrimitive(it.primitiva, it.escala, it.retrasoMs) }
                vibrador.vibrate(composicion.compose())
            }
        }
    }

    @SuppressLint("NewApi")
    private fun primitivaSoportada(primitiva: Int): Boolean = sdk >= 30 && vibrador.areAllPrimitivesSupported(primitiva)
}
