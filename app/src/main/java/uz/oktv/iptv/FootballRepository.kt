package uz.oktv.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ============================================================
// FOOTBALL-DATA.ORG orqali: jadval, gol urganlar, bugungi o'yinlar
// Bepul reja: daqiqasiga 10 so'rov — shuning uchun har bir javob
// keshlanadi (60 daqiqa), har safar ochganda emas.
// ============================================================

data class LeagueInfo(val code: String, val label: String, val emoji: String)

data class StandingRow(
    val position: Int,
    val teamName: String,
    val crestUrl: String?,
    val played: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val goalDiff: Int,
    val points: Int
)

data class ScorerRow(
    val playerName: String,
    val teamName: String,
    val goals: Int
)

data class MatchRow(
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: String,
    val utcDate: String,
    val competitionName: String
)

object FootballRepository {

    // Foydalanuvchi bergan football-data.org kaliti
    private const val API_KEY = "a4aab4d352d54d578c503b57043b4bf1"
    private const val BASE_URL = "https://api.football-data.org/v4"

    private const val CACHE_MAX_AGE_MS = 60L * 60L * 1000L // 1 soat
    private const val PREFS_NAME = "football_cache"

    val LEAGUES = listOf(
        LeagueInfo("PL", "Angliya — Premer-liga", "🏴"),
        LeagueInfo("PD", "Ispaniya — La Liga", "🇪🇸"),
        LeagueInfo("BL1", "Germaniya — Bundesliga", "🇩🇪"),
        LeagueInfo("SA", "Italiya — Seriya A", "🇮🇹"),
        LeagueInfo("FL1", "Fransiya — Ligue 1", "🇫🇷"),
        LeagueInfo("CL", "Chempionlar ligasi", "🏆")
    )

    private fun cacheKey(kind: String, code: String) = "${kind}_$code"

    private fun readCache(context: Context, key: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedAt = prefs.getLong("${key}_time", 0L)
        if (System.currentTimeMillis() - savedAt > CACHE_MAX_AGE_MS) return null
        return prefs.getString(key, null)
    }

    private fun writeCache(context: Context, key: String, json: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(key, json)
            .putLong("${key}_time", System.currentTimeMillis())
            .apply()
    }

    private fun request(path: String): String? {
        return try {
            val conn = URL("$BASE_URL$path").openConnection() as HttpURLConnection
            conn.setRequestProperty("X-Auth-Token", API_KEY)
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun fetchStandings(context: Context, leagueCode: String): List<StandingRow> {
        val key = cacheKey("standings", leagueCode)
        val json = readCache(context, key) ?: request("/competitions/$leagueCode/standings")
            ?.also { writeCache(context, key, it) }
            ?: readCache(context, key) // agar so'rov muvaffaqiyatsiz bo'lsa, eskirgan kesh bo'lsa ham ko'rsatamiz
            ?: return emptyList()

        return try {
            val root = JSONObject(json)
            val standingsArr = root.getJSONArray("standings")
            // "TOTAL" turini topamiz (ba'zi ligalarda GROUP bosqichlar ham bo'ladi)
            var table: JSONArray? = null
            for (i in 0 until standingsArr.length()) {
                val s = standingsArr.getJSONObject(i)
                if (s.optString("type") == "TOTAL") {
                    table = s.getJSONArray("table")
                    break
                }
            }
            table = table ?: standingsArr.optJSONObject(0)?.optJSONArray("table")
            if (table == null) return emptyList()

            (0 until table.length()).map { i ->
                val row = table.getJSONObject(i)
                val team = row.getJSONObject("team")
                StandingRow(
                    position = row.optInt("position"),
                    teamName = team.optString("shortName", team.optString("name")),
                    crestUrl = team.optString("crest", null),
                    played = row.optInt("playedGames"),
                    won = row.optInt("won"),
                    draw = row.optInt("draw"),
                    lost = row.optInt("lost"),
                    goalDiff = row.optInt("goalDifference"),
                    points = row.optInt("points")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun fetchScorers(context: Context, leagueCode: String): List<ScorerRow> {
        val key = cacheKey("scorers", leagueCode)
        val json = readCache(context, key) ?: request("/competitions/$leagueCode/scorers?limit=15")
            ?.also { writeCache(context, key, it) }
            ?: readCache(context, key)
            ?: return emptyList()

        return try {
            val root = JSONObject(json)
            val arr = root.getJSONArray("scorers")
            (0 until arr.length()).map { i ->
                val row = arr.getJSONObject(i)
                val player = row.getJSONObject("player")
                val team = row.getJSONObject("team")
                ScorerRow(
                    playerName = player.optString("name"),
                    teamName = team.optString("shortName", team.optString("name")),
                    goals = row.optInt("goals")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Keyingi 7 kun ichidagi barcha o'yinlar. */
    fun fetchUpcomingMatches(context: Context): List<MatchRow> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val today = sdf.format(java.util.Date())
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 7)
        val nextWeek = sdf.format(cal.time)

        val key = cacheKey("upcoming", "$today-$nextWeek")
        val json = readCache(context, key) ?: request("/matches?dateFrom=$today&dateTo=$nextWeek")
            ?.also { writeCache(context, key, it) }
            ?: readCache(context, key)
            ?: return emptyList()

        return try {
            val root = JSONObject(json)
            val arr = root.getJSONArray("matches")
            (0 until arr.length()).mapNotNull { i ->
                val row = arr.getJSONObject(i)
                val comp = row.getJSONObject("competition")
                if (LEAGUES.none { it.code == comp.optString("code") }) return@mapNotNull null
                val score = row.getJSONObject("score").getJSONObject("fullTime")
                MatchRow(
                    homeTeam = row.getJSONObject("homeTeam").optString("shortName", row.getJSONObject("homeTeam").optString("name")),
                    awayTeam = row.getJSONObject("awayTeam").optString("shortName", row.getJSONObject("awayTeam").optString("name")),
                    homeScore = if (score.isNull("home")) null else score.optInt("home"),
                    awayScore = if (score.isNull("away")) null else score.optInt("away"),
                    status = row.optString("status"),
                    utcDate = row.optString("utcDate"),
                    competitionName = comp.optString("name")
                )
            }.sortedBy { it.utcDate }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Bugungi barcha o'yinlar (barcha 6 liga bo'yicha birlashtirilgan). */
    fun fetchTodayMatches(context: Context): List<MatchRow> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val today = sdf.format(java.util.Date())

        val key = cacheKey("matches", today)
        val json = readCache(context, key) ?: request("/matches?dateFrom=$today&dateTo=$today")
            ?.also { writeCache(context, key, it) }
            ?: readCache(context, key)
            ?: return emptyList()

        return try {
            val root = JSONObject(json)
            val arr = root.getJSONArray("matches")
            (0 until arr.length()).mapNotNull { i ->
                val row = arr.getJSONObject(i)
                val comp = row.getJSONObject("competition")
                // Faqat bizning 6 ligamizga tegishlilarini ko'rsatamiz
                if (LEAGUES.none { it.code == comp.optString("code") }) return@mapNotNull null

                val score = row.getJSONObject("score").getJSONObject("fullTime")
                MatchRow(
                    homeTeam = row.getJSONObject("homeTeam").optString("shortName", row.getJSONObject("homeTeam").optString("name")),
                    awayTeam = row.getJSONObject("awayTeam").optString("shortName", row.getJSONObject("awayTeam").optString("name")),
                    homeScore = if (score.isNull("home")) null else score.optInt("home"),
                    awayScore = if (score.isNull("away")) null else score.optInt("away"),
                    status = row.optString("status"),
                    utcDate = row.optString("utcDate"),
                    competitionName = comp.optString("name")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
