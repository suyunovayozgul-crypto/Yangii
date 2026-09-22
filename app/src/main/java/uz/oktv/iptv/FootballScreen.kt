package uz.oktv.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class FootballTab { STANDINGS, SCORERS, TODAY, UPCOMING }

/**
 * Bugungi o'yinni EPG jadvalidan qaysi kanalda ketayotganini
 * (jamoa nomlari asosida, taxminiy) topadi.
 */
private fun findChannelForMatch(
    match: MatchRow,
    channels: List<M3UChannel>,
    epgScheduleMap: Map<String, List<EpgProgram>>
): M3UChannel? {
    val home = match.homeTeam.lowercase()
    val away = match.awayTeam.lowercase()

    for (channel in channels) {
        val schedule = getScheduleForChannel(channel, epgScheduleMap)
        val hit = schedule.any { program ->
            val t = program.title.lowercase()
            t.contains(home) && t.contains(away)
        }
        if (hit) return channel
    }
    return null
}

@Composable
fun FootballHubScreen(
    channels: List<M3UChannel>,
    epgScheduleMap: Map<String, List<EpgProgram>>,
    lang: uz.oktv.iptv.model.AppLang = uz.oktv.iptv.model.AppLang.RU,
    onClose: () -> Unit,
    onChannelSelected: (M3UChannel) -> Unit
) {
    fun s(key: String) = uz.oktv.iptv.utils.Strings.get(key, lang)
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedLeague by remember { mutableStateOf(FootballRepository.LEAGUES.first()) }
    var tab by remember { mutableStateOf(FootballTab.STANDINGS) }

    var standings by remember { mutableStateOf<List<StandingRow>>(emptyList()) }
    var scorers by remember { mutableStateOf<List<ScorerRow>>(emptyList()) }
    var todayMatches by remember { mutableStateOf<List<MatchRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedLeague, tab) {
        loading = true
        withContext(Dispatchers.IO) {
            when (tab) {
                FootballTab.STANDINGS -> {
                    val result = FootballRepository.fetchStandings(context, selectedLeague.code)
                    withContext(Dispatchers.Main) { standings = result }
                }
                FootballTab.SCORERS -> {
                    val result = FootballRepository.fetchScorers(context, selectedLeague.code)
                    withContext(Dispatchers.Main) { scorers = result }
                }
                FootballTab.TODAY -> {
                    val result = FootballRepository.fetchTodayMatches(context)
                    withContext(Dispatchers.Main) { todayMatches = result }
                }
                FootballTab.UPCOMING -> {
                    val result = FootballRepository.fetchUpcomingMatches(context)
                    withContext(Dispatchers.Main) { todayMatches = result }
                }
            }
        }
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05010D))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // Sarlavha
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s("football_title"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Liga tanlash
            LazyRow(modifier = Modifier.fillMaxWidth()) {
                items(FootballRepository.LEAGUES) { league ->
                    val active = league.code == selectedLeague.code
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (active) Color(0xFF2563EB) else Color(0xFF161F36))
                            .clickable { selectedLeague = league }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("${league.emoji} ${league.label}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bo'lim tanlash (jadval / gol urganlar / bugungi o'yinlar)
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    FootballTab.STANDINGS to s("fb_standings"),
                    FootballTab.SCORERS   to s("fb_scorers"),
                    FootballTab.TODAY     to s("fb_today"),
                    FootballTab.UPCOMING  to s("fb_upcoming")
                ).forEach { (t, label) ->
                    val active = tab == t
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) Color(0xFF1D4ED8) else Color(0xFF161F36))
                            .clickable { tab = t }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF2563EB))
                }
            } else {
                when (tab) {
                    FootballTab.STANDINGS -> StandingsTable(standings, lang)
                    FootballTab.SCORERS -> ScorersList(scorers, lang)
                    FootballTab.TODAY, FootballTab.UPCOMING -> TodayMatchesList(
                        matches = todayMatches,
                        channels = channels,
                        epgScheduleMap = epgScheduleMap,
                        lang = lang,
                        onChannelSelected = onChannelSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun StandingsTable(rows: List<StandingRow>, lang: uz.oktv.iptv.model.AppLang = uz.oktv.iptv.model.AppLang.RU) {
    if (rows.isEmpty()) {
        Text(uz.oktv.iptv.utils.Strings.get("fb_no_data", lang), color = Color(0xFF94A3B8), fontSize = 12.sp)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("#", color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.width(24.dp))
                Text(uz.oktv.iptv.utils.Strings.get("fb_team", lang), color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(uz.oktv.iptv.utils.Strings.get("fb_played", lang), color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.width(28.dp))
                Text(uz.oktv.iptv.utils.Strings.get("fb_diff", lang), color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.width(36.dp))
                Text(uz.oktv.iptv.utils.Strings.get("fb_points", lang), color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.width(30.dp))
            }
        }
        items(rows) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${row.position}", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                if (!row.crestUrl.isNullOrEmpty()) {
                    AsyncImage(model = row.crestUrl, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(row.teamName, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${row.played}", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.width(28.dp))
                Text("${row.goalDiff}", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.width(36.dp))
                Text("${row.points}", color = Color(0xFFF59E0B), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
            }
        }
    }
}

@Composable
private fun ScorersList(rows: List<ScorerRow>, lang: uz.oktv.iptv.model.AppLang = uz.oktv.iptv.model.AppLang.RU) {
    if (rows.isEmpty()) {
        Text(uz.oktv.iptv.utils.Strings.get("fb_no_data", lang), color = Color(0xFF94A3B8), fontSize = 12.sp)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(rows) { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${index + 1}", color = Color(0xFF94A3B8), fontSize = 12.sp, modifier = Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.playerName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(row.teamName, color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Text("⚽ ${row.goals}", color = Color(0xFFF59E0B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TodayMatchesList(
    matches: List<MatchRow>,
    channels: List<M3UChannel>,
    epgScheduleMap: Map<String, List<EpgProgram>>,
    lang: uz.oktv.iptv.model.AppLang = uz.oktv.iptv.model.AppLang.RU,
    onChannelSelected: (M3UChannel) -> Unit
) {
    if (matches.isEmpty()) {
        Text(uz.oktv.iptv.utils.Strings.get("fb_no_today", lang), color = Color(0xFF94A3B8), fontSize = 12.sp)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(matches) { match ->
            val channel = remember(match) { findChannelForMatch(match, channels, epgScheduleMap) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF161F36))
                    .then(
                        if (channel != null) Modifier.clickable { onChannelSelected(channel) } else Modifier
                    )
                    .padding(12.dp)
            ) {
                Text(match.competitionName, color = Color(0xFF94A3B8), fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${match.homeTeam}  ${match.homeScore ?: "-"} : ${match.awayScore ?: "-"}  ${match.awayTeam}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (channel != null) {
                        uz.oktv.iptv.utils.Strings.get("fb_channel", lang) + " " + channel.name
                    } else {
                        uz.oktv.iptv.utils.Strings.get("fb_no_channel", lang)
                    },
                    color = if (channel != null) Color(0xFF34D399) else Color(0xFF6B7280),
                    fontSize = 11.sp
                )
            }
        }
    }
}
