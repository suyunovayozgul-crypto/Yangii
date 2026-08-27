package com.signal.iptv

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object M3UParser {

    private val nameRegex = Regex(",([^,]*)$")
    private val logoRegex = Regex("tvg-logo=\"([^\"]*)\"")
    private val countryRegex = Regex("tvg-country=\"([^\"]*)\"")
    private val groupRegex = Regex("group-title=\"([^\"]*)\"")
    private val tvgIdRegex = Regex("tvg-id=\"([^\"]*)\"")

    /** Blocking network fetch — call from a background thread. */
    fun fetch(playlistUrl: String): List<Channel> {
        val connection = URL(playlistUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (MirovoyTV Android)")

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
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val name = pendingName
                    if (name != null) {
                        channels.add(
                            Channel(
                                name = name,
                                url = line,
                                logo = pendingLogo,
                                group = pendingGroup,
                                tvgId = pendingTvgId
                            )
                        )
                        pendingName = null
                        pendingLogo = ""
                        pendingGroup = ""
                        pendingTvgId = ""
                    }
                }
            }
        }
        return channels
    }
}
