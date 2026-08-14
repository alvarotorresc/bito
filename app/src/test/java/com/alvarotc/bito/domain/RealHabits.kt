package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period

/**
 * The architect's real habit list (`docs/anexo-habitos-reales.md`) as fixtures.
 *
 * These are the habits the engine has to get right, so they are the ones the
 * tests use. Between them they cover the 5 presets of the functional document,
 * the 3 metrics, the 3 periods, the 3 directions and both log modes.
 *
 * Every habit is born on [HABIT_BIRTH]; scenarios that care about the start of
 * a habit's life move it with [createdOn].
 */
internal object RealHabits {
    // -----------------------------------------------------------------------
    // Preset 1 — "Hábito diario" (sí/no) · CHECK · DAY · AT_LEAST 1
    // -----------------------------------------------------------------------

    val makeBed = dailyCheck("make-bed", "Hacer la cama")
    val meditate = dailyCheck("meditate", "Meditar")
    val readBeforeSleep = dailyCheck("read-before-sleep", "Leer antes de dormir")
    val supplements = dailyCheck("supplements", "Tomar suplementos")
    val floss = dailyCheck("floss", "Pasar hilo dental")
    val journaling = dailyCheck("journaling", "Journaling")
    val stretching = dailyCheck("stretching", "Estiramientos")

    // -----------------------------------------------------------------------
    // Preset 2 — "Cantidad" · COUNT · DAY · AT_LEAST
    // -----------------------------------------------------------------------

    /** 8 vasos/día, modo contador con paso +1. */
    val water = dailyAmount("water", "Beber agua", target = 8, unit = "vasos")

    /** 9.000 pasos/día en modo "solo cumplido": el objetivo es referencia, el registro es un tap. */
    val steps = dailyAmount("steps", "Pasos", target = 9_000, unit = "pasos", logMode = LogMode.BINARY)

    val pagesRead = dailyAmount("pages-read", "Páginas leídas", target = 10, unit = "páginas")
    val protein = dailyAmount("protein", "Proteína", target = 150, unit = "g")
    val calories = dailyAmount("calories", "Calorías", target = 2_200, unit = "kcal")
    val fruitAndVeg = dailyAmount("fruit-and-veg", "Fruta/verdura", target = 5, unit = "raciones")

    // -----------------------------------------------------------------------
    // Preset 3 — "Duración" · DURATION · DAY · AT_LEAST (minutos)
    // -----------------------------------------------------------------------

    val workout = dailyDuration("workout", "Ejercicio/entrenamiento", minutes = 45)
    val guitar = dailyDuration("guitar", "Practicar guitarra", minutes = 20)
    val languageStudy = dailyDuration("language-study", "Estudiar idioma", minutes = 15)
    val meditationTime = dailyDuration("meditation-time", "Meditación", minutes = 10)
    val walking = dailyDuration("walking", "Caminar", minutes = 30)
    val reading = dailyDuration("reading", "Lectura", minutes = 30)
    val sleep = dailyDuration("sleep", "Sueño", minutes = 420)

    // -----------------------------------------------------------------------
    // Preset 4 — "X veces por semana" · CHECK · WEEK · AT_LEAST (cuentan DÍAS)
    // -----------------------------------------------------------------------

    val strengthTraining = weeklyFrequency("strength", "Entrenamiento de fuerza", timesPerWeek = 3)
    val cardio = weeklyFrequency("cardio", "Cardio", timesPerWeek = 2)
    val cookAtHome = weeklyFrequency("cook-at-home", "Cocinar en casa", timesPerWeek = 5)
    val callFamily = weeklyFrequency("call-family", "Llamar a familia", timesPerWeek = 1)
    val deepCleaning = weeklyFrequency("deep-cleaning", "Limpieza a fondo", timesPerWeek = 1)
    val socialSport = weeklyFrequency("social-sport", "Deporte social", timesPerWeek = 1)
    val publishContent = weeklyFrequency("publish-content", "Publicar contenido", timesPerWeek = 1)

    // -----------------------------------------------------------------------
    // Preset 5a — "Dejar de hacer": abstinencia total · ZERO · DAY
    // -----------------------------------------------------------------------

    val noSmoking = abstinence("no-smoking", "No fumar")
    val noSugar = abstinence("no-sugar", "No azúcar/ultraprocesados")
    val noPhoneInBed = abstinence("no-phone-in-bed", "No móvil en la cama")
    val noNailBiting = abstinence("no-nail-biting", "No morderse las uñas")
    val noAlcohol = abstinence("no-alcohol", "No alcohol")

    // -----------------------------------------------------------------------
    // Preset 5b — "Dejar de hacer": límite · AT_MOST
    // -----------------------------------------------------------------------

    /** Redes sociales ≤ 30 min/día. */
    val socialMedia =
        Habit(
            id = "social-media",
            name = "No redes sociales",
            metric = Metric.DURATION,
            period = Period.DAY,
            direction = Direction.AT_MOST,
            target = 30,
            unit = "min",
            createdOnDay = HABIT_BIRTH,
        )

    /** Comida a domicilio ≤ 1 día/semana. */
    val foodDelivery =
        Habit(
            id = "food-delivery",
            name = "No comida a domicilio",
            metric = Metric.CHECK,
            period = Period.WEEK,
            direction = Direction.AT_MOST,
            target = 1,
            createdOnDay = HABIT_BIRTH,
        )

    // -----------------------------------------------------------------------
    // Período MONTH — el anexo no tiene hábitos mensuales; estos dos existen
    // para cubrir el tercer período del modelo con la misma forma que el resto.
    // -----------------------------------------------------------------------

    /** 2 libros terminados al mes. */
    val booksFinished =
        Habit(
            id = "books-finished",
            name = "Libros terminados",
            metric = Metric.COUNT,
            period = Period.MONTH,
            direction = Direction.AT_LEAST,
            target = 2,
            unit = "libros",
            createdOnDay = HABIT_BIRTH,
        )

    /** Comer fuera como máximo 4 días al mes. */
    val eatingOut =
        Habit(
            id = "eating-out",
            name = "No comer fuera",
            metric = Metric.CHECK,
            period = Period.MONTH,
            direction = Direction.AT_MOST,
            target = 4,
            createdOnDay = HABIT_BIRTH,
        )

    /** Every fixture habit, handy for "the engine ignores unrelated habits" checks. */
    val all: List<Habit> =
        listOf(
            makeBed,
            meditate,
            readBeforeSleep,
            supplements,
            floss,
            journaling,
            stretching,
            water,
            steps,
            pagesRead,
            protein,
            calories,
            fruitAndVeg,
            workout,
            guitar,
            languageStudy,
            meditationTime,
            walking,
            reading,
            sleep,
            strengthTraining,
            cardio,
            cookAtHome,
            callFamily,
            deepCleaning,
            socialSport,
            publishContent,
            noSmoking,
            noSugar,
            noPhoneInBed,
            noNailBiting,
            noAlcohol,
            socialMedia,
            foodDelivery,
            booksFinished,
            eatingOut,
        )

    private fun dailyCheck(
        id: String,
        name: String,
    ): Habit =
        Habit(
            id = id,
            name = name,
            metric = Metric.CHECK,
            period = Period.DAY,
            direction = Direction.AT_LEAST,
            target = 1,
            createdOnDay = HABIT_BIRTH,
        )

    private fun dailyAmount(
        id: String,
        name: String,
        target: Int,
        unit: String,
        logMode: LogMode = LogMode.COUNTER,
    ): Habit =
        Habit(
            id = id,
            name = name,
            metric = Metric.COUNT,
            period = Period.DAY,
            direction = Direction.AT_LEAST,
            target = target,
            unit = unit,
            logMode = logMode,
            createdOnDay = HABIT_BIRTH,
        )

    private fun dailyDuration(
        id: String,
        name: String,
        minutes: Int,
    ): Habit =
        Habit(
            id = id,
            name = name,
            metric = Metric.DURATION,
            period = Period.DAY,
            direction = Direction.AT_LEAST,
            target = minutes,
            unit = "min",
            createdOnDay = HABIT_BIRTH,
        )

    private fun weeklyFrequency(
        id: String,
        name: String,
        timesPerWeek: Int,
    ): Habit =
        Habit(
            id = id,
            name = name,
            metric = Metric.CHECK,
            period = Period.WEEK,
            direction = Direction.AT_LEAST,
            target = timesPerWeek,
            createdOnDay = HABIT_BIRTH,
        )

    private fun abstinence(
        id: String,
        name: String,
    ): Habit =
        Habit(
            id = id,
            name = name,
            metric = Metric.CHECK,
            period = Period.DAY,
            direction = Direction.ZERO,
            target = 0,
            createdOnDay = HABIT_BIRTH,
        )
}
