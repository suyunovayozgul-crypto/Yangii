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
        const val PLAYLIST_URL = "https://oktv.uz/1x1WjiMO.m3u8"
        const val TELEGRAM_URL = "https://t.me/mirovoytvuz"
        const val EPG_URL = "https://iptvx.one/epg/epg_lite.xml.gz"
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
    private lateinit var tabsRow: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var searchBox: EditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var drawerCategories: LinearLayout
    private lateinit var adapter: ChannelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        toolbar = findViewById(R.id.toolbar)
        tabsRow = findViewById(R.id.tabsRow)
        recyclerView = findViewById(R.id.channelList)
        statusText = findViewById(R.id.statusText)
        searchBox = findViewById(R.id.searchBox)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        drawerCategories = findViewById(R.id.drawerCategories)

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

        adapter = ChannelAdapter(emptyList()) { channel, _ ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("channel_name", channel.name)
            intent.putExtra("channel_url", channel.url)
            intent.putExtra("channel_group", channel.group)
            intent.putExtra("channel_tvgid", channel.tvgId)
            intent.putExtra("channel_logo", channel.logo)
            intent.putExtra("channel_useragent", channel.userAgent)
            intent.putExtra("channel_referrer", channel.referrer)
            startActivity(intent)
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
        EpgRepository.ensureLoaded(EPG_URL) { adapter.refreshEpgRows() }
        startEpgRowRefreshLoop()
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

    private var fetchRetryCount = 0
    private val fetchRetryDelaysMs = longArrayOf(2000, 4000, 8000, 15000, 30000)

    private fun fetchPlaylist() {
        statusText.visibility = View.VISIBLE
        statusText.text = getString(R.string.loading)
        recyclerView.visibility = View.GONE

        executor.execute {
            try {
                val channels = M3UParser.fetch(PLAYLIST_URL)
                mainHandler.post {
                    fetchRetryCount = 0
                    onLoaded(channels)
                }
            } catch (e: Exception) {
                mainHandler.post { onFetchFailed(e) }
            }
        }
    }

    /**
     * MUHIM: avval bitta urinish muvaffaqiyatsiz bo'lsa, kanallar ro'yxati
     * abadiy bo'sh qolar edi ("kanallar ko'rinmay qoldi" holati aynan shundan
     * kelib chiqqan) — endi internet birozgina qoqilib qolsa ham, ilova bir
     * necha marta, ortib boruvchi kutish bilan, o'zi qayta urinadi.
     */
    private fun onFetchFailed(e: Exception) {
        swipeRefresh.isRefreshing = false
        if (fetchRetryCount < fetchRetryDelaysMs.size) {
            val delay = fetchRetryDelaysMs[fetchRetryCount]
            fetchRetryCount++
            statusText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            statusText.text = getString(R.string.loading)
            mainHandler.postDelayed({ fetchPlaylist() }, delay)
        } else {
            statusText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            statusText.text = getString(R.string.load_error, e.message ?: "")
        }
    }

    private fun onLoaded(channels: List<Channel>) {
        swipeRefresh.isRefreshing = false
        // Agar server internet uzilishi sabab to'liqsiz (juda qisqa) ro'yxat
        // yuborsa — buni haqiqiy yangilanish deb qabul qilmaymiz, chunki bu
        // "kanallar birdan yo'qolib qoladi" holatining aynan o'zi. Eskisini
        // saqlab qolamiz va qayta urinamiz.
        if (allChannels.isNotEmpty() && channels.size < allChannels.size / 2) {
            onFetchFailed(Exception("Incomplete playlist (${channels.size}/${allChannels.size})"))
            return
        }
        allChannels = channels
        categories = channels.map { it.group.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        buildTabs()
        buildDrawerCategories()
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

    private fun buildDrawerCategories() {
        drawerCategories.removeAllViews()
        categories.forEach { cat ->
            val row = TextView(this)
            row.text = cat.uppercase()
            row.textSize = 13f
            row.setPadding(60, 24, 20, 24)
            row.setTextColor(
                ContextCompat.getColor(this, if (currentCategory == cat) R.color.amber else R.color.text)
            )
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            row.setBackgroundResource(outValue.resourceId)
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener {
                selectCategory(cat)
                drawerLayout.closeDrawers()
            }
            drawerCategories.addView(row)
        }
    }

    private fun selectCategory(cat: String?) {
        if (currentCategory == cat) return
        currentCategory = cat
        buildTabs()
        buildDrawerCategories()
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

    /** Ilova fonga ketib qaytganda ro'yxat bo'sh qolib ketgan bo'lsa — qayta yuklaymiz. */
    override fun onResume() {
        super.onResume()
        if (allChannels.isEmpty() && fetchRetryCount == 0) {
            fetchPlaylist()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
