package uz.oktv.iptv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import uz.oktv.iptv.model.AppLang
import uz.oktv.iptv.utils.Strings

private val BgBlack      = Color(0xFF000000)
private val CardPurple   = Color(0xFF5C2D91)
private val TextWhite    = Color.White
private val TextGray     = Color(0xFFBDBDBD)
private val OutlineWhite = Color.White
private val LiveRed      = Color(0xFFE53935)
private val LiveGreen    = Color(0xFF16A34A)

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
            .background(BgBlack)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mirovoy ", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text("TV",       color = CardPurple, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
                Text(APP_VERSION_NAME, color = TextGray, fontSize = 11.sp)
            }

            Spacer(Modifier.height(20.dp))

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
                        MenuCard(Icons.Filled.Movie,        s("home_movies"),  null, Modifier.weight(1f).fillMaxHeight(), onMovies)
                        MenuCard(Icons.Filled.Tv,           s("home_series"),  null, Modifier.weight(1f).fillMaxHeight(), onSeries)
                    }
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MenuCard(Icons.Filled.SportsSoccer, s("home_sport"),    null, Modifier.weight(1f).fillMaxHeight(), onSport)
                        MenuCard(Icons.Filled.SwapHoriz,    s("home_playlist"), null, Modifier.weight(1f).fillMaxHeight(), onPlaylist)
                    }
                }
            }

            // ── Sport e'lonlari (EPG) — logotip + gorizontal scroll ──
            if (sportItems.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(s("home_now_sport"), color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(16.dp))
                Text(s("home_now_movies"), color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
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

            Spacer(Modifier.height(16.dp))

            // ── Quyi tugmalar ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
    icon: ImageVector, label: String, subtitle: String?,
    modifier: Modifier, onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardPurple)
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = TextWhite, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (!subtitle.isNullOrEmpty()) {
            Text(subtitle, color = TextWhite.copy(0.7f), fontSize = 9.sp, textAlign = TextAlign.Center)
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
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, OutlineWhite, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = TextWhite, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
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
            .width(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1A1A))
            .border(
                1.dp,
                if (isNow) LiveGreen else Color(0xFF444444),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Logotip + jonli/arxiv belgisi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kanal logotipi
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CardPurple),
                    contentAlignment = Alignment.Center
                ) {
                    Text(channel.name.take(2).uppercase(), color = TextWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Jonli = yashil, arxiv = qizil
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isNow) LiveGreen else LiveRed)
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
            color = Color(0xFF38BDF8),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (isNow) liveBadge else "🕐 ${prog.startTimeStr}",
            color = if (isNow) LiveGreen else TextGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
