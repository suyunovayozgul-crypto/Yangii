package com.signal.iptv

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * VLC ham ochib bo'lolmagan kanallar uchun ENG OXIRGI chora — WebView ichida
 * Chromium'ning o'zi (hls.js JavaScript kutubxonasi orqali) ochadi.
 *
 * NEGA KERAK: Chromium'ning HLS/video dekoderi ExoPlayer va VLC'dan farqli
 * yo'l bilan ishlaydi — ba'zan ikkalasi ham rad etgan "notekis" oqimlarni
 * WebView muvaffaqiyatli ochadi (boshqa IPTV ilovalarida ham shu texnika
 * ishlatiladi). Kamchiligi: ba'zi qurilmalarda video tasvir emas, faqat ovoz
 * chiqishi mumkin — shu sabab bu FAQAT oxirgi chora, VLC ham ishlamaganda.
 */
class WebFallbackPlayer(
    private val webView: WebView,
    private val onReady: () -> Unit,
    private val onFailed: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var readyCheckRunnable: Runnable? = null
    private var watchdogRunnable: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun play(url: String, userAgent: String) {
        cancelTimers()
        webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            if (userAgent.isNotBlank()) userAgentString = userAgent
        }
        webView.setBackgroundColor(Color.BLACK)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                // Sahifa yuklandi — video haqiqatan ijro boshlaganini bir
                // necha soniya ichida tekshiramiz (aks holda "ready" deb
                // hisoblab, aslida qora ekranda qolib ketishi mumkin).
                scheduleReadyCheck()
            }
        }
        webView.loadDataWithBaseURL(
            "https://signal.iptv",
            buildHtml(url),
            "text/html",
            "UTF-8",
            null
        )
        scheduleWatchdog()
    }

    private fun scheduleReadyCheck() {
        readyCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            webView.evaluateJavascript(
                "document.getElementById('v') && !document.getElementById('v').paused"
            ) { result ->
                if (result == "true") {
                    watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
                    onReady()
                }
            }
        }
        readyCheckRunnable = runnable
        mainHandler.postDelayed(runnable, 2500)
    }

    private fun scheduleWatchdog() {
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { onFailed("Web HLS: video 9s ichida boshlanmadi") }
        watchdogRunnable = runnable
        mainHandler.postDelayed(runnable, 9000)
    }

    private fun cancelTimers() {
        readyCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    fun stop() {
        cancelTimers()
        webView.stopLoading()
        webView.loadUrl("about:blank")
    }

    private fun buildHtml(streamUrl: String): String {
        return """
            <!DOCTYPE html>
            <html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
            <style>
                html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
                video { position:absolute; left:0; top:0; width:100%; height:100%; background:#000; object-fit:contain; }
            </style>
            </head><body>
            <video id="v" autoplay playsinline webkit-playsinline></video>
            <script>
                var video = document.getElementById('v');
                var url = '$streamUrl';
                if (window.Hls && Hls.isSupported()) {
                    var hls = new Hls({ enableWorker: true, maxBufferLength: 10, liveSyncDurationCount: 3 });
                    hls.on(Hls.Events.MEDIA_ATTACHED, function () { hls.loadSource(url); });
                    hls.on(Hls.Events.MANIFEST_PARSED, function () { video.play().catch(function(){}); });
                    hls.on(Hls.Events.ERROR, function (e, data) {
                        if (data.fatal) {
                            if (data.type === Hls.ErrorTypes.NETWORK_ERROR) hls.startLoad();
                            else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) hls.recoverMediaError();
                        }
                    });
                    hls.attachMedia(video);
                } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                    video.src = url;
                    video.addEventListener('loadedmetadata', function () { video.play(); });
                }
            </script>
            </body></html>
        """.trimIndent()
    }
}
