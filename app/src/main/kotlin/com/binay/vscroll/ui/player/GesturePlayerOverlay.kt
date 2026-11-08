package com.binay.vscroll.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Left & right edge gesture strips:
 * - Left 20%: vertical drag adjusts screen brightness
 * - Right 20%: vertical drag adjusts media volume
 * - Tap on either edge toggles controls visibility
 */
@Composable
fun BrightnessAndVolumeGestureStrips(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var currentBrightness by remember {
        val initial = activity?.window?.attributes?.screenBrightness
        mutableFloatStateOf(if (initial != null && initial in 0.01f..1.0f) initial else 0.5f)
    }
    var accumulatedVolumeDragY by remember { mutableFloatStateOf(0f) }

    // Restore default system brightness when leaving playback
    DisposableEffect(activity) {
        onDispose {
            activity?.let { act ->
                val lp = act.window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                act.window.attributes = lp
            }
        }
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left 20% Strip: Screen Brightness
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.20f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    playerViewModel.toggleControlsVisibility()
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            activity?.let { act ->
                                val delta = -dragAmount.y / 600f
                                val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                                currentBrightness = newBrightness

                                val lp = act.window.attributes
                                lp.screenBrightness = newBrightness
                                act.window.attributes = lp

                                val percent = (newBrightness * 100).toInt()
                                playerViewModel.showBrightnessHud(percent)
                            }
                        }
                    )
                }
        )

        // Right 20% Strip: Media Volume
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.25f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    playerViewModel.toggleControlsVisibility()
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { accumulatedVolumeDragY = 0f },
                        onDragEnd = { accumulatedVolumeDragY = 0f },
                        onDragCancel = { accumulatedVolumeDragY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedVolumeDragY += -dragAmount.y
                            val sensitivity = 35f // 35dp/px movement per volume step
                            if (kotlin.math.abs(accumulatedVolumeDragY) >= sensitivity) {
                                val steps = (accumulatedVolumeDragY / sensitivity).toInt()
                                accumulatedVolumeDragY -= steps * sensitivity

                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val newVol = (curVol + steps).coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                val percent = if (maxVol > 0) ((newVol.toFloat() / maxVol.toFloat()) * 100).toInt() else 0
                                playerViewModel.showVolumeHud(percent)
                            }
                        }
                    )
                }
        )
    }
}

/**
 * Centered floating HUD indicator showing volume or brightness adjustments.
 */
@Composable
fun GestureHudIndicator(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val gestureHudState by playerViewModel.gestureHudState.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = gestureHudState.isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier
                .width(170.dp)
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                val icon = if (gestureHudState.isVolume) {
                    if (gestureHudState.percent == 0) {
                        Icons.AutoMirrored.Filled.VolumeMute
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    }
                } else {
                    if (gestureHudState.percent < 40) {
                        Icons.Default.BrightnessLow
                    } else if (gestureHudState.percent < 80) {
                        Icons.Default.BrightnessMedium
                    } else {
                        Icons.Default.WbSunny
                    }
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (gestureHudState.isVolume) "Volume" else "Brightness",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${gestureHudState.percent}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { gestureHudState.percent / 100f },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
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
