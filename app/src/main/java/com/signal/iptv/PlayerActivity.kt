package com.signal.iptv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import okhttp3.OkHttpClient
import javax.net.ssl.X509TrustManager
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // OkHttp mijozi bitta marta yaratiladi va barcha kanallar (va qayta
    // urinishlar) uchun qayta ishlatiladi — ulanish poolini saqlab qolish
    // uchun bu muhim, har safar yangi OkHttpClient yaratish bu afzallikni
    // yo'qqa chiqaradi.
    // Ko'p bepul IPTV panellari (masalan, ba'zi 4K/tort kanallarni beruvchi
    // serverlar) HTTPS sertifikatini noto'g'ri sozlagan bo'ladi — sertifikat
    // zanjiri to'liq emas yoki o'z-o'ziga imzolangan. Android'ning standart
    // tekshiruvi buni "SSLHandshakeException: Trust anchor for certification
    // path not found" deb rad etadi — bu ExoPlayer'da juda keng tarqalgan,
    // hujjatlashtirilgan muammo (google/ExoPlayer#4383, #5009, #7786) va
    // aynan "ba'zi kanallar ochiladi, ba'zilari ochilmaydi" holatiga to'g'ri
    // keladi (faqat sertifikati chala serverlar ta'sirlanadi). VLC-asosli
    // dasturlar (masalan Televizo) ko'pincha bunday xatolarni e'tiborsiz
    // qoldiradi — biz ham xuddi shunday, faqat video oqimi uchun (boshqa
    // hech qanday tarmoq so'rovi uchun emas), qilamiz.
    private val lenientTrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<javax.net.ssl.TrustManager>(lenientTrustManager), java.security.SecureRandom())
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .sslSocketFactory(sslContext.socketFactory, lenientTrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private var allChannels: List<Channel> = emptyList()
    private var categories: List<String> = emptyList()
    private var currentCategory: String? = null
    private var searchTerm = ""

    private var currentChannel: Channel? = null

    // === Xatolikdan tiklanish holati ===
    // Ko'p bepul IPTV oqimlari vaqti-vaqti bilan segment/ulanish xatoligi beradi.
    // Avval yengil (soft) qayta urinish qilinadi — shunchaki qayta prepare().
    // Bu yordam bermasa, ExoPlayer'ning o'zi butunlay yangidan yaratiladi
    // (native dekoder holati "osilib qolgan" bo'lishi mumkin — xuddi ilovani
    // to'liq qayta ishga tushirgandagi kabi yangi holatga o'tkazadi). Faqat shu
    // ham yordam bermasa, foydalanuvchiga "Qayta urinish" tugmasi ko'rsatiladi.
    private var retryCount: Int = 0
    private var hardRecoverAttempted = false

    // Ba'zi kanallar "titroq" ishlaydi: bir-ikki soniya o'ynaydi, uziladi,
    // yana o'ynaydi... Agar retryCount har bir shunday qisqa "o'ynash"
    // lahzasida DARHOL nolga tushirilsa, hisoblagich hech qachon
    // maxSoftRetries'ga yetmaydi — va foydalanuvchi abadiy "Ulanish
    // tiklanmoqda..." holatida qolib, "Qayta urinish" tugmasini hech qachon
    // ko'rmaydi. Shu sabab hisoblagichni faqat oqim STABLE_PLAYBACK_MS
    // davomida uzluksiz o'ynagandan KEYIN nolga tushiramiz.
    private val stableRetryResetRunnable = Runnable {
        retryCount = 0
        hardRecoverAttempted = false
    }

    // ExoPlayer'ning barcha urinishlari (soft retry + hard recover) tugagach,
    // libmpv zaxira dvigateli har bir kanal uchun FAQAT BIR MARTA sinaladi —
    // aks holda mpv ham muvaffaqiyatsiz bo'lgan kanalda ikkalasi orasida
    // abadiy "aylanib" qolish xavfi bor.
    private var mpvFallbackAttempted = false

    // ExoPlayer'ning watchdog/handlePlaybackError zanjiri mpv muvaffaqiyatli
    // ishga tushgandan keyin ham keyinroq ishga tushib, mpv orqali yaxshi
    // ko'rsatilayotgan videoni "xato" deb bosib qo'ymasligi uchun.
    private var mpvPlaybackActive = false

    // === "Abadiy yuklanish" qo'riqchisi ===
    // Ba'zi o'lik/geo-blok qilingan IPTV serverlar ulanishni na xato beradi,
    // na yopadi — shunchaki hech narsa yubormay, cheksiz "buffering" holatida
    // ushlab turadi. Bunday holatda ExoPlayer'ning onPlayerError() HECH QACHON
    // chaqirilmaydi, shuning uchun pastdagi butun qayta urinish zanjiri
    // ishga tushmay qoladi — aynan shu "qora ekran, abadiy yuklanadi" holatiga
    // sabab bo'lgan joy. Yechim: prepare() chaqirilganda mustaqil taymer
    // boshlaymiz; agar OPEN_TIMEOUT_MS ichida STATE_READY'ga yetmasa — buni
    // xuddi xatolik kelgandek qabul qilib, xuddi shu tiklash zanjirini
    // qo'lda ishga tushiramiz.
    private var openWatchdogRunnable: Runnable? = null
    private val maxSoftRetries: Int = 2

    // Ekran to'ldirish rejimlari orasida aylanish (kichik/katta ekran).
    private val resizeModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    private var resizeModeIndex = 0
    private var isLandscape = true

    private lateinit var channelsOverlay: View
    private lateinit var playerView: PlayerView
    private lateinit var mpvSurfaceView: android.view.SurfaceView
    private var mpvFallback: MpvFallbackPlayer? = null
    private lateinit var overlayGroup: View
    private lateinit var titleView: TextView
    private lateinit var programView: TextView
    private lateinit var nextProgramView: TextView
    private lateinit var statsView: TextView
    private lateinit var streamErrorView: TextView
    private lateinit var retryBtn: Button
    private lateinit var diagnosticsBtn: Button

    // 100% aniq bilish uchun taxmin emas, HAQIQIY ma'lumot kerak: qaysi
    // bosqich (soft retry / hard recover / mpv) yiqilgani, ExoPlayer'ning
    // aynan qaysi xato kodi va — eng muhimi — agar bu HTTP javobi bo'lsa,
    // aynan qaysi status kodi (403, 404, 451 va h.k.) qaytgani.
    private var lastFailStage: String = ""
    private var lastErrorDetail: String = ""
    private lateinit var resizeLabel: TextView
    private lateinit var categoryRail: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var statusText: TextView
    private lateinit var channelRecycler: RecyclerView
    private lateinit var adapter: ChannelAdapter
    private lateinit var gestureDetector: GestureDetector
    private lateinit var playPauseBtn: ImageButton

    private val epgRefreshRunnable = object : Runnable {
        override fun run() {
            updateProgramLabel()
            mainHandler.postDelayed(this, EPG_LABEL_REFRESH_MS)
        }
    }

    // Ekranda ko'rsatiladigan texnik ma'lumot: o'lcham, fps, audio tili —
    // foydalanuvchi "necha fps, qanaqa kanal qaysi tilida ketyapti" deb so'ragan edi.
    private val statsRunnable = object : Runnable {
        override fun run() {
            updateStatsLabel()
            mainHandler.postDelayed(this, STATS_REFRESH_MS)
        }
    }

    // "Muzlab qolish" qo'riqchisi: ba'zi bepul IPTV serverlari oqimni jim
    // to'xtatib qo'yadi — ExoPlayer xato bermaydi, shunchaki pozitsiya
    // ilgarilamay qoladi. Shu tekshiruv har WATCHDOG_INTERVAL_MS'da bir marta
    // pozitsiyani solishtiradi; agar ijro davom etayotgan bo'lsa-yu, pozitsiya
    // qotib qolgan bo'lsa — kanalni jimgina qayta yuklaydi.
    private var lastWatchdogPosition = -1L
    private var lastWatchdogUnchangedCount = 0
    private val stallWatchdogRunnable = object : Runnable {
        override fun run() {
            val exoPlayer = player
            val channel = currentChannel
            if (exoPlayer != null && channel != null && exoPlayer.playWhenReady &&
                exoPlayer.playbackState != Player.STATE_IDLE
            ) {
                val position = exoPlayer.currentPosition
                if (position == lastWatchdogPosition) {
                    lastWatchdogUnchangedCount++
                    if (lastWatchdogUnchangedCount >= 2) {
                        lastWatchdogUnchangedCount = 0
                        preparePlayback(channel)
                    }
                } else {
                    lastWatchdogUnchangedCount = 0
                }
                lastWatchdogPosition = position
            }
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private val hideResizeLabelRunnable = Runnable {
        resizeLabel.animate().alpha(0f).setDuration(200).withEndAction {
            resizeLabel.visibility = View.GONE
        }.start()
    }

    // Overlay (tepadagi panel + pastdagi panel + o'ng tomondagi tugmalar) bir marta
    // bosilganda ko'rinadi/yashiriladi, va harakatsiz qolsa o'zi avtomatik yashiriladi.
    private var overlayVisible = true
    private val hideOverlayRunnable = Runnable { setOverlayVisible(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContentView(R.layout.activity_player)
        hideSystemBars()

        val name = intent.getStringExtra("channel_name") ?: ""
        val url = intent.getStringExtra("channel_url") ?: ""
        // To'liq kanal ma'lumoti (tvg-id, logo, maxsus sarlavhalar) MainActivity'dan
        // shu extra'lar orqali keladi — faqat nom/url o'tkazilsa, birinchi ochilgan
        // kanalning EPG'i va maxsus User-Agent/Referer'i ishlamay qolar edi.
        val initialChannel = Channel(
            name = name,
            url = url,
            logo = intent.getStringExtra("channel_logo") ?: "",
            group = intent.getStringExtra("channel_group") ?: "",
            tvgId = intent.getStringExtra("channel_tvgid") ?: "",
            userAgent = intent.getStringExtra("channel_useragent") ?: "",
            referrer = intent.getStringExtra("channel_referrer") ?: ""
        )

        // MUHIM: ekran bir necha daqiqadan keyin o'zi o'chib/qulflanib qolsa,
        // video to'xtab, "barcha kanallar ishlamay qoldi" holatiga olib kelardi.
        // Shu bayroq bilan bu ekranda turgan vaqtda ekran hech qachon o'chmaydi.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        channelsOverlay = findViewById(R.id.channelsOverlay)
        playerView = findViewById(R.id.playerView)
        mpvSurfaceView = findViewById(R.id.mpvSurfaceView)
        overlayGroup = findViewById(R.id.overlayGroup)
        titleView = findViewById(R.id.playerTitle)
        programView = findViewById(R.id.playerProgram)
        nextProgramView = findViewById(R.id.playerNextProgram)
        statsView = findViewById(R.id.playerStats)
        streamErrorView = findViewById(R.id.playerStreamError)
        retryBtn = findViewById(R.id.playerRetryBtn)
        retryBtn.stateListAnimator = null
        diagnosticsBtn = findViewById(R.id.playerDiagnosticsBtn)
        diagnosticsBtn.stateListAnimator = null
        diagnosticsBtn.setOnClickListener { shareDiagnostics() }
        resizeLabel = findViewById(R.id.playerResizeLabel)
        categoryRail = findViewById(R.id.categoryRail)
        searchBox = findViewById(R.id.playerSearchBox)
        statusText = findViewById(R.id.playerStatusText)
        channelRecycler = findViewById(R.id.playerChannelList)
        playPauseBtn = findViewById(R.id.playerPlayPauseBtn)

        titleView.text = name

        findViewById<ImageButton>(R.id.playerBackBtn).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.playerChannelsBtn).setOnClickListener {
            openChannelsOverlay()
            keepOverlayVisible()
        }
        findViewById<ImageButton>(R.id.channelsCloseBtn).setOnClickListener {
            closeChannelsOverlay()
        }
        findViewById<ImageButton>(R.id.playerResizeBtn).setOnClickListener {
            cycleResizeMode()
            keepOverlayVisible()
        }
        findViewById<ImageButton>(R.id.playerRotateBtn).setOnClickListener {
            toggleOrientation()
            keepOverlayVisible()
        }
        findViewById<ImageButton>(R.id.playerAudioBtn).setOnClickListener {
            showAudioTrackDialog()
            keepOverlayVisible()
        }
        findViewById<ImageButton>(R.id.playerEpgBtn).setOnClickListener {
            showEpgDialog()
            keepOverlayVisible()
        }
        findViewById<ImageButton>(R.id.playerChannelUpBtn).setOnClickListener {
            switchChannel(1)
            keepOverlayVisible()
        }
        findViewById<ImageButton>(R.id.playerChannelDownBtn).setOnClickListener {
            switchChannel(-1)
            keepOverlayVisible()
        }
        playPauseBtn.setOnClickListener {
            togglePlayPause()
            keepOverlayVisible()
        }
        retryBtn.setOnClickListener {
            hardRecover()
            keepOverlayVisible()
        }

        adapter = ChannelAdapter(emptyList()) { channel, _ ->
            playChannel(channel)
            closeChannelsOverlay()
        }
        channelRecycler.layoutManager = LinearLayoutManager(this)
        channelRecycler.adapter = adapter

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchTerm = s?.toString()?.trim()?.lowercase() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        setupPlayer()
        setupGestures()
        playChannel(initialChannel)
        loadChannelsForDrawer()
        scheduleAutoHide()

        EpgRepository.ensureLoaded(MainActivity.EPG_URL) {
            updateProgramLabel()
            adapter.refreshEpgRows()
        }
        mainHandler.postDelayed(epgRefreshRunnable, EPG_LABEL_REFRESH_MS)
        mainHandler.postDelayed(statsRunnable, STATS_REFRESH_MS)
    }

    private fun setupPlayer() {
        // Ko'p bepul IPTV serverlari ExoPlayer'ning standart so'rovini
        // (User-Agent'siz yoki noma'lum User-Agent bilan) rad etadi —
        // brauzerga o'xshash User-Agent va uzunroq timeout beramiz. OkHttp
        // orqali yuboramiz — Televizo kabi ilovalar bilan bir xil tarmoq
        // uslubi, ba'zi serverlar oddiy HttpURLConnection'ni sukut bilan
        // rad etadi.
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        // NextRenderersFactory = DefaultRenderersFactory + FFmpeg software decoders.
        // Ko'p bepul IPTV kanallari AC-3/E-AC-3 audio bilan keladi; standart
        // MediaCodec dekoder buni har doim ham qo'llab-quvvatlamaydi (video ketadi,
        // ovoz esa sukut saqlaydi) — shu factory bo'lsa, ExoPlayer avtomatik ravishda
        // FFmpeg dasturiy dekoderiga o'tadi (avval hardware, keyin fallback sifatida).
        val renderersFactory = NextRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        // Jonli IPTV oqimlar uchun moslashtirilgan bufer: kanal ochilganda
        // tezroq boshlansin, internet birozgina sekinlashsa ham qayta-qayta
        // "yuklanmoqda" chiqavermasin.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs
                50_000, // maxBufferMs
                1_000,  // bufferForPlaybackMs — birinchi kadr shu qadar bufer bilan boshlaydi
                2_000   // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        trackSelector = DefaultTrackSelector(this)

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
        player = exoPlayer
        playerView.player = exoPlayer
        playerView.resizeMode = resizeModes[resizeModeIndex]
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                lastErrorDetail = describeError(error)
                handlePlaybackError()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    cancelOpenWatchdog()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    hideStreamError()
                    cancelOpenWatchdog()
                    mainHandler.postDelayed(stableRetryResetRunnable, STABLE_PLAYBACK_MS)
                } else {
                    mainHandler.removeCallbacks(stableRetryResetRunnable)
                }
                updatePlayPauseIcon()
            }
        })
    }

    /**
     * Oqim xatoligini bosqichma-bosqich tiklash: avval bir necha yengil qayta
     * urinish (tezroq va arzonroq), so'ng playerni to'liq qayta yaratish
     * (og'irroq, lekin "osilib qolgan" holatlarni tuzatadi), va faqat shu ham
     * yordam bermasa — foydalanuvchiga "Qayta urinish" tugmasini ko'rsatish.
     */
    private fun handlePlaybackError() {
        val channel = currentChannel ?: return
        if (retryCount < maxSoftRetries) {
            retryCount++
            lastFailStage = "soft-retry $retryCount/$maxSoftRetries"
            statusHint(getString(R.string.stream_reconnecting))
            mainHandler.postDelayed({ softRetry() }, 1500L * retryCount)
        } else if (!hardRecoverAttempted) {
            hardRecoverAttempted = true
            lastFailStage = "hard-recover"
            statusHint(getString(R.string.stream_reconnecting))
            mainHandler.postDelayed({ hardRecoverSilently() }, 1500L)
        } else if (!mpvFallbackAttempted) {
            mpvFallbackAttempted = true
            lastFailStage = "mpv-fallback"
            statusHint(getString(R.string.stream_reconnecting))
            tryMpvFallback(channel)
        } else {
            lastFailStage = "hammasi tugadi (soft+hard+mpv)"
            showStreamError(channel.name)
        }
    }

    /**
     * ExoPlayer/mpv'ning xato sababini imkon qadar aniq matnga aylantiradi —
     * jumladan HTTP javob kodini (403, 404, 451 va h.k.) sabab zanjiridan
     * qidirib topadi. Bu "taxmin qilish"ni butunlay yo'qotadi: xato matni
     * o'zi aynan nima sodir bo'lganini ko'rsatadi.
     */
    private fun describeError(error: Throwable): String {
        var cause: Throwable? = error
        var httpCode: Int? = null
        var httpMessage: String? = null
        while (cause != null) {
            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                httpCode = cause.responseCode
                httpMessage = cause.responseMessage
            }
            cause = cause.cause
        }
        val base = if (error is PlaybackException) error.errorCodeName else error.javaClass.simpleName
        val rootMsg = generateSequence(error) { it.cause }.lastOrNull()?.message ?: error.message
        return buildString {
            append(base)
            if (httpCode != null) append(" | HTTP $httpCode ${httpMessage ?: ""}".trimEnd())
            if (!rootMsg.isNullOrBlank()) append(" | $rootMsg")
        }
    }

    /**
     * Ekranda ko'rinib turgan kanal + oxirgi xato haqida to'liq texnik matn
     * tayyorlab, Android'ning ulashish oynasini ochadi — foydalanuvchi buni
     * bevosita Telegram/istalgan messenjer orqali yuborishi mumkin. Ekran
     * suratidan farqli o'laroq, bu yerda HAQIQIY xato kodi, ishlatilgan
     * User-Agent/Referer va qaysi bosqichda (soft/hard/mpv) yiqilgani —
     * hammasi matn ko'rinishida, aniq va nusxa ko'chirish mumkin.
     */
    private fun shareDiagnostics() {
        val channel = currentChannel ?: return
        val referer = deriveReferer(channel)
        val userAgent = channel.userAgent.ifBlank { M3UParser.BROWSER_USER_AGENT }
        val report = buildString {
            appendLine("Kanal: ${channel.name}")
            appendLine("Havola: ${channel.url}")
            appendLine("Bosqich: ${lastFailStage.ifBlank { "noma'lum" }}")
            appendLine("Xato: ${lastErrorDetail.ifBlank { "noma'lum" }}")
            appendLine("User-Agent: $userAgent")
            appendLine("Referer: ${referer.ifBlank { "(yo'q)" }}")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.send_diagnostics)))
    }

    /**
     * ExoPlayer taslim bo'lgandan keyingi OXIRGI chora: libmpv (FFmpeg) orqali
     * xuddi shu havolani ochishga urinamiz. Muvaffaqiyatli bo'lsa — mpv'ning
     * o'z sirtini ko'rsatib, ExoPlayer'ni orqaga suramiz. Muvaffaqiyatsiz
     * bo'lsa — odatdagidek "Qayta urinish" xatosi ko'rsatiladi.
     */
    private fun tryMpvFallback(channel: Channel) {
        val userAgent = channel.userAgent.ifBlank { M3UParser.BROWSER_USER_AGENT }
        val referer = deriveReferer(channel)
        val fallback = mpvFallback ?: MpvFallbackPlayer(
            context = this,
            surfaceView = mpvSurfaceView,
            onReady = {
                if (currentChannel?.url == channel.url) {
                    mpvPlaybackActive = true
                    cancelOpenWatchdog()
                    player?.pause()
                    playerView.visibility = View.GONE
                    mpvSurfaceView.visibility = View.VISIBLE
                    hideStreamError()
                }
            },
            onFailed = { reason ->
                android.util.Log.w("PlayerActivity", "mpv fallback ishlamadi: $reason")
                lastErrorDetail = "mpv: $reason"
                mpvPlaybackActive = false
                if (currentChannel?.url == channel.url) {
                    mpvSurfaceView.visibility = View.GONE
                    playerView.visibility = View.VISIBLE
                    showStreamError(channel.name)
                }
            },
        ).also { mpvFallback = it }
        fallback.play(channel.url, userAgent, referer)
    }

    /** mpv fallback'ni to'xtatadi va ExoPlayer sirtini qaytaradi. */
    private fun stopMpvFallback(destroy: Boolean) {
        mpvFallback?.stop(destroy)
        if (destroy) mpvFallback = null
        mpvPlaybackActive = false
        mpvSurfaceView.visibility = View.GONE
        playerView.visibility = View.VISIBLE
    }

    private fun softRetry() {
        val channel = currentChannel ?: return
        preparePlayback(channel)
    }

    /** Avtomatik, foydalanuvchiga ko'rinmaydigan to'liq playerni qayta yaratish. */
    private fun hardRecoverSilently() {
        player?.release()
        setupPlayer()
        retryCount = 0
        val channel = currentChannel ?: return
        preparePlayback(channel)
        updatePlayPauseIcon()
    }

    /** Foydalanuvchi "Qayta urinish" tugmasini bosganda chaqiriladi. */
    private fun hardRecover() {
        hideStreamError()
        retryCount = 0
        hardRecoverAttempted = false
        mpvFallbackAttempted = false
        stopMpvFallback(destroy = false)
        player?.release()
        setupPlayer()
        val channel = currentChannel ?: return
        preparePlayback(channel)
        updatePlayPauseIcon()
    }

    /**
     * Har bir kanal uchun media manbasini ALOHIDA quramiz (umumiy bitta
     * DataSource.Factory o'rniga) — chunki ba'zi IPTV serverlar faqat playlist
     * shu kanal uchun ko'rsatgan maxsus User-Agent yoki Referer bilan kelgan
     * so'rovlarni qabul qiladi (#EXTVLCOPT yoki url|User-Agent=...&Referer=...
     * ko'rinishida beriladi). Boshqa ilova (masalan Televizo) xuddi shu
     * sarlavhalarni o'qib yuboradi, biz ham xuddi shunday qilishimiz kerak —
     * aks holda o'sha kanal umuman ochilmaydi, garchi oqimning o'zi ishlab
     * turgan bo'lsa ham.
     */
    /**
     * Ko'p bepul IPTV serverlari (masalan oktv.uz kabi) so'rov Referer/Origin
     * sarlavhalarisiz kelsa — 403 bilan rad etadi, garchi User-Agent to'g'ri
     * bo'lsa ham. Playlist o'zi Referer ko'rsatmagan bo'lsa, kanalning O'Z
     * domenidan (masalan https://ru.oktv.uz/) sun'iy Referer/Origin yasab
     * yuboramiz — bu ko'plab "faqat brauzerdan kelayotganday" tekshiruvni
     * chetlab o'tadi (Televizo va boshqa ilovalar ham aynan shunday qiladi).
     */
    private fun deriveReferer(channel: Channel): String {
        if (channel.referrer.isNotBlank()) return channel.referrer
        return try {
            val uri = android.net.Uri.parse(channel.url)
            "${uri.scheme}://${uri.host}/"
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Ko'p IPTV panellari (ayniqsa eski Xtream-uslubidagi serverlar) URL'ga
     * ".m3u8" kengaytmasini qo'yadi, lekin aslida haqiqiy HLS playlist
     * o'rniga xom MPEG-TS baytlarini to'g'ridan-to'g'ri oqim sifatida
     * yuboradi. ExoPlayer formatni FAQAT URL kengaytmasiga qarab tanlaydi —
     * shu sabab bunday hollarda noto'g'ri ravishda HLS-parserga yuboradi va
     * "does not start with #EXTM3U" xatosini beradi, garchi oqimning o'zi
     * boshqa (masalan FFmpeg-asosli) pleyerlarda mutlaqo muammosiz ochilsa
     * ham — chunki ular kengaytmaga emas, HAQIQIY dastlabki baytlarga qarab
     * formatni aniqlaydi.
     *
     * Shu funksiya aynan shu narsani qiladi: playback boshlashdan oldin
     * serverdan bir necha kilobayt so'raymiz (Range: bytes=0-2047) va:
     *  - agar javob "#EXTM3U" bilan boshlansa → bu haqiqatan HLS,
     *  - agar birinchi bayt 0x47 (MPEG-TS sinxron baytı) bo'lsa → xom TS,
     *  - aks holda hech narsa demaymiz — standart (kengaytmaga asoslangan)
     *    xatti-harakatga qaytamiz, hozir ishlab turgan kanallarni
     *    buzmaslik uchun.
     *
     * Hidlash 4 soniyadan ortiq davom etsa yoki xato bersa ham xuddi shu —
     * standart xatti-harakatga qaytamiz, hech qachon playback'ni to'xtatib
     * turmaymiz.
     */
    private fun sniffMimeType(url: String, userAgent: String, referer: String): String? {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-2047")
            if (referer.isNotBlank()) {
                requestBuilder
                    .header("Referer", referer)
                    .header("Origin", referer.trimEnd('/'))
            }
            val sniffClient = okHttpClient.newBuilder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build()
            sniffClient.newCall(requestBuilder.build()).execute().use { response ->
                val source = response.body?.source() ?: return null
                val ok = try {
                    source.request(2048)
                } catch (e: Exception) {
                    true // qisqaroq oqim ham bo'lishi mumkin, baribir bor narsani tekshiramiz
                }
                val bytes = source.buffer.snapshot().toByteArray()
                when {
                    bytes.isEmpty() -> null
                    bytes[0] == 0x47.toByte() -> MimeTypes.VIDEO_MP2T
                    String(bytes, Charsets.ISO_8859_1).trimStart().startsWith("#EXTM3U") ->
                        MimeTypes.APPLICATION_M3U8
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun preparePlayback(channel: Channel) {
        val exoPlayer = player ?: return
        val userAgent = channel.userAgent.ifBlank { M3UParser.BROWSER_USER_AGENT }
        val referer = deriveReferer(channel)

        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        // Hidlashni fon oqimida qilamiz — tarmoq so'rovi bo'lgani uchun
        // asosiy (UI) oqimni bloklamasligi kerak.
        executor.execute {
            val sniffedMime = sniffMimeType(channel.url, userAgent, referer)
            mainHandler.post {
                // Shu orada foydalanuvchi boshqa kanalga o'tgan bo'lishi mumkin —
                // bunday holda eski hidlash natijasini qo'llamaymiz.
                if (currentChannel?.url != channel.url) return@post
                val currentExoPlayer = player ?: return@post

                val httpFactory = OkHttpDataSource.Factory(okHttpClient)
                    .setUserAgent(userAgent)
                if (referer.isNotBlank()) {
                    httpFactory.setDefaultRequestProperties(
                        mapOf("Referer" to referer, "Origin" to referer.trimEnd('/'))
                    )
                }

                val mediaItemBuilder = MediaItem.Builder().setUri(channel.url)
                if (sniffedMime != null) {
                    mediaItemBuilder.setMimeType(sniffedMime)
                }
                val mediaSource = DefaultMediaSourceFactory(httpFactory)
                    .createMediaSource(mediaItemBuilder.build())

                currentExoPlayer.setMediaSource(mediaSource)
                currentExoPlayer.prepare()
                currentExoPlayer.playWhenReady = true
                scheduleOpenWatchdog(channel)
            }
        }
    }

    /**
     * Agar OPEN_TIMEOUT_MS ichida shu kanal STATE_READY'ga yetmasa — bu
     * "abadiy yuklanadigan" o'lik oqim degani. ExoPlayer o'zi xato bermasa ham,
     * xuddi xatolik kelgandek qayta tiklash zanjirini (softRetry → hardRecover
     * → xato ko'rsatish) qo'lda ishga tushiramiz.
     */
    private fun scheduleOpenWatchdog(channel: Channel) {
        cancelOpenWatchdog()
        val runnable = Runnable {
            val exoPlayer = player
            if (exoPlayer != null && currentChannel?.url == channel.url &&
                exoPlayer.playbackState != Player.STATE_READY &&
                !mpvPlaybackActive
            ) {
                handlePlaybackError()
            }
        }
        openWatchdogRunnable = runnable
        mainHandler.postDelayed(runnable, OPEN_TIMEOUT_MS)
    }

    private fun cancelOpenWatchdog() {
        openWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        openWatchdogRunnable = null
    }

    private fun statusHint(text: String) {
        streamErrorView.visibility = View.VISIBLE
        streamErrorView.text = text
        retryBtn.visibility = View.GONE
    }

    private fun showStreamError(channelName: String) {
        streamErrorView.visibility = View.VISIBLE
        val base = getString(R.string.stream_error, channelName)
        streamErrorView.text = if (lastErrorDetail.isNotBlank()) "$base\n$lastErrorDetail" else base
        retryBtn.visibility = View.VISIBLE
        diagnosticsBtn.visibility = View.VISIBLE
        keepOverlayVisible()
    }

    private fun hideStreamError() {
        streamErrorView.visibility = View.GONE
        retryBtn.visibility = View.GONE
        diagnosticsBtn.visibility = View.GONE
    }

    /**
     * Video ustida bir marta bosish — overlayni ko'rsatish/yashirish.
     * Tepaga/pastga svayp — jonli efirda menyuga qaytmasdan kanal almashtirish.
     */
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val deltaY = e2.y - e1.y
                val deltaX = e2.x - e1.x
                if (abs(deltaY) > abs(deltaX) && abs(deltaY) > SWIPE_MIN_DISTANCE_PX) {
                    if (deltaY < 0) switchChannel(1) else switchChannel(-1)
                    keepOverlayVisible()
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                setOverlayVisible(!overlayVisible)
                return true
            }
        })
        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    // === Kanallar to'liq ekran paneli ===

    private fun openChannelsOverlay() {
        channelsOverlay.visibility = View.VISIBLE
    }

    private fun closeChannelsOverlay() {
        channelsOverlay.visibility = View.GONE
    }

    private fun isChannelsOverlayOpen(): Boolean = channelsOverlay.visibility == View.VISIBLE

    // === Overlay ko'rsatish / yashirish ===

    private fun setOverlayVisible(visible: Boolean) {
        overlayVisible = visible
        overlayGroup.visibility = if (visible) View.VISIBLE else View.GONE
        mainHandler.removeCallbacks(hideOverlayRunnable)
        if (visible) {
            mainHandler.postDelayed(hideOverlayRunnable, OVERLAY_AUTO_HIDE_MS)
        }
    }

    /** Overlay tugmalaridan biri bosilganda — panel ko'rinishda qolsin va taymer qayta boshlansin. */
    private fun keepOverlayVisible() {
        overlayVisible = true
        overlayGroup.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideOverlayRunnable)
        mainHandler.postDelayed(hideOverlayRunnable, OVERLAY_AUTO_HIDE_MS)
    }

    private fun scheduleAutoHide() {
        mainHandler.removeCallbacks(hideOverlayRunnable)
        mainHandler.postDelayed(hideOverlayRunnable, OVERLAY_AUTO_HIDE_MS)
    }

    private fun togglePlayPause() {
        val exoPlayer = player ?: return
        exoPlayer.playWhenReady = !exoPlayer.playWhenReady
        updatePlayPauseIcon()
    }

    private fun updatePlayPauseIcon() {
        val isPlaying = player?.playWhenReady == true
        playPauseBtn.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        playPauseBtn.contentDescription =
            getString(if (isPlaying) R.string.player_pause_button else R.string.player_play_button)
    }

    /**
     * TV pult tugmalari: DPAD Up/Down va Channel Up/Down bevosita kanal
     * almashtiradi — menyuni ochish shart emas. Bu faqat kanallar oynasi
     * yopiq bo'lganda ishlaydi, aks holda pult ro'yxat ichida yurishi kerak.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !isChannelsOverlayOpen()) {
            when (event.keyCode) {
                // Pultdagi maxsus Channel+/- tugmalari har doim to'g'ridan-to'g'ri
                // kanal almashtiradi — overlay ko'rinsin yoki yo'q, farqi yo'q.
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    switchChannel(1)
                    keepOverlayVisible()
                    return true
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    switchChannel(-1)
                    keepOverlayVisible()
                    return true
                }
                // DPAD Up/Down: overlay yashirin bo'lsa (ya'ni foydalanuvchi shunchaki
                // tomosha qilyapti) — bitta bosishning o'zi bevosita kanal almashtiradi.
                // Overlay ochiq bo'lsa — bu tugmalar pult bilan tugmalar orasida
                // yurish uchun tizimga qoldiriladi (fokusni boshqarish uchun).
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!overlayVisible) {
                        switchChannel(if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) 1 else -1)
                        keepOverlayVisible()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    val focused = currentFocus
                    if (!overlayVisible || focused == null || focused === playerView) {
                        setOverlayVisible(!overlayVisible)
                        return true
                    }
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                    togglePlayPause()
                    keepOverlayVisible()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun switchChannel(direction: Int) {
        if (allChannels.isEmpty()) return
        val current = currentChannel
        val currentIndex = allChannels.indexOfFirst { it.url == current?.url }
        val startIndex = if (currentIndex == -1) 0 else currentIndex
        val nextIndex = ((startIndex + direction) % allChannels.size + allChannels.size) % allChannels.size
        playChannel(allChannels[nextIndex])
    }

    private fun cycleResizeMode() {
        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
        playerView.resizeMode = resizeModes[resizeModeIndex]
        val label = when (resizeModes[resizeModeIndex]) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> getString(R.string.resize_zoom)
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> getString(R.string.resize_fill)
            else -> getString(R.string.resize_fit)
        }
        showResizeLabel(label)
    }

    private fun showResizeLabel(text: String) {
        resizeLabel.text = text
        resizeLabel.visibility = View.VISIBLE
        resizeLabel.alpha = 1f
        mainHandler.removeCallbacks(hideResizeLabelRunnable)
        mainHandler.postDelayed(hideResizeLabelRunnable, 1200)
    }

    private fun toggleOrientation() {
        isLandscape = !isLandscape
        requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    private fun playChannel(channel: Channel) {
        currentChannel = channel
        retryCount = 0
        hardRecoverAttempted = false
        mpvFallbackAttempted = false
        // Har bir yangi kanal ExoPlayer bilan BOSHIDAN boshlanadi — agar
        // oldingi kanalda mpv fallback faol bo'lgan bo'lsa, uni yashirib,
        // ExoPlayer sirtini qaytaramiz.
        stopMpvFallback(destroy = false)
        hideStreamError()
        titleView.text = channel.name
        updateProgramLabel()
        statsView.visibility = View.GONE
        // Eski oqimni to'liq to'xtatib, media ro'yxatini tozalab, keyin yangisini
        // qo'yamiz — shunda avvalgi kanalning dekoder holati keyingi kanalga
        // "yopishib qolmaydi" (aynan shu, ba'zi kanal ishlab turib to'satdan
        // ishlamay qolishining asosiy sababi edi).
        preparePlayback(channel)
        updatePlayPauseIcon()
        adapter.markSelectedByUrl(channel.url)
    }

    private fun updateProgramLabel() {
        val channel = currentChannel ?: return
        val nowPlaying = EpgRepository.currentProgramme(channel.tvgId)
        if (nowPlaying != null && nowPlaying.title.isNotBlank()) {
            programView.visibility = View.VISIBLE
            programView.text = getString(R.string.epg_now_prefix, nowPlaying.title)
        } else {
            programView.visibility = View.GONE
        }

        val next = EpgRepository.nextProgramme(channel.tvgId)
        if (next != null && next.title.isNotBlank()) {
            nextProgramView.visibility = View.VISIBLE
            val time = timeFormat.format(java.util.Date(next.start))
            nextProgramView.text = getString(R.string.epg_next_prefix, "$time — ${next.title}")
        } else {
            nextProgramView.visibility = View.GONE
        }
    }

    /** Ekranda o'lcham, fps va joriy audio tilini ko'rsatadi. */
    private fun updateStatsLabel() {
        val exoPlayer = player
        if (exoPlayer == null) {
            statsView.visibility = View.GONE
            return
        }
        val video = exoPlayer.videoFormat
        val audio = exoPlayer.audioFormat
        val parts = mutableListOf<String>()
        if (video != null && video.width > 0 && video.height > 0) {
            parts.add("${video.width}×${video.height}")
            if (video.frameRate > 0f) {
                parts.add("${Math.round(video.frameRate)}fps")
            }
        }
        if (audio != null) {
            val lang = audio.language?.uppercase(Locale.US)
            if (!lang.isNullOrBlank()) parts.add(lang)
        }
        if (parts.isEmpty()) {
            statsView.visibility = View.GONE
        } else {
            statsView.visibility = View.VISIBLE
            statsView.text = parts.joinToString("  ·  ")
        }
    }

    /** Joriy kanalning mavjud audio treklari (tillari) orasidan tanlash oynasi. */
    private fun showAudioTrackDialog() {
        val exoPlayer = player ?: return
        val audioGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.isEmpty()) {
            Toast.makeText(this, R.string.audio_track_none, Toast.LENGTH_SHORT).show()
            return
        }

        val labels = mutableListOf(getString(R.string.audio_track_auto))
        val entries = mutableListOf<Pair<androidx.media3.common.Tracks.Group, Int>?>(null)
        audioGroups.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language?.uppercase(Locale.US)
                val label = format.label ?: lang ?: getString(R.string.audio_track_unknown)
                labels.add(label)
                entries.add(group to i)
            }
        }

        AlertDialog.Builder(this, R.style.SignalDialogTheme)
            .setTitle(R.string.audio_track_dialog_title)
            .setItems(labels.toTypedArray()) { dialog, which ->
                val entry = entries[which]
                trackSelector.parameters = if (entry == null) {
                    trackSelector.parameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .build()
                } else {
                    val (group, index) = entry
                    trackSelector.parameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                        .build()
                }
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Joriy kanalning to'liq dastur jadvali — faqat "hozir" emas, balki
     * o'tgan va kelasi ko'rsatuvlar ham. Foydalanuvchi aynan shuni so'ragan edi.
     */
    private fun showEpgDialog() {
        val channel = currentChannel ?: return
        val schedule = EpgRepository.fullSchedule(channel.tvgId)

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt()
        container.setPadding(pad, pad, pad, pad)

        if (schedule.isEmpty()) {
            val empty = TextView(this)
            empty.text = getString(R.string.epg_no_data)
            empty.setTextColor(ContextCompat.getColor(this, R.color.muted))
            empty.textSize = 13f
            container.addView(empty)
        } else {
            val now = System.currentTimeMillis()
            schedule.forEach { programme ->
                val isNow = now in programme.start until programme.stop
                val row = TextView(this)
                val time = timeFormat.format(java.util.Date(programme.start))
                row.text = "$time   ${programme.title}"
                row.setPadding(0, pad / 2, 0, pad / 2)
                row.textSize = 13f
                row.setTextColor(
                    ContextCompat.getColor(this, if (isNow) R.color.amber else R.color.text)
                )
                row.setTypeface(row.typeface, if (isNow) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                container.addView(row)
            }
        }

        val scroll = ScrollView(this)
        scroll.addView(container)

        AlertDialog.Builder(this, R.style.SignalDialogTheme)
            .setTitle(getString(R.string.epg_dialog_title, channel.name))
            .setView(scroll)
            .setPositiveButton(R.string.epg_close) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private var drawerFetchRetryCount = 0
    private val drawerFetchRetryDelaysMs = longArrayOf(2000, 4000, 8000, 15000, 30000)

    /** MUHIM: avval bitta urinish yetarli emas edi — internet birozgina qoqilib
     * qolsa, kanal ro'yxati (drawer) abadiy bo'sh qolar edi. Endi bir necha marta
     * qayta urinadi. */
    private fun loadChannelsForDrawer() {
        executor.execute {
            try {
                val channels = M3UParser.fetch(MainActivity.PLAYLIST_URL)
                mainHandler.post {
                    drawerFetchRetryCount = 0
                    onChannelsLoaded(channels)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (drawerFetchRetryCount < drawerFetchRetryDelaysMs.size) {
                        val delay = drawerFetchRetryDelaysMs[drawerFetchRetryCount]
                        drawerFetchRetryCount++
                        mainHandler.postDelayed({ loadChannelsForDrawer() }, delay)
                    } else {
                        statusText.visibility = View.VISIBLE
                        statusText.text = getString(R.string.load_error, e.message ?: "")
                    }
                }
            }
        }
    }

    private fun onChannelsLoaded(channels: List<Channel>) {
        if (allChannels.isNotEmpty() && channels.size < allChannels.size / 2) {
            if (drawerFetchRetryCount < drawerFetchRetryDelaysMs.size) {
                val delay = drawerFetchRetryDelaysMs[drawerFetchRetryCount]
                drawerFetchRetryCount++
                mainHandler.postDelayed({ loadChannelsForDrawer() }, delay)
            }
            return
        }
        allChannels = channels
        categories = channels.map { it.group.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        buildTabs()
        applyFilter()
        adapter.markSelectedByUrl(currentChannel?.url ?: "")

        // To'liq ro'yxat yuklangач joriy kanalning mos yozuvini topib, uning
        // tvg-id/guruh ma'lumotini sinxronlaymiz (ijro davom etadi, faqat EPG
        // yorlig'i to'g'ri ko'rinishi uchun) — playback qayta boshlanmaydi.
        val current = currentChannel
        if (current != null) {
            val match = channels.firstOrNull { it.url == current.url }
            if (match != null && match.tvgId != current.tvgId) {
                currentChannel = match
                updateProgramLabel()
            }
        }
    }

    /**
     * Televizo uslubidagi chap ustun: toifalar tik ro'yxat holida, har biri
     * to'liq kenglikda bosiladigan qator — gorizontal chip qatoridan farqli
     * o'laroq, ko'p toifa bo'lsa ham hammasi tik skroll bilan ko'rinadi.
     */
    private fun buildTabs() {
        categoryRail.removeAllViews()
        categoryRail.addView(makeCategoryRow(getString(R.string.category_all), currentCategory == null) {
            currentCategory = null
            buildTabs()
            applyFilter()
        })
        categories.forEach { cat ->
            categoryRail.addView(makeCategoryRow(cat.uppercase(), currentCategory == cat) {
                currentCategory = cat
                buildTabs()
                applyFilter()
            })
        }
    }

    private fun makeCategoryRow(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        val row = TextView(this)
        row.text = label
        row.textSize = 12.5f
        val padH = (16 * resources.displayMetrics.density).toInt()
        val padV = (13 * resources.displayMetrics.density).toInt()
        row.setPadding(padH, padV, padH, padV)
        row.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.amber else R.color.text)
        )
        row.setBackgroundColor(
            ContextCompat.getColor(this, if (selected) R.color.panel2 else android.R.color.transparent)
        )
        row.isClickable = true
        row.isFocusable = true
        row.maxLines = 2
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        row.setOnClickListener { onClick() }
        return row
    }

    private fun applyFilter() {
        var filtered = if (currentCategory == null) {
            allChannels
        } else {
            allChannels.filter { it.group.equals(currentCategory, ignoreCase = true) }
        }
        if (searchTerm.isNotEmpty()) {
            filtered = filtered.filter { it.name.lowercase().contains(searchTerm) }
        }

        if (filtered.isEmpty()) {
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.no_channels)
        } else {
            statusText.visibility = View.GONE
            adapter.updateData(filtered)
            adapter.markSelectedByUrl(currentChannel?.url ?: "")
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onBackPressed() {
        if (isChannelsOverlayOpen()) {
            closeChannelsOverlay()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * MUHIM: onStop() playerni to'liq bo'shatadi (quyida), lekin oldin bu playerni
     * qayta tiklaydigan hech narsa yo'q edi — shuning uchun ilova fonga ketib
     * qaytganda (ekran o'chib-yonganda, boshqa ilova ustiga chiqib qaytganda va h.k.)
     * video hech qachon qayta boshlanmas edi, va faqat ilovani butunlay yopib qayta
     * ochish yordam berardi. Endi onStart() playerni yo'q bo'lsa qayta yaratadi va
     * joriy kanalni qayta ishga tushiradi.
     */
    override fun onStart() {
        super.onStart()
        if (player == null) {
            setupPlayer()
            currentChannel?.let { channel ->
                preparePlayback(channel)
                updatePlayPauseIcon()
            }
        }
        if (allChannels.isEmpty() && drawerFetchRetryCount == 0) {
            loadChannelsForDrawer()
        }
        lastWatchdogPosition = -1L
        lastWatchdogUnchangedCount = 0
        mainHandler.removeCallbacks(stallWatchdogRunnable)
        mainHandler.postDelayed(stallWatchdogRunnable, WATCHDOG_INTERVAL_MS)
    }

    override fun onStop() {
        super.onStop()
        mainHandler.removeCallbacks(stallWatchdogRunnable)
        cancelOpenWatchdog()
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        mpvFallback?.stop(destroy = true)
        mpvFallback = null
    }

    companion object {
        private const val SWIPE_MIN_DISTANCE_PX = 120
        private const val EPG_LABEL_REFRESH_MS = 30_000L
        private const val OVERLAY_AUTO_HIDE_MS = 4_000L
        private const val STATS_REFRESH_MS = 2_000L
        private const val WATCHDOG_INTERVAL_MS = 8_000L
        // Shu qadar vaqt ichida kanal STATE_READY'ga yetmasa — "abadiy
        // yuklanadigan" o'lik oqim deb qabul qilinadi va tiklash zanjiri
        // qo'lda ishga tushiriladi.
        private const val OPEN_TIMEOUT_MS = 12_000L
        private const val STABLE_PLAYBACK_MS = 5_000L
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    }
}
