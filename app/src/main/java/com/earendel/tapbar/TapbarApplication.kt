package com.earendel.tapbar

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

class TapbarApplication : Application() {

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            invalidateAppCache(intent.data?.schemeSpecificPart)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val reattach = Runnable { TapZone.active?.attach() }

    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            TapZone.active?.attach()
            mainHandler.removeCallbacks(reattach)
            mainHandler.postDelayed(reattach, 600L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        val lockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, lockReceiver, lockFilter, ContextCompat.RECEIVER_EXPORTED)
    }
}