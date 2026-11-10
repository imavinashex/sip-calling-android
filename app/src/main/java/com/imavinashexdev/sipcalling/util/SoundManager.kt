package com.imavinashexdev.sipcalling.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object SoundManager {
    private const val TAG = "SoundManager"
    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null

    private val toneExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, 80)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init failed with STREAM_DTMF, trying STREAM_MUSIC", e)
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            } catch (_: Exception) {
            }
        }
    }

    fun playDtmfTone(char: Char) {
        val toneType = when (char) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        toneExecutor.execute {
            try {
                toneGenerator?.startTone(toneType, 60)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play DTMF tone", e)
            }
        }
    }

    @Synchronized
    fun startRingtoneAndVibration(context: Context) {
        cancelVibration(context)
        if (ringtone?.isPlaying == true) {
            return
        }

        try {
            ringtone?.stop()
            ringtone = null
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

            // Play default Android system ringtone only if ringer is normal (not silent/vibrate)
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    play()
                }
            }
            // Vibration completely disabled to prevent loop/stuck issues
        } catch (e: Exception) {
            Log.e(TAG, "Error starting ringtone", e)
        }
    }

    @Synchronized
    fun stopRingtoneAndVibration(context: Context) {
        cancelVibration(context)
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping ringtone", e)
        }
    }

    fun cancelVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.cancel()
            }
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }
}
