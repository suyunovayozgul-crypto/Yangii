package uz.oktv.iptv

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

const val APP_VERSION_NAME = "v1.27 PRO"

data class M3UChannel(
    val id: Int,
    val tvgId: String,
    val name: String,
    val group: String,
    val logo: String,
    val url: String
)

data class EpgProgram(
    val startTimeMs: Long,
    val stopTimeMs: Long,
    val startTimeStr: String,
    val stopTimeStr: String,
    val dateStr: String,
    val title: String,
    val desc: String
)

data class KinopoiskData(
    val rating: String? = null,
    val posterUrl: String? = null,
    val year: String? = null,
    val description: String? = null,
    val genres: String? = null
)

data class AudioTrackItem(
    val groupIndex: Int,
    val trackIndexInGroup: Int,
    val language: String,
    val shortCode: String,
    val label: String,
    val isSelected: Boolean
)

// РАБОЧИЕ РЕЖИМЫ ТРАНСФОРМАЦИИ ВИДЕО ДЛЯ TV
enum class PlayerAspectMode(val title: String, val resizeMode: Int) {
    ASPECT_16_9("16:9", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ASPECT_FILL("Растянуть (Fill)", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ASPECT_ZOOM("Масштаб (Zoom)", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    ASPECT_WIDTH("По ширине", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    ASPECT_HEIGHT("По высоте", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT)
}

enum class AppScreenState { CHECKING, AUTH, LOADING_SYSTEM, MAIN_PLAYER }
enum class AppContentMode { TV, VOD }

enum class AppLang(val code: String, val title: String) {
    RU("ru", "Русский"),
    UZ("uz", "O'zbekcha"),
    EN("en", "English"),
    UK("uk", "Українська"),
    TR("tr", "Türkçe")
}

object Strings {
    private val translations = mapOf(
        "digital_tv" to mapOf(AppLang.RU to "Цифровое ТВ", AppLang.UZ to "Raqamli TV", AppLang.EN to "Digital TV", AppLang.UK to "Цифрове ТБ", AppLang.TR to "Dijital TV"),
        "vod" to mapOf(AppLang.RU to "Медиатека (Фильмы)", AppLang.UZ to "Mediateka (Filmlar)", AppLang.EN to "VOD / Cinema", AppLang.UK to "Медіатека (Фільми)", AppLang.TR to "Sinema / VOD"),
        "favorites" to mapOf(AppLang.RU to "Избранное", AppLang.UZ to "Saralanganlar", AppLang.EN to "Favorites", AppLang.UK to "Вибране", AppLang.TR to "Favoriler"),
        "categories" to mapOf(AppLang.RU to "Категории", AppLang.UZ to "Kategoriyalar", AppLang.EN to "Categories", AppLang.UK to "Категорії", AppLang.TR to "Kategoriler"),
        "playback_settings" to mapOf(AppLang.RU to "Настройки плеера", AppLang.UZ to "Pleyer sozlamalari", AppLang.EN to "Playback Settings", AppLang.UK to "Налаштування плеєра", AppLang.TR to "Oynatıcı Ayarları"),
        "language" to mapOf(AppLang.RU to "Язык интерфейса", AppLang.UZ to "Interfeys tili", AppLang.EN to "Language", AppLang.UK to "Мова інтерфейсу", AppLang.TR to "Dil Seçimi"),
        "speedtest" to mapOf(AppLang.RU to "Проверка скорости", AppLang.UZ to "Tezlikni tekshirish", AppLang.EN to "Speed Test", AppLang.UK to "Перевірка швидкості", AppLang.TR to "Hız Testi"),
        "support" to mapOf(AppLang.RU to "Поддержка", AppLang.UZ to "Qo'llab-quvvatlash", AppLang.EN to "Support", AppLang.UK to "Підтримка", AppLang.TR to "Destek"),
        "logout" to mapOf(AppLang.RU to "Сброс токена / Выход", AppLang.UZ to "Tokenni o'chirish / Chiqish", AppLang.EN to "Reset Token / Logout", AppLang.UK to "Скидання токена / Вихід", AppLang.TR to "Token Sıfırla / Çıkış"),
        "update_epg" to mapOf(AppLang.RU to "Обновить телепрограмму (EPG)", AppLang.UZ to "Teledasturni yangilash (EPG)", AppLang.EN to "Update EPG Schedule", AppLang.UK to "Оновити телепрограму (EPG)", AppLang.TR to "Yayın Akışını Güncelle"),
        "menu" to mapOf(AppLang.RU to "МЕНЮ", AppLang.UZ to "MENYU", AppLang.EN to "MENU", AppLang.UK to "МЕНЮ", AppLang.TR to "MENÜ"),
        "all" to mapOf(AppLang.RU to "Все", AppLang.UZ to "Barchasi", AppLang.EN to "All", AppLang.UK to "Всі", AppLang.TR to "Tümü"),
        "live" to mapOf(AppLang.RU to "Прямой эфир", AppLang.UZ to "Jonli efir", AppLang.EN to "Live Stream", AppLang.UK to "Прямий ефір", AppLang.TR to "Canlı Yayın"),
        "epg_schedule" to mapOf(AppLang.RU to "ТЕЛЕПРОГРАММА НА НЕДЕЛЮ", AppLang.UZ to "HAFTALIK TELEDASTUR", AppLang.EN to "WEEKLY EPG SCHEDULE", AppLang.UK to "ТИЖНЕВА ТЕЛЕПРОГРАМА", AppLang.TR to "HAFTALIK YAYIN AKIŞI"),
        "select_category" to mapOf(AppLang.RU to "ВЫБОР КАТЕГОРИИ", AppLang.UZ to "KATEGORIYA TANLASH", AppLang.EN to "SELECT CATEGORY", AppLang.UK to "ВИБІР КАТЕГОРІЇ", AppLang.TR to "KATEGORİ SEÇİN"),
        "speed_test_title" to mapOf(AppLang.RU to "Тест скорости сети Mirovoy TV", AppLang.UZ to "Mirovoy TV tarmoq tezligi testi", AppLang.EN to "Mirovoy TV Speed Test", AppLang.UK to "Тест швидкості мережі Mirovoy TV", AppLang.TR to "Mirovoy TV Hız Testi"),
        "ping" to mapOf(AppLang.RU to "Пинг", AppLang.UZ to "Ping", AppLang.EN to "Ping", AppLang.UK to "Пінг", AppLang.TR to "Ping"),
        "speed" to mapOf(AppLang.RU to "Скорость", AppLang.UZ to "Tezlik", AppLang.EN to "Speed", AppLang.UK to "Швидкість", AppLang.TR to "Hız"),
        "start_test" to mapOf(AppLang.RU to "НАЧАТЬ ТЕСТ", AppLang.UZ to "TESTNI BOSHLASH", AppLang.EN to "START TEST", AppLang.UK to "ПОЧАТИ ТЕСТ", AppLang.TR to "TESTİ BAŞLAT"),
        "audio_tracks" to mapOf(AppLang.RU to "ЗВУКОВЫЕ ДОРОЖКИ (АУДИО)", AppLang.UZ to "OVOZ YO'LLARI (AUDIO)", AppLang.EN to "AUDIO TRACKS", AppLang.UK to "ЗВУКОВі ДОРОЖКИ (АУДІО)", AppLang.TR to "SES PARÇALARI")
    )

    fun get(key: String, lang: AppLang): String {
        return translations[key]?.get(lang) ?: translations[key]?.get(AppLang.RU) ?: key
    }
}

fun getHlsHtml(streamUrl: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
            <style>
                html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
                video { position: absolute; left: 0; top: 0; width: 100%; height: 100%; background: #000; object-fit: contain; }
            </style>
        </head>
        <body>
            <video id="video" autoplay playsinline webkit-playsinline></video>
            <script>
                const video = document.getElementById('video');
                const url = '$streamUrl';
                if (Hls.isSupported()) {
                    const hls = new Hls({ 
                        debug: false, 
                        enableWorker: true, 
                        lowLatencyMode: false, 
                        backBufferLength: 0,
                        maxBufferLength: 10,
                        maxMaxBufferLength: 12,
                        maxBufferSize: 8 * 1024 * 1024,
                        liveSyncDurationCount: 3
                    });
                    hls.on(Hls.Events.MEDIA_ATTACHED, function () { hls.loadSource(url); });
                    hls.on(Hls.Events.MANIFEST_PARSED, function () { video.play().catch(e => console.log(e)); });
                    hls.on(Hls.Events.ERROR, function (event, data) {
                        if (data.fatal) {
                            switch (data.type) {
                                case Hls.ErrorTypes.NETWORK_ERROR: hls.startLoad(); break;
                                case Hls.ErrorTypes.MEDIA_ERROR: hls.recoverMediaError(); break;
                                default: hls.destroy(); break;
                            }
                        }
                    });
                    hls.attachMedia(video);
                } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                    video.src = url;
                    video.addEventListener('loadedmetadata', function () { video.play(); });
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

fun fastParseXmltvDate(raw: String?): Long {
    if (raw.isNullOrBlank() || raw.length < 14) return 0L
    val s = raw.trim()
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

fun formatTimeMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun calculateEpgProgress(program: EpgProgram?, currentTimeMs: Long): Float {
    if (program == null) return 0f
    val start = program.startTimeMs
    val stop = program.stopTimeMs
    if (start <= 0L || stop <= start) return 0f
    if (currentTimeMs <= start) return 0f
    if (currentTimeMs >= stop) return 1f
    return ((currentTimeMs - start).toFloat() / (stop - start).toFloat()).coerceIn(0f, 1f)
}

fun unifyCyrillicLatin(str: String): String {
    val s = str.lowercase(Locale.getDefault())
    val map = mapOf('h' to 'н', 't' to 'т', 'b' to 'в', 'c' to 'с', 'p' to 'р', 'a' to 'а', 'o' to 'о', 'e' to 'е', 'x' to 'х', 'k' to 'к', 'm' to 'м')
    val sb = StringBuilder()
    for (ch in s) {
        sb.append(map[ch] ?: ch)
    }
    return sb.toString()
}

fun normalizeForEpg(str: String): String {
    return unifyCyrillicLatin(str).replace(Regex("[^a-zа-я0-9]"), "")
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
    return schedule.sortedBy { it.startTimeMs }.firstOrNull { it.startTimeMs <= now && (it.stopTimeMs == 0L || it.stopTimeMs > now) }
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
    return epgMap[rawTvg] ?: epgMap[rawName] ?: epgMap[cleanTvg] ?: epgMap[cleanName] ?: epgMap[stripped] ?: emptyList()
}

fun cleanTitleForSearch(title: String): String {
    var n = title.lowercase(Locale.getDefault())
    n = n.replace(Regex("(?i)\\.(mp4|mkv|avi)$"), "")
    n = n.replace(Regex("(?i)\\b(webrip|hdrip|1080p|720p|bdrip|dvdrip|webdl|hdtv|x264|x265|rip|ma|dub|money|studio|х/ф|т/с|д/ф|м/ф|с/р|фильм|мультфильм|сериал|шоу|новости|премьера)\\b"), "")
    n = n.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ\\s]"), " ")
    n = n.replace(Regex("\\s+"), " ")
    return n.trim()
}

fun isLikelyFilmOrCartoon(title: String): Boolean {
    val t = title.lowercase(Locale.getDefault())
    return t.contains("х/ф") || t.contains("м/ф") || t.contains("т/с") || t.contains("д/ф") ||
            t.contains("фильм") || t.contains("мультфильм") || t.contains("кино") || t.contains("сериал")
}

fun Modifier.denimDoubleBorder(
    outerRadius: Float = 14f,
    innerRadius: Float = 10f
): Modifier = this
    .border(1.5.dp, Color(0xFF1E3A8A).copy(alpha = 0.8f), RoundedCornerShape(outerRadius.dp))
    .padding(2.5.dp)
    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(innerRadius.dp))