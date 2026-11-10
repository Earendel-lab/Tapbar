package com.earendel.tapbar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class ScreenRecordActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenRecordService.start(this, result.resultCode, data)
        }
        finish()
    }

    private val audioLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestProjection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val needsAudioPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            Prefs(this).screenRecordAudio &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        if (needsAudioPermission) {
            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            requestProjection()
        }
    }

    private fun requestProjection() {
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        } catch (t: Throwable) {
            Log.e("Tapbar", "Could not request screen capture", t)
            finish()
        }
    }
}
