package com.signal.iptv

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var allChannels: List<Channel> = emptyList()
    private var categories: List<String> = emptyList()
    private var currentCategory: String? = null
    private var searchTerm = ""

    private var currentName: String = ""
    private var currentUrl: String = ""

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var tabsRow: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var statusText: TextView
    private lateinit var channelRecycler: RecyclerView
    private lateinit var adapter: ChannelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemBars()
        setContentView(R.layout.activity_player)

        currentName = intent.getStringExtra("channel_name") ?: ""
        currentUrl = intent.getStringExtra("channel_url") ?: ""

        drawerLayout = findViewById(R.id.playerDrawerLayout)
        playerView = findViewById(R.id.playerView)
        titleView = findViewById(R.id.playerTitle)
        tabsRow = findViewById(R.id.playerTabsRow)
        searchBox = findViewById(R.id.playerSearchBox)
        statusText = findViewById(R.id.playerStatusText)
        channelRecycler = findViewById(R.id.playerChannelList)

        titleView.text = currentName

        findViewById<ImageButton>(R.id.playerBackBtn).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.playerChannelsBtn).setOnClickListener {
            drawerLayout.openDrawer(Gravity.END)
        }

        adapter = ChannelAdapter(emptyList()) { channel, _ ->
            playChannel(channel.name, channel.url)
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
        playChannel(currentName, currentUrl)
        loadChannelsForDrawer()
    }

    private fun setupPlayer() {
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                titleView.text = getString(R.string.stream_error, currentName)
            }
        })
    }

    private fun playChannel(name: String, url: String) {
        currentName = name
        currentUrl = url
        titleView.text = name
        val exoPlayer = player ?: return
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
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
        btn.setPadding(24, 12, 24, 12)
        btn.background = null
        btn.isAllCaps = true
        btn.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.amber else R.color.muted)
        )
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
}
