package com.alvarotc.bito.domain.model

enum class HabiDayPhase { AWAKE, WAITING, ASLEEP }

enum class HabiPose { STANDING, WAITING, SLEEPING, TIPPED, SEATED }

enum class HabiCue { LOGGED, ALL_DONE, FAILED, SEALED, PERFECT_DAY }

data class HabiCueEnvelope(val cue: HabiCue, val id: Long)

fun HabiDayPhase.toPose(): HabiPose =
    when (this) {
        HabiDayPhase.AWAKE -> HabiPose.STANDING
        HabiDayPhase.WAITING -> HabiPose.WAITING
        HabiDayPhase.ASLEEP -> HabiPose.SLEEPING
    }
