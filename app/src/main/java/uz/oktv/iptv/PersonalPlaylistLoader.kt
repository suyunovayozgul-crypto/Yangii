package uz.oktv.iptv

import java.net.HttpURLConnection
import java.net.URL

data class PersonalPlaylistLoadResult(
    val channels: List<M3UChannel>,
    val categories: List<String>
)

object PersonalPlaylistLoader {

    fun load(urlString: String): PersonalPlaylistLoadResult {
        val parsedChannels = mutableListOf<M3UChannel>()
        val categories = mutableSetOf<String>()

        val url = URL(urlString.trim())
        val conn = url.openConnection() as HttpURLConnection

        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = true

        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }

            conn.inputStream.bufferedReader().use { reader ->
                var currentTvgId = ""
                var currentName = ""
                var currentGroup = "Общие"
                var currentLogo = ""
                var idCounter = 1

                reader.forEachLine { rawLine ->
                    val line = rawLine.trim()

                    if (line.isEmpty()) return@forEachLine

                    if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                        currentTvgId = extractAttribute(line, "tvg-id")
                        currentLogo = extractAttribute(line, "tvg-logo")

                        val group =
                            extractAttribute(line, "group-title")
                                .ifEmpty { extractExtGrp(line) }

                        currentGroup = group.ifEmpty { "Общие" }

                        currentName =
                            line.substringAfterLast(",")
                                .trim()

                        categories.add(currentGroup)

                    } else if (
                        line.isNotEmpty() &&
                        !line.startsWith("#")
                    ) {
                        parsedChannels += M3UChannel(
                            id = idCounter++,
                            tvgId = currentTvgId,
                            name = currentName.ifEmpty { "Канал $idCounter" },
                            group = currentGroup,
                            logo = currentLogo,
                            url = line
                        )

                        currentTvgId = ""
                        currentName = ""
                        currentLogo = ""
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        val finalCategories =
            listOf("Все", "❤️ Избранное") +
                categories.toList().sorted()

        return PersonalPlaylistLoadResult(
            channels = parsedChannels,
            categories = finalCategories
        )
    }

    private fun extractAttribute(
        line: String,
        name: String
    ): String {
        val marker = "$name=\""
        val start = line.indexOf(marker, ignoreCase = true)
        if (start < 0) return ""

        val valueStart = start + marker.length
        val end = line.indexOf('"', valueStart)

        return if (end > valueStart) {
            line.substring(valueStart, end)
        } else {
            ""
        }
    }

    private fun extractExtGrp(line: String): String {
        val marker = "#EXTGRP:"
        if (!line.startsWith(marker, ignoreCase = true)) {
            return ""
        }

        return line.substringAfter(":", "")
            .trim()
    }
}
