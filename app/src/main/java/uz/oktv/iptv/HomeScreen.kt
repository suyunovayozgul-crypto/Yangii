package uz.oktv.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(
    channels: List<M3UChannel> = emptyList(),
    epgMap: Map<String, List<EpgProgram>> = emptyMap(),
    subscriptionExpires: String = "",
    onLiveTv: () -> Unit,
    onMovies: () -> Unit,
    onSport: () -> Unit,
    onSeries: () -> Unit,
    onPlaylist: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // TMDB ma'lumotlari
    var newMovies by remember { mutableStateOf<List<TmdbMovie>>(emptyList()) }
    var trendingMovies by remember { mutableStateOf<List<TmdbMovie>>(emptyList()) }
    var tmdbLoading by remember { mutableStateOf(true) }

    // Hozirgi vaqt
    val nowMs = System.currentTimeMillis()

    // EPG dan sport o'yinlari — hozir yoki 2 soat ichida
    val sportPrograms = remember(epgMap, channels) {
        val sportKeywords = listOf("футбол", "хоккей", "баскетбол", "теннис", "ufc", "матч",
            "чемпионат", "лига", "кубок", "sport", "football", "soccer",
            "basketball", "hockey", "tennis", "match", "champion")

        val sportChannelNames = listOf("матч", "sport", "eurosport", "setanta",
            "футбол", "nba", "ufc", "formula")

        val result = mutableListOf<Triple<M3UChannel, EpgProgram, Boolean>>()

        channels.filter { ch ->
            sportChannelNames.any { kw -> ch.name.lowercase().contains(kw) } ||
            ch.group.lowercase().contains("спорт") ||
            ch.group.lowercase().contains("sport")
        }.forEach { ch ->
            val programs = epgMap[ch.tvgId] ?: epgMap[ch.name] ?: return@forEach
            programs.forEach { prog ->
                val isNow = prog.startTimeMs <= nowMs && prog.stopTimeMs >= nowMs
                val isSoon = prog.startTimeMs > nowMs && prog.startTimeMs - nowMs < 2 * 3600 * 1000
                if ((isNow || isSoon) && sportKeywords.any { kw -> prog.title.lowercase().contains(kw) }) {
                    result.add(Triple(ch, prog, isNow))
                }
            }
        }
        result.take(6)
    }

    // TMDB yuklash
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val movies  = TmdbRepository.fetchNowPlaying()
                val trending = TmdbRepository.fetchTrending()
                launch(Dispatchers.Main) {
                    newMovies     = movies
                    trendingMovies = trending
                    tmdbLoading   = false
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { tmdbLoading = false }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── HEADER ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.mirovoy_logo_full),
                    contentDescription = "Mirovoy TV",
                    modifier = Modifier.height(30.dp).widthIn(max = 150.dp),
                    contentScale = ContentScale.Fit
                )
                if (subscriptionExpires.isNotEmpty()) {
                    Text(
                        text = "Muddat: $subscriptionExpires",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            // ── ASOSIY KARTOCHKALAR ──────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Live TV — katta
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(130.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1A3A6B), Color(0xFF2563EB))
                                )
                            )
                            .clickable { onLiveTv() }
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("📺", fontSize = 32.sp)
                            Column {
                                Text("Jonli Efir", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("${channels.size} kanal", color = Color.White.copy(0.7f), fontSize = 10.sp)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            Triple("🎬", "Kinolar", onMovies),
                            Triple("🏆", "Sport", onSport)
                        ).forEach { (icon, title, action) ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0D1322))
                                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                    .clickable { action() }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(icon, fontSize = 22.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Seriallar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF4A1A6B), Color(0xFF9333EA))))
                        .clickable { onSeries() }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📽️", fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("Seriallar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── SPORT O'YINLARI (EPG dan) ────────────────────────────
            if (sportPrograms.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚽ Muhim O'yinlar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        sportPrograms.forEach { (channel, program, isNow) ->
                            Box(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0D1322))
                                    .border(
                                        1.dp,
                                        if (isNow) Color(0xFF16A34A) else Color(0xFF1E293B),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSport() }
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                                    if (isNow) {
                                        Text("🔴 JONLI", color = Color(0xFF16A34A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("🕐 ${program.startTimeStr}", color = Color(0xFF94A3B8), fontSize = 9.sp)
                                    }
                                    Text(program.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(channel.name, color = Color(0xFF38BDF8), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // ── YANGI KINOLAR (TMDB) ─────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔥 Yangi Kinolar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                if (tmdbLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF2563EB), modifier = Modifier.size(24.dp))
                    }
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        newMovies.forEach { movie ->
                            Column(
                                modifier = Modifier
                                    .width(110.dp)
                                    .clickable { onMovies() }
                            ) {
                                AsyncImage(
                                    model = movie.posterUrl,
                                    contentDescription = movie.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(155.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = R.drawable.ic_tv_placeholder)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(movie.title, color = Color.White, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text("⭐ ${movie.rating}", color = Color(0xFFF59E0B), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            // ── TRENDLAR (TMDB) ──────────────────────────────────────
            if (trendingMovies.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📈 Trendlar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        trendingMovies.forEach { movie ->
                            Column(modifier = Modifier.width(110.dp).clickable { onMovies() }) {
                                AsyncImage(
                                    model = movie.posterUrl,
                                    contentDescription = movie.title,
                                    modifier = Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = R.drawable.ic_tv_placeholder)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(movie.title, color = Color.White, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text("⭐ ${movie.rating}", color = Color(0xFFF59E0B), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            // ── QUYI TUGMALAR ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("⚙️", "Sozlamalar", onSettings),
                    Triple("🔄", "Yangilash", onRefresh),
                    Triple("🔑", "Playlist", onPlaylist),
                    Triple("🚪", "Chiqish", onExit)
                ).forEach { (icon, label, action) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0D1322))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .clickable { action() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(icon, fontSize = 18.sp)
                            Text(label, color = Color(0xFF94A3B8), fontSize = 9.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}
