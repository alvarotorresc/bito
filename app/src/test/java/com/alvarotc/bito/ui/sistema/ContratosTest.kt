package com.alvarotc.bito.ui.sistema

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import com.alvarotc.bito.ui.sistema.componentes.Boton
import com.alvarotc.bito.ui.sistema.componentes.BotonAgua
import com.alvarotc.bito.ui.sistema.componentes.BotonesHoja
import com.alvarotc.bito.ui.sistema.componentes.CalendarioElegir
import com.alvarotc.bito.ui.sistema.componentes.Campo
import com.alvarotc.bito.ui.sistema.componentes.CampoGrande
import com.alvarotc.bito.ui.sistema.componentes.CanalMesa
import com.alvarotc.bito.ui.sistema.componentes.CifraRodillo
import com.alvarotc.bito.ui.sistema.componentes.Dialogo
import com.alvarotc.bito.ui.sistema.componentes.ElegirConAgua
import com.alvarotc.bito.ui.sistema.componentes.Emblema
import com.alvarotc.bito.ui.sistema.componentes.EstratoPlegable
import com.alvarotc.bito.ui.sistema.componentes.FamiliaLogro
import com.alvarotc.bito.ui.sistema.componentes.FilaEnlace
import com.alvarotc.bito.ui.sistema.componentes.FilaInterruptor
import com.alvarotc.bito.ui.sistema.componentes.FilaValor
import com.alvarotc.bito.ui.sistema.componentes.Globito
import com.alvarotc.bito.ui.sistema.componentes.Hoja
import com.alvarotc.bito.ui.sistema.componentes.HojaPersonalidad
import com.alvarotc.bito.ui.sistema.componentes.Interruptor
import com.alvarotc.bito.ui.sistema.componentes.MenuFila
import com.alvarotc.bito.ui.sistema.componentes.MesaCanales
import com.alvarotc.bito.ui.sistema.componentes.Monedita
import com.alvarotc.bito.ui.sistema.componentes.OpcionAgua
import com.alvarotc.bito.ui.sistema.componentes.Plegable
import com.alvarotc.bito.ui.sistema.componentes.RelojCielo
import com.alvarotc.bito.ui.sistema.componentes.Saldo
import com.alvarotc.bito.ui.sistema.componentes.Segmentado
import com.alvarotc.bito.ui.sistema.componentes.Stepper
import com.alvarotc.bito.ui.sistema.componentes.rememberAvisoEstado
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContratosTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `every component composes with its contract signature`() {
        rule.setContent {
            ConSistema {
                Column {
                    Boton("a", {})
                    BotonesHoja("Guardar", {}, suave = "Ahora no")
                    BotonAgua("Copiar", trabajando = false, alPulsar = {})
                    Interruptor(true, {})
                    Segmentado(listOf("a", "b"), 0, {})
                    FilaInterruptor("x", true, {})
                    FilaValor("x", "y", {})
                    FilaEnlace("x", {})
                    Stepper(3, {}, 1, 5, descripcion = "n")
                    CifraRodillo(7)
                    Campo("v", {})
                    CampoGrande("1", {})
                    Plegable("p", false, {}) { Text("x") }
                    EstratoPlegable(BitoClaro.estratos.general, "General", "resumen", false, {}) { Text("x") }
                    ElegirConAgua(listOf(OpcionAgua("a", "A")), "a", {})
                    Monedita(18.dp)
                    Saldo(12)
                    Emblema(FamiliaLogro.RACHAS, 0.5f, encendido = false)
                    RelojCielo(510, {})
                    CalendarioElegir(LocalDate.of(2026, 10, 14), null, {})
                    MesaCanales(CanalMesa.entries.associateWith { 5 }, { _, _ -> })
                    Globito(true, "hola")
                    AvisoHost(rememberAvisoEstado())
                    Hoja(abierta = false, alCerrar = {}) {}
                    Dialogo(false, "t", "Borrar", {}, {})
                    MenuFila(false, {}, emptyList())
                    HojaPersonalidad(false, Personality.NEUTRA, {}, {})
                }
            }
        }
        rule.waitForIdle()
    }
}
