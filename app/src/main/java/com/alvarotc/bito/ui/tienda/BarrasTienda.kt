package com.alvarotc.bito.ui.tienda

import android.content.res.Resources
import android.text.format.DateFormat
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.SaleWindow
import com.alvarotc.bito.domain.model.easterSunday
import com.alvarotc.bito.ui.habi.itemNameRes
import com.alvarotc.bito.ui.stats.BadgeStrings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** La tabla de la barra de prueba (spec §3.3.4), pura: la pantalla la pinta y el estado la decide. */
object BarrasTienda {
    /**
     * La barra de [prueba], o null si no hay prueba o si lo probado ya es tuyo (lo tuyo no abre barra).
     * [poseidos] son los ids que ya tienes; [mejorRacha] la mejor racha de cualquier hábito; [hoy] decide la
     * ventana de venta. Los textos salen de [res], con el idioma y la `Locale` que lleve.
     */
    fun barraDe(
        prueba: PruebaTienda?,
        poseidos: Set<String>,
        saldo: Int,
        mejorRacha: Int,
        hoy: LocalDate,
        res: Resources,
    ): BarraPrueba? =
        when (prueba) {
            null -> null
            is PruebaTienda.Pieza -> HabiCatalog.byId(prueba.itemId)?.let { pieza(it, poseidos, saldo, mejorRacha, hoy, res) }
            is PruebaTienda.Objeto -> HabiCatalog.byId(prueba.itemId)?.let { objeto(it, poseidos, saldo, res) }
            is PruebaTienda.Especial -> especial(prueba.logroId, res)
        }

    private fun pieza(
        item: CatalogItem,
        poseidos: Set<String>,
        saldo: Int,
        mejorRacha: Int,
        hoy: LocalDate,
        res: Resources,
    ): BarraPrueba? {
        if (item.category == CustomizationCategory.GARDEN_OBJECT || item.default || item.id in poseidos) return null
        val nombre = nombreDe(item.id, res)
        val precio = item.price
        val racha = item.requiredStreak
        return when {
            precio == null && racha != null ->
                BarraPrueba.NoSeCompra(
                    nombre,
                    MotivoNoSeCompra.RACHA,
                    TextoBarra.Plano(R.string.store_bar_streak, listOf(racha, mejorRacha, (racha - mejorRacha).coerceAtLeast(0))),
                    objeto = false,
                )
            precio == null -> null
            !HabiCatalog.isOnSale(item, hoy) ->
                BarraPrueba.NoSeCompra(
                    nombre,
                    MotivoNoSeCompra.TEMPORADA,
                    TextoBarra.Plano(R.string.store_bar_out_of_season, listOf(ventana(item, res))),
                    objeto = false,
                )
            saldo < precio -> BarraPrueba.SinSaldo(nombre, precio, saldo, objeto = false)
            else -> {
                val nota =
                    item.season?.let {
                        TextoBarra.Plano(R.string.store_bar_in_season, listOf(precio, saldo - precio, fin(item, hoy, res)))
                    }
                BarraPrueba.Comprar(nombre, precio, saldo - precio, nota, objeto = false)
            }
        }
    }

    private fun objeto(
        item: CatalogItem,
        poseidos: Set<String>,
        saldo: Int,
        res: Resources,
    ): BarraPrueba? {
        val precio = item.price
        if (item.category != CustomizationCategory.GARDEN_OBJECT || precio == null || item.id in poseidos) return null
        val nombre = nombreDe(item.id, res)
        return if (saldo < precio) {
            BarraPrueba.SinSaldo(nombre, precio, saldo, objeto = true)
        } else {
            BarraPrueba.Comprar(
                nombre,
                precio,
                saldo - precio,
                TextoBarra.Plano(R.string.store_bar_garden, listOf(precio, saldo - precio)),
                objeto = true,
            )
        }
    }

    private fun especial(
        logroId: String,
        res: Resources,
    ): BarraPrueba? {
        val especial = GardenSpecial.entries.firstOrNull { it.badgeId == logroId } ?: return null
        val logro = res.getString(BadgeStrings.badgeNameRes(logroId))
        val como = res.getString(BadgeStrings.badgeHowRes(logroId))
        return BarraPrueba.NoSeCompra(
            res.getString(nombreDeEspecial(especial)),
            MotivoNoSeCompra.LOGRO,
            TextoBarra.Plano(R.string.store_bar_badge, listOf(logro, como)),
            objeto = true,
        )
    }

    /** El nombre que se enseña de una pieza del armario o de un objeto de jardín. */
    fun nombreDe(
        itemId: String,
        res: Resources,
    ): String =
        when (itemId) {
            "garden-banco" -> res.getString(R.string.store_garden_bench)
            "garden-regadera" -> res.getString(R.string.store_garden_can)
            else -> res.getString(itemNameRes(itemId))
        }

    private fun nombreDeEspecial(special: GardenSpecial): Int =
        when (special) {
            GardenSpecial.LANTERN -> R.string.jardin_especial_farolillo
            GardenSpecial.STONE_PATH -> R.string.jardin_especial_caminito
            GardenSpecial.POND -> R.string.jardin_especial_estanque
            GardenSpecial.MUSHROOMS -> R.string.jardin_especial_setas
            GardenSpecial.BIG_CHERRY -> R.string.jardin_especial_cerezo_grande
            GardenSpecial.GOLDEN_TREE -> R.string.jardin_especial_arbol_dorado
        }

    /**
     * La ventana de venta ya formada, para el mensaje de fuera de temporada: una sola plantilla
     * (`store_bar_out_of_season`) que recibe «del 1 de diciembre al 6 de enero» o «en las dos semanas antes del
     * Miércoles de Ceniza», sin armar dos fechas sueltas.
     */
    private fun ventana(
        item: CatalogItem,
        res: Resources,
    ): String =
        when (val season = item.season) {
            is SaleWindow.Carnival -> res.getString(R.string.store_season_carnival)
            is SaleWindow.Fixed ->
                if (season.startMonth == 12 && season.startDay == 1 && season.endMonth == 1 && season.endDay == 6) {
                    res.getString(R.string.store_season_long_dec)
                } else {
                    res.getString(
                        R.string.store_season_fixed,
                        diaYMes(season.startMonth, season.startDay, res),
                        diaYMes(season.endMonth, season.endDay, res),
                    )
                }
            null -> ""
        }

    /** El último día a la venta de la ventana que contiene [hoy], con el idioma de [res]. */
    private fun fin(
        item: CatalogItem,
        hoy: LocalDate,
        res: Resources,
    ): String =
        when (val season = item.season) {
            is SaleWindow.Fixed -> diaYMes(season.endMonth, season.endDay, res)
            is SaleWindow.Carnival -> {
                val ultimo = easterSunday(hoy.year).minusDays(ASH_WEDNESDAY_BEFORE_EASTER + 1)
                diaYMes(ultimo.monthValue, ultimo.dayOfMonth, res)
            }
            null -> ""
        }

    // La Pascua cae 46 días después del Miércoles de Ceniza; la víspera es el último día de carnaval.
    private const val ASH_WEDNESDAY_BEFORE_EASTER = 46L

    // 2024 es bisiesto: acepta el 29 de febrero. Solo se usan el día y el mes.
    private fun diaYMes(
        mes: Int,
        dia: Int,
        res: Resources,
    ): String {
        val locale: Locale = res.configuration.locales[0]
        val patron = DateFormat.getBestDateTimePattern(locale, "dMMMM")
        return DateTimeFormatter.ofPattern(patron, locale).format(LocalDate.of(2024, mes, dia))
    }
}
