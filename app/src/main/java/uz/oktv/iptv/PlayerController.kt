package uz.oktv.iptv

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@UnstableApi
class PlayerController(
    private val player: ExoPlayer
) {

    private var currentUrl: String? = null

    fun play(url: String) {
        if (url.isBlank()) {
            player.stop()
            player.clearMediaItems()
            currentUrl = null
            return
        }

        val uri = Uri.parse(url)

        if (currentUrl == url && player.mediaItemCount > 0) {
            player.playWhenReady = true
            player.play()
            return
        }

        currentUrl = url

        player.stop()
        player.clearMediaItems()

        if (url.contains(".m3u8", ignoreCase = true)) {

            val dataSourceFactory =
                DefaultHttpDataSource.Factory()
                    .setUserAgent("MirovoyTV-Player/2.5")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(10000)
                    .setReadTimeoutMs(10000)

            val extractorFactory =
                DefaultHlsExtractorFactory(
                    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                        DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                        DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS,
                    true
                )

            val mediaSource =
                HlsMediaSource.Factory(dataSourceFactory)
                    .setExtractorFactory(extractorFactory)
                    .setAllowChunklessPreparation(false)
                    .createMediaSource(
                        MediaItem.Builder()
                            .setUri(uri)
                            .setLiveConfiguration(
                                MediaItem.LiveConfiguration.Builder()
                                    .setTargetOffsetMs(3000)
                                    .setMaxOffsetMs(6000)
                                    .setMinOffsetMs(1500)
                                    .build()
                            )
                            .build()
                    )

            player.setMediaSource(mediaSource)

        } else {

            val dataSourceFactory =
                DefaultHttpDataSource.Factory()
                    .setUserAgent("MirovoyTV-Mobile-Player-2026")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(10000)
                    .setReadTimeoutMs(10000)

            val mediaSource =
                DefaultMediaSourceFactory(dataSourceFactory)
                    .createMediaSource(
                        MediaItem.fromUri(uri)
                    )

            player.setMediaSource(mediaSource)
        }

        player.playbackParameters =
            androidx.media3.common.PlaybackParameters(1.0f)

        player.prepare()
        player.playWhenReady = true
        player.play()
    }

    fun stop() {
        currentUrl = null
        player.stop()
        player.clearMediaItems()
    }

    fun playCurrent() {
        if (player.mediaItemCount > 0) {
            player.playWhenReady = true
            player.play()
        }
    }
}
