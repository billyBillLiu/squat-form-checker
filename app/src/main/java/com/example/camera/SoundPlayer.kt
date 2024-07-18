package com.example.camera

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundPlayer(context: Context) {
    private val soundPool: SoundPool
    private val soundId: Int

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        soundId = soundPool.load(context, R.raw.snap_sound, 1)
    }

    fun playSound() {
        soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun release() {
        soundPool.release()
    }
}