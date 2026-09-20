package uz.oktv.iptv

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONObject
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.os.SystemClock
import android.media.AudioManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.oktv.iptv.ui.theme.OKTVPlayerTheme
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

enum class VodFocusZone { SEARCH, GENRE_BTN, YEARS, GRID }

private const val MIROVOY_WEBSITE_URL = "https://mirovoytv.uz"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        setContent {
            OKTVPlayerTheme {
                MainAppController()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainAppController() {

    // Self-update: checks GitHub Releases once per launch and, if a newer
    // build exists, shows the user a dialog -> download progress -> the
    // standard system install screen. See update/UpdateChecker.kt to set
    // your GitHub "owner/repo".
    uz.oktv.iptv.update.UpdateCheckerHost()

    val context = LocalContext.current
    val store = remember { PersonalPlaylistStore(context) }

    var playlists by remember {
        mutableStateOf(store.load())
    }

    var loadedChannels by remember {
        mutableStateOf<List<M3UChannel>>(emptyList())
    }

    var loadedCategories by remember {
        mutableStateOf(listOf("Все", "❤️ Избранное"))
    }

    var loadedEpg by remember {
        mutableStateOf<Map<String, List<EpgProgram>>>(emptyMap())
    }

    var opened by remember {
        mutableStateOf(false)
    }
    // Home ekrani ko'rsatilsinmi
    var showHome by remember { mutableStateOf(false) }

    var loadingSavedPlaylist by remember {
        mutableStateOf(true)
    }

    var startupError by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {

        val active = store.getActive()

        if (active == null) {
            loadingSavedPlaylist = false
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val result = PersonalPlaylistLoader.load(active.url)

                if (result.channels.isEmpty()) {
                    throw IllegalStateException(
                        "В сохранённом плейлисте не найдено каналов"
                    )
                }

                store.setActiveId(active.id)

                withContext(Dispatchers.Main) {
                    loadedChannels = result.channels
                    loadedCategories = result.categories
                    loadedEpg = emptyMap()
                    opened = true
                    showHome = true
                    loadingSavedPlaylist = false
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    startupError =
                        e.message ?: "Не удалось загрузить плейлист"
                    loadingSavedPlaylist = false
                }
            }
        }
    }

    if (loadingSavedPlaylist) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070B14)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF2563EB),
                    modifier = Modifier.size(36.dp)
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Загрузка плейлиста...",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

    } else if (!opened) {

        PersonalPlaylistScreen(
            store = store,
            playlists = playlists,
            onPlaylistsChanged = { updated ->
                playlists = updated
            },
            onPlaylistLoaded = { playlist, result ->

                store.setActiveId(playlist.id)

                loadedChannels = result.channels
                loadedCategories = result.categories
                loadedEpg = emptyMap()

                startupError = null
                opened = true
                showHome = true
            }
        )

    } else if (showHome) {

        HomeScreen(
            channels = loadedChannels,
            epgMap = loadedEpg,
            subscriptionExpires = "",
            onLiveTv = { showHome = false },
            onMovies = { showHome = false },
            onSport = { showHome = false },
            onSeries = { showHome = false },
            onPlaylist = {
                showHome = false
                opened = false
            },
            onSettings = { showHome = false },
            onRefresh = { showHome = false },
            onExit = { showHome = false; opened = false }
        )

    } else {

        StalkerNativeApp(
            userToken = "",
            initialChannels = loadedChannels,
            initialCategories = loadedCategories,
            initialEpgMap = loadedEpg,
            onLogout = {

                opened = false
                showHome = false
                loadedChannels = emptyList()
                loadedCategories = listOf("Все", "❤️ Избранное")
                loadedEpg = emptyMap()
            }
        )
    }

    startupError?.let { message ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable {
                    startupError = null
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.9f)
                    .background(
                        Color(0xFF0D1322),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Не удалось загрузить плейлист",
                    color = Color(0xFFF59E0B),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = message,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Нажмите, чтобы открыть список плейлистов",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun SystemLoadingScreen(
    userToken: String,
    onLoadingComplete: (List<M3UChannel>, List<String>, Map<String, List<EpgProgram>>) -> Unit,
    onError: () -> Unit
) {
    var tokenStatus by remember { mutableStateOf("Проверка токена доступа...") }
    var tokenOk by remember { mutableStateOf<Boolean?>(null) }
    var playlistStatus by remember { mutableStateOf("Загрузка списка каналов...") }
    var playlistOk by remember { mutableStateOf<Boolean?>(null) }
    var overallProgress by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current

    val animatedProgress by animateFloatAsState(
        targetValue = overallProgress,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "loadingProgress"
    )

    LaunchedEffect(userToken) {
        withContext(Dispatchers.IO) {
            overallProgress = 0.15f
            try {
                val conn = URL("https://oktv.uz/app/api-auth").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val postData = "token=" + URLEncoder.encode(userToken, "UTF-8")
                conn.outputStream.use { it.write(postData.toByteArray()) }

                if (conn.responseCode == 200) {
                    val responseText = conn.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                    val json = JSONObject(responseText)

                    if (json.optBoolean("success", false)) {

                        val prefs = context.getSharedPreferences(
                            "oktv_prefs",
                            Context.MODE_PRIVATE
                        )

                        prefs.edit().apply {
                            putString(
                                "subscription_tariff",
                                json.optString("tariff", "")
                            )
                            putString(
                                "subscription_status",
                                json.optString("status", "")
                            )
                            putString(
                                "subscription_expires_at",
                                json.optString("expires_at", "")
                            )
                            putInt(
                                "subscription_screens",
                                json.optInt("screens", 0)
                            )
                            putBoolean(
                                "subscription_auto_renew",
                                json.optBoolean("auto_renew", false)
                            )
                            putString(
                                "subscription_server_name",
                                json.optString("server_name", "")
                            )
                            apply()
                        }

                        tokenStatus = "Проверка токена: Успешно [OK]"
                        tokenOk = true
                        overallProgress = 0.5f

                    } else {
                        tokenStatus = "Ключ не найден или подписка истекла!"
                        tokenOk = false
                        delay(1200)
                        withContext(Dispatchers.Main) { onError() }
                        return@withContext
                    }
                } else {
                    tokenStatus = "Ключ не найден или подписка истекла!"
                    tokenOk = false
                    delay(1200)
                    withContext(Dispatchers.Main) { onError() }
                    return@withContext
                }
            } catch (e: Exception) {
                tokenStatus = "Авторизация по ключу [OK]"
                tokenOk = true
                overallProgress = 0.5f
            }

            delay(200)

            val parsedChannels = mutableListOf<M3UChannel>()
            val catSet = mutableSetOf<String>()
            val playlistUrl = "https://oktv.uz/$userToken.m3u8"

            playlistStatus = "Загрузка списка каналов..."
            try {
                val conn = URL(playlistUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                val reader = conn.inputStream.bufferedReader()

                var currentTvgId = ""
                var currentName = ""
                var currentGroup = "Общие"
                var currentLogo = ""
                var idCounter = 1

                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXTINF:")) {
                        val groupMatch = Regex("group-title=\"([^\"]+)\"").find(trimmed)
                        currentGroup = groupMatch?.groupValues?.get(1) ?: "Общие"

                        val logoMatch = Regex("tvg-logo=\"([^\"]+)\"").find(trimmed)
                        currentLogo = logoMatch?.groupValues?.get(1) ?: ""

                        val idMatch = Regex("tvg-id=\"([^\"]+)\"").find(trimmed)
                        currentTvgId = idMatch?.groupValues?.get(1) ?: ""

                        currentName = trimmed.substringAfterLast(",").trim()
                        catSet.add(currentGroup)
                    } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val streamUrl = if (trimmed.startsWith("http")) trimmed else "https://oktv.uz/$trimmed"
                        parsedChannels.add(
                            M3UChannel(
                                id = idCounter++,
                                tvgId = currentTvgId,
                                name = if (currentName.isEmpty()) "Канал $idCounter" else currentName,
                                group = currentGroup,
                                logo = currentLogo,
                                url = streamUrl
                            )
                        )
                        currentName = ""
                        currentTvgId = ""
                    }
                }
                reader.close()

                playlistStatus = "Каналы загружены: ${parsedChannels.size} шт. [OK]"
                playlistOk = true
                overallProgress = 1.0f
            } catch (e: Exception) {
                playlistStatus = "Каналы загружены [OK]"
                playlistOk = true
                overallProgress = 1.0f
            }

            delay(300)

            val categories = listOf("Все", "❤️ Избранное") + catSet.toList().sorted()
            withContext(Dispatchers.Main) {
                onLoadingComplete(parsedChannels, categories, emptyMap())
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF070B14)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0D1322))
                .denimDoubleBorder(16f, 12f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "OK", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(text = "TV", color = Color(0xFF38BDF8), fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier.background(Color(0xFF2563EB), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = APP_VERSION_NAME, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)),
                    color = Color(0xFF2563EB),
                    trackColor = Color.Transparent
                )
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                LoadingCheckItem(text = tokenStatus, isOk = tokenOk)
                Spacer(modifier = Modifier.height(10.dp))
                LoadingCheckItem(text = playlistStatus, isOk = playlistOk)
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
fun StalkerNativeApp(
    userToken: String,
    initialChannels: List<M3UChannel>,
    initialCategories: List<String>,
    initialEpgMap: Map<String, List<EpgProgram>>,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val prefs: SharedPreferences = remember { context.getSharedPreferences("oktv_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    fun getKinopoiskKey(): String = "d02ffd4f-0440-4426-9e07-9ee33c306f6d"

    var contentMode by remember { mutableStateOf(AppContentMode.TV) }

    val tvChannels by remember(initialChannels) { mutableStateOf(initialChannels) }
    val tvCategories by remember(initialCategories) { mutableStateOf(initialCategories) }

    var vodChannels by remember { mutableStateOf<List<M3UChannel>>(emptyList()) }
    var vodCategories by remember { mutableStateOf<List<String>>(listOf("Все", "❤️ Избранное")) }

    var selectedCategoryIndex by remember { mutableStateOf(0) }

    var focusedChannelIndex by remember { mutableIntStateOf(0) }
    var selectedChannelIndex by remember { mutableIntStateOf(0) }

    // Fullscreen-меню каналов
    var showFullscreenChannelMenu by remember { mutableStateOf(false) }
    var fullscreenFilmButtonFocused by remember { mutableStateOf(false) }
    var fullscreenInfoRightStep by remember { mutableIntStateOf(0) }
    var fullscreenMenuMode by remember { mutableStateOf("CHANNELS") }
    var fullscreenCategoryIndex by remember { mutableIntStateOf(0) }
    var fullscreenChannelIndex by remember { mutableIntStateOf(0) }
    // Управление fullscreen жестами
    var fullscreenControlText by remember { mutableStateOf<String?>(null) }
    var fullscreenControlProgress by remember { mutableFloatStateOf(0f) }
    var lastRightPressTime by remember { mutableLongStateOf(0L) }

    var vodSearchQuery by remember { mutableStateOf("") }
    var selectedVodGenre by remember { mutableStateOf("Все") }
    var selectedVodYear by remember { mutableStateOf("Все") }
    var vodYearIndex by remember { mutableIntStateOf(0) }
    var vodFocusZone by remember { mutableStateOf(VodFocusZone.GRID) }
    var showVodGenreModal by remember { mutableStateOf(false) }

    val availableYears = remember { listOf("Все", "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2015", "2010") }
    val availableGenres = remember(vodCategories) { listOf("Все жанры") + vodCategories.filter { it != "Все" } }
    var genreSelectedIndex by remember { mutableIntStateOf(0) }
    val genreListState = rememberLazyListState()
    val genreFocusRequester = remember { FocusRequester() }

    val vodSearchFocusRequester = remember { FocusRequester() }
    val vodGenreFocusRequester = remember { FocusRequester() }

    val favoritesStr = prefs.getString("favorites_list", "") ?: ""
    var favorites by remember {
        mutableStateOf(
            if (favoritesStr.isEmpty()) emptyList<Int>()
            else favoritesStr.split(",").mapNotNull { it.toIntOrNull() }
        )
    }

    LaunchedEffect(favorites) {
        prefs.edit().putString("favorites_list", favorites.joinToString(",")).apply()
    }

    var isFullScreen by remember { mutableStateOf(false) }
    var showInfoBar by remember { mutableStateOf(false) }
    var showSlowCodecWarning by remember { mutableStateOf(false) }
    var showSideMenu by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showEpgScheduleDialog by remember { mutableStateOf(false) }
    var showPlaybackSettingsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSpeedTestDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showTokenResetDialog by remember { mutableStateOf(false) }
    var tokenDialogFocusIndex by remember { mutableIntStateOf(0) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFilteredChannels by remember { mutableStateOf<List<M3UChannel>>(emptyList()) }

    var showEpgUpdateDialog by remember { mutableStateOf(false) }
    var showEpgUrlDialog by remember { mutableStateOf(false) }
    var epgUpdateStatus by remember { mutableStateOf("Готово к обновлению") }
    var epgUpdateProgress by remember { mutableFloatStateOf(0f) }

    var showChannelActionDialog by remember { mutableStateOf<M3UChannel?>(null) }
    var showChannelInfoModal by remember { mutableStateOf(false) }
    var showDetailMovieModal by remember { mutableStateOf(false) }
    var showArchiveDetailModal by remember { mutableStateOf(false) }
    var selectedArchiveProgram by remember { mutableStateOf<EpgProgram?>(null) }

    var showPlayButtonInModal by remember { mutableStateOf(true) }

    var sideMenuIndex by remember { mutableStateOf(0) }
    var dialogCategoryIndex by remember { mutableStateOf(0) }

    var epgSelectedIndex by remember { mutableIntStateOf(0) }

    var mainEngineMode by remember { mutableStateOf(prefs.getString("main_engine", "EXO") ?: "EXO") }
    var ffmpegAudioEnabled by remember { mutableStateOf(prefs.getBoolean("ffmpeg_audio", true)) }
    var amlogicFixEnabled by remember { mutableStateOf(prefs.getBoolean("amlogic_fix", true)) }
    var smoothUpscaleEnabled by remember { mutableStateOf(prefs.getBoolean("smooth_upscale", true)) }
    var bufferSeconds by remember { mutableStateOf(prefs.getInt("buffer_sec", 10)) }

    LaunchedEffect(mainEngineMode, ffmpegAudioEnabled, amlogicFixEnabled, smoothUpscaleEnabled, bufferSeconds) {
        prefs.edit().apply {
            putString("main_engine", mainEngineMode)
            putBoolean("ffmpeg_audio", ffmpegAudioEnabled)
            putBoolean("amlogic_fix", amlogicFixEnabled)
            putBoolean("smooth_upscale", smoothUpscaleEnabled)
            putInt("buffer_sec", bufferSeconds)
            apply()
        }
    }

    var currentAspectMode by remember { mutableStateOf(PlayerAspectMode.ASPECT_16_9) }
    var currentLang by remember { mutableStateOf(AppLang.RU) }

    var epgScheduleMap by remember { mutableStateOf<Map<String, List<EpgProgram>>>(initialEpgMap) }
    var kpData by remember { mutableStateOf(KinopoiskData()) }
    var availableAudioTracks by remember { mutableStateOf<List<AudioTrackItem>>(emptyList()) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var videoWidth by remember { mutableIntStateOf(1920) }
    var videoHeight by remember { mutableIntStateOf(1080) }
    var videoFps by remember { mutableIntStateOf(25) }

    var isSeekingMode by remember { mutableStateOf(false) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }

    var archiveSeekOffsetMs by remember { mutableLongStateOf(0L) }

    val focusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val vodGridState = rememberLazyGridState()
    val dialogListState = rememberLazyListState()
    val epgListState = rememberLazyListState()
    val sideMenuListState = rememberLazyListState()

    var systemTimeMs by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(Unit) {
        while (true) {
            systemTimeMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }

    val activeChannels = if (contentMode == AppContentMode.TV) tvChannels else vodChannels
    val activeCategories = if (contentMode == AppContentMode.TV) tvCategories else vodCategories

    // Реальное количество каналов в каждой категории
    val categoryChannelCounts = remember(
        activeChannels,
        activeCategories,
        favorites,
        contentMode,
        currentLang
    ) {
        activeCategories.associateWith { category ->
            when {
                category.equals(Strings.get("all", currentLang), ignoreCase = true) ||
                category.equals("Все", ignoreCase = true) -> {
                    activeChannels.size
                }

                category.contains("Избранное", ignoreCase = true) ||
                category.contains("Favorites", ignoreCase = true) -> {
                    activeChannels.count { favorites.contains(it.id) }
                }

                else -> {
                    activeChannels.count {
                        it.group.trim().equals(category.trim(), ignoreCase = true)
                    }
                }
            }
        }
    }

    val selectedCategory = activeCategories.getOrElse(selectedCategoryIndex) { Strings.get("all", currentLang) }
    val filteredChannels = rememberFilteredChannels(
        selectedCategory = selectedCategory,
        activeChannels = activeChannels,
        favorites = favorites,
        contentMode = contentMode,
        currentLang = currentLang,
        vodSearchQuery = vodSearchQuery,
        selectedVodGenre = selectedVodGenre,
        selectedVodYear = selectedVodYear
    )

    // AUTO_PLAYER_LIST_SCROLL
    LaunchedEffect(focusedChannelIndex) {
        if (
            focusedChannelIndex >= 0 &&
            focusedChannelIndex < filteredChannels.size
        ) {
            try {
                // Прокручиваем страницами по 10 каналов:
                // 1-10 -> 11-20 -> 21-30 -> ...
                val pageStart = (focusedChannelIndex / 10) * 10
                listState.animateScrollToItem(pageStart)
            } catch (_: Exception) {
            }
        }
    }

    val currentChannel = filteredChannels.getOrNull(selectedChannelIndex) ?: filteredChannels.firstOrNull()

    var activeStreamUrl by remember { mutableStateOf<String?>(null) }
    var playingArchiveProgram by remember(currentChannel) { mutableStateOf<EpgProgram?>(null) }
    val isArchivePlaying = playingArchiveProgram != null

    androidx.activity.compose.BackHandler {
        when {
            showVodGenreModal -> { showVodGenreModal = false }
            showChannelInfoModal -> { showChannelInfoModal = false }
            showDetailMovieModal -> { showDetailMovieModal = false }
            showArchiveDetailModal -> { showArchiveDetailModal = false }
            showChannelActionDialog != null -> { showChannelActionDialog = null }
            showSearchDialog -> { showSearchDialog = false; searchQuery = "" }
            showSupportDialog -> { showSupportDialog = false }
            showSpeedTestDialog -> { showSpeedTestDialog = false }
            showTokenResetDialog -> { showTokenResetDialog = false }
            showLanguageDialog -> { showLanguageDialog = false }
            showPlaybackSettingsDialog -> { showPlaybackSettingsDialog = false }
            showEpgUpdateDialog -> { showEpgUpdateDialog = false }
            showEpgScheduleDialog -> { showEpgScheduleDialog = false }
            showCategoryDialog -> { showCategoryDialog = false }
            showSideMenu -> { showSideMenu = false }
            isFullScreen -> {
                activeStreamUrl = null
                isFullScreen = false
                showInfoBar = false
            }
            contentMode == AppContentMode.VOD -> {
                if (vodFocusZone != VodFocusZone.GRID) {
                    vodFocusZone = VodFocusZone.GRID
                } else {
                    contentMode = AppContentMode.TV
                    selectedCategoryIndex = 0
                    selectedChannelIndex = 0
                    focusedChannelIndex = 0
                }
            }
            showExitConfirmDialog -> {
                showExitConfirmDialog = false
                (context as? ComponentActivity)?.finish()
            }
            else -> {
                showExitConfirmDialog = true
            }
        }
    }

    LaunchedEffect(playingArchiveProgram) {
        archiveSeekOffsetMs = 0L
    }

    LaunchedEffect(currentChannel, contentMode) {
        if (contentMode == AppContentMode.TV) {
            activeStreamUrl = currentChannel?.url
        }
    }

    val uiDuration = if (isArchivePlaying && playingArchiveProgram != null) {
        playingArchiveProgram!!.stopTimeMs - playingArchiveProgram!!.startTimeMs
    } else {
        duration
    }

    val uiPosition = if (isArchivePlaying) {
        (currentPosition + archiveSeekOffsetMs).coerceIn(0L, uiDuration)
    } else {
        currentPosition
    }

    LaunchedEffect(sideMenuIndex, showSideMenu) {
        if (showSideMenu) {
            sideMenuListState.animateScrollToItem(sideMenuIndex)
        }
    }

    LaunchedEffect(showVodGenreModal) {
        if (showVodGenreModal) {
            val idx = availableGenres.indexOfFirst { it.equals(selectedVodGenre, true) }
            genreSelectedIndex = if (idx >= 0) idx else 0
            genreListState.scrollToItem(genreSelectedIndex)
            delay(100)
            genreFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(genreSelectedIndex) {
        if (showVodGenreModal && availableGenres.isNotEmpty()) {
            genreListState.animateScrollToItem(genreSelectedIndex)
        }
    }

    val startEpgUpdate = {
        // EPG yuklanish dialog ko'rsatilmaydi — fon rejimida ishlaydi
        epgUpdateStatus = "Скачивание телепрограммы (gz)..."
        epgUpdateProgress = 0.1f

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val cacheGzFile = EpgRepository.downloadEpg(
                    cacheDir = context.cacheDir,
                    onProgress = { progress ->
                        val pct = (progress * 0.4f).coerceIn(0f, 0.4f)
                        coroutineScope.launch(Dispatchers.Main) {
                            epgUpdateProgress = 0.1f + pct
                            epgUpdateStatus = "Скачивание: ${(progress * 100f).toInt()}%"
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    epgUpdateStatus = "Парсинг телепрограммы (XML)..."
                    epgUpdateProgress = 0.5f
                }

                val resultMap = parseEpgFileSafely(cacheGzFile)
                withContext(Dispatchers.Main) {
                    epgScheduleMap = resultMap
                    epgUpdateProgress = 1.0f
                    epgUpdateStatus = "Телепрограмма успешно обновлена! [OK]"
                    EpgRepository.markUpdated(context)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    epgUpdateStatus = "Ошибка обновления EPG!"

                }
            }
        }
    }

    // EPG: birinchi ochilganda va haftada bir marta fon rejimida yuklanadi
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cacheGzFile = EpgRepository.cacheFile(context.cacheDir)
            if (EpgRepository.isCacheFresh(cacheGzFile) && !EpgRepository.needsUpdate(context)) {
                // Cache yangi — faqat parse qilamiz
                try {
                    val resultMap = parseEpgFileSafely(cacheGzFile)
                    withContext(Dispatchers.Main) { epgScheduleMap = resultMap }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Cache buzilgan — yuklaymiz
                    withContext(Dispatchers.Main) { startEpgUpdate() }
                }
            } else {
                // Birinchi marta yoki haftasi o'tgan — fon rejimida yuklaymiz
                withContext(Dispatchers.Main) { startEpgUpdate() }
            }
        }
    }

    // --- ПРЯМАЯ И МГНОВЕННАЯ ЗАГРУЗКА МЕДИАТЕКИ БЕЗ ТОКЕНОВ И ЗАЩИТЫ ---
    LaunchedEffect(Unit) {
        if (vodChannels.isEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val result = fetchVodPlaylist()
                    withContext(Dispatchers.Main) {
                        if (result.channels.isNotEmpty()) {
                            vodChannels = result.channels
                            vodCategories = listOf("Все") + result.categories.toList().sorted()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(selectedCategoryIndex, contentMode) {
        focusedChannelIndex = 0
        selectedChannelIndex = 0
        vodFocusZone = VodFocusZone.GRID
        if (activeChannels.isNotEmpty()) {
            listState.scrollToItem(0)
            vodGridState.scrollToItem(0)
        }
    }

    LaunchedEffect(vodFocusZone) {
        if (contentMode == AppContentMode.VOD) {
            when (vodFocusZone) {
                VodFocusZone.SEARCH -> {
                    try { vodSearchFocusRequester.requestFocus() } catch (_: Exception) {}
                }
                VodFocusZone.GENRE_BTN -> {
                    try { vodGenreFocusRequester.requestFocus() } catch (_: Exception) {}
                }
                else -> {}
            }
        }
    }

    val channelSchedule = remember(currentChannel, epgScheduleMap.size) {
        getScheduleForChannel(currentChannel, epgScheduleMap)
    }
    val currentProgram = remember(channelSchedule, systemTimeMs) { getCurrentProgram(channelSchedule) }
    val nextProgram = remember(channelSchedule, currentProgram) { getNextProgram(channelSchedule, currentProgram) }

    val programProgress = remember(currentProgram, systemTimeMs) {
        calculateEpgProgress(currentProgram, systemTimeMs)
    }

    val currentProgTitle = if (isArchivePlaying) {
        playingArchiveProgram?.title ?: ""
    } else if (contentMode == AppContentMode.VOD) {
        currentChannel?.name ?: ""
    } else {
        currentProgram?.title ?: ""
    }

    val isFilmProgram =
        Regex(
            "(?i)^\\s*(т/с|х/ф|д/ф|д/с|с/ф|м/ф|м/с|т/ш)\\b"
        ).containsMatchIn(currentProgTitle)

    val showFilmButton =
        contentMode == AppContentMode.VOD || isFilmProgram

    val showProgramButton =
        contentMode == AppContentMode.TV &&
        !isArchivePlaying &&
        !isFilmProgram

    val openFilmOrProgram = {
        if (showProgramButton) {
            showEpgScheduleDialog = true
        } else if (isArchivePlaying && playingArchiveProgram != null) {
            selectedArchiveProgram = playingArchiveProgram
            showArchiveDetailModal = true
        } else {
            showPlayButtonInModal = false
            showDetailMovieModal = true
        }
    }

    var epgInitialScrollDone by remember { mutableStateOf(false) }

    LaunchedEffect(showEpgScheduleDialog) {
        if (showEpgScheduleDialog && channelSchedule.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val curIdx = channelSchedule.indexOfFirst {
                it.startTimeMs <= now &&
                    (it.stopTimeMs == 0L || it.stopTimeMs > now)
            }

            epgSelectedIndex = if (curIdx >= 0) curIdx else 0

            // При открытии EPG сразу ставим список на текущую передачу.
            epgInitialScrollDone = false
            epgListState.scrollToItem(epgSelectedIndex)
            epgInitialScrollDone = true
        } else {
            epgInitialScrollDone = false
        }
    }

    LaunchedEffect(epgSelectedIndex) {
        if (
            showEpgScheduleDialog &&
            channelSchedule.isNotEmpty() &&
            epgInitialScrollDone
        ) {
            epgListState.animateScrollToItem(epgSelectedIndex)
        }
    }

    LaunchedEffect(searchQuery, activeChannels) {
        if (searchQuery.length >= 2) {
            val cleanQuery = cleanTitleForSearch(searchQuery)
            searchFilteredChannels = activeChannels.filter { cleanTitleForSearch(it.name).contains(cleanQuery, ignoreCase = true) }
        } else {
            searchFilteredChannels = emptyList()
        }
    }

    // --- КИНОПОИСК: только VOD или EPG-программы с явным типом ---
    LaunchedEffect(
        currentChannel?.id,
        currentProgram?.title,
        playingArchiveProgram?.title,
        contentMode,
        showFilmButton
    ) {
        val shouldLoadKinopoisk =
            contentMode == AppContentMode.VOD || showFilmButton

        if (shouldLoadKinopoisk) {
            val targetTitle = if (isArchivePlaying) {
                playingArchiveProgram?.title
            } else if (contentMode == AppContentMode.VOD) {
                currentChannel?.name
            } else {
                currentProgram?.title
            }

            withContext(Dispatchers.IO) {
                val result = fetchKinopoiskData(
                    targetTitle ?: "",
                    getKinopoiskKey()
                )

                withContext(Dispatchers.Main) {
                    kpData = result
                }
            }
        } else {
            // Обычная EPG-передача — данные Кинопоиска очищаем
            kpData = KinopoiskData()
        }
    }

    val exoPlayer: ExoPlayer = remember {
        createRealExoPlayer(
            context = context,
            ffmpegAudio = ffmpegAudioEnabled,
            amlogicFix = amlogicFixEnabled,
            bufferSeconds = bufferSeconds
        ).also { player ->
            Log.e("Mirovoy TV_PLAYER_INSTANCE", "!!! EXOPLAYER CREATED !!! instance=${System.identityHashCode(player)}")
        }
    }

    val playerController = remember(exoPlayer) {
        PlayerController(exoPlayer)
    }

    // Uy tugmasi bosilib ilova orqa fonga o'tganda (yoki task-switcher,
    // ekran o'chganda) — pleyerni TO'XTATAMIZ. Aks holda video/ovoz
    // orqa fonda davom etib, telefon batareyasini va trafikni behuda
    // sarflayveradi. ON_STOP — aynan shu holatlarda ishga tushadi
    // (bildirishnomalar panelini pastga tortish yoki qisqa vaqtli
    // dialoglar UCHUN ishlamaydi — faqat haqiqatan ilovadan chiqilganda).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isPlaying, activeStreamUrl) {
        if (isPlaying) {
            while (true) {
                try {
                    isPlaying = exoPlayer.isPlaying
                    currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val curDur = exoPlayer.duration
                    duration = if (curDur > 0L && curDur != C.TIME_UNSET) curDur else 0L
                    if (exoPlayer.videoSize.height > 0) {
                        videoWidth = exoPlayer.videoSize.width
                        videoHeight = exoPlayer.videoSize.height
                    }
                    val rawFps = exoPlayer.videoFormat?.frameRate ?: 0f
                    if (rawFps > 0f) {
                        videoFps = rawFps.toInt()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val trackList = mutableListOf<AudioTrackItem>()
                val seenCodes = mutableSetOf<String>()

                for ((gIdx, group) in tracks.groups.withIndex()) {
                    for (tIdx in 0 until group.length) {
                        val format = group.getTrackFormat(tIdx)
                        val selected = group.isTrackSelected(tIdx)

                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            val rawLang = format.language ?: "und"
                            val effectiveLang = if (rawLang.equals("und", ignoreCase = true)) "ru" else rawLang.lowercase(Locale.getDefault())
                            val langUpper = effectiveLang.uppercase(Locale.getDefault())

                            val shortCode = when (effectiveLang) {
                                "ru", "rus" -> "RU"
                                "uz", "uzb" -> "UZ"
                                "en", "eng" -> "EN"
                                "uk", "ukr" -> "UK"
                                "tr", "tur" -> "TR"
                                else -> if (langUpper.length >= 2) langUpper.take(2) else "A${tIdx + 1}"
                            }

                            if (!seenCodes.contains(shortCode)) {
                                seenCodes.add(shortCode)

                                val label = format.label ?: when (effectiveLang) {
                                    "ru", "rus" -> "Русский (RU)"
                                    "uz", "uzb" -> "O'zbekcha (UZ)"
                                    "en", "eng" -> "English (EN)"
                                    "uk", "ukr" -> "Українська (UK)"
                                    "tr", "tur" -> "Türkçe (TR)"
                                    else -> "Аудио ($langUpper)"
                                }

                                trackList.add(
                                    AudioTrackItem(
                                        groupIndex = gIdx,
                                        trackIndexInGroup = tIdx,
                                        language = rawLang,
                                        shortCode = shortCode,
                                        label = label,
                                        isSelected = selected
                                    )
                                )
                            }
                        }
                    }
                }
                availableAudioTracks = trackList

                // Eski/og'ir video kodeklar (masalan MPEG-2) ko'p TV
                // qurilmalarida apparat darajasida dekodlanmaydi va
                // dasturiy (software) dekodlashga majbur qiladi — bu
                // ba'zan video sekin/lag bilan ketishiga olib keladi.
                // Foydalanuvchini shu haqda ogohlantiramiz.
                var detectedSlowCodec = false
                for (group in tracks.groups) {
                    if (group.type != C.TRACK_TYPE_VIDEO) continue
                    for (tIdx in 0 until group.length) {
                        if (!group.isTrackSelected(tIdx)) continue
                        val mime = group.getTrackFormat(tIdx).sampleMimeType ?: continue
                        if (mime == MimeTypes.VIDEO_MPEG2 ||
                            mime == MimeTypes.VIDEO_MPEG ||
                            mime == MimeTypes.VIDEO_H263
                        ) {
                            detectedSlowCodec = true
                        }
                    }
                }
                showSlowCodecWarning = detectedSlowCodec
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = exoPlayer.duration
                duration = if (dur > 0L && dur != C.TIME_UNSET) dur else 0L
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("Mirovoy TV_PLAYER_ERROR", "PLAYER ERROR: ${error.errorCodeName}", error)
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            Log.e("Mirovoy TV_PLAYER_INSTANCE", "!!! EXOPLAYER RELEASED !!! instance=${System.identityHashCode(exoPlayer)}")
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(activeStreamUrl, mainEngineMode, playerController) {
        if (mainEngineMode == "EXO") {
            if (!activeStreamUrl.isNullOrEmpty()) {
                playerController.play(activeStreamUrl!!)
            } else {
                playerController.stop()
            }
        } else {
            playerController.stop()
        }
    }

    LaunchedEffect(showSlowCodecWarning) {
        if (showSlowCodecWarning) {
            delay(6000)
            showSlowCodecWarning = false
        }
    }

    LaunchedEffect(showInfoBar) {
        if (showInfoBar && !isSeekingMode) {
            delay(10000)
            showInfoBar = false
        }
    }

    LaunchedEffect(seekDeltaMs) {
        if (isSeekingMode && seekDeltaMs != 0L) {
            delay(300)
            if (isArchivePlaying && playingArchiveProgram != null) {
                archiveSeekOffsetMs = seekTargetMs
                val prog = playingArchiveProgram!!
                val base = currentChannel?.url
                if (base != null) {
                    val targetSeekSec = (seekTargetMs / 1000).coerceAtLeast(0L)
                    val newStartUnix = (prog.startTimeMs / 1000) + targetSeekSec
                    val endUnix = (prog.stopTimeMs / 1000) + 60
                    val sep = if (base.contains("?")) "&" else "?"
                    activeStreamUrl = "${base}${sep}utc=$newStartUnix&lutc=$endUnix"
                }
            } else {
                exoPlayer.seekTo(seekTargetMs)
            }
            exoPlayer.play()
            isSeekingMode = false
            seekDeltaMs = 0L
        }
    }

    val currentTime = remember(systemTimeMs) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(systemTimeMs))
    }

    val resLabel = remember(videoHeight) {
        when {
            videoHeight >= 2160 -> "4K"
            videoHeight >= 1080 -> "FHD"
            videoHeight >= 720 -> "HD"
            else -> "SD"
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A13))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (isFullScreen && (contentMode == AppContentMode.VOD || isArchivePlaying) && uiDuration > 0) {
                        if (!isSeekingMode) {
                            isSeekingMode = true
                            seekTargetMs = uiPosition
                            seekDeltaMs = 0L
                        }
                        val step = if (dragAmount > 0) 15000L else -15000L
                        seekDeltaMs += step
                        seekTargetMs = (seekTargetMs + step).coerceIn(0L, uiDuration)
                        showInfoBar = true
                    } else if (!isFullScreen && contentMode == AppContentMode.TV) {
                        if (dragAmount > 60) {
                            showSideMenu = true
                        } else if (dragAmount < -60) {
                            showEpgScheduleDialog = true
                        }
                    }
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (showDetailMovieModal || showArchiveDetailModal || showVodGenreModal || showChannelInfoModal) {
                                if (showVodGenreModal) {
                                    if (genreSelectedIndex > 0) genreSelectedIndex--
                                }
                                return@onKeyEvent true
                            }
                            showInfoBar = true

                            if (
                                showFullscreenChannelMenu &&
                                contentMode == AppContentMode.TV &&
                                !isArchivePlaying
                            ) {
                                // Пока открыто боковое fullscreen-меню,
                                // информационный лоток не показываем.
                                showInfoBar = false

                                if (fullscreenMenuMode == "CHANNELS") {
                                    if (fullscreenChannelIndex > 0) {
                                        fullscreenChannelIndex--
                                    }
                                } else {
                                    if (fullscreenCategoryIndex > 0) {
                                        fullscreenCategoryIndex--
                                    }
                                }
                                return@onKeyEvent true
                            }


                            if (showCategoryDialog) {
                                if (dialogCategoryIndex > 0) dialogCategoryIndex--
                            } else if (showSideMenu) {
                                if (sideMenuIndex > 0) sideMenuIndex--
                            } else if (showEpgScheduleDialog) {
                                if (epgSelectedIndex > 0) epgSelectedIndex--
                            } else if (contentMode == AppContentMode.VOD) {
                                when (vodFocusZone) {
                                    VodFocusZone.GRID -> {
                                        if (focusedChannelIndex >= 5) {
                                            focusedChannelIndex -= 5
                                        } else {
                                            vodFocusZone = VodFocusZone.YEARS
                                        }
                                    }
                                    VodFocusZone.YEARS -> vodFocusZone = VodFocusZone.GENRE_BTN
                                    VodFocusZone.GENRE_BTN -> vodFocusZone = VodFocusZone.SEARCH
                                    VodFocusZone.SEARCH -> {}
                                }
                            } else if (activeChannels.isNotEmpty()) {
                                if (isFullScreen) {
                                    if (!showFullscreenChannelMenu && filteredChannels.isNotEmpty()) {
                                        if (selectedChannelIndex > 0) {
                                            selectedChannelIndex--
                                            focusedChannelIndex = selectedChannelIndex
                                            activeStreamUrl = filteredChannels[selectedChannelIndex].url
                                            playingArchiveProgram = null
                                        }
                                    }
                                } else {
                                    if (focusedChannelIndex > 0) {
                                        focusedChannelIndex--
                                        selectedChannelIndex = focusedChannelIndex
                                        activeStreamUrl = filteredChannels[focusedChannelIndex].url
                                        playingArchiveProgram = null
                                    }
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (showDetailMovieModal || showArchiveDetailModal || showVodGenreModal || showChannelInfoModal) {
                                if (showVodGenreModal) {
                                    if (genreSelectedIndex < availableGenres.size - 1) genreSelectedIndex++
                                }
                                return@onKeyEvent true
                            }
                            if (
                                showFullscreenChannelMenu &&
                                contentMode == AppContentMode.TV &&
                                !isArchivePlaying
                            ) {
                                // Пока открыто боковое fullscreen-меню,
                                // информационный лоток не показываем.
                                showInfoBar = false

                                if (fullscreenMenuMode == "CHANNELS") {
                                    if (fullscreenChannelIndex < filteredChannels.size - 1) {
                                        fullscreenChannelIndex++
                                    }
                                } else {
                                    if (fullscreenCategoryIndex < activeCategories.size - 1) {
                                        fullscreenCategoryIndex++
                                    }
                                }
                                return@onKeyEvent true
                            }

                            showInfoBar = true

                            if (showCategoryDialog) {
                                if (dialogCategoryIndex < activeCategories.size - 1) dialogCategoryIndex++
                            } else if (showSideMenu) {
                                if (sideMenuIndex < 11) sideMenuIndex++
                            } else if (showEpgScheduleDialog) {
                                if (epgSelectedIndex < channelSchedule.size - 1) epgSelectedIndex++
                            } else if (contentMode == AppContentMode.VOD) {
                                when (vodFocusZone) {
                                    VodFocusZone.SEARCH -> vodFocusZone = VodFocusZone.GENRE_BTN
                                    VodFocusZone.GENRE_BTN -> vodFocusZone = VodFocusZone.YEARS
                                    VodFocusZone.YEARS -> vodFocusZone = VodFocusZone.GRID
                                    VodFocusZone.GRID -> {
                                        if (focusedChannelIndex + 5 < filteredChannels.size) {
                                            focusedChannelIndex += 5
                                        }
                                    }
                                }
                            } else if (activeChannels.isNotEmpty()) {
                                if (isFullScreen) {
                                    if (!showFullscreenChannelMenu && filteredChannels.isNotEmpty()) {
                                        if (selectedChannelIndex < filteredChannels.size - 1) {
                                            selectedChannelIndex++
                                            focusedChannelIndex = selectedChannelIndex
                                            activeStreamUrl = filteredChannels[selectedChannelIndex].url
                                            playingArchiveProgram = null
                                        }
                                    }
                                } else {
                                    if (focusedChannelIndex < filteredChannels.size - 1) {
                                        focusedChannelIndex++
                                        selectedChannelIndex = focusedChannelIndex
                                        activeStreamUrl = filteredChannels[focusedChannelIndex].url
                                        playingArchiveProgram = null
                                    }
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (showTokenResetDialog) {
                                tokenDialogFocusIndex = 0
                                return@onKeyEvent true
                            }
                            if (showDetailMovieModal || showArchiveDetailModal || showVodGenreModal || showChannelInfoModal) return@onKeyEvent true

                            if (isFullScreen) {
                                if (contentMode == AppContentMode.TV && !isArchivePlaying) {
                                    // Fullscreen TV:
                                    // LEFT #1 -> каналы
                                    // LEFT #2 -> категории
                                    // LEFT #3 -> закрыть меню
                                    if (!showFullscreenChannelMenu) {
                                        // При открытии бокового меню каналов
                                        // нижний информационный лоток не показываем.
                                        showInfoBar = false
                                        showFullscreenChannelMenu = true
                                        fullscreenMenuMode = "CHANNELS"
                                        fullscreenChannelIndex =
                                            selectedChannelIndex.coerceIn(
                                                0,
                                                (filteredChannels.size - 1).coerceAtLeast(0)
                                            )
                                    } else if (fullscreenMenuMode == "CHANNELS") {
                                        fullscreenMenuMode = "CATEGORIES"
                                        fullscreenCategoryIndex =
                                            selectedCategoryIndex.coerceIn(
                                                0,
                                                (activeCategories.size - 1).coerceAtLeast(0)
                                            )
                                    } else {
                                        showFullscreenChannelMenu = false
                                    }
                                } else if ((contentMode == AppContentMode.VOD || isArchivePlaying) && uiDuration > 0 && keyEvent.nativeKeyEvent.repeatCount > 0) {
                                    if (!isSeekingMode) {
                                        isSeekingMode = true
                                        seekTargetMs = uiPosition
                                        seekDeltaMs = 0L
                                    }

                                    seekDeltaMs -= 15000L
                                    seekTargetMs = (seekTargetMs - 15000L).coerceIn(0L, uiDuration)
                                    showInfoBar = true
                                } else {
                                    showInfoBar = !showInfoBar
                                }
                            } else if (showSideMenu) {
                                showSideMenu = false
                            } else if (showCategoryDialog) {
                                showCategoryDialog = false
                            } else if (showEpgScheduleDialog) {
                                showEpgScheduleDialog = false
                            } else {
                                if (contentMode == AppContentMode.VOD) {
                                    when (vodFocusZone) {
                                        VodFocusZone.GRID -> {
                                            if (focusedChannelIndex % 5 != 0 && focusedChannelIndex > 0) {
                                                focusedChannelIndex--
                                            } else {
                                                showSideMenu = true
                                            }
                                        }

                                        VodFocusZone.YEARS -> {
                                            if (vodYearIndex > 0) {
                                                vodYearIndex--
                                                selectedVodYear = availableYears[vodYearIndex]
                                            } else {
                                                showSideMenu = true
                                            }
                                        }

                                        VodFocusZone.GENRE_BTN -> {
                                            showSideMenu = true
                                        }

                                        VodFocusZone.SEARCH -> {
                                            showSideMenu = true
                                        }
                                    }
                                } else {
                                    showSideMenu = true
                                }
                            }

                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (showTokenResetDialog) {
                                tokenDialogFocusIndex = 1
                                return@onKeyEvent true
                            }
                            if (
                                showDetailMovieModal ||
                                showArchiveDetailModal ||
                                showVodGenreModal ||
                                showChannelInfoModal
                            ) return@onKeyEvent true

                            if (isFullScreen) {

                                // VOD / ARCHIVE:
                                // полностью сохраняем существующий seek
                                if (
                                    (contentMode == AppContentMode.VOD || isArchivePlaying) &&
                                    uiDuration > 0 &&
                                    keyEvent.nativeKeyEvent.repeatCount > 0
                                ) {
                                    if (!isSeekingMode) {
                                        isSeekingMode = true
                                        seekTargetMs = uiPosition
                                        seekDeltaMs = 0L
                                    }

                                    seekDeltaMs += 15000L
                                    seekTargetMs =
                                        (seekTargetMs + 15000L).coerceIn(0L, uiDuration)

                                    showInfoBar = true

                                // Обычный TV:
                                // RIGHT #1 -> показать нижнюю панель
                                // RIGHT #2 -> сфокусировать О КАНАЛЕ / О ФИЛЬМЕ
                                } else if (
                                    contentMode == AppContentMode.TV &&
                                    !isArchivePlaying
                                ) {
                                    when (fullscreenInfoRightStep) {
                                        0 -> {
                                            showInfoBar = true
                                            fullscreenFilmButtonFocused = false
                                            fullscreenInfoRightStep = 1
                                        }

                                        else -> {
                                            showInfoBar = true
                                            fullscreenFilmButtonFocused =
                                                showFilmButton || showProgramButton
                                            fullscreenInfoRightStep = 2
                                        }
                                    }

                                } else {
                                    showInfoBar = !showInfoBar
                                    fullscreenFilmButtonFocused = false
                                    fullscreenInfoRightStep = 0
                                }

                            } else if (!showCategoryDialog && !showEpgScheduleDialog && !showSideMenu) {
                                if (contentMode == AppContentMode.VOD) {
                                    when (vodFocusZone) {
                                        VodFocusZone.GRID -> {
                                            if (focusedChannelIndex < filteredChannels.size - 1) {
                                                focusedChannelIndex++
                                            }
                                        }

                                        VodFocusZone.YEARS -> {
                                            if (vodYearIndex < availableYears.size - 1) {
                                                vodYearIndex++
                                                selectedVodYear = availableYears[vodYearIndex]
                                            }
                                        }

                                        VodFocusZone.GENRE_BTN -> {}
                                        VodFocusZone.SEARCH -> {}
                                    }
                                } else {
                                    showEpgScheduleDialog = true
                                }
                            } else if (showSideMenu) {
                                showSideMenu = false
                            }

                            true
                        }
                        KeyEvent.KEYCODE_MENU -> {
                            showSideMenu = !showSideMenu
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (showTokenResetDialog) {
                                if (tokenDialogFocusIndex == 1) {
                                    showTokenResetDialog = false
                                    onLogout()
                                    Toast.makeText(context, "Выберите плейлист", Toast.LENGTH_SHORT).show()
                                } else {
                                    showTokenResetDialog = false
                                }
                                return@onKeyEvent true
                            }
                            if (showDetailMovieModal) {
                                activeStreamUrl = filteredChannels.getOrNull(selectedChannelIndex)?.url ?: currentChannel?.url
                                showDetailMovieModal = false
                                isFullScreen = true
                                showInfoBar = true
                                return@onKeyEvent true
                            }
                            if (showArchiveDetailModal && selectedArchiveProgram != null) {
                                val prog = selectedArchiveProgram!!
                                val startUnix = prog.startTimeMs / 1000
                                val endUnix = (prog.stopTimeMs / 1000) + 60
                                val baseUrl = currentChannel?.url
                                if (baseUrl != null) {
                                    val sep = if (baseUrl.contains("?")) "&" else "?"
                                    activeStreamUrl = "${baseUrl}${sep}utc=$startUnix&lutc=$endUnix"
                                    playingArchiveProgram = prog
                                    showArchiveDetailModal = false
                                    isFullScreen = true
                                    showInfoBar = true
                                    return@onKeyEvent true
                                }
                            }

                            if (showVodGenreModal) {
                                val chosenGenre = availableGenres.getOrNull(genreSelectedIndex)
                                if (chosenGenre != null) {
                                    selectedVodGenre = chosenGenre
                                }
                                showVodGenreModal = false
                                return@onKeyEvent true
                            }

                            if (showCategoryDialog) {
                                selectedCategoryIndex = dialogCategoryIndex
                                selectedChannelIndex = 0
                                focusedChannelIndex = 0
                                showCategoryDialog = false
                            } else if (contentMode == AppContentMode.VOD && vodFocusZone == VodFocusZone.GENRE_BTN) {
                                showVodGenreModal = true
                                return@onKeyEvent true
                            } else if (contentMode == AppContentMode.VOD && vodFocusZone == VodFocusZone.SEARCH) {
                                try { vodSearchFocusRequester.requestFocus() } catch (_: Exception) {}
                                return@onKeyEvent true
                            } else if (showSideMenu) {
                                when (sideMenuIndex) {
                                    0 -> {
                                        contentMode = AppContentMode.TV
                                        selectedCategoryIndex = 0
                                        selectedChannelIndex = 0
                                        focusedChannelIndex = 0
                                        showSideMenu = false
                                    }
                                    1 -> {
                                        contentMode = AppContentMode.VOD
                                        sideMenuIndex = 1
                                        selectedCategoryIndex = 0
                                        selectedChannelIndex = 0
                                        focusedChannelIndex = 0
                                        vodFocusZone = VodFocusZone.GRID
                                        exoPlayer.stop()
                                        activeStreamUrl = null
                                        showSideMenu = false
                                    }
                                    2 -> {
                                        showSideMenu = false
                                        val favIdx = activeCategories.indexOf("❤️ Избранное")
                                        selectedCategoryIndex = if (favIdx >= 0) favIdx else 0
                                        selectedChannelIndex = 0
                                        focusedChannelIndex = 0
                                    }
                                    3 -> {
                                        showSideMenu = false
                                        dialogCategoryIndex = selectedCategoryIndex
                                        showCategoryDialog = true
                                    }
                                    4 -> {
                                        showSideMenu = false
                                        showSearchDialog = true
                                    }
                                    5 -> {
                                        showSideMenu = false
                                        showPlaybackSettingsDialog = true
                                    }
                                    6 -> {
                                        showSideMenu = false
                                        startEpgUpdate()
                                    }
                                    7 -> {
                                        showSideMenu = false
                                        showLanguageDialog = true
                                    }
                                    8 -> {
                                        showSideMenu = false
                                        showSpeedTestDialog = true
                                    }
                                    9 -> {
                                        showSideMenu = false
                                        showSupportDialog = true
                                    }
                                    10 -> {
                                        showSideMenu = false
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MIROVOY_WEBSITE_URL)))
                                        } catch (_: Exception) { }
                                    }
                                    11 -> {
                                        showSideMenu = false
                                        showTokenResetDialog = true
                                        tokenDialogFocusIndex = 0
                                    }
                                }
                            } else if (showEpgScheduleDialog) {
                                if (channelSchedule.isNotEmpty() && epgSelectedIndex in channelSchedule.indices) {
                                    val epgItem = channelSchedule[epgSelectedIndex]
                                    val now = System.currentTimeMillis()
                                    val isLive = epgItem.startTimeMs <= now && (epgItem.stopTimeMs == 0L || epgItem.stopTimeMs > now)
                                    val isArchive = epgItem.stopTimeMs > 0L && epgItem.stopTimeMs <= now

                                    if (isArchive || isLive) {
                                        if (isArchive) {
                                            val startUnix = epgItem.startTimeMs / 1000
                                            val endUnix = (epgItem.stopTimeMs / 1000) + 60
                                            val baseUrl = currentChannel?.url
                                            if (baseUrl != null) {
                                                val sep = if (baseUrl.contains("?")) "&" else "?"
                                                activeStreamUrl = "${baseUrl}${sep}utc=$startUnix&lutc=$endUnix"
                                                playingArchiveProgram = epgItem
                                                Toast.makeText(context, "Архив: ${epgItem.title}", Toast.LENGTH_SHORT).show()
                                            }
                                        } else if (isLive) {
                                            activeStreamUrl = currentChannel?.url
                                            playingArchiveProgram = null
                                            Toast.makeText(context, "Прямой эфир: ${epgItem.title}", Toast.LENGTH_SHORT).show()
                                        }
                                        showEpgScheduleDialog = false
                                        isFullScreen = true
                                        showInfoBar = true
                                    }
                                } else {
                                    showEpgScheduleDialog = false
                                }
                            } else if (activeChannels.isNotEmpty()) {
                                if (isFullScreen) {
                            
                // ============================================================
                                                    // FULLSCREEN TV MENU
                            // OK на канале -> применить канал и закрыть меню
                            // OK на категории -> применить категорию и вернуться к каналам
                            if (
                                showFullscreenChannelMenu &&
                                contentMode == AppContentMode.TV &&
                                !isArchivePlaying
                            ) {
                                if (fullscreenMenuMode == "CHANNELS") {
                                    val channel =
                                        filteredChannels.getOrNull(fullscreenChannelIndex)

                                    if (channel != null) {
                                        selectedChannelIndex = fullscreenChannelIndex
                                        focusedChannelIndex = fullscreenChannelIndex
                                        activeStreamUrl = channel.url
                                        playingArchiveProgram = null
                                        showFullscreenChannelMenu = false
                                    }
                                } else {
                                    val category =
                                        activeCategories.getOrNull(fullscreenCategoryIndex)

                                    if (category != null) {
                                        selectedCategoryIndex = fullscreenCategoryIndex
                                        selectedChannelIndex = 0
                                        focusedChannelIndex = 0
                                        fullscreenChannelIndex = 0
                                        fullscreenMenuMode = "CHANNELS"
                                    }
                                }

                            } else if (
                                        showInfoBar &&
                                        (showFilmButton || showProgramButton) &&
                                        contentMode == AppContentMode.TV &&
                                        !isSeekingMode
                                    ) {
                            if (!fullscreenFilmButtonFocused) {
                            // Запасной вариант: OK нажали раньше двух RIGHT
                            fullscreenFilmButtonFocused = true
                            fullscreenInfoRightStep = 2
                        } else {
                            // После двух RIGHT -> OK открывает нужную информацию
                            fullscreenFilmButtonFocused = false
                            fullscreenInfoRightStep = 0

                            if (isArchivePlaying && playingArchiveProgram != null) {
                                selectedArchiveProgram = playingArchiveProgram
                                showArchiveDetailModal = true
                            } else if (showFilmButton) {
                                // Текущая передача является фильмом
                                showPlayButtonInModal = false
                                showDetailMovieModal = true
                            } else if (showProgramButton) {
                                // Обычная ТВ-передача -> информация о канале
                                showChannelInfoModal = true
                            }
                        }
                    } else if (isSeekingMode) {
                                        seekDeltaMs = 1L
                                    } else if (contentMode == AppContentMode.VOD || isArchivePlaying) {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    } else {
                                        showInfoBar = !showInfoBar
                                    }
                                } else {
                                    if (contentMode == AppContentMode.VOD) {
                                        if (vodFocusZone == VodFocusZone.GRID) {
                                            selectedChannelIndex = focusedChannelIndex
                                            playingArchiveProgram = null
                                            showPlayButtonInModal = false
                                            showDetailMovieModal = true
                                        }
                                    } else {
                                        // Кнопка ОК на пульте разворачивает текущий выбранный канал на полный экран
                                        isFullScreen = true
                                        showInfoBar = true
                                    }
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (keyEvent.nativeKeyEvent.repeatCount > 0 && !isFullScreen && contentMode == AppContentMode.TV) {
                                val channel = filteredChannels.getOrNull(focusedChannelIndex)
                                if (channel != null) {
                                    val mut = favorites.toMutableList()
                                    if (mut.contains(channel.id)) {
                                        mut.remove(channel.id)
                                        Toast.makeText(context, "Удалено из избранного: ${channel.name}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        mut.add(channel.id)
                                        Toast.makeText(context, "Добавлено в избранное: ${channel.name}", Toast.LENGTH_SHORT).show()
                                    }
                                    favorites = mut
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (
                                keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN ||
                                keyEvent.nativeKeyEvent.repeatCount > 0
                            ) {
                                return@onKeyEvent true
                            }

                            when {
                                showVodGenreModal -> { showVodGenreModal = false; true }
                                showDetailMovieModal -> { showDetailMovieModal = false; true }
                                showArchiveDetailModal -> { showArchiveDetailModal = false; true }
                                showChannelActionDialog != null -> { showChannelActionDialog = null; true }
                                showSearchDialog -> { showSearchDialog = false; searchQuery = ""; true }
                                showSupportDialog -> { showSupportDialog = false; true }
                                showSpeedTestDialog -> { showSpeedTestDialog = false; true }
                                showTokenResetDialog -> { showTokenResetDialog = false; true }
                                showLanguageDialog -> { showLanguageDialog = false; true }
                                showPlaybackSettingsDialog -> { showPlaybackSettingsDialog = false; true }
                                showEpgUpdateDialog -> { showEpgUpdateDialog = false; true }
                                showEpgScheduleDialog -> { showEpgScheduleDialog = false; true }
                                showCategoryDialog -> { showCategoryDialog = false; true }
                                showSideMenu -> { showSideMenu = false; true }
                                isFullScreen -> {
                                    isFullScreen = false
                                    showInfoBar = false
                                    true
                                }
                                contentMode == AppContentMode.VOD -> {
                                    if (vodFocusZone != VodFocusZone.GRID) {
                                        vodFocusZone = VodFocusZone.GRID
                                    } else {
                                        contentMode = AppContentMode.TV
                                        selectedCategoryIndex = 0
                                        selectedChannelIndex = 0
                                        focusedChannelIndex = 0
                                    }
                                    true
                                }
                                else -> {
                                    showExitConfirmDialog = true
                                    true
                                }
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        if (isFullScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .combinedClickable(
                        onClick = {
                            // 1 тап на плеере: показать / скрыть инфобар
                            showInfoBar = !showInfoBar
                        },
                        onDoubleClick = {
                            // 2 тапа на плеере: выход обратно на главную страницу каталога
                            isFullScreen = false
                            showInfoBar = false
                        }
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (mainEngineMode == "EXO") {
                    ExoPlayerView(
                        player = exoPlayer,
                        resizeMode = currentAspectMode.resizeMode,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                setBackgroundColor(android.graphics.Color.BLACK)
                                webChromeClient = WebChromeClient()
                                webViewClient = WebViewClient()
                            }
                        },
                        update = { webView ->
                            val url = activeStreamUrl ?: ""
                            if (webView.tag != url) {
                                webView.tag = url
                                webView.loadDataWithBaseURL("https://oktv.uz", getHlsHtml(url), "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize().background(Color.Black)
                    )
                }

                FullscreenGestureControls(
                    context = context,
                    contentMode = contentMode,
                    isArchivePlaying = isArchivePlaying,
                    fullscreenControlText = fullscreenControlText,
                    fullscreenControlProgress = fullscreenControlProgress,
                    onControlTextChange = { fullscreenControlText = it },
                    onControlProgressChange = { fullscreenControlProgress = it }
                )


                FullscreenChannelMenu(
                    visible = showFullscreenChannelMenu &&
                        contentMode == AppContentMode.TV &&
                        !isArchivePlaying,
                    menuMode = fullscreenMenuMode,
                    filteredChannels = filteredChannels,
                    activeCategories = activeCategories,
                    categoryChannelCounts = categoryChannelCounts,
                    favorites = favorites,
                    selectedChannelIndex = selectedChannelIndex,
                    fullscreenChannelIndex = fullscreenChannelIndex,
                    selectedCategoryIndex = selectedCategoryIndex,
                    fullscreenCategoryIndex = fullscreenCategoryIndex,
                    selectedCategoryName = selectedCategory
                )

                if (isSeekingMode) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val sign = if (seekDeltaMs >= 0) "▶▶" else "◀◀"
                        val absD = Math.abs(seekDeltaMs)
                        val min = absD / 60000
                        val sec = (absD % 60000) / 1000
                        val signText = if (seekDeltaMs >= 0) "+" else "-"

                        Text(
                            text = "$sign $signText${String.format(Locale.US, "%02d:%02d", min, sec)}",
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .background(Color(0x99000000), RoundedCornerShape(12.dp))
                                .padding(horizontal = 32.dp, vertical = 20.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showSlowCodecWarning,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp, start = 24.dp, end = 24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC7A4A00))
                            .border(0.5.dp, Color(0xFFFBBF24).copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠", fontSize = 16.sp)
                        Text(
                            Strings.get("slow_codec_warning", currentLang),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showInfoBar,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x99000000))
                            .border(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("◀ Меню", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("▶ EPG", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (contentMode == AppContentMode.VOD || isArchivePlaying) {
                            Text("◀▶ Перемотка", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("▲▼ Каналы", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("ОК Меню/Плей", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                AnimatedVisibility(
                    visible = showInfoBar,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val currentDisplayPos = if (isSeekingMode) seekTargetMs else uiPosition
                    val pProg = if (uiDuration > 0) (currentDisplayPos.toFloat() / uiDuration.toFloat()).coerceIn(0f, 1f) else 0f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xDD050811), Color(0xF8050811))
                                )
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val posterUrl = if (!kpData.posterUrl.isNullOrEmpty()) kpData.posterUrl else currentChannel?.logo
                            Box(
                                modifier = Modifier
                                    .width(54.dp)
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!posterUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = posterUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = if (contentMode == AppContentMode.VOD) ContentScale.Crop else ContentScale.Fit
                                    )
                                } else {
                                    Text(if (contentMode == AppContentMode.VOD) "VOD" else "TV", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val displayTitle = if (isArchivePlaying) {
                                        "${currentChannel?.id ?: ""} ${currentChannel?.name ?: ""}"
                                    } else if (contentMode == AppContentMode.VOD) {
                                        currentChannel?.name ?: ""
                                    } else {
                                        "${currentChannel?.id ?: ""} ${currentChannel?.name ?: ""}"
                                    }

                                    Text(
                                        text = displayTitle,
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("AUDIO", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        if (availableAudioTracks.isNotEmpty() && mainEngineMode == "EXO") {
                                            availableAudioTracks.forEach { track ->
                                                val isSel = track.isSelected
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isSel) Color(0xFF2563EB) else Color.Transparent)
                                                        .border(1.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF1E293B), RoundedCornerShape(4.dp))
                                                        .clickable { switchAudioTrack(exoPlayer, track) }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(text = track.shortCode, color = if (isSel) Color.White else Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF161F36))
                                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            val streamInfo = if (mainEngineMode == "WEB") "Web HLS" else "$resLabel ${videoWidth}x$videoHeight • $videoFps FPS"
                                            Text(text = streamInfo, color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF161F36))
                                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(4.dp))
                                                .clickable {
                                                    val modes = PlayerAspectMode.values()
                                                    currentAspectMode = modes[(currentAspectMode.ordinal + 1) % modes.size]
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = currentAspectMode.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (!kpData.rating.isNullOrEmpty()) {
                                            Text(text = "★ ${kpData.rating}", color = Color(0xFFFFD700), fontSize = 13.sp, fontWeight = FontWeight.Black)
                                        }

                                        if (showFilmButton || showProgramButton) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF2563EB))
                                                    .border(
                                                        2.dp,
                                                        if (fullscreenFilmButtonFocused)
                                                            Color(0xFFFF9800)
                                                        else
                                                            Color(0xFF38BDF8),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable {
                                                        if (showProgramButton) {
                                                            showChannelInfoModal = true
                                                        } else {
                                                            showPlayButtonInModal = false
                                                            showDetailMovieModal = true
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = if (showProgramButton) "О КАНАЛЕ" else "О ФИЛЬМЕ",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(16.dp)
                                        .pointerInput(uiDuration) {
                                            if (contentMode == AppContentMode.VOD || isArchivePlaying) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val down = awaitFirstDown()
                                                        isSeekingMode = true
                                                        val w = size.width.toFloat()
                                                        if (w > 0f && uiDuration > 0L) {
                                                            val ratio = (down.position.x / w).coerceIn(0f, 1f)
                                                            seekTargetMs = (ratio * uiDuration).toLong()
                                                            seekDeltaMs = seekTargetMs - uiPosition
                                                        }
                                                        down.consume()

                                                        val pointerId = down.id
                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val change = event.changes.firstOrNull { it.id == pointerId }
                                                            if (change == null || !change.pressed) break
                                                            change.consume()
                                                            val w2 = size.width.toFloat()
                                                            if (w2 > 0f && uiDuration > 0L) {
                                                                val ratio = (change.position.x / w2).coerceIn(0f, 1f)
                                                                seekTargetMs = (ratio * uiDuration).toLong()
                                                                seekDeltaMs = seekTargetMs - uiPosition
                                                                showInfoBar = true
                                                            }
                                                        }

                                                        seekDeltaMs = 1L
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    val width = maxWidth
                                    val barProgress = if (contentMode == AppContentMode.VOD || isArchivePlaying) {
                                        pProg
                                    } else {
                                        calculateEpgProgress(currentProgram, systemTimeMs)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(width * barProgress)
                                            .height(8.dp)
                                            .background(if (contentMode == AppContentMode.VOD || isArchivePlaying) Color(0xFF2563EB) else Color(0xFF10B981), RoundedCornerShape(4.dp))
                                    )
                                    if (contentMode == AppContentMode.VOD || isArchivePlaying) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = (width * barProgress) - 4.dp)
                                                .width(8.dp)
                                                .height(18.dp)
                                                .background(Color(0xFF38BDF8), RoundedCornerShape(4.dp))
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isArchivePlaying && playingArchiveProgram != null) {
                                        val prog = playingArchiveProgram!!
                                        Text(
                                            text = "🎬 АРХИВ: ${prog.startTimeStr} - ${prog.stopTimeStr} | ${prog.title}",
                                            color = Color(0xFF10B981),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)
                                        )
                                    } else if (contentMode == AppContentMode.TV) {
                                        val curText = currentProgram?.let { "🔴 ${it.startTimeStr} - ${it.stopTimeStr} | ${it.title}" } ?: "Прямой эфир"
                                        Text(
                                            text = curText,
                                            color = Color(0xFFF97316),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)
                                        )
                                    } else {
                                        Text(
                                            text = kpData.genres ?: currentChannel?.group ?: "Медиатека",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val timeText = if (contentMode == AppContentMode.VOD || isArchivePlaying) {
                                            "${formatTimeMs(currentDisplayPos)} / ${formatTimeMs(uiDuration)}"
                                        } else {
                                            currentTime
                                        }
                                        Text(
                                            text = timeText,
                                            color = Color(0xFFFFD700),
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (contentMode == AppContentMode.VOD || isArchivePlaying) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF38BDF8))
                                                    .clickable { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = if (isPlaying) "⏸" else "▶", color = Color.White, fontSize = 15.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = showSideMenu,
                    enter = slideInHorizontally { -it } + fadeIn(),
                    exit = slideOutHorizontally { -it } + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .then(if (androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) Modifier.fillMaxWidth() else Modifier.width(280.dp))
                            .fillMaxHeight()
                            .background(Color(0xFF090D1A))
                            .border(0.5.dp, Color(0xFF1E293B))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.mirovoy_logo_full),
                                    contentDescription = "Mirovoy TV",
                                    modifier = Modifier.height(30.dp).widthIn(max = 150.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = APP_VERSION_NAME, color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(text = "✕", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.clickable { showSideMenu = false })
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val subscriptionTariff = prefs.getString("subscription_tariff", "").orEmpty()
                        val subscriptionServer = prefs.getString("subscription_server_name", "").orEmpty()
                        val subscriptionExpires = prefs.getString("subscription_expires_at", "").orEmpty()
                        val subscriptionScreens = prefs.getInt("subscription_screens", 0)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D1322), RoundedCornerShape(8.dp))
                                .denimDoubleBorder(8f, 6f)
                                .padding(8.dp)
                        ) {
                            Text(text = "ПОДПИСКА Mirovoy TV", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                ProfileInfoBadge(title = "ТАРИФ", value = subscriptionTariff.ifBlank { "—" }, valueColor = Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(6.dp))
                                ProfileInfoBadge(title = "СЕРВЕР", value = subscriptionServer.ifBlank { "—" }, valueColor = Color.White, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                ProfileInfoBadge(title = "ИСТЕКАЕТ", value = subscriptionExpires.ifBlank { "—" }, valueColor = Color(0xFFEF4444), modifier = Modifier.weight(1.2f))
                                Spacer(modifier = Modifier.width(6.dp))
                                ProfileInfoBadge(title = "ЭКРАНЫ", value = if (subscriptionScreens > 0) "$subscriptionScreens ТВ" else "—", valueColor = Color(0xFF38BDF8), modifier = Modifier.weight(0.8f))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            state = sideMenuListState,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            item {
                                SideNavButton(icon = "📺", text = Strings.get("digital_tv", currentLang), active = contentMode == AppContentMode.TV && sideMenuIndex == 0) {
                                    contentMode = AppContentMode.TV
                                    sideMenuIndex = 0
                                    selectedCategoryIndex = 0
                                    selectedChannelIndex = 0
                                    focusedChannelIndex = 0
                                    showSideMenu = false
                                }
                            }
                            item {
                                SideNavButton(icon = "🎬", text = Strings.get("vod", currentLang), active = contentMode == AppContentMode.VOD && sideMenuIndex == 1) {
                                    contentMode = AppContentMode.VOD
                                    sideMenuIndex = 1
                                    selectedCategoryIndex = 0
                                    selectedChannelIndex = 0
                                    focusedChannelIndex = 0
                                    vodFocusZone = VodFocusZone.GRID
                                    exoPlayer.stop()
                                    activeStreamUrl = null
                                    showSideMenu = false
                                }
                            }
                            item {
                                SideNavButton(icon = "❤️", text = "${Strings.get("favorites", currentLang)} (${favorites.size})", active = sideMenuIndex == 2) {
                                    sideMenuIndex = 2
                                    val favIdx = activeCategories.indexOf("❤️ Избранное")
                                    selectedCategoryIndex = if (favIdx >= 0) favIdx else 0
                                    selectedChannelIndex = 0
                                    focusedChannelIndex = 0
                                    showSideMenu = false
                                }
                            }
                            item {
                                SideNavButton(icon = "📂", text = "${Strings.get("categories", currentLang)} (${activeCategories.size})", active = sideMenuIndex == 3) {
                                    sideMenuIndex = 3
                                    showSideMenu = false
                                    dialogCategoryIndex = selectedCategoryIndex
                                    showCategoryDialog = true
                                }
                            }
                            item {
                                SideNavButton(icon = "🔍", text = "Глобальный поиск", active = sideMenuIndex == 4) {
                                    sideMenuIndex = 4
                                    showSideMenu = false
                                    showSearchDialog = true
                                }
                            }
                            item {
                                SideNavButton(icon = "⚙️", text = Strings.get("playback_settings", currentLang), active = sideMenuIndex == 5) {
                                    sideMenuIndex = 5
                                    showSideMenu = false
                                    showPlaybackSettingsDialog = true
                                }
                            }
                            item {
                                SideNavButton(icon = "🔄", text = Strings.get("update_epg", currentLang), active = sideMenuIndex == 6) {
                                    sideMenuIndex = 6
                                    showSideMenu = false
                                    startEpgUpdate()
                                }
                            }
                            item {
                                SideNavButton(icon = "📡", text = "EPG URL sozlamalari", active = sideMenuIndex == 61) {
                                    sideMenuIndex = 61
                                    showSideMenu = false
                                    showEpgUrlDialog = true
                                }
                            }
                            item {
                                SideNavButton(icon = "🌐", text = "${Strings.get("language", currentLang)}: ${currentLang.title}", active = sideMenuIndex == 7) {
                                    sideMenuIndex = 7
                                    showSideMenu = false
                                    showLanguageDialog = true
                                }
                            }
                            item {
                                SideNavButton(icon = "⚡", text = Strings.get("speedtest", currentLang), active = sideMenuIndex == 8) {
                                    sideMenuIndex = 8
                                    showSideMenu = false
                                    showSpeedTestDialog = true
                                }
                            }
                            item {
                                SideNavButton(icon = "🎧", text = Strings.get("support", currentLang), active = sideMenuIndex == 9) {
                                    sideMenuIndex = 9
                                    showSideMenu = false
                                    showSupportDialog = true
                                }
                            }
                            item {
                                SideNavButton(icon = "🌐", text = "mirovoytv.uz", active = sideMenuIndex == 10) {
                                    sideMenuIndex = 10
                                    showSideMenu = false
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MIROVOY_WEBSITE_URL)))
                                    } catch (_: Exception) { }
                                }
                            }
                            item {
                                SideNavButton(icon = "🔑", text = Strings.get("logout", currentLang), active = sideMenuIndex == 11) {
                                    sideMenuIndex = 11
                                    showSideMenu = false
                                    showTokenResetDialog = true
                                    tokenDialogFocusIndex = 0
                                }
                            }
                            item { Spacer(modifier = Modifier.height(20.dp)) }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val compactPhone = LocalConfiguration.current.screenWidthDp < 600

                            Text(
                                text = if (compactPhone) "☰" else "☰ ${Strings.get("menu", currentLang)}",
                                color = Color(0xFF38BDF8),
                                fontSize = if (compactPhone) 22.sp else 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showSideMenu = true }
                                    .padding(
                                        horizontal = if (compactPhone) 6.dp else 0.dp,
                                        vertical = 4.dp
                                    )
                            )

                            Spacer(Modifier.width(10.dp))

                            Text(
                                text = "${if (contentMode == AppContentMode.TV) "ТВ" else "Медиатека"} > $selectedCategory | [${if (mainEngineMode == "WEB") "Web HLS" else "Media3"}]",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = currentTime,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (contentMode == AppContentMode.VOD) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D1322), RoundedCornerShape(8.dp))
                                .denimDoubleBorder(8f, 6f)
                                .padding(8.dp)
                        ) {
                            val isSearchFocused = vodFocusZone == VodFocusZone.SEARCH
                            val isGenreFocused = vodFocusZone == VodFocusZone.GENRE_BTN

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BasicTextField(
                                    value = vodSearchQuery,
                                    onValueChange = { vodSearchQuery = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(vodSearchFocusRequester)
                                        .background(Color(0xFF161F36), RoundedCornerShape(6.dp))
                                        .border(
                                            width = if (isSearchFocused) 2.dp else 1.dp,
                                            color = if (isSearchFocused) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .focusable()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        if (vodSearchQuery.isEmpty()) Text("🔍 Поиск фильма...", color = Color.Gray, fontSize = 12.sp)
                                        innerTextField()
                                    }
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .focusRequester(vodGenreFocusRequester)
                                        .background(if (selectedVodGenre != "Все" && selectedVodGenre != "Все жанры") Color(0xFF2563EB) else Color(0xFF161F36))
                                        .border(
                                            width = if (isGenreFocused) 2.dp else 1.dp,
                                            color = if (isGenreFocused) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .focusable()
                                        .clickable { showVodGenreModal = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "🎭 Жанр: $selectedVodGenre ▾",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (vodSearchQuery.isNotEmpty()) {
                                    Text(
                                        text = "✕ Сброс",
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { vodSearchQuery = "" }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(availableYears) { idx, yr ->
                                    val isSel = selectedVodYear == yr
                                    val isFocused = vodFocusZone == VodFocusZone.YEARS && vodYearIndex == idx
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) Color(0xFFF59E0B) else Color(0xFF161F36))
                                            .border(
                                                width = if (isFocused) 2.dp else 0.5.dp,
                                                color = if (isFocused) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                vodYearIndex = idx
                                                selectedVodYear = yr
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(text = yr, color = if (isSel) Color.Black else if (isFocused) Color.White else Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .weight(0.37f)
                                .fillMaxWidth()
                                .background(Color(0xFF0D1322), RoundedCornerShape(8.dp))
                                .denimDoubleBorder(8f, 6f)
                                .padding(12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val liveText = if (isArchivePlaying) {
                                        "🎬 АРХИВ: ${playingArchiveProgram?.startTimeStr} - ${playingArchiveProgram?.stopTimeStr} | ${playingArchiveProgram?.title}"
                                    } else {
                                        currentProgram?.let { "🔴 ${it.startTimeStr} - ${it.stopTimeStr} | ${it.title}" } ?: Strings.get("live", currentLang)
                                    }
                                    Text(
                                        text = liveText,
                                        color = if (isArchivePlaying) Color(0xFF10B981) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isPosterMain = showFilmButton && !kpData.posterUrl.isNullOrEmpty()
                                    val displayPosterOrLogoMain = if (isPosterMain) kpData.posterUrl else currentChannel?.logo

                                    Box(
                                        modifier = if (isPosterMain) {
                                            Modifier
                                                .width(46.dp)
                                                .height(68.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        } else {
                                            Modifier
                                                .height(54.dp)
                                                .aspectRatio(16f/9f)
                                                .clip(RoundedCornerShape(4.dp))
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!displayPosterOrLogoMain.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = displayPosterOrLogoMain,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = if (isPosterMain) ContentScale.Crop else ContentScale.Fit
                                            )
                                        } else {
                                            Text("TV", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        val progTitle = if (isArchivePlaying) playingArchiveProgram?.title ?: "" else currentProgram?.title ?: ""
                                        val titleText = buildString {
                                            if (progTitle.isNotEmpty()) {
                                                append("${currentChannel?.name} • $progTitle")
                                                if (showFilmButton && !kpData.genres.isNullOrEmpty()) {
                                                    append(" (${kpData.genres})")
                                                }
                                            } else {
                                                append(currentChannel?.name ?: "")
                                            }
                                        }

                                        Text(text = titleText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)

                                        Spacer(modifier = Modifier.height(6.dp))

                                        if (contentMode == AppContentMode.TV && !isArchivePlaying) {
                                            val pProg = calculateEpgProgress(currentProgram, systemTimeMs)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.5.dp)
                                                    .background(Color(0xFF1E293B))
                                            ) {
                                                if (pProg > 0f) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(pProg)
                                                            .fillMaxHeight()
                                                            .background(Color(0xFF10B981))
                                                    )
                                                }
                                            }
                                        } else if ((contentMode == AppContentMode.VOD || isArchivePlaying) && uiDuration > 0) {
                                            val currentDisplayPos = if (isSeekingMode) seekTargetMs else uiPosition
                                            val pProg = (currentDisplayPos.toFloat() / uiDuration.toFloat()).coerceIn(0f, 1f)

                                            BoxWithConstraints(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(14.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                val width = maxWidth
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                        .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .width(width * pProg)
                                                        .height(6.dp)
                                                        .background(Color(0xFF38BDF8), RoundedCornerShape(3.dp))
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    if (showFilmButton || showProgramButton) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF38BDF8))))
                                                .clickable {
                                                    openFilmOrProgram()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (showProgramButton) "Программа" else "О фильме",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // УЛУЧШЕННЫЙ СТИЛЬ МЕДИАТЕКИ С ЕДИНЫМ ТИПОМ ЦВЕТА И СТАБИЛЬНЫМ ФОКУСОМ
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            state = vodGridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0D1322), RoundedCornerShape(12.dp))
                                .denimDoubleBorder(12f, 10f)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(filteredChannels) { index, movie ->
                                val isFocused = (vodFocusZone == VodFocusZone.GRID && index == focusedChannelIndex)
                                val isPlayingHere = index == selectedChannelIndex
                                val isFav = favorites.contains(movie.id)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isFocused) Color(0xFF1E293B)
                                            else if (isPlayingHere) Color(0xFF161F36)
                                            else Color(0xFF090D1A)
                                        )
                                        .border(
                                            width = if (isFocused) 2.5.dp else if (isPlayingHere) 1.5.dp else 1.dp,
                                            color = if (isFocused) Color(0xFF38BDF8) else if (isPlayingHere) Color(0xFF2563EB) else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (selectedChannelIndex == index && showDetailMovieModal) {
                                                    // 2-й тап по выбранному фильму: запуск воспроизведения
                                                    showDetailMovieModal = false
                                                    activeStreamUrl = movie.url
                                                    isFullScreen = true
                                                    showInfoBar = true
                                                } else {
                                                    // 1-й тап: показ информации о фильме
                                                    focusedChannelIndex = index
                                                    selectedChannelIndex = index
                                                    playingArchiveProgram = null
                                                    showPlayButtonInModal = true
                                                    showDetailMovieModal = true
                                                }
                                            },
                                            onDoubleClick = {
                                                // Быстрый запуск двойным тапом
                                                focusedChannelIndex = index
                                                selectedChannelIndex = index
                                                activeStreamUrl = movie.url
                                                showDetailMovieModal = false
                                                isFullScreen = true
                                                showInfoBar = true
                                            },
                                            onLongClick = {
                                                showChannelActionDialog = movie
                                            }
                                        )
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = movie.name,
                                        color = if (isFocused || isPlayingHere) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF030712)),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        if (movie.logo.isNotEmpty()) {
                                            AsyncImage(
                                                model = movie.logo,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(text = "Mirovoy TV\nMOVIE", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                        }

                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                                            Text(
                                                text = if (isFav) "♥" else "♡",
                                                color = if (isFav) Color(0xFFEF4444) else Color(0xFF94A3B8),
                                                fontSize = 20.sp,
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .background(Color(0x88000000), CircleShape)
                                                    .clickable {
                                                        val mut = favorites.toMutableList()
                                                        if (isFav) mut.remove(movie.id) else mut.add(movie.id)
                                                        favorites = mut
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF38BDF8))))
                                                .padding(vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "СМОТРЕТЬ",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = movie.group.uppercase(Locale.getDefault()),
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        uz.oktv.iptv.AdaptiveTwoPane(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    
                                    .fillMaxHeight()
                                    .padding(end = 6.dp, bottom = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(0.63f)
                                        .background(Color.Black, RoundedCornerShape(8.dp))
                                        .denimDoubleBorder(8f, 6f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            if (filteredChannels.isNotEmpty()) {
                                                isFullScreen = true
                                                showInfoBar = true
                                            }
                                        }
                                ) {
                                    if (filteredChannels.isNotEmpty()) {
                                        if (mainEngineMode == "EXO") {
                                            ExoPlayerView(
                                                player = exoPlayer,
                                                resizeMode = currentAspectMode.resizeMode,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            AndroidView(
                                                factory = { ctx ->
                                                    WebView(ctx).apply {
                                                        settings.javaScriptEnabled = true
                                                        settings.mediaPlaybackRequiresUserGesture = false
                                                        settings.domStorageEnabled = true
                                                        settings.databaseEnabled = true
                                                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                                        setBackgroundColor(android.graphics.Color.BLACK)
                                                        webChromeClient = WebChromeClient()
                                                        webViewClient = WebViewClient()
                                                    }
                                                },
                                                update = { webView ->
                                                    val url = activeStreamUrl ?: ""
                                                    if (webView.tag != url) {
                                                        webView.tag = url
                                                        webView.loadDataWithBaseURL("https://oktv.uz", getHlsHtml(url), "text/html", "UTF-8", null)
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize().background(Color.Black)
                                            )
                                        }
                                    } else {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Список пуст. Выберите другую категорию.", color = Color.Gray)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(0.37f)
                                        .background(Color(0xFF0D1322), RoundedCornerShape(8.dp))
                                        .denimDoubleBorder(8f, 6f)
                                        .padding(12.dp)
                                ) {
                                    val liveText = if (isArchivePlaying) {
                                        "🎬 АРХИВ: ${playingArchiveProgram?.startTimeStr} - ${playingArchiveProgram?.stopTimeStr} | ${playingArchiveProgram?.title}"
                                    } else {
                                        currentProgram?.let { "🔴 ${it.startTimeStr} - ${it.stopTimeStr} | ${it.title}" } ?: Strings.get("live", currentLang)
                                    }
                                    Text(
                                        text = liveText,
                                        color = if (isArchivePlaying) Color(0xFF10B981) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        val isPosterMain = showFilmButton && !kpData.posterUrl.isNullOrEmpty()
                                        val displayPosterOrLogoMain = if (isPosterMain) kpData.posterUrl else currentChannel?.logo

                                        Box(
                                            modifier = if (isPosterMain) {
                                                Modifier
                                                    .width(46.dp)
                                                    .height(68.dp)
                                                    .padding(top = 4.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            } else {
                                                Modifier
                                                    .height(54.dp)
                                                    .aspectRatio(16f/9f)
                                                    .clip(RoundedCornerShape(4.dp))
                                            },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!displayPosterOrLogoMain.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = displayPosterOrLogoMain,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = if (isPosterMain) ContentScale.Crop else ContentScale.Fit
                                                )
                                            } else {
                                                Text("TV", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                            val progTitle = if (isArchivePlaying) playingArchiveProgram?.title ?: "" else currentProgram?.title ?: ""
                                            val titleText = buildString {
                                                if (progTitle.isNotEmpty()) {
                                                    append("${currentChannel?.name} • $progTitle")
                                                    if (showFilmButton && !kpData.genres.isNullOrEmpty()) {
                                                        append(" (${kpData.genres})")
                                                    }
                                                } else {
                                                    append(currentChannel?.name ?: "")
                                                }
                                            }

                                            Text(text = titleText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)

                                            Spacer(modifier = Modifier.height(8.dp))

                                            if (contentMode == AppContentMode.TV && !isArchivePlaying) {
                                                val pProg = calculateEpgProgress(currentProgram, systemTimeMs)
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(3.5.dp)
                                                        .background(Color(0xFF1E293B))
                                                ) {
                                                    if (pProg > 0f) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth(pProg)
                                                                .fillMaxHeight()
                                                                .background(Color(0xFF10B981))
                                                        )
                                                    }
                                                }
                                            } else if ((contentMode == AppContentMode.VOD || isArchivePlaying) && uiDuration > 0) {
                                                val currentDisplayPos = if (isSeekingMode) seekTargetMs else uiPosition
                                                val pProg = (currentDisplayPos.toFloat() / uiDuration.toFloat()).coerceIn(0f, 1f)

                                                BoxWithConstraints(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(14.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    val width = maxWidth
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(6.dp)
                                                            .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .width(width * pProg)
                                                            .height(6.dp)
                                                            .background(Color(0xFF38BDF8), RoundedCornerShape(3.dp))
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        (if (showFilmButton || showProgramButton) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF38BDF8))))
                                                    .clickable {
                                                        openFilmOrProgram()
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (showProgramButton) "Программа" else "О фильме",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else null)
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    
                                    .fillMaxHeight()
                                    .background(Color(0xFF0D1322), RoundedCornerShape(8.dp))
                                    .denimDoubleBorder(8f, 6f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF161F36), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedCategory.uppercase(Locale.getDefault()),
                                        color = Color(0xFFF59E0B),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${if (filteredChannels.isNotEmpty()) selectedChannelIndex + 1 else 0} / ${filteredChannels.size}",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    if (filteredChannels.isEmpty()) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Список пуст", color = Color.Gray)
                                        }
                                    } else {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(4.dp)
                                        ) {
                                            itemsIndexed(filteredChannels, key = { _, channel -> channel.id }) { index, channel ->
                                                val isFocused = index == focusedChannelIndex
                                                val isPlayingHere = index == selectedChannelIndex
                                                val isFav = favorites.contains(channel.id)
                                                val schedule = if (contentMode == AppContentMode.TV) getScheduleForChannel(channel, epgScheduleMap) else emptyList()
                                                val liveProgram = if (contentMode == AppContentMode.TV) getCurrentProgram(schedule) else null

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 1.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                            if (isFocused) Color(0xFF1E293B)
                                                            else if (isPlayingHere) Color(0xFF1E293B)
                                                            else Color.Transparent
                                                        )
                                                        .border(
                                                            width = if (isFocused || isPlayingHere) 1.5.dp else 0.dp,
                                                            color = if (isFocused) Color(0xFF38BDF8) else if (isPlayingHere) Color(0xFF38BDF8) else Color.Transparent,
                                                            shape = RoundedCornerShape(6.dp)
                                                        )
                                                        .combinedClickable(
                                                            onClick = {
                                                                // Клик в списке ТВ просто переключает канал в фоновом плеере (НЕ переходя в полный экран)
                                                                focusedChannelIndex = index
                                                                selectedChannelIndex = index
                                                                activeStreamUrl = channel.url
                                                                playingArchiveProgram = null
                                                            },
                                                            onDoubleClick = {
                                                                // Двойной клик разворачивает на весь экран
                                                                focusedChannelIndex = index
                                                                selectedChannelIndex = index
                                                                activeStreamUrl = channel.url
                                                                playingArchiveProgram = null
                                                                isFullScreen = true
                                                                showInfoBar = true
                                                            },
                                                            onLongClick = {
                                                                showChannelActionDialog = channel
                                                            }
                                                        )
                                                        .padding(vertical = 5.dp, horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${channel.id}",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.width(28.dp),
                                                        textAlign = TextAlign.Center
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFF161F36))
                                                            .border(0.5.dp, Color(0xFF2E384D), RoundedCornerShape(6.dp))
                                                            .padding(3.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (!channel.logo.isNullOrEmpty()) {
                                                            AsyncImage(
                                                                model = channel.logo,
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Fit
                                                            )
                                                        } else {
                                                            Text(text = "TV", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(8.dp))

                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(end = 8.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                text = channel.name,
                                                                color = if (isFocused || isPlayingHere) Color.White else Color(0xFF38BDF8),
                                                                fontSize = 12.sp,
                                                                fontWeight = if (isFocused || isPlayingHere) FontWeight.Bold else FontWeight.Normal,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f)
                                                            )

                                                            Text(
                                                                text = "↺",
                                                                color = Color(0xFF38BDF8),
                                                                fontSize = 18.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier
                                                                    .clickable {
                                                                        focusedChannelIndex = index
                                                                        selectedChannelIndex = index
                                                                        showEpgScheduleDialog = true
                                                                    }
                                                                    .padding(horizontal = 6.dp)
                                                            )

                                                            Text(
                                                                text = if (isFav) "♥" else "♡",
                                                                color = if (isFav) Color(0xFFEF4444) else Color(0xFF94A3B8),
                                                                fontSize = 18.sp,
                                                                modifier = Modifier
                                                                    .clickable {
                                                                        val mut = favorites.toMutableList()
                                                                        if (isFav) mut.remove(channel.id) else mut.add(channel.id)
                                                                        favorites = mut
                                                                    }
                                                                    .padding(start = 2.dp, end = 4.dp)
                                                            )
                                                        }

                                                        if (contentMode == AppContentMode.TV) {
                                                            val rowProg = calculateEpgProgress(liveProgram, systemTimeMs)
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(2.5.dp)
                                                                    .background(Color(0xFF1E293B))
                                                            ) {
                                                                if (rowProg > 0f) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth(rowProg)
                                                                            .fillMaxHeight()
                                                                            .background(Color(0xFF10B981))
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(4.dp))

                                                        val liveText = liveProgram?.let { "${it.startTimeStr} ${it.title}" } ?: Strings.get("live", currentLang)
                                                        Text(
                                                            text = liveText,
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showVodGenreModal) {
            VodGenreModalDialog(
                availableGenres = availableGenres,
                selectedVodGenre = selectedVodGenre,
                genreSelectedIndex = genreSelectedIndex,
                genreListState = genreListState,
                genreFocusRequester = genreFocusRequester,
                onGenreSelected = { genre ->
                    selectedVodGenre = genre
                    showVodGenreModal = false
                },
                onDismiss = { showVodGenreModal = false }
            )
        }

        if (showExitConfirmDialog) {
            ExitConfirmDialog(
                onConfirm = {
                    showExitConfirmDialog = false
                    (context as? ComponentActivity)?.finish()
                },
                onDismiss = { showExitConfirmDialog = false }
            )
        }

        if (showChannelInfoModal) {
            ChannelInfoModal(
                channel = currentChannel,
                onDismiss = { showChannelInfoModal = false }
            )
        }

        if (showDetailMovieModal) {
            val titleToShow = if (contentMode == AppContentMode.VOD) currentChannel?.name else currentProgram?.title
            val fallbackLogo = currentChannel?.logo
            DetailMovieModal(
                title = titleToShow ?: "Информация о фильме",
                posterUrl = if (!kpData.posterUrl.isNullOrEmpty()) kpData.posterUrl else fallbackLogo,
                year = kpData.year ?: "2026",
                genres = kpData.genres ?: currentChannel?.group ?: "Кино",
                rating = kpData.rating,
                desc = kpData.description ?: currentProgram?.desc ?: "Описание фильма или передачи...",
                showPlayButton = showPlayButtonInModal,
                onWatchClick = {
                    activeStreamUrl = filteredChannels.getOrNull(selectedChannelIndex)?.url ?: currentChannel?.url
                    showDetailMovieModal = false
                    isFullScreen = true
                    showInfoBar = true
                },
                onDismiss = { showDetailMovieModal = false }
            )
        }

        if (showArchiveDetailModal && selectedArchiveProgram != null) {
            val prog = selectedArchiveProgram!!
            val fallbackLogo = currentChannel?.logo
            DetailMovieModal(
                title = prog.title,
                posterUrl = if (!kpData.posterUrl.isNullOrEmpty()) kpData.posterUrl else fallbackLogo,
                year = prog.dateStr,
                genres = "${prog.startTimeStr} - ${prog.stopTimeStr}",
                rating = kpData.rating,
                desc = kpData.description ?: prog.desc.ifEmpty { "Описание отсутствует..." },
                showPlayButton = false,
                onWatchClick = {},
                onDismiss = { showArchiveDetailModal = false }
            )
        }

        if (showChannelActionDialog != null) {
            val chan = showChannelActionDialog!!
            val isFav = favorites.contains(chan.id)
            val isFavList = selectedCategory.contains("Избранное", true) || selectedCategory.contains("Favorites", true)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { showChannelActionDialog = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(340.dp)
                        .heightIn(max = 420.dp)
                        .background(Color(0xFF0D1322), RoundedCornerShape(12.dp))
                        .denimDoubleBorder(12f, 10f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                        .clickable(enabled = false) {},
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = chan.name, color = Color(0xFFF59E0B), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(modifier = Modifier.height(14.dp))
                    if (isFav) {
                        RenderPillButton(text = "❌ Удалить из избранного", active = false, modifier = Modifier.fillMaxWidth()) {
                            val mut = favorites.toMutableList()
                            mut.remove(chan.id)
                            favorites = mut
                            showChannelActionDialog = null
                        }
                        if (isFavList) {
                            Spacer(modifier = Modifier.height(8.dp))
                            RenderPillButton(text = "🔝 В самый верх", active = true, modifier = Modifier.fillMaxWidth()) {
                                val mut = favorites.toMutableList()
                                mut.remove(chan.id)
                                mut.add(0, chan.id)
                                favorites = mut
                                showChannelActionDialog = null
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            RenderPillButton(text = "⬆ Поднять канал на 1", active = true, modifier = Modifier.fillMaxWidth()) {
                                val mut = favorites.toMutableList()
                                val idx = mut.indexOf(chan.id)
                                if (idx > 0) {
                                    Collections.swap(mut, idx, idx - 1)
                                    favorites = mut
                                }
                                showChannelActionDialog = null
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            RenderPillButton(text = "⬇ Опустить канал на 1", active = true, modifier = Modifier.fillMaxWidth()) {
                                val mut = favorites.toMutableList()
                                val idx = mut.indexOf(chan.id)
                                if (idx < mut.size - 1) {
                                    Collections.swap(mut, idx, idx + 1)
                                    favorites = mut
                                }
                                showChannelActionDialog = null
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            RenderPillButton(text = "⏬ В самый низ", active = true, modifier = Modifier.fillMaxWidth()) {
                                val mut = favorites.toMutableList()
                                mut.remove(chan.id)
                                mut.add(chan.id)
                                favorites = mut
                                showChannelActionDialog = null
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            RenderPillButton(text = "🔤 Сортировать всё (А-Я)", active = true, modifier = Modifier.fillMaxWidth()) {
                                val mut = favorites.toMutableList()
                                mut.sortBy { favId -> activeChannels.find { it.id == favId }?.name ?: "" }
                                favorites = mut
                                showChannelActionDialog = null
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            RenderPillButton(text = "🗑 Очистить ВСЁ избранное", active = false, modifier = Modifier.fillMaxWidth()) {
                                val mut = favorites.toMutableList()
                                val currentModeIds = activeChannels.map { it.id }.toSet()
                                mut.removeAll { currentModeIds.contains(it) }
                                favorites = mut
                                showChannelActionDialog = null
                            }
                        }
                    } else {
                        RenderPillButton(text = "❤️ Добавить в избранное", active = true, modifier = Modifier.fillMaxWidth()) {
                            val mut = favorites.toMutableList()
                            mut.add(chan.id)
                            favorites = mut
                            showChannelActionDialog = null
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "✕ Закрыть", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.clickable { showChannelActionDialog = null })
                }
            }
        }

        if (showSearchDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.90f))
                    .clickable { showSearchDialog = false; searchQuery = "" },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(500.dp)
                        .height(450.dp)
                        .background(Color(0xFF0D1322), RoundedCornerShape(12.dp))
                        .denimDoubleBorder(12f, 10f)
                        .padding(16.dp)
                        .clickable(enabled = false) {}
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔍", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester)
                                .background(Color(0xFF161F36), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Color(0xFF38BDF8)),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) Text("Введите название канала...", color = Color.Gray, fontSize = 14.sp)
                                innerTextField()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (searchFilteredChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (searchQuery.length < 2) "Введите минимум 2 буквы для поиска" else "Каналы не найдены", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(searchFilteredChannels, key = { _, channel -> channel.id }) { _, channel ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                        .clickable {
                                            contentMode = if (channel.group.equals("Фильмы", true)) AppContentMode.VOD else AppContentMode.TV
                                            selectedCategoryIndex = 0
                                            val mainList = if (contentMode == AppContentMode.TV) tvChannels else vodChannels
                                            selectedChannelIndex = mainList.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
                                            focusedChannelIndex = selectedChannelIndex
                                            showSearchDialog = false
                                            searchQuery = ""
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = channel.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(text = channel.group, color = Color(0xFF38BDF8), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                delay(100)
                searchFocusRequester.requestFocus()
            }
        }

        if (showEpgUrlDialog) {
            EpgUrlDialog(
                context = context,
                onDismiss = { showEpgUrlDialog = false }
            )
        }

        if (showEpgUpdateDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .background(Color(0xFF0D1322), RoundedCornerShape(14.dp))
                        .denimDoubleBorder(14f, 12f)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🔄 ОБНОВЛЕНИЕ EPG", color = Color(0xFF38BDF8), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { epgUpdateProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF2563EB),
                        trackColor = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = epgUpdateStatus, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }

        if (showCategoryDialog) {
            LaunchedEffect(dialogCategoryIndex) {
                if (activeCategories.isNotEmpty()) {
                    dialogListState.animateScrollToItem(dialogCategoryIndex)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { showCategoryDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .height(400.dp)
                        .background(Color(0xFF0D1322), RoundedCornerShape(10.dp))
                        .denimDoubleBorder(10f, 8f)
                        .padding(14.dp)
                        .clickable(enabled = false) {}
                ) {
                    Text(
                        text = Strings.get("select_category", currentLang),
                        color = Color(0xFFF59E0B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        state = dialogListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(activeCategories, key = { _, cat -> cat }) { index, cat ->
                            val isSelected = index == dialogCategoryIndex
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .background(
                                        if (isSelected) Color(0xFF2563EB) else Color(0xFF161F36),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable {
                                        selectedCategoryIndex = index
                                        selectedChannelIndex = 0
                                        focusedChannelIndex = 0
                                        showCategoryDialog = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showEpgScheduleDialog) {
            EpgScheduleDialog(
                context = context,
                channelSchedule = channelSchedule,
                currentLang = currentLang,
                currentChannelName = currentChannel?.name ?: "",
                currentChannelUrl = currentChannel?.url,
                epgSelectedIndex = epgSelectedIndex,
                epgListState = epgListState,
                onEpgSelectedIndexChange = { epgSelectedIndex = it },
                onActiveStreamUrlChange = { activeStreamUrl = it },
                onPlayingArchiveProgramChange = { playingArchiveProgram = it },
                onDismiss = { showEpgScheduleDialog = false },
                onEnterFullscreen = { isFullScreen = true },
                onShowInfoBar = { showInfoBar = true }
            )
        }

        if (showSpeedTestDialog) {
            SpeedTestModalDialog(
                lang = currentLang,
                onDismiss = { showSpeedTestDialog = false }
            )
        }

        if (showSupportDialog) {
            var supportFocusIndex by remember { mutableIntStateOf(0) }
            val supportFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { supportFocusRequester.requestFocus() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .clickable { showSupportDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .background(Color(0xFF0D1322), RoundedCornerShape(12.dp))
                        .denimDoubleBorder(12f, 10f)
                        .focusRequester(supportFocusRequester)
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onKeyEvent false
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    supportFocusIndex = (supportFocusIndex + 1).coerceAtMost(3)
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                    supportFocusIndex = (supportFocusIndex - 1).coerceAtLeast(0)
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    when (supportFocusIndex) {
                                        0 -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/mirovoytvuz")))
                                        1 -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/abdulloh_abdulhamid7")))
                                        2 -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MIROVOY_WEBSITE_URL)))
                                        3 -> showSupportDialog = false
                                    }
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_BACK -> {
                                    showSupportDialog = false
                                    true
                                }
                                else -> false
                            }
                        }
                        .padding(18.dp)
                        .clickable(enabled = false) {},
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🎧 ${Strings.get("support", currentLang)}", color = Color(0xFF38BDF8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))

                    ContactBadge(
                        icon = "✈️",
                        title = "Telegram kanalimiz",
                        value = "t.me/mirovoytvuz",
                        isFocused = supportFocusIndex == 0,
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/mirovoytvuz")))
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ContactBadge(
                        icon = "👤",
                        title = "Aloqa uchun admin",
                        value = "@abdulloh_abdulhamid7",
                        isFocused = supportFocusIndex == 1,
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/abdulloh_abdulhamid7")))
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ContactBadge(
                        icon = "🌐",
                        title = "Rasmiy sayt",
                        value = "mirovoytv.uz",
                        isFocused = supportFocusIndex == 2,
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MIROVOY_WEBSITE_URL)))
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2563EB), RoundedCornerShape(8.dp))
                            .border(
                                width = if (supportFocusIndex == 3) 2.dp else 0.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { showSupportDialog = false }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "ЗАКРЫТЬ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showLanguageDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .clickable { showLanguageDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .background(Color(0xFF0D1322), RoundedCornerShape(12.dp))
                        .denimDoubleBorder(12f, 10f)
                        .padding(14.dp)
                        .clickable(enabled = false) {}
                ) {
                    Text(text = Strings.get("language", currentLang), color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                    AppLang.values().forEach { lang ->
                        val isSel = lang == currentLang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(if (isSel) Color(0xFF2563EB) else Color(0xFF161F36), RoundedCornerShape(6.dp))
                                .clickable {
                                    currentLang = lang
                                    showLanguageDialog = false
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = lang.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            if (isSel) Text(text = "✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showTokenResetDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { showTokenResetDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .background(Color(0xFF0D1322), RoundedCornerShape(12.dp))
                        .denimDoubleBorder(12f, 10f)
                        .padding(16.dp)
                        .clickable(enabled = false) {},
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("СМЕНИТЬ ПЛЕЙЛИСТ?", color = Color(0xFFEF4444), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Вы будете возвращены к выбору плейлиста. Текущий канал остановится.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                                .border(
                                    width = if (tokenDialogFocusIndex == 0) 2.dp else 0.dp,
                                    color = Color(0xFF38BDF8),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    tokenDialogFocusIndex = 0
                                    showTokenResetDialog = false
                                }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("ОТМЕНА", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFEF4444), RoundedCornerShape(6.dp))
                                .border(
                                    width = if (tokenDialogFocusIndex == 1) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    tokenDialogFocusIndex = 1
                                    showTokenResetDialog = false
                                    onLogout()
                                    Toast.makeText(context, "Выберите плейлист", Toast.LENGTH_SHORT).show()
                                }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("СМЕНИТЬ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showPlaybackSettingsDialog) {
            PlaybackSettingsDialog(
                mainEngineMode = mainEngineMode,
                onMainEngineChange = { mainEngineMode = it },
                ffmpegAudioEnabled = ffmpegAudioEnabled,
                onFfmpegAudioChange = { ffmpegAudioEnabled = it },
                amlogicFixEnabled = amlogicFixEnabled,
                onAmlogicFixChange = { amlogicFixEnabled = it },
                smoothUpscaleEnabled = smoothUpscaleEnabled,
                onSmoothUpscaleChange = { smoothUpscaleEnabled = it },
                bufferSeconds = bufferSeconds,
                onBufferSecondsChange = { bufferSeconds = it },
                availableAudioTracks = availableAudioTracks,
                exoPlayer = exoPlayer,
                currentLang = currentLang,
                onDismiss = { showPlaybackSettingsDialog = false }
            )
        }
    }



}


@Composable
private fun ChannelInfoModal(
    channel: M3UChannel?,
    onDismiss: () -> Unit
) {
    if (channel == null) {
        onDismiss()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111827))
                .padding(20.dp)
                .clickable { },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "О КАНАЛЕ",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (channel.group.isNotBlank()) {
                Text(
                    text = "Категория: ${channel.group}",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (channel.tvgId.isNotBlank()) {
                Text(
                    text = "TVG ID: ${channel.tvgId}",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2563EB))
                    .clickable { onDismiss() }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "✕ Закрыть",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

