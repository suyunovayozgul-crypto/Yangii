package uz.oktv.iptv

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object EpgRepository {

    private const val DEFAULT_EPG_URL = "https://oktv.uz/epg.xml.gz"

    fun cacheFile(cacheDir: File): File {
        return File(cacheDir, "epg_cache.xml.gz")
    }

    fun isCacheFresh(
        cacheFile: File,
        maxAgeMs: Long = 24L * 3600L * 1000L
    ): Boolean {
        return cacheFile.exists() &&
            (System.currentTimeMillis() - cacheFile.lastModified() < maxAgeMs)
    }

    fun downloadEpg(
        cacheDir: File,
        epgUrl: String = DEFAULT_EPG_URL,
        onProgress: (Float) -> Unit = {}
    ): File {

        val cacheGzFile = cacheFile(cacheDir)

        val conn = URL(epgUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000

        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException(
                    "EPG HTTP ${conn.responseCode}"
                )
            }

            val fileLength = conn.contentLength.toLong()
            var downloaded = 0L

            BufferedInputStream(
                conn.inputStream,
                262144
            ).use { input ->

                FileOutputStream(cacheGzFile).use { output ->

                    val buffer = ByteArray(32768)

                    while (true) {
                        val bytesRead = input.read(buffer)

                        if (bytesRead == -1) break

                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        if (fileLength > 0L) {
                            onProgress(
                                (downloaded.toFloat() / fileLength.toFloat())
                                    .coerceIn(0f, 1f)
                            )
                        }
                    }
                }
            }

            return cacheGzFile
        } finally {
            conn.disconnect()
        }
    }
}
