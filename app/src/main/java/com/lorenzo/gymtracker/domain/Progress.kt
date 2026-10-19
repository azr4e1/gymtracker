package com.lorenzo.gymtracker.domain

import com.lorenzo.gymtracker.data.db.dao.SetRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the progress chart of an exercise plots, one value per workout. */
enum class Metric {
    /** Estimated one-rep max of the best set. */
    ONE_REP_MAX,
    MAX_WEIGHT,

    /** Weight × reps, summed over the workout's sets. */
    VOLUME,
    MAX_REPS,
    TOTAL_REPS,
    LONGEST,
    TOTAL_TIME,
}

/** A workout's value for a [Metric]. */
data class SessionPoint(val sessionId: Long, val date: LocalDate, val value: Double)

fun SetRecord.date(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()

private val SetRecord.kg: Double get() = weightKg?.takeIf { it > 0 } ?: 0.0

/**
 * Epley estimate of the weight that could be lifted once: weight × (1 + reps / 30).
 * Null without weight or reps.
 */
fun estimatedOneRepMax(weightKg: Double, reps: Int): Double? = when {
    weightKg <= 0 || reps <= 0 -> null
    reps == 1 -> weightKg
    else -> weightKg * (1 + reps / 30.0)
}

/** The metrics that make sense for these sets (all of one exercise), most useful first. */
fun availableMetrics(sets: List<SetRecord>): List<Metric> = buildList {
    val repSets = sets.filter { !it.timed }
    if (repSets.any { it.kg > 0 }) addAll(listOf(Metric.ONE_REP_MAX, Metric.MAX_WEIGHT, Metric.VOLUME))
    else if (repSets.any { (it.reps ?: 0) > 0 }) addAll(listOf(Metric.MAX_REPS, Metric.TOTAL_REPS))
    if (sets.any { it.timed && (it.durationSec ?: 0) > 0 }) addAll(listOf(Metric.LONGEST, Metric.TOTAL_TIME))
}

/** One point per workout, oldest first. Workouts without a value for [metric] are left out. */
fun sessionPoints(sets: List<SetRecord>, metric: Metric, zone: ZoneId = ZoneId.systemDefault()): List<SessionPoint> =
    sets.groupBy { it.sessionId }.values
        .sortedBy { it.first().startedAt }
        .mapNotNull { session ->
            val value = metric.valueOf(session)
            if (value != null && value > 0) SessionPoint(session.first().sessionId, session.first().date(zone), value) else null
        }

/** This metric's value for one workout's sets, or null if it has none. */
fun Metric.valueOf(sets: List<SetRecord>): Double? {
    val repSets = sets.filter { !it.timed }
    val timedSets = sets.filter { it.timed }
    return when (this) {
        Metric.ONE_REP_MAX -> repSets.mapNotNull { estimatedOneRepMax(it.kg, it.reps ?: 0) }.maxOrNull()
        Metric.MAX_WEIGHT -> repSets.filter { (it.reps ?: 0) > 0 }.maxOfOrNull { it.kg }
        Metric.VOLUME -> repSets.sumOf { it.kg * (it.reps ?: 0) }
        Metric.MAX_REPS -> repSets.maxOfOrNull { (it.reps ?: 0).toDouble() }
        Metric.TOTAL_REPS -> repSets.sumOf { (it.reps ?: 0).toDouble() }
        Metric.LONGEST -> timedSets.maxOfOrNull { (it.durationSec ?: 0).toDouble() }
        Metric.TOTAL_TIME -> timedSets.sumOf { (it.durationSec ?: 0).toDouble() }
    }
}

/** A personal best and the workout it was set in. */
data class Record(val metric: Metric, val value: Double, val sessionId: Long, val date: LocalDate)

/** The best value of each available metric over all of [sets] (one exercise). */
fun records(sets: List<SetRecord>, zone: ZoneId = ZoneId.systemDefault()): List<Record> =
    availableMetrics(sets).mapNotNull { metric ->
        sessionPoints(sets, metric, zone).maxByOrNull { it.value }?.let { Record(metric, it.value, it.sessionId, it.date) }
    }

/**
 * The records [sessionId] set for one exercise: metrics where it beat every earlier workout.
 * The first workout with an exercise sets no records (there's nothing to beat).
 */
fun newRecords(sets: List<SetRecord>, sessionId: Long): List<Metric> {
    val session = sets.filter { it.sessionId == sessionId }.ifEmpty { return emptyList() }
    val startedAt = session.first().startedAt
    val earlier = sets.filter { it.startedAt < startedAt }
    if (earlier.isEmpty()) return emptyList()
    // Only "best" metrics count as records; totals depend on how many sets were planned.
    val candidates = listOf(Metric.ONE_REP_MAX, Metric.MAX_WEIGHT, Metric.MAX_REPS, Metric.LONGEST)
    return availableMetrics(session).filter { it in candidates }.filter { metric ->
        val value = metric.valueOf(session) ?: return@filter false
        val best = earlier.groupBy { it.sessionId }.values.mapNotNull { metric.valueOf(it) }.maxOrNull() ?: return@filter false
        value > best + 1e-9
    }.let { found ->
        // Heaviest weight and estimated 1RM often move together: show the clearer one.
        if (Metric.MAX_WEIGHT in found) found - Metric.ONE_REP_MAX else found
    }
}
