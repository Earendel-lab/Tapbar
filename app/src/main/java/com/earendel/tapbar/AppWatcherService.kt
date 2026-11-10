package com.earendel.tapbar

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AppWatcherService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private var lastSeenPkg: String? = null
    private var lastQueryTime = 0L

    private val poller = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        lastQueryTime = System.currentTimeMillis() - 4000L
        startForegroundNow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (prefs.hardDisableApps.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(poller)
        handler.post(poller)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        if (prefs.hardDisableActive) {
            restoreAccessibility()
        }
        super.onDestroy()
    }

    private fun checkForeground() {
        try {
            if (prefs.hardDisableApps.isEmpty()) {
                stopSelf()
                return
            }
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(lastQueryTime, now)
            lastQueryTime = now
            var latestFg: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                if (isForegroundEvent) {
                    latestFg = event.packageName
                }
            }
            if (latestFg != null && latestFg != lastSeenPkg) {
                lastSeenPkg = latestFg
                handleForegroundChange(latestFg)
            }
        } catch (t: Throwable) {
            Log.e("Tapbar", "Watcher check failed", t)
        }
    }

    private fun handleForegroundChange(pkg: String) {
        val flagged = prefs.hardDisableApps.contains(pkg)
        if (flagged && !prefs.hardDisableActive) {
            disableAccessibility()
        } else if (!flagged && prefs.hardDisableActive) {
            restoreAccessibility()
        }
    }

    private fun disableAccessibility() {
        try {
            val resolver = contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val enabledFlag = Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
            val entries = current.split(':').filter { it.isNotBlank() }
            val isListed = entries.any { it.equals(expectedFull(), ignoreCase = true) || it.equals(expectedShort(), ignoreCase = true) }
            if (!isListed) return
            val newList = entries.filterNot { it.equals(expectedFull(), ignoreCase = true) || it.equals(expectedShort(), ignoreCase = true) }
                .joinToString(":")
            prefs.hardDisableSavedServices = current
            prefs.hardDisableSavedEnabled = enabledFlag == 1
            applySetting(newList, if (newList.isBlank()) 0 else null) { ok ->
                if (ok) {
                    prefs.hardDisableActive = true
                    showToast(R.string.hide_toast_paused)
                } else {
                    prefs.hardDisableSavedServices = null
                    showToast(R.string.hide_toast_failed)
                }
            }
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not disable accessibility for flagged app", t)
        }
    }

    private fun restoreAccessibility() {
        try {
            val saved = prefs.hardDisableSavedServices
            if (saved == null) {
                prefs.hardDisableActive = false
                return
            }
            applySetting(saved, if (prefs.hardDisableSavedEnabled) 1 else 0) { ok ->
                if (ok) {
                    prefs.hardDisableActive = false
                    prefs.hardDisableSavedServices = null
                }
            }
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not restore accessibility", t)
        }
    }

    private fun applySetting(list: String, enabledFlag: Int?, onDone: (Boolean) -> Unit) {
        if (hasSecureSettings(this)) {
            val written = try {
                Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list)
                if (enabledFlag != null) {
                    Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, enabledFlag)
                }
                true
            } catch (t: Throwable) {
                false
            }
            if (written) {
                onDone(true)
                return
            }
        }
        val cmd = buildString {
            append("settings put secure enabled_accessibility_services ")
            append(shellQuote(list))
            if (enabledFlag != null) {
                append(" && settings put secure accessibility_enabled ")
                append(enabledFlag)
            }
        }
        ShellAccess.run(applicationContext, cmd, prefs.shellMethod) { ok -> onDone(ok) }
    }

    private fun showToast(resId: Int) {
        handler.post { Toast.makeText(applicationContext, resId, Toast.LENGTH_SHORT).show() }
    }

    private fun expectedFull() = "$packageName/${TapAccessibilityService::class.java.name}"
    private fun expectedShort() = "$packageName/.${TapAccessibilityService::class.java.simpleName}"

    private fun shellQuote(v: String) = "'" + v.replace("'", "'\\''") + "'"

    private fun startForegroundNow() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.watcher_channel_name), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.watcher_notification_title))
            .setSmallIcon(R.drawable.ic_stat_tap)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompatStart.start(this, NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "tapbar_watcher"
        private const val NOTIFICATION_ID = 7421
        private const val POLL_INTERVAL_MS = 1000L

        fun hasSecureSettings(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        fun hasUsageAccess(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun syncState(context: Context) {
            val prefs = Prefs(context)
            val intent = Intent(context, AppWatcherService::class.java)
            if (prefs.hardDisableApps.isNotEmpty() && hasUsageAccess(context)) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}

private object ServiceCompatStart {
    fun start(service: Service, id: Int, notification: android.app.Notification) {
        service.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }
}
