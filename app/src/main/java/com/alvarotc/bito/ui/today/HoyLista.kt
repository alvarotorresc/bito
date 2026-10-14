package com.alvarotc.bito.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.alvarotc.bito.ui.inicio.NavegacionInicio
import com.alvarotc.bito.ui.sistema.componentes.AvisoEstado

/** La lista de Hoy. Provisional: cae a la pantalla actual hasta que la rehaga el carril c2. */
@Composable
fun HoyLista(
    viewModel: TodayViewModel,
    navegar: NavegacionInicio,
    listState: LazyListState,
    avisos: AvisoEstado,
    encabezado: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    creado: String? = null,
    alCreadoVisto: () -> Unit = {},
) {
    Box(modifier.testTag("hoy-lista")) {
        TodayScreen(
            viewModel,
            onCreateHabit = navegar.crearHabito,
            onOpenHabit = navegar.abrirFicha,
            onOpenHabi = navegar.abrirTienda,
            onOpenReview = navegar.abrirRepaso,
            onOpenTasks = navegar.abrirTareas,
            onStartFocus = { navegar.abrirRuta("focus?taskId=$it") },
            onOpenBreathing = { navegar.abrirRuta("breathing") },
        )
    }
}
