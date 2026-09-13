package uz.oktv.iptv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

// ============================================================
// AUDIO TRACK
// ============================================================

@OptIn(UnstableApi::class)
fun switchAudioTrack(
    player: ExoPlayer,
    track: AudioTrackItem
) {
    val groups = player.currentTracks.groups

    if (track.groupIndex < groups.size) {
        val group = groups[track.groupIndex]

        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(
                    TrackSelectionOverride(
                        group.mediaTrackGroup,
                        listOf(track.trackIndexInGroup)
                    )
                )
                .build()
    }
}


// ============================================================
// CREATE REAL EXOPLAYER
// ============================================================

@OptIn(UnstableApi::class)
fun createRealExoPlayer(
    context: Context,
    ffmpegAudio: Boolean,
    amlogicFix: Boolean,
    bufferSeconds: Int
): ExoPlayer {

    // ========================================================
    // RENDERERS
    // ========================================================

    // Mirovoy TV audio fix: NextRenderersFactory adds FFmpeg software
    // decoders (AC-3/E-AC-3/DTS and other streams that some Android devices
    // expose as supported but cannot actually play through the speaker).
    // Prefer the FFmpeg extension so the same stream has a reliable audio
    // path on phones and Smart TVs.
    val renderersFactory =
        NextRenderersFactory(context).apply {
            // TV fix: use hardware decoders first, fall back to FFmpeg
            // (software) only when the platform truly can't decode the
            // stream (e.g. AC-3/DTS audio). PREFER forced software video
            // decoding too, which weaker TV chips can't keep up with —
            // that showed up as playback looking like it runs at ~0.5x.
            setExtensionRendererMode(
                if (ffmpegAudio)
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                else
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            )
            setEnableDecoderFallback(true)
        }


    // ========================================================
    // HTTP
    // ========================================================

    val httpDataSourceFactory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(
                "OKTV-Player/2.5"
            )
            .setAllowCrossProtocolRedirects(
                true
            )
            .setConnectTimeoutMs(
                10000
            )
            .setReadTimeoutMs(
                10000
            )


    // ========================================================
    // MPEG-TS
    // ========================================================

    val extractorsFactory =
        DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                        DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                        DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
            )


    // ========================================================
    // TRACK SELECTOR
    // ========================================================

    val trackSelector =
        DefaultTrackSelector(context).apply {

            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguages(
                        "ru",
                        "uz",
                        "en"
                    )
                    .setExceedAudioConstraintsIfNecessary(
                        true
                    )
                    .setExceedRendererCapabilitiesIfNecessary(
                        true
                    )
                    .setExceedVideoConstraintsIfNecessary(
                        true
                    )
            )
        }


    // ========================================================
    // BUFFER (АДАПТИРОВАНО ПОД КОРОТКОЕ ОКНО СЕРВЕРА ~14-22 СЕК)
    // ========================================================
    // Ранее bufferSeconds и amlogicFix никак не влияли на реальный
    // буфер (значения были жёстко прописаны). Теперь оба параметра
    // реально используются: bufferSeconds задаёт базовый запас, а
    // amlogicFix увеличивает максимальный буфер — это не ускоряет
    // сам декодер (на слабом чипе software-декодирование останется
    // медленным), но даёт плееру больше запаса перед кадрами и
    // заметно снижает частоту рывков/пересборки буфера на слабых
    // ТВ-приставках и медленных (MPEG-2) каналах.
    val minBufferMs = (bufferSeconds.coerceIn(2, 30) * 1000)
    val maxBufferMs = if (amlogicFix) {
        (minBufferMs * 3).coerceAtLeast(18000)
    } else {
        (minBufferMs * 2).coerceAtLeast(8000)
    }
    val playbackStartBufferMs = (minBufferMs / 3).coerceAtLeast(800)
    val playbackAfterRebufferMs = (minBufferMs / 2).coerceAtLeast(1200)

    val loadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                playbackStartBufferMs,
                playbackAfterRebufferMs
            )
            .setBackBuffer(
                0,
                false
            )
            .setPrioritizeTimeOverSizeThresholds(
                true
            )
            .build()


    // ========================================================
    // EXOPLAYER
    // ========================================================

    val player =
        ExoPlayer.Builder(
            context,
            renderersFactory
        )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    context,
                    extractorsFactory
                )
                    .setDataSourceFactory(
                        httpDataSourceFactory
                    )
                    .setLoadErrorHandlingPolicy(
                        DefaultLoadErrorHandlingPolicy(
                            3
                        )
                    )
            )
            .setTrackSelector(
                trackSelector
            )
            .setLoadControl(
                loadControl
            )
            .build()
            .apply {

                repeatMode =
                    Player.REPEAT_MODE_OFF

                playWhenReady =
                    true
            }


    // ========================================================
    // WATCHDOG
    // ========================================================

    val handler =
        Handler(
            Looper.getMainLooper()
        )


    var lastPosition = -1L
    var lastBufferedPosition = -1L

    var lastRenderedVideoFrames = -1

    var positionStuckSeconds = 0
    var bufferStuckSeconds = 0
    var videoStuckSeconds = 0

    var recoveryInProgress = false
    var lastRecoveryTime = 0L


    val diagnosticRunnable =
        object : Runnable {

            override fun run() {

                try {

                    val state =
                        player.playbackState

                    val isPlaying =
                        player.isPlaying

                    val isLoading =
                        player.isLoading

                    val currentPosition =
                        player.currentPosition

                    val bufferedPosition =
                        player.bufferedPosition

                    val totalBuffered =
                        player.totalBufferedDuration


                    val mediaUri =
                        player.currentMediaItem
                            ?.localConfiguration
                            ?.uri
                            ?.toString()
                            ?: "NO_MEDIA"


                    val videoFormat =
                        player.videoFormat?.let {

                            "${it.sampleMimeType} " +
                                    "${it.width}x${it.height} " +
                                    "${it.frameRate}fps"

                        } ?: "NO_VIDEO"


                    val audioFormat =
                        player.audioFormat?.let {

                            "${it.sampleMimeType} " +
                                    "${it.channelCount}ch " +
                                    "${it.sampleRate}Hz"

                        } ?: "NO_AUDIO"


                    // ====================================================
                    // VIDEO DECODER COUNTERS
                    // ====================================================

                    val renderedVideoFrames =
                        player.videoDecoderCounters
                            ?.renderedOutputBufferCount
                            ?: -1


                    val videoFramesDelta =
                        if (
                            lastRenderedVideoFrames >= 0 &&
                            renderedVideoFrames >= 0
                        ) {

                            renderedVideoFrames -
                                    lastRenderedVideoFrames

                        } else {

                            0
                        }


                    // ====================================================
                    // POSITION DELTA
                    // ====================================================

                    val positionDelta =
                        if (
                            lastPosition >= 0
                        ) {

                            currentPosition -
                                    lastPosition

                        } else {

                            0L
                        }


                    // ====================================================
                    // BUFFER DELTA
                    // ====================================================

                    val bufferDelta =
                        if (
                            lastBufferedPosition >= 0
                        ) {

                            bufferedPosition -
                                    lastBufferedPosition

                        } else {

                            0L
                        }


                    Log.d(
                        "OKTV_DIAGNOSTIC",
                        "state=$state | " +
                                "isPlaying=$isPlaying | " +
                                "isLoading=$isLoading | " +
                                "pos=$currentPosition | " +
                                "posDelta=$positionDelta | " +
                                "buffered=$bufferedPosition | " +
                                "bufferDelta=$bufferDelta | " +
                                "totalBuf=$totalBuffered | " +
                                "video=$videoFormat | " +
                                "audio=$audioFormat | " +
                                "renderedFrames=$renderedVideoFrames | " +
                                "frameDelta=$videoFramesDelta"
                    )


                    // ====================================================
                    // WATCHDOG ONLY WHEN PLAYING
                    // ====================================================

                    if (
                        state == Player.STATE_READY &&
                        isPlaying
                    ) {

                        val isHlsSeekJump =
                            positionDelta < -2000 &&
                                    positionDelta > -15000

                        val normalPositionMovement =
                            (positionDelta >= 50 && positionDelta <= 2000) ||
                                    isHlsSeekJump

                        if (normalPositionMovement) {
                            positionStuckSeconds = 0
                        } else {
                            positionStuckSeconds++
                        }


                        val bufferNotMoving =
                            lastBufferedPosition >= 0 &&
                                    abs(bufferDelta) < 50


                        if (
                            bufferNotMoving
                        ) {

                            bufferStuckSeconds++

                        } else {

                            bufferStuckSeconds = 0
                        }


                        val videoExists =
                            player.videoFormat != null &&
                                    player.videoFormat?.width ?: 0 > 0 &&
                                    player.videoFormat?.height ?: 0 > 0


                        if (
                            videoExists &&
                            renderedVideoFrames >= 0
                        ) {

                            if (
                                lastRenderedVideoFrames >= 0 &&
                                videoFramesDelta <= 0
                            ) {

                                videoStuckSeconds++

                            } else {

                                videoStuckSeconds = 0
                            }

                        } else {

                            videoStuckSeconds = 0
                        }


                        val realVideoFreeze =
                            videoExists &&
                                    renderedVideoFrames >= 0 &&
                                    videoStuckSeconds >= 7


                        val realSourceStall =
                            positionStuckSeconds >= 7 &&
                                    bufferStuckSeconds >= 7


                        if (
                            (realVideoFreeze ||
                                    realSourceStall) &&
                            !recoveryInProgress
                        ) {

                            val now =
                                System.currentTimeMillis()


                            if (
                                now -
                                lastRecoveryTime >=
                                30000
                            ) {

                                Log.e(
                                    "OKTV_WATCHDOG",
                                    "REAL HLS PROBLEM DETECTED | " +
                                            "videoFreeze=$realVideoFreeze | " +
                                            "sourceStall=$realSourceStall | " +
                                            "videoStuck=$videoStuckSeconds | " +
                                            "positionStuck=$positionStuckSeconds | " +
                                            "bufferStuck=$bufferStuckSeconds | " +
                                            "pos=$currentPosition | " +
                                            "buffered=$bufferedPosition | " +
                                            "totalBuf=$totalBuffered | " +
                                            "renderedFrames=$renderedVideoFrames | " +
                                            "video=$videoFormat | " +
                                            "audio=$audioFormat | " +
                                            "uri=$mediaUri"
                                )


                                val currentItem =
                                    player.currentMediaItem


                                if (
                                    currentItem != null
                                ) {

                                    recoveryInProgress =
                                        true

                                    lastRecoveryTime =
                                        now


                                    try {

                                        Log.w(
                                            "OKTV_WATCHDOG",
                                            "HARD HLS SOURCE RECOVERY START"
                                        )


                                        player.stop()
                                        player.clearMediaItems()


                                        val freshItem =
                                            MediaItem.Builder()
                                                .setUri(
                                                    currentItem
                                                        .localConfiguration
                                                        ?.uri
                                                )
                                                .build()


                                        player.setMediaItem(
                                            freshItem
                                        )


                                        player.prepare()
                                        player.seekToDefaultPosition()
                                        player.play()


                                        Log.w(
                                            "OKTV_WATCHDOG",
                                            "HARD HLS SOURCE RECOVERY FINISHED"
                                        )

                                    } catch (e: Exception) {

                                        Log.e(
                                            "OKTV_WATCHDOG",
                                            "HLS SOURCE RECOVERY FAILED",
                                            e
                                        )

                                    } finally {

                                        positionStuckSeconds =
                                            0

                                        bufferStuckSeconds =
                                            0

                                        videoStuckSeconds =
                                            0

                                        lastPosition =
                                            -1L

                                        lastBufferedPosition =
                                            -1L

                                        lastRenderedVideoFrames =
                                            -1

                                        recoveryInProgress =
                                            false
                                    }
                                }
                            }
                        }

                    } else {

                        positionStuckSeconds = 0
                        bufferStuckSeconds = 0
                        videoStuckSeconds = 0
                    }


                    lastPosition =
                        currentPosition

                    lastBufferedPosition =
                        bufferedPosition

                    lastRenderedVideoFrames =
                        renderedVideoFrames

                } catch (e: Exception) {

                    Log.e(
                        "OKTV_DIAGNOSTIC",
                        "Diagnostic exception",
                        e
                    )
                }


                handler.postDelayed(
                    this,
                    1000
                )
            }
        }


    handler.postDelayed(
        diagnosticRunnable,
        1000
    )


    // ========================================================
    // PLAYER LISTENER
    // ========================================================

    player.addListener(
        object : Player.Listener {

            override fun onPlayerError(
                error: androidx.media3.common.PlaybackException
            ) {

                Log.e(
                    "OKTV_PLAYER_ERROR",
                    "PLAYER ERROR | " +
                            "code=${error.errorCodeName} | " +
                            "message=${error.message}",
                    error
                )

                if (
                    error.errorCode ==
                    androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW &&
                    !recoveryInProgress
                ) {

                    val now = System.currentTimeMillis()

                    if (now - lastRecoveryTime >= 10000L) {

                        recoveryInProgress = true
                        lastRecoveryTime = now

                        Log.w(
                            "OKTV_WATCHDOG",
                            "BEHIND LIVE WINDOW -> REPREPARE CURRENT SOURCE"
                        )

                        try {

                            player.prepare()
                            player.seekToDefaultPosition()
                            player.playWhenReady = true
                            player.play()

                            Log.w(
                                "OKTV_WATCHDOG",
                                "BEHIND LIVE WINDOW -> REPREPARE PLAY"
                            )

                        } catch (e: Exception) {

                            Log.e(
                                "OKTV_WATCHDOG",
                                "BEHIND LIVE WINDOW -> REPREPARE FAILED",
                                e
                            )

                        } finally {

                            recoveryInProgress = false
                        }

                    } else {

                        Log.w(
                            "OKTV_WATCHDOG",
                            "BEHIND LIVE WINDOW -> COOLDOWN"
                        )
                    }
                }
            }


            override fun onPlaybackStateChanged(
                playbackState: Int
            ) {

                val stateStr =
                    when (playbackState) {

                        Player.STATE_IDLE ->
                            "IDLE"

                        Player.STATE_BUFFERING ->
                            "BUFFERING"

                        Player.STATE_READY ->
                            "READY"

                        Player.STATE_ENDED ->
                            "ENDED"

                        else ->
                            playbackState.toString()
                    }


                Log.d(
                    "OKTV_PLAYER_STATE",
                    "PlaybackState changed to: $stateStr"
                )
            }


            override fun onIsLoadingChanged(
                isLoading: Boolean
            ) {

                Log.d(
                    "OKTV_PLAYER_LOADING",
                    "isLoading=$isLoading | " +
                            "pos=${player.currentPosition} | " +
                            "buffered=${player.bufferedPosition} | " +
                            "totalBuf=${player.totalBufferedDuration}"
                )
            }


            override fun onTracksChanged(
                tracks: androidx.media3.common.Tracks
            ) {

                val videoFormat =
                    player.videoFormat?.let {

                        "${it.sampleMimeType} " +
                                "${it.width}x${it.height}"

                    } ?: "NO_VIDEO"


                val audioFormat =
                    player.audioFormat?.let {

                        "${it.sampleMimeType} " +
                                "${it.channelCount}ch " +
                                "${it.sampleRate}Hz"

                    } ?: "NO_AUDIO"


                Log.d(
                    "OKTV_PLAYER_TRACKS",
                    "video=$videoFormat | " +
                            "audio=$audioFormat"
                )
            }
        }
    )


    // ========================================================
    // INIT LOG
    // ========================================================

    Log.d(
        "OKTV_PLAYER_INIT",
        "Player created | " +
                "FFmpeg=OFF | " +
                "Media3=ON | " +
                "buffer=4-8s | " +
                "backBuffer=0 | " +
                "connectTimeout=10s | " +
                "readTimeout=10s | " +
                "videoWatchdog=7s | " +
                "sourceWatchdog=7s | " +
                "hardHlsRecovery=true | " +
                "liveSeek=DEFAULT | " +
                "amlogicFix=$amlogicFix | " +
                "requestedBufferSeconds=$bufferSeconds"
    )


    return player
}


// ============================================================
// LOGIN CHECK
// ============================================================

fun performLoginCheck(
    token: String,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    onSuccess: (String) -> Unit
) {

    setLoading(true)
    setError(null)

    Thread {

        try {

            val conn =
                URL(
                    "https://oktv.uz/app/api-auth"
                )
                    .openConnection() as HttpURLConnection


            conn.requestMethod =
                "POST"

            conn.doOutput =
                true

            conn.connectTimeout =
                6000

            conn.readTimeout =
                6000


            val postData =
                "token=" +
                        URLEncoder.encode(
                            token,
                            "UTF-8"
                        )


            conn.outputStream.use {
                it.write(
                    postData.toByteArray()
                )
            }


            if (
                conn.responseCode == 200
            ) {

                Handler(
                    Looper.getMainLooper()
                ).post {

                    setLoading(false)

                    onSuccess(
                        token
                    )
                }

            } else {

                Handler(
                    Looper.getMainLooper()
                ).post {

                    setLoading(false)

                    setError(
                        "Ключ не найден или срок подписки истек!"
                    )
                }
            }


            conn.disconnect()

        } catch (e: Exception) {

            Handler(
                Looper.getMainLooper()
            ).post {

                setLoading(false)

                setError(
                    "Ошибка сети! Проверьте подключение к интернету."
                )
            }
        }
    }.start()
}