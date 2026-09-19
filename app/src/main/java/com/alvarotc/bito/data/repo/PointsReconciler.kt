package com.alvarotc.bito.data.repo

import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.BadgeEngine
import com.alvarotc.bito.domain.EyeRitual
import com.alvarotc.bito.domain.EyeTransition
import com.alvarotc.bito.domain.HabiEngine
import com.alvarotc.bito.domain.PerfectDays
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.PointsEvent
import com.alvarotc.bito.domain.model.PointsReason

/**
 * What a [PointsReconciler.reconcile] pass granted: nothing here was owned
 * before the call, so callers can react to it (e.g. celebrate a badge) without
 * re-deriving state themselves.
 */
data class ReconcileResult(
    val newEvents: List<PointsEvent> = emptyList(),
    val newItems: Set<String> = emptySet(),
    val newBadges: Set<String> = emptySet(),
    val eyeRitual: EyeTransition? = null,
) {
    /** Whether this pass just granted the perfect-day points event for [day]. */
    fun reachedPerfectDay(day: LogicalDay): Boolean = newEvents.any { it.reason == PointsReason.PERFECT_DAY && it.refId == "day:$day" }
}

/**
 * Appends engine-derived grants the ledger and the inventory are missing:
 * points, streak exclusives and badges alike. All three are append-only
 * (never confiscated) and idempotent, so callers run this after every write —
 * points via the ledger's unique (reason, refId) index, exclusives and
 * badges via an insert-ignore keyed on their id.
 */
class PointsReconciler(
    private val domainState: DomainStateRepository,
    private val rewards: RewardsRepository,
    private val economy: EconomyConfig = EconomyConfig(),
    // Nullable con default: hay 13 sitios de construcción en el repo (1 de producción, 12 de test).
    // Mismo idioma que TodayViewModel.habiSounds — el único sitio real lo pasa, los tests que no
    // examinan el ritual siguen compilando sin tocarse. Con null, eyeRitual es SIEMPRE null.
    private val settings: SettingsRepository? = null,
) {
    suspend fun reconcile(
        today: LogicalDay,
        nowMillis: Long,
    ): ReconcileResult {
        val state = domainState.snapshot()
        val perfectDays = PerfectDays.perfectDaysUpTo(state, today)
        val missing = PointsEngine.missingEvents(PointsEngine.earnedEvents(state, today, economy, perfectDays), state.pointsLedger)
        if (missing.isNotEmpty()) rewards.append(missing, nowMillis)

        val earned = HabiEngine.earnedExclusives(state, today)
        val missingItems = HabiEngine.missingExclusives(earned, rewards.ownedItemIds())
        if (missingItems.isNotEmpty()) rewards.grantItems(missingItems, nowMillis)

        val missingBadges = BadgeEngine.missingBadges(BadgeEngine.earnedBadges(state, today, perfectDays), rewards.unlockedBadgeIds())
        if (missingBadges.isNotEmpty()) rewards.unlockBadges(missingBadges, nowMillis)

        val eyeRitual = healEyeRitual(state, today)

        return ReconcileResult(newEvents = missing, newItems = missingItems, newBadges = missingBadges, eyeRitual = eyeRitual)
    }

    /**
     * Sana `Settings.habiEyesPainted` desde el historial y devuelve la transición cuando la hay.
     * El leer-comparar-escribir es de [SettingsRepository.healEyeLevel], que lo resuelve en un
     * solo `edit`: aquí solo se deriva el nivel que la historia justifica y se decide si el rito
     * se cuenta o se calla.
     *
     * El marcador de restore se reclama INCONDICIONALMENTE, antes del corte "sin cambio": un
     * restore sin hábitos deja stored=0 y derived=0 (no hay salto que sanar en ese pase), pero el
     * marcador tiene que gastarse igual ahí. Si solo se reclamara cuando hay cambio, quedaría
     * armado y se tragaría la transición real del primer hábito creado después del restore — el
     * primer ojo nunca se celebraría. Así, el marcador se gasta en el primer reconcile posterior
     * al restore siempre, haya o no salto que sanar en ese mismo pase.
     */
    private suspend fun healEyeRitual(
        state: DomainState,
        today: LogicalDay,
    ): EyeTransition? {
        val settings = settings ?: return null
        val silent = settings.claimSilentEyeHeal()
        val transition = settings.healEyeLevel(EyeRitual.derivedLevel(state, today))
        return if (silent) null else transition
    }
}
