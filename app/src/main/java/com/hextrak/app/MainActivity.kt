package com.hextrak.app

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.hextrak.core.Btn
import com.hextrak.core.NativeAudio
import com.hextrak.core.Tracker
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var tracker: Tracker
    private lateinit var view: TrackerView
    private lateinit var platform: AndroidPlatform

    // Direction sources (D-pad keys, HAT axis, left stick) are merged so they never fight each other.
    private val dirSources = IntArray(4) // bitmask per direction: 1 = key, 2 = hat, 4 = stick
    private val dirBtns = arrayOf(Btn.UP, Btn.DOWN, Btn.LEFT, Btn.RIGHT)
    private var l2Down = false
    private var r2Down = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        System.loadLibrary("hextrak")
        NativeAudio.init(true, 0)

        platform = AndroidPlatform(this)
        tracker = Tracker(NativeAudio, platform)
        val prefs = getSharedPreferences("hextrak", Context.MODE_PRIVATE)
        tracker.swapAB = prefs.getBoolean("swapAB", false)
        tracker.swapXY = prefs.getBoolean("swapXY", false)
        tracker.follow = prefs.getBoolean("follow", false)

        val auto = platform.loadProject("AUTOSAVE")
        if (auto == null || !tracker.loadText(auto)) tracker.loadDemo()

        view = TrackerView(this, tracker)
        setContentView(view)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        NativeAudio.setRunning(true)
        view.start()
    }

    override fun onPause() {
        super.onPause()
        tracker.stopPlayback()
        tracker.autosave()
        getSharedPreferences("hextrak", Context.MODE_PRIVATE).edit()
            .putBoolean("swapAB", tracker.swapAB)
            .putBoolean("swapXY", tracker.swapXY)
            .putBoolean("follow", tracker.follow)
            .apply()
        view.stop()
        NativeAudio.setRunning(false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    // ------------------------------------------------------------------ keys
    private fun mapKey(code: Int): Btn? = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> Btn.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Btn.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Btn.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Btn.RIGHT
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_X -> Btn.A
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_Z -> Btn.B
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_S -> Btn.X
        KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_A -> Btn.Y
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q -> Btn.L1
        KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_W -> Btn.R1
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_1 -> Btn.L2
        KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_2 -> Btn.R2
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> Btn.START
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> Btn.SELECT
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> Btn.BACK
        else -> null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val b = mapKey(event.keyCode) ?: return super.dispatchKeyEvent(event)
        val dirIdx = dirBtns.indexOf(b)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                if (dirIdx >= 0) setDir(dirIdx, 1, true) else tracker.press(b)
            }
            KeyEvent.ACTION_UP -> if (dirIdx >= 0) setDir(dirIdx, 1, false) else tracker.release(b)
        }
        return true
    }

    private fun setDir(i: Int, src: Int, on: Boolean) {
        val before = dirSources[i] != 0
        dirSources[i] = if (on) dirSources[i] or src else dirSources[i] and src.inv()
        val after = dirSources[i] != 0
        if (!before && after) tracker.press(dirBtns[i])
        else if (before && !after) tracker.release(dirBtns[i])
    }

    // ------------------------------------------------------------------ analog: hat, sticks, triggers
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val joystick = event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!joystick || event.action != MotionEvent.ACTION_MOVE) return super.dispatchGenericMotionEvent(event)

        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        setDir(0, 2, hy < -0.5f); setDir(1, 2, hy > 0.5f)
        setDir(2, 2, hx < -0.5f); setDir(3, 2, hx > 0.5f)

        val lx = deadzone(event, MotionEvent.AXIS_X)
        val ly = deadzone(event, MotionEvent.AXIS_Y)
        stickDir(0, ly < 0, abs(ly)); stickDir(1, ly > 0, abs(ly))
        stickDir(2, lx < 0, abs(lx)); stickDir(3, lx > 0, abs(lx))

        // right stick: most pads use Z/RZ, some use RX/RY
        var rx = deadzone(event, MotionEvent.AXIS_Z)
        var ry = deadzone(event, MotionEvent.AXIS_RZ)
        if (rx == 0f && ry == 0f) { rx = deadzone(event, MotionEvent.AXIS_RX); ry = deadzone(event, MotionEvent.AXIS_RY) }
        tracker.stick(rx, ry)

        val lt = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        if (!l2Down && lt > 0.6f) { l2Down = true; tracker.press(Btn.L2) } else if (l2Down && lt < 0.3f) { l2Down = false; tracker.release(Btn.L2) }
        if (!r2Down && rt > 0.6f) { r2Down = true; tracker.press(Btn.R2) } else if (r2Down && rt < 0.3f) { r2Down = false; tracker.release(Btn.R2) }
        return true
    }

    private fun stickDir(i: Int, sideOk: Boolean, mag: Float) {
        val on = dirSources[i] and 4 != 0
        if (!on && sideOk && mag > 0.6f) setDir(i, 4, true)
        else if (on && (!sideOk || mag < 0.35f)) setDir(i, 4, false)
    }

    private fun deadzone(e: MotionEvent, axis: Int): Float {
        val v = e.getAxisValue(axis)
        val flat = e.device?.getMotionRange(axis, e.source)?.flat ?: 0.1f
        return if (abs(v) <= maxOf(flat, 0.15f)) 0f else v
    }
}
