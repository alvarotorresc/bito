package com.alvarotc.bito.ui.sesion

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.sesion.FondoGrabado
import com.alvarotc.bito.sesion.NoMolestar
import com.alvarotc.bito.ui.habi.HabiSpec
import org.robolectric.Shadows

/** Lo que hace falta para probar el permiso de No molestar sin salir de la prueba: un Android que "vuelve" al instante. */
class BancoPermiso {
    val contexto: Context = ApplicationProvider.getApplicationContext()
    private val sombra = Shadows.shadowOf(contexto.getSystemService(NotificationManager::class.java))
    val noMolestar = NoMolestar(contexto)
    val lanzados = mutableListOf<Intent>()

    /** Lo que el sistema hará la próxima vez que se le pida el permiso: darlo o no. */
    var concederAlLanzar = false

    fun conceder(si: Boolean) = sombra.setNotificationPolicyAccessGranted(si)

    private val registro =
        object : ActivityResultRegistry() {
            override fun <I : Any?, O : Any?> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                lanzados += input as Intent
                if (concederAlLanzar) conceder(true)
                dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
            }
        }

    @Composable
    fun Con(content: @Composable () -> Unit) =
        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides
                object : ActivityResultRegistryOwner {
                    override val activityResultRegistry = registro
                },
            content = content,
        )
}

fun habiCon(personalidad: Personality) = HabiSpec(Mood.NORMAL, personalidad, EquippedSet())

/** Las tareas del mock. */
val TAREAS_MOCK =
    listOf(
        OpcionTarea("t0", "Informe trimestral", "Abrir el documento"),
        OpcionTarea("t1", "Pagar el alquiler", null),
    )

fun datosMock(
    fondo: FondoGrabado = FondoGrabado(),
    noMolestar: NoMolestar? = null,
    personalidad: Personality = Personality.NEUTRA,
    avisos: MutableList<String> = mutableListOf(),
) = DatosPreparar(
    tareas = TAREAS_MOCK,
    spec = habiCon(personalidad),
    fondo = fondo,
    noMolestar = noMolestar,
    aviso = { avisos += it },
    objetivo = { null },
)
