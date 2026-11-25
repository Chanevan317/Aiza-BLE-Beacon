package com.aiza.app.ring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import com.aiza.app.AizaApplication
import com.aiza.app.MainActivity
import com.aiza.app.R

/**
 * Plays the ring as an alarm so it is heard even when the ringer is silent, vibrates, and posts a
 * full-screen notification that opens the ring screen over the lock screen (FR-RNG-02).
 * If the alarm volume is low it is raised to [MIN_VOLUME] for the ring and put back afterwards.
 */
class AndroidRingOutput(private val context: Context) : RingOutput {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var player: MediaPlayer? = null
    private var restoreVolume: Int? = null

    init {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Tag rings this phone", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When you press the button on your tag, the phone rings so you can find it."
                setSound(null, null) // the app plays the sound itself, as an alarm
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    override fun start(ring: PhoneRing) = main.post {
        stopNow(cancelNotification = false)
        raiseVolume()
        player = runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        notifications.notify(NOTIFICATION_ID, notification(ring))
    }.let {}

    override fun stop() = main.post { stopNow(cancelNotification = true) }.let {}

    private fun stopNow(cancelNotification: Boolean) {
        player?.runCatching { stop(); release() }
        player = null
        vibrator().cancel()
        restoreVolume?.let { v -> runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        restoreVolume = null
        if (cancelNotification) notifications.cancel(NOTIFICATION_ID)
    }

    private fun raiseVolume() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val now = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val target = (max * MIN_VOLUME).toInt().coerceAtLeast(1)
        if (now < target) runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0); restoreVolume = now }
    }

    private fun vibrator() = context.getSystemService(VibratorManager::class.java).defaultVibrator

    private fun notification(ring: PhoneRing): Notification {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getBroadcast(
            context, 1, Intent(context, StopRingReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("${ring.tagName} is ringing your phone")
            .setContentText("Someone pressed the button on your tag")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setDeleteIntent(stop)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "phone_ring"
        private const val NOTIFICATION_ID = 2001
        private const val MIN_VOLUME = 0.7f
    }
}

/** Stop button in the ring notification. */
class StopRingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as AizaApplication).container.phoneRinger.stop()
    }
}
