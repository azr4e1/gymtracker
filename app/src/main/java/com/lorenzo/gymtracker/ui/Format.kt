package com.lorenzo.gymtracker.ui

import com.lorenzo.gymtracker.data.db.entity.SessionSet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Up to one decimal, dropping ".0": 100.0 -> "100", 62.5 -> "62.5". */
fun Double.formatAmount(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/** A weight with up to two decimals, so 1.25 kg plates show: 60.0 -> "60", 43.75 -> "43.75". */
fun Double.formatWeight(): String {
    val rounded = (this * 100).roundToInt() / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/** 0, 950, 1.2K, 15K. */
fun Double.formatCompact(): String = when {
    this < 1000 -> roundToInt().toString()
    this < 10_000 -> "${(this / 100).roundToInt() / 10.0}K".replace(".0K", "K")
    else -> "${(this / 1000).roundToInt()}K"
}

/** A time span in seconds as "0:45", "2:30" or "1:02:03". */
fun formatClock(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** A workout's length as "45 min" or "1 h 05 min". */
fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000).toInt().coerceAtLeast(0)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h %02d min".format(minutes % 60)
}

/** Weights are stored in kg; pounds are converted on the way in and out. */
const val KG_PER_LB = 0.45359237

fun Double.kgToLb(): Double = this / KG_PER_LB

fun Double.lbToKg(): Double = this * KG_PER_LB

/** Parses user input, accepting both "1.5" and "1,5". */
fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** "1 set", "3 sets". */
fun plural(count: Int, one: String, many: String): String = "$count ${if (count == 1) one else many}"

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

fun Long.toLocalDate(): LocalDate = toLocalDateTime().toLocalDate()

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Clock time of an epoch-millisecond timestamp, e.g. "18:05". */
fun Long.formatTime(): String = toLocalDateTime().format(timeFormatter)

/** "Today", "Yesterday", "Mon 13 Oct", or with the year when it's not this year. */
fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))
    }
}

/**
 * Sets in one line: "60 kg × 8, 8, 6" when the weight is the same, "60 × 8, 62.5 × 6" when not,
 * "12, 10 reps" without weight, "0:45, 0:40" for timed sets ("20 kg · 0:45, 0:40" with weight).
 */
fun summarizeSets(sets: List<SessionSet>, timed: Boolean): String {
    if (sets.isEmpty()) return ""
    val weights = sets.map { it.weightKg?.takeIf { w -> w > 0 } }.distinct()
    if (timed) {
        return when {
            weights == listOf(null) -> sets.joinToString(", ") { formatClock(it.durationSec ?: 0) }
            weights.size == 1 -> "${weights[0]!!.formatWeight()} kg · " + sets.joinToString(", ") { formatClock(it.durationSec ?: 0) }
            else -> sets.joinToString(", ") { s -> "${s.weightKg?.formatWeight() ?: "BW"} kg ${formatClock(s.durationSec ?: 0)}" }
        }
    }
    val reps = sets.joinToString(", ") { it.reps?.toString() ?: "–" }
    return when {
        weights == listOf(null) -> "$reps reps"
        weights.size == 1 -> "${weights[0]!!.formatWeight()} kg × $reps"
        else -> sets.joinToString(", ") { s -> "${s.weightKg?.formatWeight() ?: "BW"} × ${s.reps ?: "–"}" }
    }
}
