package com.binay.vscroll.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Manages full-screen sticky immersive mode:
 * - Hides status bar and navigation bar when enabled.
 * - Allows transient reveal on swipe from edge.
 * - Restores status and navigation bars on dispose or when disabled.
 */
@Composable
fun SystemUiImmersiveMode(enabled: Boolean = true) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity, enabled) {
        setSystemBarsImmersive(activity?.window, enabled)
        onDispose {
            setSystemBarsImmersive(activity?.window, false)
        }
    }
}

private fun setSystemBarsImmersive(window: Window?, immersive: Boolean) {
    if (window == null) return
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    if (immersive) {
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        insetsController.show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun Context.findActivity(): Activity? {
    var cur = this
    while (cur is ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    return null
}

