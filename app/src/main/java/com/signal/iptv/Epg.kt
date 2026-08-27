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

    /**
     * Kicks off a background download+parse if not already loaded/loading.
     * [onLoaded] is called on the main thread once data is ready (immediately
     * if it already is). Safe to call multiple times (e.g. from every screen).
     */
    fun ensureLoaded(url: String, onLoaded: (() -> Unit)? = null) {
        if (isLoaded) {
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
                emptyMap()
            }
            programmesByChannel = result
            isLoaded = true
            mainHandler.post {
                val callbacks = synchronized(pendingCallbacks) {
                    val copy = pendingCallbacks.toList()
                    pendingCallbacks.clear()
                    copy
                }
                callbacks.forEach { it.invoke() }
            }
        }
    }

    /** The programme airing right now on [tvgId], or null if unknown/no data. */
    fun currentProgramme(tvgId: String, now: Long = System.currentTimeMillis()): Programme? {
        if (tvgId.isBlank()) return null
        val list = programmesByChannel[tvgId] ?: return null
        return list.firstOrNull { now in it.start until it.stop }
    }

    /** The next scheduled programme on [tvgId] after [now], or null. */
    fun nextProgramme(tvgId: String, now: Long = System.currentTimeMillis()): Programme? {
        if (tvgId.isBlank()) return null
        val list = programmesByChannel[tvgId] ?: return null
        return list.firstOrNull { it.start >= now }
    }

    private fun fetchAndParse(url: String): Map<String, List<Programme>> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (MirovoyTV Android)")

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
                                map.getOrPut(channelId) { mutableListOf() }
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

    private fun parseTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            timeFormat.parse(raw.trim())?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
