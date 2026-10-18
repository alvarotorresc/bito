package com.alvarotc.bito.testutil

import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin

/**
 * Repara, al empezar cada test de Robolectric, el aplicador global de snapshots de Compose.
 *
 * `GlobalSnapshotManager` y `AndroidUiDispatcher.Main` son estáticos por sandbox. Si un test acaba
 * con una escritura de estado global cuyo `post` del dispatcher sigue en la cola del looper
 * principal, Robolectric vacía esa cola al reiniciar el entorno y el mensaje se pierde. Las
 * banderas `scheduledTrampolineDispatch` / `scheduledFrameDispatch` se quedan en true para
 * siempre, el dispatcher no vuelve a postear y ninguna escritura global se aplica sola: cada test
 * posterior del sandbox que escribe estado desde el hilo principal bombea el reloj hasta
 * AppNotIdleException (ver EnvenenaSnapshotTest).
 *
 * Al empezar un test no puede quedar ningún callback legítimo pendiente del test anterior, así que
 * una bandera en true significa un callback perdido (o a punto de perderse): se ejecuta en el acto
 * el mismo `dispatchCallback` que Robolectric descarta.
 *
 * Se registra en META-INF/services, así que corre en todos los tests sin tocar cada clase. Usa
 * reflexión sobre campos privados de compose-ui 1.7.x: si desaparecen tras subir de versión,
 * falla en voz alta en lugar de dejar de reparar en silencio.
 */
class ReparaAplicadorSnapshots : TestEnvironmentLifecyclePlugin {
    override fun onSetupApplicationState() {
        val loader = Thread.currentThread().contextClassLoader ?: return
        val clase =
            try {
                Class.forName(DISPATCHER, false, loader)
            } catch (_: ClassNotFoundException) {
                return
            }
        val perezoso = campo(clase, "Main\$delegate").get(null) as Lazy<*>
        if (!perezoso.isInitialized()) return
        val contexto = perezoso.value as kotlin.coroutines.CoroutineContext
        val dispatcher = contexto[kotlin.coroutines.ContinuationInterceptor] ?: return
        check(dispatcher.javaClass == clase) { "AndroidUiDispatcher.Main ya no es un AndroidUiDispatcher" }

        val trampolin = campo(clase, "scheduledTrampolineDispatch").getBoolean(dispatcher)
        val fotograma = campo(clase, "scheduledFrameDispatch").getBoolean(dispatcher)
        if (!trampolin && !fotograma) return
        // doFrame hace lo mismo que el post y el fotograma perdidos juntos: vacía la cola trampolín
        // (el consumidor de GlobalSnapshotManager aplica y baja `sent`) y despacha el fotograma,
        // dejando las dos banderas en false. Se corre aquí mismo, en el hilo principal del sandbox,
        // porque un post se lo volvería a llevar el reinicio de la cola que viene después.
        val callback = campo(clase, "dispatchCallback").get(dispatcher) as android.view.Choreographer.FrameCallback
        callback.doFrame(System.nanoTime())
        check(!campo(clase, "scheduledTrampolineDispatch").getBoolean(dispatcher)) { "el aplicador sigue atascado" }
    }

    private fun campo(
        clase: Class<*>,
        nombre: String,
    ) = clase.getDeclaredField(nombre).apply { isAccessible = true }

    private companion object {
        const val DISPATCHER = "androidx.compose.ui.platform.AndroidUiDispatcher"
    }
}
