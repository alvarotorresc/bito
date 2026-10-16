package com.alvarotc.bito.ui.habi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.propuestas.TextoHabi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Decision 9: quien no ha dado su nombre no recibe un apodo. Cada texto de Habi que lleva `%1$s`
 * en los contextos que no cambian tiene su gemelo `_sin_nombre`, que dice lo mismo sin llamar a
 * nadie por nada y sigue las reglas de voz (spec 8.3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextosSinNombreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val conNombre: List<Int> =
        Personality.entries.flatMap { p ->
            Mood.entries.map { m -> HabiVoice.homeRes(m, p) } +
                listOf(
                    HabiVoice.badgeUnlockedRes(p),
                    HabiVoice.taskFocusRes(p),
                    HabiVoice.breathingDoneRes(p),
                    HabiVoice.freezerInfoRes(p),
                    HabiVoice.taskDoneRes(p),
                    HabiVoice.perfectDayNotifRes(p),
                )
        }

    private fun nombre(res: Int) = context.resources.getResourceEntryName(res)

    private fun gemelos(): List<Int> =
        conNombre.map { res ->
            val gemelo = HabiVoice.sinNombre(res)
            assertNotNull("${nombre(res)} no tiene gemelo sin nombre", gemelo)
            gemelo!!
        }

    @Test
    fun `every name text has a nameless twin`() {
        val gemelos = gemelos()
        assertEquals(30, gemelos.size)
        assertEquals("cada gemelo es propio", gemelos.size, gemelos.toSet().size)
        for ((res, gemelo) in conNombre.zip(gemelos)) {
            assertEquals("${nombre(res)}_sin_nombre", nombre(gemelo))
        }
        for (qualifiers in listOf("en", "es")) {
            val ctx = contextoEn(qualifiers)
            for (gemelo in gemelos) {
                assertFalse("[$qualifiers] ${nombre(gemelo)} aun lleva %1\$s", ctx.getString(gemelo).contains("%1\$s"))
            }
        }
    }

    @Test
    fun `greetings, perfect day sheets and onboarding previews have a nameless twin`() {
        val textos =
            Personality.entries.flatMap { p ->
                Mood.entries.map { m -> HabiVoice.greetingRes(m, p) } +
                    listOf(HabiVoice.perfectDayRes(p), HabiVoice.onboardingPreviewRes(p))
            }
        assertEquals(18, textos.size)
        val campeon = Regex("campe[oó]n\\w*|champ\\w*", RegexOption.IGNORE_CASE)
        for (res in textos) {
            val gemelo = HabiVoice.sinNombre(res)
            assertNotNull("${nombre(res)} no tiene gemelo sin nombre", gemelo)
            assertEquals("${nombre(res)}_sin_nombre", nombre(gemelo!!))
            for (qualifiers in listOf("en", "es")) {
                val texto = contextoEn(qualifiers).getString(gemelo)
                assertFalse("[$qualifiers] ${nombre(gemelo)} lleva hueco", texto.contains("%1\$s"))
                assertFalse("[$qualifiers] ${nombre(gemelo)} campeon", campeon.containsMatchIn(texto))
                assertFalse("[$qualifiers] ${nombre(gemelo)} raya larga", texto.contains('—'))
                if (nombre(gemelo).contains("neutra")) assertFalse("[$qualifiers] ${nombre(gemelo)} exclamacion", texto.contains('!'))
            }
        }
    }

    @Test
    fun `sinNombre of a text without a twin is null`() {
        assertEquals(null, HabiVoice.sinNombre(R.string.habi_form_prompt_neutra))
    }

    @Test
    fun `nameless texts follow the voice rules`() {
        val campeon = Regex("campe[oó]n\\w*|champ\\w*", RegexOption.IGNORE_CASE)
        val generoEs =
            Regex(
                "\\b(estoy|me siento|me quedo|soy|ando|estaba|quedo)\\s+" +
                    "(content|cansad|dormid|list|orgullos|preocupad|agotad|emocionad|ilusionad|satisfech|tranquil|nervios)[oa]s?\\b",
                RegexOption.IGNORE_CASE,
            )
        val pronombreEs = Regex("\\b(él|ella)\\b", RegexOption.IGNORE_CASE)
        val generoEn = Regex("\\b(he|she|him|her|his|hers)\\b", RegexOption.IGNORE_CASE)
        for (qualifiers in listOf("en", "es")) {
            val ctx = contextoEn(qualifiers)
            for (gemelo in gemelos()) {
                val texto = ctx.getString(gemelo, "", if (nombre(gemelo).contains("task_focus")) 5 else "X")
                val id = "[$qualifiers] ${nombre(gemelo)}"
                assertFalse("$id campeon", campeon.containsMatchIn(texto))
                assertFalse("$id raya larga", texto.contains('—'))
                assertFalse("$id genero", generoEs.containsMatchIn(texto) || pronombreEs.containsMatchIn(texto))
                if (qualifiers == "en") assertFalse("$id genero", generoEn.containsMatchIn(texto))
                if (nombre(gemelo).contains("neutra")) assertFalse("$id exclamacion en la Neutra", texto.contains('!'))
            }
        }
    }

    @Test
    fun `the neutral perfect day notice says nothing was left`() {
        val ctx = contextoEn("es")
        val conNombre = ctx.getString(R.string.habi_perfect_notif_neutra, "Álvaro")
        assertEquals("Día perfecto, Álvaro. Hoy no ha quedado nada.", conNombre)
        val gemelo = HabiVoice.sinNombre(R.string.habi_perfect_notif_neutra)!!
        assertTrue(ctx.getString(gemelo).contains("Hoy no ha quedado nada."))
        val en = contextoEn("en")
        assertEquals("Perfect day, Álvaro. Nothing was left undone today.", en.getString(R.string.habi_perfect_notif_neutra, "Álvaro"))
    }

    @Test
    fun `conNombre with a name keeps the name argument`() {
        assertEquals(
            TextoHabi.Recurso(R.string.habi_home_neutra_normal, listOf("Álvaro")),
            HabiVoice.conNombre(R.string.habi_home_neutra_normal, "Álvaro"),
        )
    }

    @Test
    fun `conNombre without a name uses the twin`() {
        assertEquals(
            TextoHabi.Recurso(HabiVoice.sinNombre(R.string.habi_home_neutra_normal)!!),
            HabiVoice.conNombre(R.string.habi_home_neutra_normal, ""),
        )
        assertEquals(
            TextoHabi.Recurso(HabiVoice.sinNombre(R.string.habi_home_neutra_normal)!!),
            HabiVoice.conNombre(R.string.habi_home_neutra_normal, "   "),
        )
    }

    @Test
    fun `conNombre keeps the numbering of the extra arguments`() {
        val con = HabiVoice.conNombre(R.string.habi_badge_sargento, "Álvaro", "Racha")
        assertEquals(TextoHabi.Recurso(R.string.habi_badge_sargento, listOf("Álvaro", "Racha")), con)
        val sin = HabiVoice.conNombre(R.string.habi_badge_sargento, "", "Racha") as TextoHabi.Recurso
        assertEquals(HabiVoice.sinNombre(R.string.habi_badge_sargento), sin.res)
        assertEquals("Racha. Te lo has ganado. Era cuestión de tiempo.", contextoEn("es").getString(sin.res, *sin.args.toTypedArray()))
    }

    private fun contextoEn(qualifiers: String): Context {
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(java.util.Locale.forLanguageTag(qualifiers))
        return context.createConfigurationContext(config)
    }
}
