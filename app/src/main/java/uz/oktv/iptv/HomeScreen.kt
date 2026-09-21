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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import uz.oktv.iptv.model.AppLang
import uz.oktv.iptv.utils.Strings

// ── Yangi dizayn ranglari (kinematik/premium uslub) ──────────────────
private val BgTop      = Color(0xFF0A1420)
private val BgBottom   = Color(0xFF0F2530)
private val CardBg     = Color(0xFF102936)
private val CardBorder = Color(0xFF1C2C38)
private val Accent     = Color(0xFFE5245A)
private val Blue       = Color(0xFF4FC3F7)
private val TextWhite  = Color(0xFFF5F8FA)
private val TextGray   = Color(0xFF7E93A6)
private val LiveGreen  = Color(0xFF16A34A)
private val LiveRed    = Color(0xFFE53935)

private val AppGradient = Brush.verticalGradient(listOf(BgTop, BgBottom))

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

    val sportKeywords = remember { listOf(
        "real madrid","barcelona","atletico","manchester city","manchester united",
        "liverpool","chelsea","arsenal","tottenham","bayern","borussia","dortmund",
        "juventus","milan","inter","napoli","roma","psg","paris saint-germain",
        "ajax","porto","benfica","sevilla","valencia",
        "cska","зенит","zenit","spartak","спартак","lokomotiv","локомотив",
        "dynamo","динамо","krasnodar","краснодар",
        "pakhtakor","пахтакор","bunyodkor","бунёдкор","nasaf","neftchi",
        "сборная","national team","uzbekistan","россия","германия","франция",
        "англия","испания","италия","бразилия","аргентина","португалия",
        "лига чемпионов","champions league","europa league","лига европы",
        "premier league","la liga","serie a","bundesliga","ligue 1",
        "чемпионат мира","world cup","евро","euro","copa america",
        "кубок","суперкубок","super cup","олимпиада","olympics",
        "финал","полуфинал","четвертьфинал","final","semifinal",
        "ufc","mma","бокс","boxing","теннис","wimbledon","roland garros",
        "formula 1","гран-при","grand prix","nba","nhl","хоккей","hockey",
        "баскетбол","basketball","волейбол","биатлон","борьба","wrestling",
        "матч","match","турнир","tournament","чемпионат","championship","лига","league"
    ) }

    val sportChKw = remember { listOf(
        "матч","sport","eurosport","setanta","футбол","nba","ufc",
        "formula","бокс","arena","арена","bein"
    ) }

    val kinoChKw = remember { listOf(
        "кино","cinema","movie","film","premier","tv1000","наше кино","мегахит","amedia"
    ) }

    // Sport o'yinlari — hozir yoki 3 soat ichida
    val sportItems = remember(epgMap, channels) {
        channels.filter { ch ->
            sportChKw.any { ch.name.lowercase().contains(it) } ||
            ch.group.lowercase().contains("спорт") ||
            ch.group.lowercase().contains("sport")
        }.mapNotNull { ch ->
            val prog = (epgMap[ch.tvgId] ?: epgMap[ch.name])?.firstOrNull { p ->
                val isNow  = p.startTimeMs <= nowMs && p.stopTimeMs >= nowMs
                val isSoon = p.startTimeMs > nowMs && (p.startTimeMs - nowMs) < 3 * 3600 * 1000L
                (isNow || isSoon) && sportKeywords.any { k -> p.title.lowercase().contains(k) }
            }
            if (prog != null) Triple(ch, prog, prog.startTimeMs <= nowMs && prog.stopTimeMs >= nowMs) else null
        }.sortedByDescending { (_, _, isNow) -> isNow }.take(10)
    }

    // Kino — hozir efirda
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

    fun s(key: String) = Strings.get(key, lang)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppGradient)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            // ── Logo ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "MIROVOY",
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        "IPTV PLAYER",
                        color = Blue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
                Text(APP_VERSION_NAME, color = TextGray, fontSize = 11.sp)
            }

            Spacer(Modifier.height(22.dp))

            // ── Asosiy grid ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live TV — katta chap
                MenuCard(
                    icon     = Icons.Filled.LiveTv,
                    label    = s("home_live"),
                    subtitle = "${channels.size} ${s("home_channels")}",
                    accented = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick  = onLiveTv
                )

                // 2x2 grid
                Column(
                    modifier = Modifier.weight(2f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MenuCard(Icons.Filled.Movie,        s("home_movies"),  null, false, Modifier.weight(1f).fillMaxHeight(), onMovies)
                        MenuCard(Icons.Filled.Tv,           s("home_series"),  null, false, Modifier.weight(1f).fillMaxHeight(), onSeries)
                    }
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MenuCard(Icons.Filled.SportsSoccer, s("home_sport"),    null, false, Modifier.weight(1f).fillMaxHeight(), onSport)
                        MenuCard(Icons.Filled.SwapHoriz,    s("home_playlist"), null, false, Modifier.weight(1f).fillMaxHeight(), onPlaylist)
                    }
                }
            }

            // ── Sport e'lonlari (EPG) — logotip + gorizontal scroll ──
            if (sportItems.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(s("home_now_sport"), color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sportItems.forEach { (ch, prog, isNow) ->
                        SportCard(
                            channel  = ch,
                            prog     = prog,
                            isNow    = isNow,
                            liveBadge = s("home_live_badge"),
                            onClick  = onSport
                        )
                    }
                }
            }

            // ── Kino e'lonlari (EPG) ──────────────────────────────────
            if (movieItems.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(s("home_now_movies"), color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    movieItems.forEach { (ch, prog) ->
                        SportCard(
                            channel   = ch,
                            prog      = prog,
                            isNow     = true,
                            liveBadge = s("home_live_badge"),
                            onClick   = onMovies
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Quyi tugmalar ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BottomBtn(Icons.Filled.Settings,  s("home_settings"), Modifier.weight(1f), onSettings)
                BottomBtn(Icons.Filled.Refresh,   s("home_refresh"),  Modifier.weight(1f), onRefresh)
                BottomBtn(Icons.Filled.ExitToApp, s("home_exit"),     Modifier.weight(1f), onExit)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── Menu kartochkasi ──────────────────────────────────────────────────
@Composable
private fun MenuCard(
    icon: ImageVector, label: String, subtitle: String?, accented: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(
                width = if (accented) 1.5.dp else 0.5.dp,
                color = if (accented) Accent else CardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (accented) Accent else Color(0xFF16222E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = TextWhite, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (!subtitle.isNullOrEmpty()) {
            Text(subtitle, color = TextGray, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

// ── Quyi tugma ────────────────────────────────────────────────────────
@Composable
private fun BottomBtn(
    icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Blue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = TextGray, fontSize = 9.sp)
    }
}

// ── Sport/Kino kartochkasi — logotip + nom + jonli/arxiv belgisi ──────
@Composable
private fun SportCard(
    channel: M3UChannel,
    prog: EpgProgram,
    isNow: Boolean,
    liveBadge: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(
                width = if (isNow) 1.5.dp else 0.5.dp,
                color = if (isNow) Accent else CardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Logotip + jonli/arxiv belgisi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kanal logotipi
            if (channel.logo.isNotEmpty()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF16222E)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(channel.name.take(2).uppercase(), color = Blue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Jonli = qizil (LIVE), arxiv = kulrang
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (isNow) Accent else Color(0xFF3A4A58))
            )
        }

        // Dastur nomi
        Text(
            text = prog.title,
            color = TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Kanal nomi + vaqt
        Text(
            text = channel.name,
            color = Blue,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (isNow) liveBadge else "🕐 ${prog.startTimeStr}",
            color = if (isNow) Accent else TextGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
