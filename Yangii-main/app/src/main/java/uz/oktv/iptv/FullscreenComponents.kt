package uz.oktv.iptv

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun BoxScope.FullscreenGestureControls(
    context: Context,
    contentMode: AppContentMode,
    isArchivePlaying: Boolean,
    fullscreenControlText: String?,
    fullscreenControlProgress: Float,
    onControlTextChange: (String?) -> Unit,
    onControlProgressChange: (Float) -> Unit
) {
    if (contentMode != AppContentMode.VOD && !isArchivePlaying) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var startX = 0f

                    var brightnessValue = 0.5f
                    var brightnessRemainder = 0f
                    var volumeRemainder = 0f

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            startX = offset.x
                            brightnessRemainder = 0f
                            volumeRemainder = 0f

                            if (startX < size.width / 2f) {
                                val activity = context as? android.app.Activity

                                val current =
                                    activity?.window?.attributes?.screenBrightness ?: -1f

                                brightnessValue =
                                    if (current in 0f..1f) {
                                        current
                                    } else {
                                        0.5f
                                    }
                            }
                        },

                        onVerticalDrag = { change, dragAmount ->
                            change.consume()

                            if (startX < size.width / 2f) {

                                brightnessRemainder +=
                                    -dragAmount / size.height.toFloat()

                                if (abs(brightnessRemainder) >= 0.006f) {

                                    brightnessValue = (
                                            brightnessValue + brightnessRemainder
                                            ).coerceIn(0.05f, 1f)

                                    brightnessRemainder = 0f

                                    val activity =
                                        context as? android.app.Activity

                                    activity?.window?.let { window ->
                                        val params = window.attributes
                                        params.screenBrightness =
                                            brightnessValue
                                        window.attributes = params
                                    }

                                    onControlProgressChange(
                                        brightnessValue
                                    )

                                    onControlTextChange(
                                        "☀ ${((brightnessValue * 100f).toInt()).coerceIn(0, 100)}%"
                                    )
                                }

                            } else {

                                val audioManager =
                                    context.getSystemService(
                                        Context.AUDIO_SERVICE
                                    ) as? AudioManager

                                if (audioManager != null) {

                                    val maxVolume =
                                        audioManager.getStreamMaxVolume(
                                            AudioManager.STREAM_MUSIC
                                        )

                                    volumeRemainder +=
                                        (-dragAmount / size.height.toFloat()) *
                                                maxVolume * 1.5f

                                    val step =
                                        volumeRemainder.toInt()

                                    if (step != 0) {

                                        volumeRemainder -= step

                                        val currentVolume =
                                            audioManager.getStreamVolume(
                                                AudioManager.STREAM_MUSIC
                                            )

                                        val newVolume =
                                            (currentVolume + step)
                                                .coerceIn(0, maxVolume)

                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            newVolume,
                                            0
                                        )

                                        val progress =
                                            if (maxVolume > 0) {
                                                newVolume.toFloat() /
                                                        maxVolume.toFloat()
                                            } else {
                                                0f
                                            }

                                        onControlProgressChange(
                                            progress
                                        )

                                        onControlTextChange(
                                            "🔊 ${((progress * 100f).toInt()).coerceIn(0, 100)}%"
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
        )
    }

    AnimatedVisibility(
        visible = fullscreenControlText != null,
        enter = fadeIn(),
        exit = fadeOut(),

        modifier = Modifier
            .align(
                if (fullscreenControlText?.startsWith("☀") == true)
                    Alignment.CenterStart
                else
                    Alignment.CenterEnd
            )
            .padding(horizontal = 28.dp)
    ) {

        val isBrightness =
            fullscreenControlText?.startsWith("☀") == true

        Box(
            modifier = Modifier
                .width(82.dp)
                .height(250.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xCC080D18))
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.18f),
                    RoundedCornerShape(24.dp)
                )
                .padding(vertical = 18.dp),

            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = if (isBrightness) "☀" else "🔊",
                    color = Color.White,
                    fontSize = 30.sp
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = fullscreenControlText ?: "",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Box(
                    modifier = Modifier
                        .width(9.dp)
                        .height(135.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            Color.White.copy(alpha = 0.18f)
                        )
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(
                                fullscreenControlProgress
                                    .coerceIn(0f, 1f)
                            )
                            .align(Alignment.BottomCenter)
                            .clip(
                                RoundedCornerShape(99.dp)
                            )
                            .background(
                                Color(0xFF38BDF8)
                            )
                    )
                }
            }
        }
    }

    LaunchedEffect(
        fullscreenControlText,
        fullscreenControlProgress
    ) {
        if (fullscreenControlText != null) {
            delay(1200)
            onControlTextChange(null)
        }
    }
}