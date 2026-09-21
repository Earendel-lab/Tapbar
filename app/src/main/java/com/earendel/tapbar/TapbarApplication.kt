package com.earendel.tapbar

import android.app.Application
import androidx.compose.ui.text.googlefonts.isAvailableOnDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TapbarApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        prewarmFonts()
    }

    private fun prewarmFonts() {
        appScope.launch {
            try {
                provider.isAvailableOnDevice(applicationContext)
            } catch (_: Throwable) {
            }
        }
    }
}
