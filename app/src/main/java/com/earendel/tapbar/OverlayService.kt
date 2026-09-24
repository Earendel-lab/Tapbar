package com.earendel.tapbar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class OverlayService : Service() {

    private lateinit var prefs: Prefs
    private var controller: TapZoneController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !prefs.serviceEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundSafely()

        if (TapAccessibilityService.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (controller == null) {
            val c = TapZoneController(
                context = this,
                windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                yOffsetPx = TapZoneController.statusBarHeightPx(this),
                onSwipeDown = { expandNotificationPanel() }
            )
            controller = c
            c.attach()
            TapZone.active = c
        }
        controller?.setPreview(TapZone.previewRequested)
        return START_STICKY
    }

    override fun onDestroy() {
        controller?.detach()
        if (TapZone.active === controller) TapZone.active = null
        controller = null
        super.onDestroy()
    }

    private fun expandNotificationPanel() {
        try {
            val statusBarService = getSystemService("statusbar")
            val cls = Class.forName("android.app.StatusBarManager")
            cls.getMethod("expandNotificationsPanel").invoke(statusBarService)
        } catch (t: Throwable) {
            Log.e("Tapbar", "expandNotificationsPanel unavailable")
        }
    }

    private fun startForegroundSafely() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Tapbar overlay", NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
        )
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(this.resources.getIdentifier("ic_stat_tap", "drawable", this.packageName))
            .setContentTitle(getString(this.resources.getIdentifier("notif_title", "string", this.packageName)))
            .setContentText(getString(this.resources.getIdentifier("notif_text", "string", this.packageName)))
            .setContentIntent(openApp)
            .addAction(0, getString(this.resources.getIdentifier("action_stop", "string", this.packageName)), stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        const val ACTION_STOP = "com.earendel.tapbar.STOP"
        private const val CHANNEL_ID = "tapbar_overlay"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, OverlayService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
