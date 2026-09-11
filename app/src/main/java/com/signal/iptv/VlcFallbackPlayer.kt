package com.signal.iptv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * ExoPlayer barcha qayta urinishlaridan (referer variantlari + soft retry +
 * hard recover) keyin ham ochib bo'lolmagan kanallar uchun OXIRGI chora
 * sifatida ishlaydigan zaxira dvigatel — libVLC asosida.
 *
 * NEGA KERAK: ExoPlayer'ning Java-da yozilgan HLS/TS parseri ba'zi
 * "notekis" oqimlarda (yoki server Referer'siz so'rovga HTML xato sahifa
 * qaytarganda) buzilgan manifest xatosi berib to'xtaydi. VLC'ning demuxeri
 * xuddi shunday holatlarga ancha chidamli — Televizo va shunga o'xshash
 * ilovalar aynan shu farq tufayli ochadi (bu loyihada sinovda tasdiqlangan).
 *
 * MUHIM: birinchi urinishda bu yerda faqat User-Agent berilib, Referer
 * berilmagan edi — shu sababli Referer talab qiladigan serverlarda VLC ham
 * ExoPlayer bilan bir xil sababdan rad etilgan. Bu versiyada ikkalasi ham
 * (`:http-referrer=`, `:http-user-agent=`) media options orqali beriladi.
 *
 * @param onReady Video birinchi marta haqiqatan chiqqanda (Playing hodisasi)
 *   chaqiriladi.
 * @param onFailed VLC o'zi xato bilan tugasa yoki ichki watchdog vaqtida ham
 *   video boshlanmasa chaqiriladi (sabab matni bilan).
 */
class VlcFallbackPlayer(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val onReady: () -> Unit,
    private val onFailed: (String) -> Unit,
) {
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    private var reportedReady = false

    /** Hozir VLC orqali faol ijro bormi. */
    val isActive: Boolean get() = mediaPlayer != null

    /**
     * Berilgan havolani libVLC orqali ochishga urinadi. [WATCHDOG_MS] ichida
     * video chiqmasa — [onFailed] chaqiriladi.
     */
    fun play(url: String, userAgent: String, referer: String) {
        cancelWatchdog()
        reportedReady = false

        try {
            val vlc = libVLC ?: createEngine() ?: run {
                onFailed("libVLC ishga tushmadi (qurilma qo'llab-quvvatlamasligi mumkin)")
                return
            }
            libVLC = vlc

            mediaPlayer?.release()
            val mp = MediaPlayer(vlc)
            mediaPlayer = mp

            val vout = mp.vlcVout
            vout.setVideoView(surfaceView)
            vout.attachViews()

            val media = Media(vlc, android.net.Uri.parse(url))
            media.addOption(":http-user-agent=$userAgent")
            if (referer.isNotBlank()) {
                media.addOption(":http-referrer=$referer")
            }
            // Ba'zi IPTV panellari zapdan keyin qisqa vaqt uzilib qoladi —
            // VLC'ning o'z ichki qayta-ulanish/keshlash mexanizmiga tayanamiz.
            media.addOption(":network-caching=3000")
            media.addOption(":clock-jitter=0")
            media.addOption(":clock-synchro=0")
            mp.media = media
            media.release()

            mp.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        if (!reportedReady) {
                            reportedReady = true
                            cancelWatchdog()
                            mainHandler.post { onReady() }
                        }
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        mainHandler.post {
                            cancelWatchdog()
                            onFailed("VLC EncounteredError")
                        }
                    }
                }
            }
            mp.play()
            scheduleWatchdog()
        } catch (e: Exception) {
            Log.w(TAG, "vlc play() xatoligi: ${e.message}")
            onFailed(e.message ?: "libVLC xatoligi")
        }
    }

    /** Ijroni to'xtatadi va (agar so'ralsa) libVLC nusxasini butunlay yo'q qiladi. */
    fun stop(destroy: Boolean) {
        cancelWatchdog()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.vlcVout?.detachViews()
            if (destroy) {
                mediaPlayer?.release()
                mediaPlayer = null
                libVLC?.release()
                libVLC = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "vlc stop() xatoligi: ${e.message}")
        }
    }

    private fun createEngine(): LibVLC? {
        return try {
            LibVLC(
                context,
                arrayListOf(
                    "--no-drop-late-frames",
                    "--no-skip-frames",
                    "--rtsp-tcp",
                    "--network-caching=3000"
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "libVLC yaratilmadi: ${e.message}")
            null
        }
    }

    private fun scheduleWatchdog() {
        val runnable = Runnable {
            if (!reportedReady) {
                Log.w(TAG, "vlc WATCHDOG: $WATCHDOG_MS ms ichida video chiqmadi")
                onFailed("VLC ham $WATCHDOG_MS ms ichida video bera olmadi")
            }
        }
        watchdogRunnable = runnable
        mainHandler.postDelayed(runnable, WATCHDOG_MS)
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    companion object {
        private const val TAG = "VlcFallbackPlayer"
        private const val WATCHDOG_MS = 10_000L
    }
}
