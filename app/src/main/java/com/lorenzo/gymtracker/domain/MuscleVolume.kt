package com.lorenzo.gymtracker.domain

import com.lorenzo.gymtracker.data.db.dao.SetRecord
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Muscle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Sets counted for a muscle it only helps with (secondary). Muscles it mainly works count 1. */
const val SECONDARY_SET_SHARE = 0.5

/**
 * Sets per muscle in [sets]: a ticked set counts 1 for each main muscle of its exercise and
 * [SECONDARY_SET_SHARE] for each helping one. Muscles with no sets are left out.
 */
fun muscleSets(sets: List<SetRecord>, exercises: Map<Long, Exercise>): Map<Muscle, Double> {
    val totals = mutableMapOf<Muscle, Double>()
    for (set in sets) {
        val exercise = exercises[set.exerciseId] ?: continue
        exercise.primaryMuscles.forEach { totals[it] = (totals[it] ?: 0.0) + 1.0 }
        exercise.secondaryMuscles.filter { it !in exercise.primaryMuscles }
            .forEach { totals[it] = (totals[it] ?: 0.0) + SECONDARY_SET_SHARE }
    }
    return totals
}

/** The day each muscle last had a ticked set as a main muscle, or as a helper if never. */
fun lastTrained(sets: List<SetRecord>, exercises: Map<Long, Exercise>, zone: ZoneId = ZoneId.systemDefault()): Map<Muscle, LocalDate> {
    val last = mutableMapOf<Muscle, LocalDate>()
    for (set in sets) {
        val exercise = exercises[set.exerciseId] ?: continue
        val date = set.date(zone)
        (exercise.primaryMuscles + exercise.secondaryMuscles).forEach { m ->
            if (last[m]?.isBefore(date) != false) last[m] = date
        }
    }
    return last
}

/** Periods for muscle stats. Values are shown as sets per week. */
enum class MusclePeriod(val weeks: Int) {
    THIS_WEEK(1),
    FOUR_WEEKS(4),
    TWELVE_WEEKS(12),
}

/** First day of [period]: this week's Monday, or as many whole weeks before today. */
fun MusclePeriod.start(today: LocalDate): LocalDate = when (this) {
    MusclePeriod.THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    else -> today.minusDays(weeks * 7L - 1)
}

/**
 * Last day of the [period] that's [back] periods before the current one: today for the current
 * one, otherwise a Sunday for "this week" or the day before the following period starts.
 */
fun MusclePeriod.end(back: Int, today: LocalDate): LocalDate = when {
    back == 0 -> today
    this == MusclePeriod.THIS_WEEK -> start(today).minusWeeks(back.toLong()).plusDays(6)
    else -> today.minusWeeks(weeks.toLong() * back)
}

/** Average sets per week for each muscle over [period], ending [today]. */
fun weeklyMuscleSets(
    sets: List<SetRecord>,
    exercises: Map<Long, Exercise>,
    period: MusclePeriod,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): Map<Muscle, Double> {
    val from = period.start(today)
    val inPeriod = sets.filter { val d = it.date(zone); !d.isBefore(from) && !d.isAfter(today) }
    return muscleSets(inPeriod, exercises).mapValues { it.value / period.weeks }
}
