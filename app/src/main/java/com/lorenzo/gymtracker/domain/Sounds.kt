package com.lorenzo.gymtracker.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/** Synthesised timer sounds as 16-bit mono PCM, so the app needs no audio files. */
object Sounds {
    const val SAMPLE_RATE = 44_100

    /** A short wooden "tock" for each of the last seconds of a countdown. */
    fun tick(): ShortArray = render(0.06) { t ->
        val envelope = exp(-t / 0.012)
        envelope * (0.7 * sin(2 * PI * 1_600 * t) + 0.3 * sin(2 * PI * 2_900 * t))
    }

    /**
     * A boxing bell: three quick strikes of a metal bell. A struck bell's overtones aren't whole
     * multiples of its pitch and fade faster the higher they are, which is what makes it ring.
     */
    fun bell(): ShortArray {
        val strikes = listOf(0.0, 0.30, 0.60)
        // (frequency ratio, loudness, seconds to fade to ~37%)
        val partials = listOf(
            Triple(1.0, 1.0, 0.9),
            Triple(2.32, 0.55, 0.5),
            Triple(4.25, 0.35, 0.3),
            Triple(6.63, 0.2, 0.18),
        )
        val pitch = 830.0
        return render(strikes.last() + 1.8) { t ->
            strikes.filter { t >= it }.sumOf { start ->
                val s = t - start
                // A sharp metallic click at the moment of the strike.
                val click = exp(-s / 0.003) * sin(2 * PI * 5_200 * s) * 0.4
                click + partials.sumOf { (ratio, loudness, decay) -> loudness * exp(-s / decay) * sin(2 * PI * pitch * ratio * s) }
            }
        }
    }

    /** Samples [seconds] of [wave] (a function of time), scaled so the loudest point is at 80%. */
    private fun render(seconds: Double, wave: (Double) -> Double): ShortArray {
        val values = DoubleArray((seconds * SAMPLE_RATE).toInt()) { wave(it.toDouble() / SAMPLE_RATE) }
        val peak = values.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
        // Fade out the last 10 ms so the sound never ends with a click.
        val fade = SAMPLE_RATE / 100
        return ShortArray(values.size) { i ->
            val tail = ((values.size - i).toDouble() / fade).coerceAtMost(1.0)
            (values[i] / peak * 0.8 * tail * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
