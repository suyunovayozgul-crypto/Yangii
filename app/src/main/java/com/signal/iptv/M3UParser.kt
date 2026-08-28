package com.signal.iptv

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object M3UParser {

    // Brauzerga o'xshash User-Agent — ba'zi playlist/EPG serverlari
    // noma'lum yoki bo'sh User-Agent bilan kelgan so'rovlarni 403/406 bilan
    // rad etadi (xuddi ChannelAdapter'dagi logotip yuklashda bo'lgani kabi).
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val nameRegex = Regex(",([^,]*)$")
    private val logoRegex = Regex("tvg-logo=\"([^\"]*)\"")
    private val countryRegex = Regex("tvg-country=\"([^\"]*)\"")
    private val groupRegex = Regex("group-title=\"([^\"]*)\"")
    private val tvgIdRegex = Regex("tvg-id=\"([^\"]*)\"")
    private val vlcUserAgentRegex = Regex("#EXTVLCOPT:\\s*http-user-agent\\s*=\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val vlcReferrerRegex = Regex("#EXTVLCOPT:\\s*http-referrer\\s*=\\s*(.+)$", RegexOption.IGNORE_CASE)

    /** Blocking network fetch — call from a background thread. */
    fun fetch(playlistUrl: String): List<Channel> {
        val connection = URL(playlistUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)

        val text = connection.inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream)).readText()
        }
        connection.disconnect()
        return parse(text)
    }

    fun parse(text: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pendingName: String? = null
        var pendingLogo = ""
        var pendingGroup = ""
        var pendingTvgId = ""
        var pendingUserAgent = ""
        var pendingReferrer = ""

        fun resetPending() {
            pendingName = null
            pendingLogo = ""
            pendingGroup = ""
            pendingTvgId = ""
            pendingUserAgent = ""
            pendingReferrer = ""
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    pendingName = nameRegex.find(line)?.groupValues?.get(1)?.trim()?.ifEmpty { "Noma'lum kanal" }
                        ?: "Noma'lum kanal"
                    pendingLogo = logoRegex.find(line)?.groupValues?.get(1) ?: ""
                    pendingTvgId = tvgIdRegex.find(line)?.groupValues?.get(1)?.trim() ?: ""
                    val group = groupRegex.find(line)?.groupValues?.get(1)?.trim() ?: ""
                    val country = countryRegex.find(line)?.groupValues?.get(1)?.trim() ?: ""
                    pendingGroup = if (group.isNotEmpty()) group else country
                    pendingUserAgent = ""
                    pendingReferrer = ""
                }
                // Ko'p playlistlar (xuddi VLC/Kodi kabi) kanalga xos User-Agent yoki
                // Referer'ni alohida #EXTVLCOPT qatorida beradi — shu qatorlar EXTINF
                // bilan URL orasida keladi.
                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    vlcUserAgentRegex.find(line)?.let { pendingUserAgent = it.groupValues[1].trim() }
                    vlcReferrerRegex.find(line)?.let { pendingReferrer = it.groupValues[1].trim() }
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val name = pendingName
                    if (name != null) {
                        // Ba'zi playlistlar sarlavhalarni alohida qator o'rniga to'g'ridan-to'g'ri
                        // URL oxiriga qo'shadi: http://server/stream.m3u8|User-Agent=...&Referer=...
                        var streamUrl = line
                        var userAgent = pendingUserAgent
                        var referrer = pendingReferrer
                        val pipeIndex = line.indexOf('|')
                        if (pipeIndex != -1) {
                            streamUrl = line.substring(0, pipeIndex)
                            val paramsPart = line.substring(pipeIndex + 1)
                            paramsPart.split('&').forEach { pair ->
                                val eq = pair.indexOf('=')
                                if (eq != -1) {
                                    val key = pair.substring(0, eq).trim()
                                    val value = pair.substring(eq + 1).trim()
                                    when {
                                        key.equals("User-Agent", ignoreCase = true) -> userAgent = value
                                        key.equals("Referer", ignoreCase = true) ||
                                            key.equals("Referrer", ignoreCase = true) -> referrer = value
                                    }
                                }
                            }
                        }
                        channels.add(
                            Channel(
                                name = name,
                                url = streamUrl,
                                logo = pendingLogo,
                                group = pendingGroup,
                                tvgId = pendingTvgId,
                                userAgent = userAgent,
                                referrer = referrer
                            )
                        )
                        resetPending()
                    }
                }
            }
        }
        return channels
    }
}
