package uz.oktv.iptv

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

@Composable
fun ExoPlayerView(
    player: androidx.media3.common.Player,
    resizeMode: Int,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                useController = false
                this.resizeMode = resizeMode
                setBackgroundColor(android.graphics.Color.BLACK)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }

            if (view.resizeMode != resizeMode) {
                view.resizeMode = resizeMode
            }
        },
        modifier = modifier
            .background(Color.Black)
    )
}
