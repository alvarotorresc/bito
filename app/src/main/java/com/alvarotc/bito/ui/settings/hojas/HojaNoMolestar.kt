package com.alvarotc.bito.ui.settings.hojas

import androidx.compose.runtime.Composable
import com.alvarotc.bito.ui.sistema.componentes.Hoja

@Composable
fun HojaNoMolestar(
    abierta: Boolean,
    activo: Boolean,
    desde: Int,
    hasta: Int,
    permisoDenegado: Boolean,
    alCambiarActivo: (Boolean) -> Unit,
    alCambiarDesde: (Int) -> Unit,
    alCambiarHasta: (Int) -> Unit,
    alPedirPermiso: () -> Unit,
    alCerrar: () -> Unit,
) = Hoja(abierta = abierta, alCerrar = alCerrar) {}
