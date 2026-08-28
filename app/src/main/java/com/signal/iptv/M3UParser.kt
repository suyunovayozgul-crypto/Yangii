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

    // Ba'zi provayderlar kanal oqimini himoya qilish uchun maxsus User-Agent yoki
    // Referer talab qiladi va buni playlist ichida VLC/Kodi formatidagi qo'shimcha
    // qatorlarda beradi. Boshqa pleyerlar (Televizo va h.k.) buni o'qib, so'rovga
    // qo'shadi — bizning ilova esa avval bunday qatorlarni umuman e'tiborsiz
    // qoldirardi, shu sabab ba'zi kanallar faqat "boshqa dastur"da ochilardi.
    //   #EXTVLCOPT:http-user-agent=...
    //   #EXTVLCOPT:http-referrer=... (yoki http-referer)
    //   #EXTHTTP:{"User-Agent":"...","Referrer":"..."}
    private val vlcUserAgentRegex = Regex("""#EXTVLCOPT:\s*http-user-agent\s*=\s*(.+)""", RegexOption.IGNORE_CASE)
    private val vlcRefererRegex = Regex("""#EXTVLCOPT:\s*http-referr?er\s*=\s*(.+)""", RegexOption.IGNORE_CASE)
    private val extHttpUaRegex = Regex(""""user-agent"\s*:\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
    private val extHttpRefRegex = Regex(""""referr?er"\s*:\s*"([^"]*)"""", RegexOption.IGNORE_CASE)

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
        var pendingUserAgent = ""
        var pendingReferer = ""

        fun resetPending() {
            pendingName = null
            pendingLogo = ""
            pendingGroup = ""
            pendingTvgId = ""
            pendingUserAgent = ""
            pendingReferer = ""
        }
        // NOTE: local vars stay "pendingUserAgent"/"pendingReferer" for readability;
        // they map onto Channel's userAgent/referer fields below.

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
                    pendingReferer = ""
                }
                line.startsWith("#EXTVLCOPT") -> {
                    vlcUserAgentRegex.find(line)?.groupValues?.get(1)?.trim()?.let { pendingUserAgent = it }
                    vlcRefererRegex.find(line)?.groupValues?.get(1)?.trim()?.let { pendingReferer = it }
                }
                line.startsWith("#EXTHTTP") -> {
                    extHttpUaRegex.find(line)?.groupValues?.get(1)?.trim()?.let { if (it.isNotEmpty()) pendingUserAgent = it }
                    extHttpRefRegex.find(line)?.groupValues?.get(1)?.trim()?.let { if (it.isNotEmpty()) pendingReferer = it }
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
                                tvgId = pendingTvgId,
                                userAgent = pendingUserAgent,
                                referer = pendingReferer
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
