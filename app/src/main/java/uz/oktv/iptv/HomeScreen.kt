package uz.oktv.iptv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import uz.oktv.iptv.model.AppLang
import uz.oktv.iptv.utils.Strings

// Ranglar
private val BgDark       = Color(0xFF0A1628)
private val CardDark     = Color(0xFF0D1F35)
private val CardSelected = Color(0xFF0D1F35)
private val AccentPink   = Color(0xFFE91E63)
private val AccentBlue   = Color(0xFF1E88E5)
private val TextWhite    = Color.White
private val TextGray     = Color(0xFF8899AA)
private val LiveGreen    = Color(0xFF4CAF50)
private val LiveRed      = Color(0xFFE53935)

@Composable
fun HomeScreen(
    channels: List<M3UChannel> = emptyList(),
    epgMap: Map<String, List<EpgProgram>> = emptyMap(),
    lang: AppLang = AppLang.RU,
    onLiveTv: () -> Unit,
    onMovies: () -> Unit,
    onSport: () -> Unit,
    onSeries: () -> Unit,
    onPlaylist: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onExit: () -> Unit
) {
    val nowMs = System.currentTimeMillis()
    fun s(key: String) = Strings.get(key, lang)

    val sportKeywords = remember { listOf(
        "real madrid","barcelona","atletico","manchester city","manchester united",
        "liverpool","chelsea","arsenal","tottenham","bayern","borussia","dortmund",
        "juventus","milan","inter","napoli","roma","psg",
        "cska","зенит","zenit","spartak","спартак","lokomotiv","локомотив","dynamo","динамо",
        "pakhtakor","пахтакор","bunyodkor","бунёдкор","nasaf","neftchi",
        "сборная","national team",
        "лига чемпионов","champions league","europa league","premier league",
        "la liga","serie a","bundesliga","world cup","чемпионат мира",
        "финал","final","полуфинал","semifinal","кубок","cup",
        "ufc","mma","бокс","boxing","formula 1","гран-при","nba","nhl",
        "хоккей","баскетбол","волейбол","теннис","wimbledon",
        "матч","match","турнир","лига","league","чемпионат"
    ) }

    val sportChKw = remember { listOf(
        "матч","sport","eurosport","setanta","футбол","nba","ufc","formula","бокс","arena","bein"
    ) }

    val kinoChKw = remember { listOf(
        "кино","cinema","movie","film","premier","tv1000","наше кино","мегахит","amedia"
    ) }

    val sportItems = remember(epgMap, channels) {
        channels.filter { ch ->
            sportChKw.any { ch.name.lowercase().contains(it) } ||
            ch.group.lowercase().contains("спорт") || ch.group.lowercase().contains("sport")
        }.mapNotNull { ch ->
            val prog = (epgMap[ch.tvgId] ?: epgMap[ch.name])?.firstOrNull { p ->
                val isNow  = p.startTimeMs <= nowMs && p.stopTimeMs >= nowMs
                val isSoon = p.startTimeMs > nowMs && (p.startTimeMs - nowMs) < 3 * 3600 * 1000L
                (isNow || isSoon) && sportKeywords.any { k -> p.title.lowercase().contains(k) }
            }
            if (prog != null) Triple(ch, prog, prog.startTimeMs <= nowMs && prog.stopTimeMs >= nowMs) else null
        }.sortedByDescending { (_, _, isNow) -> isNow }.take(10)
    }

    val movieItems = remember(epgMap, channels) {
        channels.filter { ch ->
            kinoChKw.any { ch.name.lowercase().contains(it) } ||
            ch.group.lowercase().contains("кино") || ch.group.lowercase().contains("фильм")
        }.mapNotNull { ch ->
            val prog = (epgMap[ch.tvgId] ?: epgMap[ch.name])?.firstOrNull { p ->
                p.startTimeMs <= nowMs && p.stopTimeMs >= nowMs
            }
            if (prog != null) Pair(ch, prog) else null
        }.take(10)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── HEADER: MIROVOY + IPTV PLAYER ────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "MIROVOY",
                        color = TextWhite,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = s("home_iptv_player"),
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                }
                Text(
                    text = APP_VERSION_NAME,
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── ASOSIY GRID — 3-rasmga o'xshash ──────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Jonli Efir — katta karta, qizil border
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardDark)
                        .border(2.dp, AccentPink, RoundedCornerShape(16.dp))
                        .clickable { onLiveTv() }
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentPink),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.LiveTv, null, tint = TextWhite, modifier = Modifier.size(28.dp))
                        }
                        Column {
                            Text(s("home_live"), color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${channels.size} ${s("home_channels")}", color = TextGray, fontSize = 12.sp)
                        }
                    }
                }

                // O'ng 2x2 grid
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallCard(Icons.Filled.Movie,        null, Modifier.weight(1f).height(109.dp), onMovies)
                        SmallCard(Icons.Filled.Tv,           null, Modifier.weight(1f).height(109.dp), onSeries)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallCard(Icons.Filled.SportsSoccer, null, Modifier.weight(1f).height(109.dp), onSport)
                        SmallCard(Icons.Filled.SwapHoriz,    null, Modifier.weight(1f).height(109.dp), onPlaylist)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── QUYI 3 TUGMA ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BottomBtn(Icons.Filled.Settings,  s("home_settings"), Modifier.weight(1f), onSettings)
                BottomBtn(Icons.Filled.Refresh,   s("home_refresh"),  Modifier.weight(1f), onRefresh)
                BottomBtn(Icons.Filled.ExitToApp, s("home_exit"),     Modifier.weight(1f), onExit)
            }

            // ── SPORT E'LONLARI ───────────────────────────────────────
            if (sportItems.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(s("home_now_sport"), color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sportItems.forEach { (ch, prog, isNow) ->
                        SportCard(ch, prog, isNow, s("home_live_badge"), onSport)
                    }
                }
            }

            // ── KINO E'LONLARI ────────────────────────────────────────
            if (movieItems.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(s("home_now_movies"), color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    movieItems.forEach { (ch, prog) ->
                        SportCard(ch, prog, true, s("home_live_badge"), onMovies)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SmallCard(icon: ImageVector, label: String?, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = TextGray, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun BottomBtn(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = AccentBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextGray, fontSize = 10.sp)
    }
}

@Composable
private fun SportCard(
    channel: M3UChannel, prog: EpgProgram,
    isNow: Boolean, liveBadge: String, onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, if (isNow) LiveGreen else Color(0xFF1E3A5F), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)).background(AccentPink),
                    contentAlignment = Alignment.Center
                ) {
                    Text(channel.name.take(2).uppercase(), color = TextWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape)
                    .background(if (isNow) LiveGreen else LiveRed)
            )
        }
        Text(prog.title, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(channel.name, color = AccentBlue, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (isNow) liveBadge else "🕐 ${prog.startTimeStr}",
            color = if (isNow) LiveGreen else TextGray,
            fontSize = 9.sp, fontWeight = FontWeight.Bold
        )
    }
}
