package com.signal.iptv

import android.os.Handler
import android.os.Looper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

/** One EPG entry: a single programme airing on a single channel. */
data class Programme(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String
)

/**
 * Downloads and parses an XMLTV (optionally gzip-compressed) EPG feed once,
 * then serves lookups from an in-memory map keyed by the XMLTV channel id
 * (matched against each playlist entry's tvg-id).
 *
 * The feed can be large, so parsing streams the XML instead of building a DOM.
 */
object EpgRepository {

    // XMLTV time format, e.g. "20260826120000 +0500"
    private val timeFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loading = AtomicBoolean(false)
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    @Volatile private var programmesByChannel: Map<String, List<Programme>> = emptyMap()
    @Volatile var isLoaded: Boolean = false
        private set

    // Oxirgi urinish muvaffaqiyatsiz tugagan bo'lsa, keyingi ensureLoaded() chaqiruvi
    // (masalan, foydalanuvchi playerni qayta ochganda) darhol qayta urinib ko'rishi kerak —
    // aks holda EPG bir marta yuklanmasa, butun ilova umri davomida abadiy yo'qolib qoladi.
    @Volatile private var lastAttemptFailed = false

    /**
     * Kicks off a background download+parse if not already loaded/loading.
     * [onLoaded] is called on the main thread once data is ready (immediately
     * if it already is). Safe to call multiple times (e.g. from every screen).
     * Agar oldingi urinish xato bo'lsa, qayta yuklashga harakat qiladi.
     */
    fun ensureLoaded(url: String, onLoaded: (() -> Unit)? = null) {
        if (isLoaded && !lastAttemptFailed) {
            onLoaded?.invoke()
            return
        }
        synchronized(pendingCallbacks) {
            if (onLoaded != null) pendingCallbacks.add(onLoaded)
        }
        if (!loading.compareAndSet(false, true)) return

        executor.execute {
            val result = try {
                fetchAndParse(url)
            } catch (e: Exception) {
                null
            }
            if (result != null && result.isNotEmpty()) {
                programmesByChannel = result
                isLoaded = true
                lastAttemptFailed = false
            } else {
                // Eski ma'lumot bo'lsa saqlanib qoladi (ekranda hech bo'lmasa eski EPG
                // ko'rinib tursin), lekin keyingi safar qayta yuklab ko'rishga urinamiz.
                lastAttemptFailed = true
                isLoaded = programmesByChannel.isNotEmpty()
            }
            loading.set(false)
            mainHandler.post {
                val callbacks = synchronized(pendingCallbacks) {
                    val copy = pendingCallbacks.toList()
                    pendingCallbacks.clear()
                    copy
                }
                callbacks.forEach { it.invoke() }
            }
            // Muvaffaqiyatsiz bo'lsa — bir muncha vaqtdan so'ng fonda avtomatik qayta urinamiz,
            // foydalanuvchi ekranni qayta ochishini kutmasdan.
            if (lastAttemptFailed) {
                mainHandler.postDelayed({ ensureLoaded(url, null) }, RETRY_DELAY_MS)
            }
        }
    }

    // Playlist va EPG fayli har xil provayderlardan kelgani uchun tvg-id yozilishi
    // bir xil bo'lmasligi mumkin (katta/kichik harf, bo'sh joy) — shuning uchun
    // kalitlar har doim normallashtirilgan (trim + lowercase) holda saqlanadi va
    // qidiriladi, aks holda mos kelishi mumkin bo'lgan yozuvlar ham topilmay qolardi.
    private fun normalizeId(id: String) = id.trim().lowercase()

    /** The programme airing right now on [tvgId], or null if unknown/no data. */
    fun currentProgramme(tvgId: String, now: Long = System.currentTimeMillis()): Programme? {
        if (tvgId.isBlank()) return null
        val list = programmesByChannel[normalizeId(tvgId)] ?: return null
        return list.firstOrNull { now in it.start until it.stop }
    }

    /** The next scheduled programme on [tvgId] after [now], or null. */
    fun nextProgramme(tvgId: String, now: Long = System.currentTimeMillis()): Programme? {
        if (tvgId.isBlank()) return null
        val list = programmesByChannel[normalizeId(tvgId)] ?: return null
        return list.firstOrNull { it.start >= now }
    }

    /**
     * To'liq dastur jadvali: o'tgan, hozirgi va kelasi ko'rsatuvlar — faqat "hozir"
     * emas, balki foydalanuvchi so'ragan "to'liq EPG" uchun. Vaqt bo'yicha saralangan.
     */
    fun fullSchedule(tvgId: String): List<Programme> {
        if (tvgId.isBlank()) return emptyList()
        return (programmesByChannel[normalizeId(tvgId)] ?: emptyList()).sortedBy { it.start }
    }

    private fun fetchAndParse(url: String): Map<String, List<Programme>> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        // EPG fayllari (ayniqsa gzip ochilgandan keyin) o'nlab megabaytgacha
        // borishi mumkin — sekin mobil tarmoqda 30s yetarli emas edi, shu
        // sabab EPG hech qachon "isLoaded" holatiga o'tolmay, doim
        // "yangilanmoqda" holida qolib ketardi.
        connection.readTimeout = 90000
        connection.instanceFollowRedirects = true
        // Ba'zi EPG serverlari (jumladan iptvx.one) generik/no'malum User-Agent'li
        // so'rovlarni bloklaydi — Televizo kabi haqiqiy ilovalar brauzerga o'xshash
        // sarlavha bilan so'raydi, biz ham xuddi shunday qilamiz.
        connection.setRequestProperty("User-Agent", M3UParser.BROWSER_USER_AGENT)
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Referer", url)

        val buffered = BufferedInputStream(connection.inputStream)
        buffered.mark(2)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()
        val isGzip = b0 == 0x1f && b1 == 0x8b

        val stream = if (isGzip) GZIPInputStream(buffered) else buffered
        val result = parse(stream)
        stream.close()
        connection.disconnect()
        return result
    }

    private fun parse(input: java.io.InputStream): Map<String, List<Programme>> {
        val map = HashMap<String, MutableList<Programme>>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, "UTF-8")

        var eventType = parser.eventType
        var curChannel: String? = null
        var curStart = 0L
        var curStop = 0L
        val titleBuilder = StringBuilder()
        var inTitle = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "programme" -> {
                            curChannel = parser.getAttributeValue(null, "channel")
                            curStart = parseTime(parser.getAttributeValue(null, "start"))
                            curStop = parseTime(parser.getAttributeValue(null, "stop"))
                            titleBuilder.setLength(0)
                        }
                        "title" -> inTitle = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) titleBuilder.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "title" -> inTitle = false
                        "programme" -> {
                            val channelId = curChannel
                            if (!channelId.isNullOrBlank() && curStart > 0L && curStop > 0L) {
                                val key = normalizeId(channelId)
                                map.getOrPut(key) { mutableListOf() }
                                    .add(Programme(channelId, curStart, curStop, titleBuilder.toString().trim()))
                            }
                            curChannel = null
                        }
                    }
                }
                else -> Unit
            }
            eventType = try { parser.next() } catch (e: Exception) { XmlPullParser.END_DOCUMENT }
        }
        return map
    }

    private const val RETRY_DELAY_MS = 45_000L

    // XMLTV manbalari vaqtni turlicha formatda beradi: "20260826120000 +0500",
    // "20260826120000+0500" (bo'shliqsiz) yoki hech qanday zona ko'rsatmasdan
    // "20260826120000". Faqat bitta qat'iy formatni kutish ba'zi manbalarda
    // butun faylni "0 dastur topildi" holiga olib kelishi mumkin edi.
    private val timeFormatNoSpace = SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US)
    private val timeFormatNoZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    private fun parseTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val trimmed = raw.trim()
        timeFormat.runCatching { parse(trimmed)?.time }.getOrNull()?.let { return it }
        timeFormatNoSpace.runCatching { parse(trimmed.replace(" ", ""))?.time }.getOrNull()?.let { return it }
        val digitsOnly = trimmed.takeWhile { it.isDigit() }
        if (digitsOnly.length >= 14) {
            timeFormatNoZone.runCatching { parse(digitsOnly.substring(0, 14))?.time }.getOrNull()?.let { return it }
        }
        return 0L
    }
}
