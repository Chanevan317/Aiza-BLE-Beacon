package com.aiza.app.separation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import com.aiza.app.MainActivity
import com.aiza.app.R
import java.util.Date

/** Left-behind notifications (FR-SEP-02) and the quiet notification of the watching service (FR-SEP-01). */
class AndroidSeparationAlerts(private val context: Context) : SeparationAlerts {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannels(listOf(
            NotificationChannel(ALERT_CHANNEL, "Left-behind alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Warns you when you walk away from a tag."
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(SERVICE_CHANNEL, "Watching tags", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while AIZA keeps your tags connected for left-behind alerts."
                setShowBadge(false)
            },
        ))
    }

    override fun leftBehind(address: String, tagName: String, reason: LeftBehindReason) {
        val time = DateFormat.getTimeFormat(context).format(Date())
        val text = when (reason) {
            LeftBehindReason.LINK_LOST -> "Lost the connection at $time. Tap to see it."
            LeftBehindReason.WEAK_SIGNAL -> "The signal is getting weak. Go back if you still need it."
        }
        manager.notify(
            notificationId(address),
            Notification.Builder(context, ALERT_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("You left $tagName behind")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(openTag(context, address))
                .setAutoCancel(true)
                .build(),
        )
    }

    override fun clear(address: String) = manager.cancel(notificationId(address))

    fun serviceNotification(count: Int): Notification =
        Notification.Builder(context, SERVICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(if (count == 1) "Watching 1 tag" else "Watching $count tags")
            .setContentText("You'll be warned if you leave one behind")
            .setContentIntent(
                PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            .setOngoing(true)
            .build()

    companion object {
        const val SERVICE_NOTIFICATION_ID = 3000
        private const val ALERT_CHANNEL = "left_behind"
        private const val SERVICE_CHANNEL = "watching_tags"

        private fun notificationId(address: String) = 3001 + (address.hashCode() and 0xffff)

        fun openTag(context: Context, address: String): PendingIntent = PendingIntent.getActivity(
            context, address.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAG, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
