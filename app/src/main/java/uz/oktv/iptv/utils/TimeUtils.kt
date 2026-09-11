package uz.oktv.iptv.utils

import uz.oktv.iptv.model.EpgProgram
import uz.oktv.iptv.model.M3UChannel
import java.text.SimpleDateFormat
import java.util.*

fun fastParseXmltvDate(raw: String?): Long {
    if (raw == null || raw.length < 14) return 0L
    val s = raw.trim()
    if (s.length < 14) return 0L
    return try {
        val year = (s[0] - '0') * 1000 + (s[1] - '0') * 100 + (s[2] - '0') * 10 + (s[3] - '0')
        val month = (s[4] - '0') * 10 + (s[5] - '0') - 1
        val day = (s[6] - '0') * 10 + (s[7] - '0')
        val hour = (s[8] - '0') * 10 + (s[9] - '0')
        val min = (s[10] - '0') * 10 + (s[11] - '0')
        val sec = (s[12] - '0') * 10 + (s[13] - '0')

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, month, day, hour, min, sec)
        cal.set(Calendar.MILLISECOND, 0)
        var time = cal.timeInMillis

        val plusIdx = s.indexOf('+')
        val minusIdx = s.indexOf('-', startIndex = 8)
        val signIdx = if (plusIdx != -1) plusIdx else minusIdx

        if (signIdx != -1 && s.length >= signIdx + 5) {
            val sign = s[signIdx]
            val tzH = (s[signIdx + 1] - '0') * 10 + (s[signIdx + 2] - '0')
            val tzM = (s[signIdx + 3] - '0') * 10 + (s[signIdx + 4] - '0')
            val offsetMs = (tzH * 3600L + tzM * 60L) * 1000L
            if (sign == '+') time -= offsetMs else time += offsetMs
        }
        time
    } catch (e: Exception) {
        0L
    }
}

fun formatTimeMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, mins, secs)
    else String.format("%02d:%02d", mins, secs)
}

fun formatLocalTime(epochMs: Long): String {
    if (epochMs == 0L) return "--:--"
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}

fun formatLocalDate(epochMs: Long): String {
    if (epochMs == 0L) return ""
    val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
    return sdf.format(Date(epochMs))
}

// ТОЧНЫЙ РАСЧЕТ ПРОГРЕССА EPG ПО ВРЕМЕНИ ДЛЯ ПОЛОСКИ
fun calculateEpgProgress(program: EpgProgram?, currentTimeMs: Long): Float {
    if (program == null) return 0f
    val start = program.startTimeMs
    val stop = program.stopTimeMs

    // Если время конца не указано или некорректно
    if (stop <= start || start == 0L) return 0f

    // Если передача еще не началась
    if (currentTimeMs <= start) return 0f

    // Если передача уже закончилась
    if (currentTimeMs >= stop) return 1f

    val elapsed = currentTimeMs - start
    val total = stop - start

    return (elapsed.toFloat() / total.toFloat()).coerceIn(0.01f, 1f)
}

fun unifyCyrillicLatin(str: String): String {
    val s = str.lowercase(Locale.getDefault())
    val map = mapOf(
        'h' to 'н', 't' to 'т', 'b' to 'в', 'c' to 'с', 'p' to 'р',
        'a' to 'а', 'o' to 'о', 'e' to 'е', 'x' to 'х', 'k' to 'к', 'm' to 'м'
    )
    val sb = StringBuilder()
    for (ch in s) {
        sb.append(map[ch] ?: ch)
    }
    return sb.toString()
}

fun normalizeForEpg(str: String): String {
    val unified = unifyCyrillicLatin(str)
    return unified.replace(Regex("[^a-zа-я0-9]"), "")
}

fun stripChannelTags(name: String): String {
    var n = name.lowercase(Locale.getDefault())
    n = n.replace(Regex("(?i)\\b(hd|fhd|uhd|4k|50fps|sd|hevc|резерв|премиум|original|orig|vip|auto)\\b"), "")
    n = n.replace(Regex("\\+[0-9]+"), "")
    n = n.replace(Regex("[\\(\\)\\[\\]\\-_]"), " ")
    return n.trim()
}

fun getCurrentProgram(schedule: List<EpgProgram>): EpgProgram? {
    if (schedule.isEmpty()) return null
    val now = System.currentTimeMillis()
    return schedule.firstOrNull { it.startTimeMs <= now && (it.stopTimeMs == 0L || it.stopTimeMs > now) }
        ?: schedule.filter { it.startTimeMs <= now }.maxByOrNull { it.startTimeMs }
        ?: schedule.firstOrNull()
}

fun getNextProgram(schedule: List<EpgProgram>, current: EpgProgram?): EpgProgram? {
    if (schedule.isEmpty() || current == null) return null
    val idx = schedule.indexOf(current)
    if (idx != -1 && idx + 1 < schedule.size) {
        return schedule[idx + 1]
    }
    return schedule.firstOrNull { it.startTimeMs > current.stopTimeMs }
}

fun getScheduleForChannel(channel: M3UChannel?, epgMap: Map<String, List<EpgProgram>>): List<EpgProgram> {
    if (channel == null || epgMap.isEmpty()) return emptyList()
    val rawName = channel.name
    val rawTvg = channel.tvgId

    val cleanName = normalizeForEpg(rawName)
    val cleanTvg = normalizeForEpg(rawTvg)
    val stripped = normalizeForEpg(stripChannelTags(rawName))

    return epgMap[rawTvg]
        ?: epgMap[rawName]
        ?: epgMap[cleanTvg]
        ?: epgMap[cleanName]
        ?: epgMap[stripped]
        ?: emptyList()
}

fun cleanTitleForSearch(title: String): String {
    var n = title.lowercase(Locale.getDefault())
    n = n.replace(Regex("(?i)\\.(mp4|mkv|avi)$"), "")
    n = n.replace(Regex("^[0-9]{1,2}:[0-9]{2}"), "")
    n = n.replace(Regex("(?i)(х/ф|т/с|д/ф|м/ф|с/р|х\\/ф|т\\/с|д\\/ф|м\\/ф|webrip|hdrip|1080p|720p|bdrip|dvdrip|webdl|hdtv|x264|x265|rip|ma|dub|money|studio|шоу|новости|премьера)"), " ")
    n = n.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ\\s]"), " ")
    n = n.replace(Regex("\\s+"), " ")
    return n.trim()
}