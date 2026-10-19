package com.lorenzo.gymtracker.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.lorenzo.gymtracker.domain.Sounds

/**
 * Plays the countdown tick and the end-of-timer bell. They follow the media volume (and play in
 * headphones, even with the phone on silent); music playing at the same time is lowered for a moment.
 */
class TimerSounds(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private val tick: AudioTrack by lazy { track(Sounds.tick()) }
    private val bell: AudioTrack by lazy { track(Sounds.bell()) }

    /** Builds the tracks ahead of time, so the first tick isn't late. */
    fun prepare() {
        tick
        bell
    }

    fun tick() = play(tick)

    fun bell() = play(bell)

    /** Lowers other audio (e.g. music) during the countdown; call [release] after. */
    fun duck() {
        audioManager?.requestAudioFocus(focusRequest)
    }

    fun release() {
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }

    private fun play(track: AudioTrack) {
        // A static track plays its samples once; it has to be rewound to play again.
        track.stop()
        track.reloadStaticData()
        track.play()
    }

    private fun track(samples: ShortArray): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Sounds.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .apply { write(samples, 0, samples.size) }
}
