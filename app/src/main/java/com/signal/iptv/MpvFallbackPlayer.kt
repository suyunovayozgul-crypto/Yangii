package com.signal.iptv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib

/**
 * ExoPlayer barcha qayta urinishlaridan (soft retry + hard recover) keyin ham
 * ochib bo'lmagan kanallar uchun OXIRGI chora sifatida ishlaydigan zaxira
 * dvigatel — libmpv (FFmpeg) asosida.
 *
 * NEGA KERAK: ExoPlayer'ning MPEG-TS parseri PAT/PMT jadvali kutilgandek
 * kelmagan yoki oqim biroz "notekis" bo'lgan hollarda hech qanday xato
 * bermay, hech qachon STATE_READY'ga ham o'tmay, abadiy BUFFERING holatida
 * qolib ketishi mumkin — garchi baytlar muntazam kelib turgan bo'lsa ham.
 * FFmpeg'ning demuxeri xuddi shunday "notekis" oqimlarga ancha chidamli —
 * VLC, TiviMate kabi ilovalar aynan shu farq tufayli ochadi.
 *
 * MUHIM: bu klass OwnTV loyihasidagi (oldin ko'rib chiqqan) libmpv
 * integratsiyasidan FAQAT YONDASHUV sifatida ilhomlangan — u yerdagi
 * OwnTVPlayer.kt 4600+ qatordan iborat, o'nlab funksiyani (subtitr
 * uslublash, deinterlace, HDR tone-mapping, proxy, va h.k.) qamrab oladi.
 * Bu yerda esa faqat ENG MUHIM qism — libmpv'ni ishga tushirish va bitta
 * havolani ochish — ataylab MINIMAL holda qayta yozilgan, chunki bu klass
 * faqat "oxirgi chora" sifatida, kamdan-kam holatda ishlaydi.
 *
 * @param onReady Video birinchi marta haqiqatan chiqqanda (fayl ochilib,
 *   kadr chizila boshlaganda) chaqiriladi.
 * @param onFailed Ichki watchdog vaqtida ham fayl ochilmasa yoki mpv o'zi
 *   xato bilan tugasa chaqiriladi (sabab matni bilan).
 */
class MpvFallbackPlayer(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val onReady: () -> Unit,
    private val onFailed: (String) -> Unit,
) : MPVLib.EventObserver, SurfaceHolder.Callback {

    private var mpv: MPVLib? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    private var reportedReady = false
    private var pendingSurfaceAttach = false

    init {
        surfaceView.holder.addCallback(this)
    }

    /** Hozir libmpv orqali faol ijro bormi. */
    val isActive: Boolean get() = mpv != null

    /**
     * Berilgan havolani libmpv orqali ochishga urinadi. [WATCHDOG_MS] ichida
     * video chiqmasa — [onFailed] chaqiriladi va bu klass o'zini tozalaydi.
     */
    fun play(url: String, userAgent: String, referer: String) {
        cancelWatchdog()
        reportedReady = false

        val engine = mpv ?: createEngine()
        if (engine == null) {
            onFailed("libmpv ishga tushmadi (qurilma qo'llab-quvvatlamasligi mumkin)")
            return
        }
        mpv = engine

        try {
            engine.setOptionString("user-agent", userAgent)
            if (referer.isNotBlank()) {
                val origin = referer.trimEnd('/')
                engine.setOptionString("http-header-fields", "Referer: $referer,Origin: $origin")
            } else {
                engine.setOptionString("http-header-fields", "")
            }
            val surface = surfaceView.holder.surface
            if (surface != null && surface.isValid) {
                engine.attachSurface(surface)
                pendingSurfaceAttach = false
            } else {
                // Surface hali tayyor emas — surfaceCreated() kelganda o'zi ulanadi.
                pendingSurfaceAttach = true
            }
            engine.command(arrayOf("loadfile", url))
            scheduleWatchdog()
        } catch (e: Exception) {
            Log.w(TAG, "mpv play() xatoligi: ${e.message}")
            onFailed(e.message ?: "libmpv xatoligi")
        }
    }

    /** Ijroni to'xtatadi va (agar so'ralsa) libmpv nusxasini butunlay yo'q qiladi. */
    fun stop(destroy: Boolean) {
        cancelWatchdog()
        val engine = mpv ?: return
        try {
            engine.command(arrayOf("stop"))
            if (destroy) {
                engine.detachSurface()
                engine.destroy()
                mpv = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "mpv stop() xatoligi: ${e.message}")
        }
    }

    private fun createEngine(): MPVLib? {
        return try {
            MPVLib.create(context)?.apply {
                setOptionString("vo", "gpu")
                setOptionString("gpu-context", "android")
                setOptionString("hwdec", "mediacodec-copy")
                setOptionString("ao", "audiotrack")
                setOptionString("force-window", "yes")
                setOptionString("idle", "yes")
                setOptionString("ytdl", "no")
                setOptionString("network-timeout", "15")
                setOptionString("cache", "yes")
                setOptionString("demuxer-max-bytes", "32MiB")
                setOptionString("demuxer-readahead-secs", "10")
                // Ba'zi IPTV panellari zapdan keyin qisqa vaqt 5xx qaytaradi —
                // FFmpeg'ning o'z ichki qayta-ulanishiga ishonamiz.
                setOptionString(
                    "stream-lavf-o",
                    "reconnect=1,reconnect_streamed=1,reconnect_delay_max=5"
                )
                addObserver(this@MpvFallbackPlayer)
            }
        } catch (e: Exception) {
            Log.w(TAG, "mpv yaratilmadi: ${e.message}")
            null
        }
    }

    private fun scheduleWatchdog() {
        val runnable = Runnable {
            if (!reportedReady) {
                Log.w(TAG, "mpv WATCHDOG: $WATCHDOG_MS ms ichida video chiqmadi")
                onFailed("libmpv ham $WATCHDOG_MS ms ichida video bera olmadi")
            }
        }
        watchdogRunnable = runnable
        mainHandler.postDelayed(runnable, WATCHDOG_MS)
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    // === SurfaceHolder.Callback — Surface hayot davri ===

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (pendingSurfaceAttach) {
            try {
                mpv?.attachSurface(holder.surface)
                pendingSurfaceAttach = false
            } catch (e: Exception) {
                Log.w(TAG, "attachSurface xatoligi: ${e.message}")
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        try {
            mpv?.detachSurface()
        } catch (e: Exception) {
            Log.w(TAG, "detachSurface xatoligi: ${e.message}")
        }
        pendingSurfaceAttach = true
    }

    // === MPVLib.EventObserver — mpv hodisalari (mpv'ning O'Z oqimida keladi!) ===

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                reportedReady = true
                cancelWatchdog()
                mainHandler.post { onReady() }
            }
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                if (!reportedReady) {
                    mainHandler.post {
                        cancelWatchdog()
                        onFailed("mpv oqimni ocholmadi (END_FILE)")
                    }
                }
            }
        }
    }

    override fun eventProperty(property: String) {}
    override fun eventProperty(property: String, value: Long) {}
    override fun eventProperty(property: String, value: Boolean) {}
    override fun eventProperty(property: String, value: String) {}
    override fun eventProperty(property: String, value: Double) {}

    companion object {
        private const val TAG = "MpvFallbackPlayer"
        private const val WATCHDOG_MS = 10_000L
    }
}
