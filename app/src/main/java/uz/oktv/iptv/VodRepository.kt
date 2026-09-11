package uz.oktv.iptv

import java.net.HttpURLConnection
import java.net.URL

data class VodPlaylistResult(
    val channels: List<M3UChannel>,
    val categories: Set<String>
)

fun fetchVodPlaylist(): VodPlaylistResult {
    val parsedVod = mutableListOf<M3UChannel>()
    val vCats = mutableSetOf<String>()
    try {
        val url = URL("https://oktv.uz/playlist.m3u")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 8000

        val reader = conn.inputStream.bufferedReader()
        var currentTitle = ""
        var currentLogo = ""
        var currentGroup = "Видео"
        var idCounter = 100000

        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                if (trimmed.contains("group-title=\"")) {
                    currentGroup = trimmed.substringAfter("group-title=\"").substringBefore("\"")
                    vCats.add(currentGroup)
                }
                if (trimmed.contains("tvg-logo=\"")) {
                    currentLogo = trimmed.substringAfter("tvg-logo=\"").substringBefore("\"")
                }
                if (trimmed.contains(",")) {
                    currentTitle = trimmed.substringAfterLast(",")
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                parsedVod.add(
                    M3UChannel(
                        id = idCounter++,
                        tvgId = "",
                        name = if (currentTitle.isEmpty()) "Фильм $idCounter" else currentTitle,
                        group = currentGroup,
                        logo = currentLogo,
                        url = trimmed
                    )
                )
                currentTitle = ""
                currentLogo = ""
            }
        }
        reader.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return VodPlaylistResult(parsedVod, vCats)
}