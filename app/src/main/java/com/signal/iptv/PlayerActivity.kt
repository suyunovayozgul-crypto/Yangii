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
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import java.util.concurrent.Executors
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var allChannels: List<Channel> = emptyList()
    private var categories: List<String> = emptyList()
    private var currentCategory: String? = null
    private var searchTerm = ""

    private var currentChannel: Channel? = null
    private var retryCount: Int = 0
    private val maxRetries: Int = 2

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
    private lateinit var titleView: TextView
    private lateinit var programView: TextView
    private lateinit var resizeLabel: TextView
    private lateinit var tabsRow: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var statusText: TextView
    private lateinit var channelRecycler: RecyclerView
    private lateinit var adapter: ChannelAdapter
    private lateinit var gestureDetector: GestureDetector

    private val epgRefreshRunnable = object : Runnable {
        override fun run() {
            updateProgramLabel()
            mainHandler.postDelayed(this, EPG_LABEL_REFRESH_MS)
        }
    }

    private val hideResizeLabelRunnable = Runnable {
        resizeLabel.animate().alpha(0f).setDuration(200).withEndAction {
            resizeLabel.visibility = View.GONE
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContentView(R.layout.activity_player)
        hideSystemBars()

        val name = intent.getStringExtra("channel_name") ?: ""
        val url = intent.getStringExtra("channel_url") ?: ""

        drawerLayout = findViewById(R.id.playerDrawerLayout)
        playerView = findViewById(R.id.playerView)
        titleView = findViewById(R.id.playerTitle)
        programView = findViewById(R.id.playerProgram)
        resizeLabel = findViewById(R.id.playerResizeLabel)
        tabsRow = findViewById(R.id.playerTabsRow)
        searchBox = findViewById(R.id.playerSearchBox)
        statusText = findViewById(R.id.playerStatusText)
        channelRecycler = findViewById(R.id.playerChannelList)

        titleView.text = name

        findViewById<ImageButton>(R.id.playerBackBtn).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.playerChannelsBtn).setOnClickListener {
            drawerLayout.openDrawer(Gravity.END)
        }
        findViewById<ImageButton>(R.id.playerResizeBtn).setOnClickListener { cycleResizeMode() }
        findViewById<ImageButton>(R.id.playerRotateBtn).setOnClickListener { toggleOrientation() }

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
        setupSwipeGesture()
        playChannel(Channel(name = name, url = url))
        loadChannelsForDrawer()

        EpgRepository.ensureLoaded(MainActivity.EPG_URL) {
            updateProgramLabel()
            adapter.refreshEpgRows()
        }
        mainHandler.postDelayed(epgRefreshRunnable, EPG_LABEL_REFRESH_MS)
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
        // FFmpeg dasturiy dekoderiga o'tadi.
        val renderersFactory = NextRenderersFactory(this)
            .setExtensionRendererMode(NextRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player = exoPlayer
        playerView.player = exoPlayer
        playerView.resizeMode = resizeModes[resizeModeIndex]
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (retryCount < maxRetries) {
                    retryCount++
                    mainHandler.postDelayed({ currentChannel?.let { playChannel(it) } }, 1500)
                } else {
                    val name = currentChannel?.name ?: ""
                    titleView.text = getString(R.string.stream_error, name)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) retryCount = 0
            }
        })
    }

    /**
     * Jonli efirda menyuga qaytmasdan kanal almashtirish: ekranda tepaga
     * yoki pastga svayp qilish keyingi/oldingi kanalga o'tkazadi.
     */
    private fun setupSwipeGesture() {
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
                    return true
                }
                return false
            }
        })
        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
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
        titleView.text = channel.name
        updateProgramLabel()
        val exoPlayer = player ?: return
        exoPlayer.setMediaItem(MediaItem.fromUri(channel.url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
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
    }
}
