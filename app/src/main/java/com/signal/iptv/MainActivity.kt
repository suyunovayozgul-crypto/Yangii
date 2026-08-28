package com.signal.iptv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        const val PLAYLIST_URL = "https://ru.oktv.uz/dSelix36.m3u8"
        const val TELEGRAM_URL = "https://t.me/mirovoytvuz"
        private const val EPG_ROW_REFRESH_MS = 60_000L
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var allChannels: List<Channel> = emptyList()
    private var categories: List<String> = emptyList()
    private var currentCategory: String? = null // null = all
    private var searchTerm = ""

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var toolbarSubtitle: TextView
    private lateinit var tabsRow: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var searchBox: EditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: ChannelAdapter
    private var lastEpgUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        toolbar = findViewById(R.id.toolbar)
        toolbarSubtitle = findViewById(R.id.toolbarSubtitle)
        tabsRow = findViewById(R.id.tabsRow)
        recyclerView = findViewById(R.id.channelList)
        statusText = findViewById(R.id.statusText)
        searchBox = findViewById(R.id.searchBox)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.menu_open, R.string.menu_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        findViewById<TextView>(R.id.drawerAllChannels).setOnClickListener {
            selectCategory(null)
            drawerLayout.closeDrawers()
        }
        findViewById<TextView>(R.id.drawerTelegram).setOnClickListener {
            drawerLayout.closeDrawers()
            openTelegram()
        }
        findViewById<TextView>(R.id.drawerAbout).setOnClickListener {
            drawerLayout.closeDrawers()
            showAbout()
        }
        findViewById<TextView>(R.id.drawerSettings).setOnClickListener {
            drawerLayout.closeDrawers()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // MUHIM (qayta dizayn): avval Telegram/Ma'lumot/Sozlamalar FAQAT
        // hamburger menyu ichida yashiringan edi. Endi shu 3 ta amal to'g'ridan-
        // to'g'ri yuqori panelda ham bor — foydalanuvchi menyuni ochmasdan ham
        // bir bosishda yeta oladi. Drawer ichidagi bir xil qatorlar orqa
        // muvofiqlik/tanish odat uchun saqlab qolindi.
        findViewById<View>(R.id.toolbarTelegramBtn).setOnClickListener { openTelegram() }
        findViewById<View>(R.id.toolbarAboutBtn).setOnClickListener { showAbout() }
        findViewById<View>(R.id.toolbarSettingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        adapter = ChannelAdapter(emptyList()) { channel, _ ->
            startActivity(channelIntent(channel))
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { fetchPlaylist() }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchTerm = s?.toString()?.trim()?.lowercase() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fetchPlaylist()
        loadEpg()
        startEpgRowRefreshLoop()
    }

    override fun onResume() {
        super.onResume()
        // Foydalanuvchi Sozlamalar ekranidan EPG havolasini o'zgartirib qaytgan
        // bo'lishi mumkin — shunday bo'lsa, darhol yangi manbadan qayta yuklaymiz.
        val current = AppPrefs.epgUrl(this)
        if (current != lastEpgUrl) loadEpg()
    }

    private fun loadEpg() {
        lastEpgUrl = AppPrefs.epgUrl(this)
        EpgRepository.ensureLoaded(lastEpgUrl) { adapter.refreshEpgRows() }
    }

    /** Periodically re-binds visible rows so "Hozir: ..." stays in sync as programmes change. */
    private fun startEpgRowRefreshLoop() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                adapter.refreshEpgRows()
                mainHandler.postDelayed(this, EPG_ROW_REFRESH_MS)
            }
        }, EPG_ROW_REFRESH_MS)
    }

    private fun fetchPlaylist() {
        statusText.visibility = View.VISIBLE
        statusText.text = getString(R.string.loading)
        recyclerView.visibility = View.GONE

        executor.execute {
            try {
                val channels = M3UParser.fetch(PLAYLIST_URL)
                mainHandler.post { onLoaded(channels) }
            } catch (e: Exception) {
                mainHandler.post {
                    swipeRefresh.isRefreshing = false
                    statusText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    statusText.text = getString(R.string.load_error, e.message ?: "")
                }
            }
        }
    }

    private fun onLoaded(channels: List<Channel>) {
        swipeRefresh.isRefreshing = false
        allChannels = channels
        toolbarSubtitle.text = getString(R.string.channel_count_subtitle, channels.size)
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
            selectCategory(null)
        })
        categories.forEach { cat ->
            tabsRow.addView(makeTabButton(cat.uppercase(), currentCategory == cat) {
                selectCategory(cat)
            })
        }
    }

    private fun makeTabButton(label: String, selected: Boolean, onClick: () -> Unit): Button {
        val btn = Button(this)
        btn.text = label
        btn.textSize = 12f
        btn.setPadding(36, 16, 36, 16)
        btn.isAllCaps = true
        btn.stateListAnimator = null
        btn.minWidth = 0
        btn.minimumWidth = 0
        btn.setBackgroundResource(if (selected) R.drawable.tab_chip_selected else R.drawable.tab_chip_unselected)
        btn.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.bg else R.color.text)
        )
        val margin = (8 * resources.displayMetrics.density).toInt()
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

    private fun selectCategory(cat: String?) {
        if (currentCategory == cat) return
        currentCategory = cat
        buildTabs()
        applyFilter()
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
            recyclerView.visibility = View.GONE
            statusText.text = getString(R.string.no_channels)
        } else {
            statusText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            adapter.updateData(filtered)
        }
    }

    /**
     * MUHIM: avval bu yerda faqat channel_name/channel_url uzatilardi — shu
     * sababli PlayerActivity ochilganda kanalning tvg-id'i yo'qolib qolar edi
     * va aynan asosiy ro'yxatdan ochilgan kanal uchun EPG hech qachon
     * ko'rinmas edi (faqat pleyer ichidagi kanal almashtirishda EPG ishlardi,
     * chunki u yerda to'liq Channel obyekti to'g'ridan-to'g'ri uzatiladi).
     * Endi tvg-id, logo, guruh va maxsus User-Agent/Referer ham uzatiladi.
     */
    private fun channelIntent(channel: Channel): Intent {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("channel_name", channel.name)
        intent.putExtra("channel_url", channel.url)
        intent.putExtra("channel_logo", channel.logo)
        intent.putExtra("channel_group", channel.group)
        intent.putExtra("channel_tvg_id", channel.tvgId)
        intent.putExtra("channel_user_agent", channel.userAgent)
        intent.putExtra("channel_referer", channel.referer)
        return intent
    }

    private fun openTelegram() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))
        startActivity(intent)
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setMessage(R.string.about_body)
            .setPositiveButton(R.string.about_ok) { d, _ -> d.dismiss() }
            .show()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawers()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
