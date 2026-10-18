package com.alvarotc.bito.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.components.formatDayMedium
import com.alvarotc.bito.ui.habi.HabiAvatar
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.sistema.BitoFormas
import com.alvarotc.bito.ui.sistema.BitoTema
import com.alvarotc.bito.ui.sistema.Duraciones
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.Medidas
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonVolver
import com.alvarotc.bito.ui.sistema.componentes.Fila
import com.alvarotc.bito.ui.sistema.componentes.masBarras
import com.alvarotc.bito.ui.sistema.componentes.rememberAvisoEstado
import com.alvarotc.bito.ui.sistema.rememberGesto
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Los hábitos archivados (spec §5.4), un estrato más abajo: tocar uno abre su ficha en lectura y
 * «Reactivar» hunde la fila, lo devuelve a Hoy y deja un aviso con Deshacer.
 */
@Composable
fun ArchivedScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenHabit: (String) -> Unit,
) {
    val archived by viewModel.archivedHabits.collectAsStateWithLifecycle()
    val colores = BitoTema.colores
    val aviso = rememberAvisoEstado()
    val scope = rememberCoroutineScope()
    val gesto = rememberGesto()
    val reducido = BitoTema.reducido
    val context = LocalContext.current
    val deshacer = stringResource(R.string.archivados_deshacer)

    // Las filas que se están hundiendo: salen de aquí cuando el hábito ya no está en la lista.
    val hundiendo = remember { mutableStateListOf<String>() }
    LaunchedEffect(archived) {
        val presentes = archived.map { it.id }.toSet()
        hundiendo.removeAll { it !in presentes }
    }

    Box(Modifier.fillMaxSize().background(colores.tAlta2)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ArchivedHeader(onBack)
            if (archived.isEmpty()) {
                ArchivedEmpty()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Medidas.margen, vertical = Medidas.e8).masBarras(arriba = false),
                ) {
                    item(key = "nota") {
                        Text(
                            stringResource(R.string.archivados_nota),
                            style = BitoTema.tipos.t14,
                            color = colores.tintaSobreTAlta2,
                            modifier = Modifier.padding(bottom = Medidas.e8),
                        )
                    }
                    items(archived, key = { it.id }) { habit ->
                        val textoReactivar = stringResource(R.string.archivados_reactivar)
                        ArchivedRow(
                            habit = habit,
                            hundida = habit.id in hundiendo,
                            textoReactivar = textoReactivar,
                            onClick = { onOpenHabit(habit.id) },
                            onReactivate = {
                                if (habit.id in hundiendo) return@ArchivedRow
                                hundiendo.add(habit.id)
                                gesto(Snd.HUNDIR, Haptica.FALL)
                                scope.launch {
                                    delay(if (reducido) Duraciones.REDUCIDO.toLong() else Duraciones.HUNDIR_FILA.toLong())
                                    viewModel.reactivate(habit)
                                    aviso.mostrar(
                                        texto = context.getString(R.string.archivados_vuelve, habit.name),
                                        accion = deshacer,
                                        alAccion = { viewModel.rearchive(habit) },
                                    )
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        AvisoHost(aviso, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ArchivedHeader(onBack: () -> Unit) {
    val colores = BitoTema.colores
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Medidas.e8, vertical = Medidas.e8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonVolver(onBack)
        Text(
            stringResource(R.string.archived_title),
            style = BitoTema.tipos.t24,
            color = colores.tinta,
            modifier = Modifier.weight(1f).padding(horizontal = Medidas.e8),
        )
    }
}

@Composable
private fun ArchivedEmpty() {
    val colores = BitoTema.colores
    Column(
        Modifier.fillMaxSize().padding(Medidas.e24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Medidas.e16, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(width = 160.dp, height = 176.dp)
                .clip(BitoFormas.hornacina)
                .background(colores.hornacina),
            contentAlignment = Alignment.BottomCenter,
        ) {
            HabiAvatar(
                HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
                Modifier.size(140.dp),
                animated = !BitoTema.reducido,
            )
        }
        Text(stringResource(R.string.archived_empty), style = BitoTema.tipos.t16, color = colores.tintaSobreTAlta2)
    }
}

/** La fila: G34 la hunde (baja 10 dp, escala .97 y se funde en 220 ms) al reactivar. */
@Composable
private fun ArchivedRow(
    habit: ArchivedHabitUi,
    hundida: Boolean,
    textoReactivar: String,
    onClick: () -> Unit,
    onReactivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducido = BitoTema.reducido
    val progreso by animateFloatAsState(
        targetValue = if (hundida) 1f else 0f,
        animationSpec = tween(if (reducido) Duraciones.REDUCIDO else Duraciones.HUNDIR_FILA),
        label = "archivadoHunde",
    )
    val subtitulo =
        habit.archivedOnDay?.let { day ->
            val date = remember(day) { formatDayMedium(day) }
            stringResource(R.string.archived_since, date)
        }
    Fila(
        etiqueta = habit.name,
        subtitulo = subtitulo,
        alPulsar = onClick,
        modifier =
            modifier
                .testTag("archived-${habit.id}")
                .graphicsLayer {
                    alpha = 1f - progreso
                    if (!reducido) {
                        translationY = 10.dp.toPx() * progreso
                        val escala = 1f - 0.03f * progreso
                        scaleX = escala
                        scaleY = escala
                    }
                },
    ) {
        Boton(textoReactivar, alPulsar = onReactivate, modifier = Modifier.testTag("archived-reactivate-${habit.id}"))
    }
}
