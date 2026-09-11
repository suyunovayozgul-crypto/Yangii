package uz.oktv.iptv

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

fun parseEpgFileSafely(cacheGzFile: File): Map<String, MutableList<EpgProgram>> {
    val resultMap = mutableMapOf<String, MutableList<EpgProgram>>()
    if (!cacheGzFile.exists()) return resultMap
    try {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()

        val nowTime = System.currentTimeMillis()
        val minValidTime = nowTime - 12 * 3600 * 1000L

        FileInputStream(cacheGzFile).use { fis ->
            BufferedInputStream(GZIPInputStream(BufferedInputStream(fis, 262144)), 262144).use { bis ->
                parser.setInput(bis, "UTF-8")
                var eventType = parser.eventType
                var curChannelId: String? = null
                var curStart = 0L
                var curStop = 0L
                var curTitle = ""
                var curDesc = ""

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val tagName = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (tagName.equals("programme", ignoreCase = true)) {
                                curChannelId = parser.getAttributeValue(null, "channel")
                                curStart = fastParseXmltvDate(parser.getAttributeValue(null, "start"))
                                curStop = fastParseXmltvDate(parser.getAttributeValue(null, "stop"))
                                curTitle = ""
                                curDesc = ""
                            } else if (tagName.equals("title", ignoreCase = true)) {
                                curTitle = parser.nextText() ?: ""
                            } else if (tagName.equals("desc", ignoreCase = true)) {
                                curDesc = parser.nextText() ?: ""
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (tagName.equals("programme", ignoreCase = true) && !curChannelId.isNullOrEmpty() && curTitle.isNotEmpty()) {
                                if (curStop == 0L || curStop >= minValidTime) {
                                    val prog = EpgProgram(
                                        startTimeMs = curStart,
                                        stopTimeMs = curStop,
                                        startTimeStr = formatLocalTime(curStart),
                                        stopTimeStr = formatLocalTime(curStop),
                                        dateStr = formatLocalDate(curStart),
                                        title = curTitle.trim(),
                                        desc = curDesc.trim()
                                    )
                                    resultMap.getOrPut(curChannelId) { mutableListOf() }.add(prog)
                                    resultMap.getOrPut(normalizeForEpg(curChannelId)) { mutableListOf() }.add(prog)
                                    resultMap.getOrPut(normalizeForEpg(stripChannelTags(curChannelId))) { mutableListOf() }.add(prog)
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return resultMap
}