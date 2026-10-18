package com.earendel.tapbar

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

class KeyboardPickerActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var field: EditText
    private var scheduled = false
    private var pickerShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setLayout(1, 1)
        window.setGravity(Gravity.TOP or Gravity.START)
        window.setDimAmount(0f)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        field = EditText(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        setContentView(field, ViewGroup.LayoutParams(1, 1))
        suppressTransition(false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (pickerShown) {
            close()
            return
        }
        if (scheduled) return
        scheduled = true
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        field.requestFocus()
        imm.showSoftInput(field, 0)
        handler.postDelayed({
            if (isFinishing) return@postDelayed
            pickerShown = true
            imm.showInputMethodPicker()
        }, PICKER_DELAY_MS)
        handler.postDelayed({ close() }, MAX_LIFETIME_MS)
    }

    override fun onStop() {
        super.onStop()
        close()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun close() {
        if (isFinishing) return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(field.windowToken, 0)
        finish()
        suppressTransition(true)
    }

    private fun suppressTransition(closing: Boolean) {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                if (closing) OVERRIDE_TRANSITION_CLOSE else OVERRIDE_TRANSITION_OPEN,
                0,
                0
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val PICKER_DELAY_MS = 200L
        private const val MAX_LIFETIME_MS = 30000L
    }
}
