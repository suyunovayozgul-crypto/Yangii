package uz.oktv.iptv

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Mirovoy TV home screen.
 * The layout intentionally follows the classic IPTV Smarters-style dashboard:
 * large Live TV card, Movies + Series cards, then compact EPG / My List actions.
 */
@Composable
fun HomeScreen(
    channels: List<M3UChannel> = emptyList(),
    epgMap: Map<String, List<EpgProgram>> = emptyMap(),
    // Callback lar — har biri o'z ishini qiladi
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
    val prefs = remember { context.getSharedPreferences("OKTV_PREFS", Context.MODE_PRIVATE) }

    // Subscription ma'lumotlari
    val tariff   = prefs.getString("subscription_tariff", "") ?: ""
    val server   = prefs.getString("subscription_server_name", "") ?: ""
    val expires  = prefs.getString("subscription_expires_at", "") ?: ""
    val screens  = prefs.getInt("subscription_screens", 0)

    val nowMs = System.currentTimeMillis()

    // EPG dan hozir efirda bo'layotgan SPORT o'yinlari
    val sportNow = remember(epgMap, channels) {
        val keywords = listOf("футбол","хоккей","баскетбол","теннис","ufc","матч",
            "чемпионат","лига","кубок","sport","football","soccer",
            "basketball","hockey","tennis","match","formula","нба","уефа")
        val sportChNames = listOf("матч","sport","eurosport","setanta",
            "футбол","nba","ufc","formula","бокс")

        channels.filter { ch ->
            sportChNames.any { ch.name.lowercase().contains(it) } ||
            ch.group.lowercase().contains("спорт") ||
            ch.group.lowercase().contains("sport")
        }.mapNotNull { ch ->
            val progs = epgMap[ch.tvgId] ?: epgMap[ch.name]
            val prog = progs?.firstOrNull { p ->
                val isNow  = p.startTimeMs <= nowMs && p.stopTimeMs >= nowMs
                val isSoon = p.startTimeMs > nowMs && (p.startTimeMs - nowMs) < 3 * 3600 * 1000L
                (isNow || isSoon) && keywords.any { k -> p.title.lowercase().contains(k) }
            }
            if (prog != null) Pair(ch, prog) else null
        }.take(6)
    }

    // EPG dan hozir efirda bo'layotgan KINO/SERIAL
    val movieNow = remember(epgMap, channels) {
        val kinoChNames = listOf("кино","cinema","movie","film","premier","боевик","комедия")
        channels.filter { ch ->
            kinoChNames.any { ch.name.lowercase().contains(it) } ||
            ch.group.lowercase().contains("кино") ||
            ch.group.lowercase().contains("фильм")
        }.mapNotNull { ch ->
            val progs = epgMap[ch.tvgId] ?: epgMap[ch.name]
            val prog = progs?.firstOrNull { p ->
                p.startTimeMs <= nowMs && p.stopTimeMs >= nowMs
            }
            if (prog != null) Pair(ch, prog) else null
        }.take(6)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070A))
    ) {
        val wide = maxWidth >= 720.dp
        val horizontalPadding = if (wide) 34.dp else 16.dp
        val topPadding = if (wide) 24.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── HEADER: Logo + versiya ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mirovoy_logo_full),
                    contentDescription = "Mirovoy TV",
                    modifier = Modifier.height(28.dp).widthIn(max = 140.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = APP_VERSION_NAME,
                    color = Color(0xFFF59E0B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── OBUNA MA'LUMOTLARI ──────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1322), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "ПОДПИСКА Mirovoy TV",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SubInfoBox("ТАРИФ",   tariff.ifBlank { "—" },  Color(0xFFF59E0B), Modifier.weight(1f))
                    SubInfoBox("СЕРВЕР",  server.ifBlank { "—" },  Color.White,       Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SubInfoBox("ИСТЕКАЕТ", expires.ifBlank { "—" }, Color(0xFFEF4444), Modifier.weight(1.2f))
                    SubInfoBox("ЭКРАНЫ", if (screens > 0) "$screens ТВ" else "—", Color(0xFF38BDF8), Modifier.weight(0.8f))
                }
            }

            // ── ASOSIY KARTOCHKALAR (ibPRO uslubi) ─────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live TV — katta, chap
                BigMenuCard(
                    icon = "📺",
                    title = "Jonli Efir",
                    subtitle = "${channels.size} kanal",
                    gradient = listOf(Color(0xFF1565C0), Color(0xFF42A5F5)),
                    modifier = Modifier.weight(1.4f).height(150.dp),
                    onClick = onLiveTv
                )

                // O'ng tomonda 2 ta kichik
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmallMenuCard("🎬", "Kinolar",  Color(0xFFE53935), Modifier.fillMaxWidth().height(70.dp), onMovies)
                    SmallMenuCard("🏆", "Sport",    Color(0xFF43A047), Modifier.fillMaxWidth().height(70.dp), onSport)
                }
            }

            // Seriallar — to'liq kenglik
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallMenuCard("📽️", "Seriallar",     Color(0xFF8E24AA), Modifier.weight(1f).height(56.dp), onSeries)
                SmallMenuCard("🔑", "Playlist",      Color(0xFF00838F), Modifier.weight(1f).height(56.dp), onPlaylist)
            }

            // ── HOZIR EFIRDA: SPORT (EPG) ───────────────────────────
            if (sportNow.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚽ Hozir Efirda — Sport", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        sportNow.forEach { (ch, prog) ->
                            val isNow = prog.startTimeMs <= nowMs && prog.stopTimeMs >= nowMs
                            EpgCard(
                                channelName = ch.name,
                                programTitle = prog.title,
                                time = if (isNow) "🔴 JONLI" else "🕐 ${prog.startTimeStr}",
                                isLive = isNow,
                                onClick = onSport
                            )
                        }
                    }
                }
            }

            // ── HOZIR EFIRDA: KINO (EPG) ────────────────────────────
            if (movieNow.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🎬 Hozir Efirda — Kino", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        movieNow.forEach { (ch, prog) ->
                            EpgCard(
                                channelName = ch.name,
                                programTitle = prog.title,
                                time = "🔴 JONLI",
                                isLive = true,
                                onClick = onMovies
                            )
                        }
                    }
                }
            }

            // ── QUYI TUGMALAR ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Triple("⚙️", "Sozlamalar", onSettings),
                    Triple("🔄", "Yangilash",  onRefresh),
                    Triple("🚪", "Chiqish",    onExit)
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

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SubInfoBox(title: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF0A0F1E), RoundedCornerShape(6.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BigMenuCard(
    icon: String,
    title: String,
    subtitle: String,
    gradient: List<Color>,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(gradient))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(icon, fontSize = 36.sp)
            Column {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.White.copy(0.7f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SmallMenuCard(
    icon: String,
    title: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(color.copy(0.6f), color)))
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EpgCard(
    channelName: String,
    programTitle: String,
    time: String,
    isLive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D1322))
            .border(1.dp, if (isLive) Color(0xFF16A34A) else Color(0xFF1E293B), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(time, color = if (isLive) Color(0xFF16A34A) else Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(programTitle, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(channelName, color = Color(0xFF38BDF8), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
