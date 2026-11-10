package com.earendel.tapbar

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private var projection: MediaProjection? = null
    private var engine: ScreenRecordEngine? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null
    private var outputFile: File? = null
    private var active = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            finishRecording()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
                if (data == null || active) {
                    if (!active) {
                        try {
                            startForegroundNow()
                        } catch (t: Throwable) {
                            Log.e("Tapbar", "Could not enter foreground", t)
                        }
                        stopSelf()
                    }
                } else {
                    beginRecording(code, data)
                }
            }
            ACTION_STOP -> finishRecording()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        recording = false
        if (active) {
            active = false
            val saved = stopEngine()
            releaseProjection()
            closeOutput(saved)
        }
        super.onDestroy()
    }

    private fun beginRecording(code: Int, data: Intent) {
        try {
            startForegroundNow()
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = manager.getMediaProjection(code, data)
            projection = mp
            mp.registerCallback(projectionCallback, handler)
            val fd = openOutput()
            val (rawW, rawH) = ScreenMetrics.sizePx(this)
            val withAudio = prefs.screenRecordAudio && hasAudioPermission()
            val e = ScreenRecordEngine(mp, rawW, rawH, resources.displayMetrics.densityDpi, fd, withAudio)
            engine = e
            e.start()
            active = true
            recording = true
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not start screen recording", t)
            abortRecording()
        }
    }

    private fun finishRecording() {
        if (!active) {
            stopSelf()
            return
        }
        active = false
        recording = false
        val saved = stopEngine()
        releaseProjection()
        closeOutput(saved)
        showToast(if (saved) R.string.record_saved else R.string.record_failed)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun abortRecording() {
        active = false
        recording = false
        stopEngine()
        releaseProjection()
        closeOutput(false)
        showToast(R.string.record_failed)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun stopEngine(): Boolean {
        val e = engine ?: return false
        engine = null
        return try {
            e.stop()
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not stop recording engine", t)
            false
        }
    }

    private fun openOutput(): FileDescriptor {
        val name = "Tapbar_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val pfd: ParcelFileDescriptor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Tapbar")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create video entry")
            outputUri = uri
            pfd = contentResolver.openFileDescriptor(uri, "rw")
                ?: throw IOException("Could not open video entry")
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            dir.mkdirs()
            val file = File(dir, name)
            outputFile = file
            pfd = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            )
        }
        descriptor = pfd
        return pfd.fileDescriptor
    }

    private fun releaseProjection() {
        val mp = projection ?: return
        projection = null
        try {
            mp.unregisterCallback(projectionCallback)
            mp.stop()
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not stop projection", t)
        }
    }

    private fun closeOutput(saved: Boolean) {
        try {
            descriptor?.close()
        } catch (_: Throwable) {
        }
        descriptor = null
        val uri = outputUri
        val file = outputFile
        outputUri = null
        outputFile = null
        try {
            if (uri != null) {
                if (saved) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                        contentResolver.update(uri, values, null, null)
                    }
                } else {
                    contentResolver.delete(uri, null, null)
                }
            } else if (file != null && !saved) {
                file.delete()
            }
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not finalize recording", t)
        }
    }

    private fun showToast(res: Int) {
        try {
            val text = LocaleHelper.wrap(this).getString(res)
            Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }
    }

    private fun startForegroundNow() {
        val lc = LocaleHelper.wrap(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, lc.getString(R.string.record_notif_channel), NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tap)
            .setContentTitle(lc.getString(R.string.record_notif_title))
            .setContentText(lc.getString(R.string.record_notif_text))
            .addAction(0, lc.getString(R.string.action_stop), stopIntent)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "tapbar_screen_record"
        private const val NOTIF_ID = 1002
        private const val ACTION_START = "com.earendel.tapbar.SCREEN_RECORD_START"
        private const val ACTION_STOP = "com.earendel.tapbar.SCREEN_RECORD_STOP"
        private const val EXTRA_CODE = "result_code"
        private const val EXTRA_DATA = "result_data"

        @Volatile
        var recording = false
            private set

        fun toggle(context: Context) {
            if (recording) {
                try {
                    context.startService(Intent(context, ScreenRecordService::class.java).setAction(ACTION_STOP))
                } catch (t: Throwable) {
                    Log.e("Tapbar", "Could not stop screen recording", t)
                }
            } else {
                try {
                    context.startActivity(
                        Intent(context, ScreenRecordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (t: Throwable) {
                    Log.e("Tapbar", "Could not open screen capture request", t)
                }
            }
        }

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenRecordService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                Log.e("Tapbar", "Could not start screen recording service", t)
            }
        }
    }
}
