package com.signal.iptv

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
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
import androidx.drawerlayout.widget.DrawerLayout
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

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
    private val maxSoftRetries: Int = 2

    // Ekran to'ldirish rejimlari orasida aylanish (kichik/katta ekran).
    private val resizeModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    private var resizeModeIndex = 0
    private var isLandscape = true

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var playerView: PlayerView
    private lateinit var overlayGroup: View
    private lateinit var titleView: TextView
    private lateinit var programView: TextView
    private lateinit var nextProgramView: TextView
    private lateinit var statsView: TextView
    private lateinit var streamErrorView: TextView
    private lateinit var retryBtn: Button
    private lateinit var resizeLabel: TextView
    private lateinit var tabsRow: LinearLayout
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

        drawerLayout = findViewById(R.id.playerDrawerLayout)
        playerView = findViewById(R.id.playerView)
        overlayGroup = findViewById(R.id.overlayGroup)
        titleView = findViewById(R.id.playerTitle)
        programView = findViewById(R.id.playerProgram)
        nextProgramView = findViewById(R.id.playerNextProgram)
        statsView = findViewById(R.id.playerStats)
        streamErrorView = findViewById(R.id.playerStreamError)
        retryBtn = findViewById(R.id.playerRetryBtn)
        retryBtn.stateListAnimator = null
        resizeLabel = findViewById(R.id.playerResizeLabel)
        tabsRow = findViewById(R.id.playerTabsRow)
        searchBox = findViewById(R.id.playerSearchBox)
        statusText = findViewById(R.id.playerStatusText)
        channelRecycler = findViewById(R.id.playerChannelList)
        playPauseBtn = findViewById(R.id.playerPlayPauseBtn)

        titleView.text = name

        findViewById<ImageButton>(R.id.playerBackBtn).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.playerChannelsBtn).setOnClickListener {
            drawerLayout.openDrawer(Gravity.END)
            keepOverlayVisible()
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
            drawerLayout.closeDrawers()
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
        playChannel(Channel(name = name, url = url))
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
        // brauzerga o'xshash User-Agent va uzunroq timeout beramiz.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

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
                handlePlaybackError()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    retryCount = 0
                    hardRecoverAttempted = false
                    hideStreamError()
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
            statusHint(getString(R.string.stream_reconnecting))
            mainHandler.postDelayed({ softRetry() }, 1500L * retryCount)
        } else if (!hardRecoverAttempted) {
            hardRecoverAttempted = true
            statusHint(getString(R.string.stream_reconnecting))
            mainHandler.postDelayed({ hardRecoverSilently() }, 1500L)
        } else {
            showStreamError(channel.name)
        }
    }

    private fun softRetry() {
        val channel = currentChannel ?: return
        val exoPlayer = player ?: return
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(MediaItem.fromUri(channel.url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /** Avtomatik, foydalanuvchiga ko'rinmaydigan to'liq playerni qayta yaratish. */
    private fun hardRecoverSilently() {
        player?.release()
        setupPlayer()
        retryCount = 0
        val channel = currentChannel ?: return
        val exoPlayer = player ?: return
        exoPlayer.setMediaItem(MediaItem.fromUri(channel.url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        updatePlayPauseIcon()
    }

    /** Foydalanuvchi "Qayta urinish" tugmasini bosganda chaqiriladi. */
    private fun hardRecover() {
        hideStreamError()
        retryCount = 0
        hardRecoverAttempted = false
        player?.release()
        setupPlayer()
        val channel = currentChannel ?: return
        val exoPlayer = player ?: return
        exoPlayer.setMediaItem(MediaItem.fromUri(channel.url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        updatePlayPauseIcon()
    }

    private fun statusHint(text: String) {
        streamErrorView.visibility = View.VISIBLE
        streamErrorView.text = text
        retryBtn.visibility = View.GONE
    }

    private fun showStreamError(channelName: String) {
        streamErrorView.visibility = View.VISIBLE
        streamErrorView.text = getString(R.string.stream_error, channelName)
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
        if (event.action == KeyEvent.ACTION_DOWN && !drawerLayout.isDrawerOpen(Gravity.END)) {
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
        hideStreamError()
        titleView.text = channel.name
        updateProgramLabel()
        statsView.visibility = View.GONE
        val exoPlayer = player ?: return
        // Eski oqimni to'liq to'xtatib, media ro'yxatini tozalab, keyin yangisini
        // qo'yamiz — shunda avvalgi kanalning dekoder holati keyingi kanalga
        // "yopishib qolmaydi" (aynan shu, ba'zi kanal ishlab turib to'satdan
        // ishlamay qolishining asosiy sababi edi).
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(MediaItem.fromUri(channel.url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
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

        AlertDialog.Builder(this)
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

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.epg_dialog_title, channel.name))
            .setView(scroll)
            .setPositiveButton(R.string.epg_close) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun loadChannelsForDrawer() {
        executor.execute {
            try {
                val channels = M3UParser.fetch(MainActivity.PLAYLIST_URL)
                mainHandler.post { onChannelsLoaded(channels) }
            } catch (e: Exception) {
                mainHandler.post {
                    statusText.visibility = View.VISIBLE
                    statusText.text = getString(R.string.load_error, e.message ?: "")
                }
            }
        }
    }

    private fun onChannelsLoaded(channels: List<Channel>) {
        allChannels = channels
        categories = channels.map { it.group.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        buildTabs()
        applyFilter()
        adapter.markSelectedByUrl(currentChannel?.url ?: "")
    }

    private fun buildTabs() {
        tabsRow.removeAllViews()
        tabsRow.addView(makeTabButton(getString(R.string.category_all), currentCategory == null) {
            currentCategory = null
            buildTabs()
            applyFilter()
        })
        categories.forEach { cat ->
            tabsRow.addView(makeTabButton(cat.uppercase(), currentCategory == cat) {
                currentCategory = cat
                buildTabs()
                applyFilter()
            })
        }
    }

    private fun makeTabButton(label: String, selected: Boolean, onClick: () -> Unit): Button {
        val btn = Button(this)
        btn.text = label
        btn.textSize = 11f
        btn.setPadding(32, 14, 32, 14)
        btn.isAllCaps = true
        btn.stateListAnimator = null
        btn.minWidth = 0
        btn.minimumWidth = 0
        btn.setBackgroundResource(if (selected) R.drawable.tab_chip_selected else R.drawable.tab_chip_unselected)
        btn.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.bg else R.color.text)
        )
        val margin = (6 * resources.displayMetrics.density).toInt()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginStart = margin
        params.topMargin = margin / 2
        params.bottomMargin = margin / 2
        btn.layoutParams = params
        btn.setOnClickListener { onClick() }
        return btn
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
        if (drawerLayout.isDrawerOpen(Gravity.END)) {
            drawerLayout.closeDrawers()
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
                player?.setMediaItem(MediaItem.fromUri(channel.url))
                player?.prepare()
                player?.playWhenReady = true
                updatePlayPauseIcon()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
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
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    }
}
