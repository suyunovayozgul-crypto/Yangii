package com.signal.iptv

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.GestureDetector

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import okhttp3.OkHttpClient
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
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            // mpv-android (ffmpeg asosida) va ko'p boshqa IPTV pleyerlar
            // faqat HTTP/1.1 ishlatadi — OkHttp esa server qo'llasa HTTP/2'ga
            // avtomatik o'tadi. Ba'zi IPTV-server/CDN'lar HTTP/2'da mijozga
            // qarab (ayniqsa qayta ishlatiladigan ulanishlarda) noto'g'ri/
            // buzilgan javob qaytarishi mumkin. Shuni chetlab o'tish uchun
            // faqat HTTP/1.1'ga majburlaymiz — bu boshqa ishlayotgan
            // pleyerlar (mpv, curl) qanday ulanayotganiga yaqinlashtiradi.
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
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
    private var lastErrorMessage: String = ""

    // Playlist ba'zi kanallar uchun Referer ko'rsatmagan bo'lsa, ExoPlayer buni
    // avtomatik "taxmin qilishi" kerak — lekin BITTA taxmin (masalan, oqimning
    // o'z domeni) har doim to'g'ri kelavermaydi: ba'zi serverlar (oktv.uz kabi)
    // playlist joylashgan "portal" saytini kutadi, ba'zilari umuman hech qanday
    // Referer kutmaydi. Shuning uchun bir nechta variantni NAVBAT bilan sinab
    // ko'ramiz — refererAttemptIndex shu navbatdagi o'rinni bildiradi va har bir
    // muvaffaqiyatsiz urinishdan keyin (retryCount hisobiga kirmasdan) bittaga
    // oshiriladi, referererCandidates() ro'yxati tugagach oddiy qayta urinish
    // zanjiriga (softRetry/hardRecover) o'tadi.
    private var refererAttemptIndex = 0

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
    private lateinit var overlayGroup: View
    private lateinit var titleView: TextView
    private lateinit var programView: TextView
    private lateinit var nextProgramView: TextView
    private lateinit var statsView: TextView
    private lateinit var streamErrorView: TextView
    private lateinit var retryBtn: Button
    private lateinit var mpvSurfaceView: SurfaceView
    // Faqat ExoPlayer BARCHA urinishlaridan (referer variantlari + soft + hard
    // recover) keyin ham ochib bo'lmagan kanalda, oxirgi chora sifatida
    // yaratiladi (lazy) — chunki libmpv'ni ishga tushirish o'zi biroz og'ir,
    // aksariyat kanallarda umuman kerak bo'lmaydi.
    private var mpvFallbackPlayer: MpvFallbackPlayer? = null
    private var mpvActive = false
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
        overlayGroup = findViewById(R.id.overlayGroup)
        titleView = findViewById(R.id.playerTitle)
        programView = findViewById(R.id.playerProgram)
        nextProgramView = findViewById(R.id.playerNextProgram)
        statsView = findViewById(R.id.playerStats)
        streamErrorView = findViewById(R.id.playerStreamError)
        retryBtn = findViewById(R.id.playerRetryBtn)
        mpvSurfaceView = findViewById(R.id.mpvSurfaceView)
        retryBtn.stateListAnimator = null
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
        // PREFER (ON emas): FFmpeg dasturiy dekoderi HAR DOIM birinchi sinaladi,
        // platform passthrough decoderidan oldin. Ba'zi telefonlar AC-3/E-AC-3'ni
        // "qo'llab-quvvatlayman" deb signal beradi-yu, aslida faqat passthrough
        // (SPDIF/HDMI ARC ulangan TV/resiver kutadi) — telefon dinamigida esa hech
        // narsa eshitilmaydi, garchi ExoPlayer buni xato deb hisoblamasa ham. PREFER
        // bu holatni butunlay chetlab o'tadi: hamma AC-3/E-AC-3 kanal endi har doim
        // dasturiy decode qilinadi, qurilmadan qat'i nazar bir xil natija beradi.
        val renderersFactory = NextRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

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
                // Xatoning aniq sababi (403, timeout, kodek, DNS va h.k.) shu yerda
                // yo'qolib qolmasligi uchun logga yozamiz va ekranga chiqarish
                // uchun saqlab qo'yamiz — aks holda faqat umumiy "ulanish
                // tiklanmoqda" matni ko'rinadi va sababni bilib bo'lmaydi.
                // "Source error" kabi umumiy xabar yetarli emas — cause zanjirini
                // qazib, HTTP status kodi (masalan 403) yoki serverning aniq javob
                // matni (masalan HTML xato sahifasi) bor bo'lsa, shuni chiqaramiz.
                lastErrorMessage = "${error.errorCodeName}: ${describeRootCause(error)}"
                val ch = currentChannel
                val usedReferer = ch?.let { refererCandidates(it).getOrNull(refererAttemptIndex) } ?: "?"
                Log.e(
                    "PlayerError",
                    "${ch?.name} url=${ch?.url} referer=\"$usedReferer\": $lastErrorMessage",
                    error
                )
                handlePlaybackError()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> playbackState.toString()
                }
                Log.d("PlayerDebug", "onPlaybackStateChanged -> $stateName (${currentChannel?.name})")
                if (playbackState == Player.STATE_READY) {
                    cancelOpenWatchdog()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    retryCount = 0
                    hardRecoverAttempted = false
                    hideStreamError()
                    cancelOpenWatchdog()
                }
                updatePlayPauseIcon()
            }
        })

        // DIAGNOSTIKA: qaysi dekoder (hardware MediaCodec yoki dasturiy FFmpeg)
        // ishlatilayotganini Logcat'ga yozib boradi — "adb logcat -s DecoderCheck"
        // bilan ko'rish mumkin. Nomida "ffmpeg" so'zi bo'lsa — FFmpeg dasturiy
        // dekoder ishlagan, aks holda — telefonning o'z (hardware) dekoderi.
        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                Log.d("DecoderCheck", "AUDIO decoder: $decoderName (${currentChannel?.name})")
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                Log.d("DecoderCheck", "VIDEO decoder: $decoderName (${currentChannel?.name})")
            }

            // TASHXIS: manifest/segment yuklashda (ExoPlayer ichki HTTP
            // qatlamida) yuz bergan xatoni to'g'ridan-to'g'ri ushlab, sababini
            // Logcat'ga yozadi — bu onPlayerError() chaqirilishidan OLDIN,
            // ba'zan undan ancha batafsil (masalan aniq qaysi segment, necha
            // marta qayta urinilgani) ma'lumot beradi.
            override fun onLoadError(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
                error: java.io.IOException,
                wasCanceled: Boolean
            ) {
                Log.e(
                    "ExoPlayerLoadError",
                    "${currentChannel?.name} uri=${loadEventInfo.uri} " +
                        "wasCanceled=$wasCanceled: ${error.message}",
                    error
                )
            }
        })
    }

    /**
     * Oqim xatoligini bosqichma-bosqich tiklash: avval bir necha yengil qayta
     * urinish (tezroq va arzonroq), so'ng playerni to'liq qayta yaratish
     * (og'irroq, lekin "osilib qolgan" holatlarni tuzatadi), va faqat shu ham
     * yordam bermasa — foydalanuvchiga "Qayta urinish" tugmasini ko'rsatish.
     */
    /**
     * ExoPlayer'ning "Source error" kabi umumiy xabari ortida ko'pincha aniqroq
     * sabab yashiringan bo'ladi: masalan HttpDataSource.InvalidResponseCodeException
     * ichida haqiqiy HTTP status kodi (403, 404...) va serverning javob tanasi
     * (ko'pincha HTML xato sahifasi) bor. cause zanjirini pastga tushib, shularni
     * topib, bitta o'qiladigan matnga aylantiramiz — shunda "nima yetishmayapti"
     * taxmin emas, aniq fakt bo'ladi.
     */
    private fun describeRootCause(error: PlaybackException): String {
        var cause: Throwable? = error
        var deepest: Throwable = error
        while (cause != null) {
            deepest = cause
            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                val body = try {
                    cause.responseBody?.toString(Charsets.UTF_8)?.take(200) ?: ""
                } catch (e: Exception) { "" }
                return "HTTP ${cause.responseCode} url=${cause.dataSpec.uri}" +
                    if (body.isNotBlank()) " body=\"${body.replace("\n", " ")}\"" else ""
            }
            cause = cause.cause
        }
        return deepest.message ?: error.message ?: "noma'lum xato"
    }

    private fun handlePlaybackError() {
        val channel = currentChannel ?: return
        // Referer variantlari hali tugamagan bo'lsa — keyingisini darhol
        // (retryCount hisobiga kirmasdan) sinab ko'ramiz. Ko'p hollarda muammo
        // aynan noto'g'ri taxmin qilingan Referer/Origin bo'ladi — server buni
        // "bot"likka o'xshab rad etib, HTML xato sahifasini qaytaradi (bu esa
        // "buzilgan manifest" xatosiga olib keladi).
        val candidateCount = refererCandidates(channel).size
        if (refererAttemptIndex < candidateCount - 1) {
            refererAttemptIndex++
            statusHint(getString(R.string.stream_reconnecting))
            mainHandler.postDelayed({ preparePlayback(channel) }, 500L)
            return
        }
        if (retryCount < maxSoftRetries) {
            retryCount++
            statusHint(getString(R.string.stream_reconnecting))
            mainHandler.postDelayed({ softRetry() }, 1500L * retryCount)
        } else if (!hardRecoverAttempted) {
            hardRecoverAttempted = true
            statusHint(getString(R.string.stream_reconnecting))
            mainHandler.postDelayed({ hardRecoverSilently() }, 1500L)
        } else if (!mpvActive) {
            // ExoPlayer'ning barcha imkoniyatlari (referer variantlari, soft
            // retry, hard recover) tugadi — endi FFmpeg demuxeriga asoslangan
            // libmpv'ni oxirgi chora sifatida sinaymiz.
            tryMpvFallback(channel)
        } else {
            showStreamError(channel.name)
        }
    }

    /**
     * ExoPlayer hech qanday urinishdan keyin ham ochib bo'lolmagan kanal uchun
     * OXIRGI chora — libmpv (FFmpeg demuxeri) orqali sinab ko'radi. Bu ham
     * muvaffaqiyatsiz bo'lsagina, foydalanuvchiga "Qayta urinish" ekrani
     * ko'rsatiladi.
     */
    private fun tryMpvFallback(channel: Channel) {
        mpvActive = true
        statusHint(getString(R.string.stream_reconnecting))
        player?.pause()
        playerView.visibility = View.INVISIBLE
        mpvSurfaceView.visibility = View.VISIBLE

        val userAgent = channel.userAgent.ifBlank { M3UParser.BROWSER_USER_AGENT }
        val referer = refererCandidates(channel).firstOrNull { it.isNotBlank() } ?: ""

        val fallback = mpvFallbackPlayer ?: MpvFallbackPlayer(
            context = this,
            surfaceView = mpvSurfaceView,
            onReady = {
                hideStreamError()
            },
            onFailed = { reason ->
                Log.e("PlayerError", "${channel.name}: mpv ham ocholmadi — $reason")
                mpvSurfaceView.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                showStreamError(channel.name)
            }
        ).also { mpvFallbackPlayer = it }

        fallback.play(channel.url, userAgent, referer)
    }

    /** ExoPlayer'ga qaytishdan oldin (yangi kanal yoki qo'lda qayta urinishda)
     * faol mpv ijrosini to'xtatib, ko'rinishni playerView'ga qaytaradi. */
    private fun stopMpvFallback() {
        if (!mpvActive) return
        mpvActive = false
        mpvFallbackPlayer?.stop(destroy = false)
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
        stopMpvFallback()
        retryCount = 0
        hardRecoverAttempted = false
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
    /**
     * Sinab ko'riladigan Referer variantlari, eng ehtimolidan boshlab:
     *   1) Playlist o'zi ko'rsatgan Referer (bo'lsa) — eng ishonchli, chunki
     *      playlist muallifi buni maxsus shu kanal uchun ko'rsatgan.
     *   2) Referer'siz — curl bilan tasdiqlangan: oktv.uz kabi serverlar
     *      Referer'siz so'rovga to'g'ri javob beradi. Buni ATAYLAB ikkinchi
     *      o'ringa qo'ydik (avval EMAS, domen taxminlaridan OLDIN) — chunki
     *      noto'g'ri taxmin qilingan Referer serverni sekinlashtirib yoki
     *      butunlay bloklab, vaqtni behuda sarflashi mumkin edi.
     *   3-4) Domen taxminlari — faqat yuqoridagilar ishlamasa, oxirgi chora
     *      sifatida sinaladi.
     */
    private fun refererCandidates(channel: Channel): List<String> {
        val list = mutableListOf<String>()
        if (channel.referrer.isNotBlank()) list.add(channel.referrer)
        list.add("")
        try {
            val portalUri = android.net.Uri.parse(MainActivity.PLAYLIST_URL)
            list.add("${portalUri.scheme}://${portalUri.host}/")
        } catch (e: Exception) { /* e'tiborsiz qoldiriladi */ }
        try {
            val uri = android.net.Uri.parse(channel.url)
            list.add("${uri.scheme}://${uri.host}/")
        } catch (e: Exception) { /* e'tiborsiz qoldiriladi */ }
        return list.distinct()
    }

    private fun preparePlayback(channel: Channel) {
        val exoPlayer = player ?: return
        val userAgent = channel.userAgent.ifBlank { M3UParser.BROWSER_USER_AGENT }
        val candidates = refererCandidates(channel)
        val referer = candidates[refererAttemptIndex.coerceIn(0, candidates.size - 1)]
        Log.d("PlayerDebug", "preparePlayback boshlandi: url=${channel.url} referer=\"$referer\" ua=\"$userAgent\"")
        try {
            val httpFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent(userAgent)
            // Server "Range" so'ralganda OkHttp'ning avtomatik gzip-ochish
            // mexanizmi ishlamay qoladi (OkHttp buni faqat Range yo'q paytda
            // qiladi) — agar server shunda ham siqilgan javob yuborsa, ExoPlayer
            // xom gzip baytlarini matn deb o'qib "#EXTM3U bilan boshlanmayapti"
            // xatosini beradi. Buni butunlay oldini olish uchun serverdan hech
            // qachon siqilgan javob so'ramaymiz.
            val headers = mutableMapOf("Accept-Encoding" to "identity")
            if (referer.isNotBlank()) {
                headers["Referer"] = referer
                // Origin — HTTP standartiga ko'ra FAQAT "sxema://host[:port]"
                // bo'lishi kerak, yo'l (path) qismisiz. referer.trimEnd('/')
                // agar referer'da yo'l bo'lsa (masalan "https://site.uz/sahifa/")
                // buni noto'g'ri "https://site.uz/sahifa" qilib qoldirar edi —
                // ba'zi serverlar buni tekshirib, mos kelmasa rad etadi.
                headers["Origin"] = try {
                    val uri = android.net.Uri.parse(referer)
                    "${uri.scheme}://${uri.host}"
                } catch (e: Exception) {
                    referer.trimEnd('/')
                }
            }
            httpFactory.setDefaultRequestProperties(headers)
            val mediaSource = DefaultMediaSourceFactory(httpFactory)
                .createMediaSource(MediaItem.fromUri(channel.url))

            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            Log.d("PlayerDebug", "prepare() chaqirildi, holat hozir: ${exoPlayer.playbackState}")
            scheduleOpenWatchdog(channel)
        } catch (e: Exception) {
            // Agar shu blok ichida biror narsa (masalan noto'g'ri URL formati)
            // kutilmagan xato bersa — bu avvalgi kodda BUTUNLAY yashirin qolar
            // edi (na onPlayerError, na watchdog ushlaydi, chunki hech qachon
            // shu funksiyaning oxiriga yetib bormaydi). Endi buni ham ochiq
            // ko'rsatamiz.
            lastErrorMessage = "PREPARE_EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
            Log.e("PlayerError", "preparePlayback xato berdi: url=${channel.url}", e)
            handlePlaybackError()
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
                exoPlayer.playbackState != Player.STATE_READY
            ) {
                // Bu yerda ExoPlayer hech qanday xato bermagan — shunchaki
                // OPEN_TIMEOUT_MS ichida STATE_READY'ga yetmadi. Sababsiz
                // "hozircha mavjud emas" deb chiqaverish o'rniga, ExoPlayer'ning
                // o'sha paytdagi holatini (IDLE/BUFFERING/ENDED, va hozirgача
                // qancha ma'lumot yuklab olingani) yozib qo'yamiz — shu orqali
                // "server umuman javob bermayapti"mi yoki "javob keladi-yu, juda
                // sekin/tugamayapti"mi ekanini ajratib bo'ladi.
                val stateName = when (exoPlayer.playbackState) {
                    Player.STATE_IDLE -> "IDLE (hali so'rov boshlanmagan yoki tugagan)"
                    Player.STATE_BUFFERING -> "BUFFERING (ma'lumot kutilmoqda)"
                    Player.STATE_ENDED -> "ENDED"
                    else -> exoPlayer.playbackState.toString()
                }
                val loadedMs = exoPlayer.bufferedPosition
                val isLoading = exoPlayer.isLoading
                lastErrorMessage = "WATCHDOG_TIMEOUT: $OPEN_TIMEOUT_MS ms ichida STATE_READY'ga yetmadi. " +
                    "holat=$stateName, isLoading=$isLoading, bufferedPositionMs=$loadedMs"
                Log.e(
                    "PlayerError",
                    "${channel.name} url=${channel.url} referer=\"${refererCandidates(channel).getOrNull(refererAttemptIndex)}\": $lastErrorMessage"
                )
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
        val detail = if (lastErrorMessage.isNotBlank()) "\n$lastErrorMessage" else ""
        streamErrorView.text = getString(R.string.stream_error, channelName) + detail
        retryBtn.visibility = View.VISIBLE
        keepOverlayVisible()
    }

    private fun hideStreamError() {
        streamErrorView.visibility = View.GONE
        retryBtn.visibility = View.GONE
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
        refererAttemptIndex = 0
        stopMpvFallback()
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
        mpvFallbackPlayer?.stop(destroy = true)
        mpvFallbackPlayer = null
        mpvActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
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
        // Log dalillari shuni ko'rsatdiki, ba'zi kanallar (masalan AC-3 audio
        // bilan keladiganlar) 12 soniyada emas, ancha ko'proq vaqtda READY
        // holatiga yetadi — 12s ichida allaqachon 20s+ video buferga yig'ilgan
        // bo'lsa ham hali READY emas edi. Shuning uchun soqchi vaqtini
        // oshiramiz, aks holda ishlayotgan oqim erta "xato" deb e'lon qilinadi.
        private const val OPEN_TIMEOUT_MS = 30_000L
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    }
}
