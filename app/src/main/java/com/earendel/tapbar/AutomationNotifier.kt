package com.earendel.tapbar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicInteger

object AutomationNotifier {

    const val CHANNEL_ID = "tapbar_automation"
    private const val APP_NAME = "TapBar"
    private const val TIMEOUT_MS = 5000L
    private const val ID_BASE = 2000
    private const val ID_SPAN = 1000

    private val counter = AtomicInteger(0)
    private val handler = Handler(Looper.getMainLooper())
    private val gestureNames = arrayOf("Single tap", "Double tap", "Triple tap", "Long press")

    fun message(zone: Int, gesture: Int): String {
        val gestureName = gestureNames.getOrElse(gesture) { gestureNames[0] }
        return "Zone ${zone + 1} - $gestureName"
    }

    fun isReady(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return isReady(nm)
    }

    private fun isReady(nm: NotificationManager): Boolean {
        if (!nm.areNotificationsEnabled()) return false
        val channel = nm.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun openSettings(context: Context) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channelBlocked = nm != null && nm.areNotificationsEnabled() &&
                nm.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
            val intent = if (channelBlocked) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            }
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not open notification settings", t)
        }
    }

    fun fire(context: Context, zone: Int, gesture: Int) {
        try {
            val app = context.applicationContext
            val nm = app.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(app, nm)
            if (!isReady(nm)) {
                openSettings(app)
                return
            }
            val text = message(zone, gesture)
            val id = ID_BASE + Math.floorMod(counter.getAndIncrement(), ID_SPAN)
            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(APP_NAME)
                .setContentText(text)
                .setTicker(text)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setTimeoutAfter(TIMEOUT_MS)
                .build()
            nm.notify(id, notification)
            handler.postDelayed({ nm.cancel(id) }, TIMEOUT_MS + 500L)
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not post automation notification", t)
        }
    }

    private fun ensureChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val name = LocaleHelper.wrap(context).getString(R.string.notif_channel_automation)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }
}
