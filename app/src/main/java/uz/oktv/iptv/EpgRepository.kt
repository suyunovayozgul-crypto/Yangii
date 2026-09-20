package uz.oktv.iptv

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object EpgRepository {

    // Yashirin asosiy EPG URL — foydalanuvchi ko'ra olmaydi
    private const val BUILTIN_EPG_URL = "https://oktv.uz/epg.xml.gz"

    // Haftada bir marta yangilanadi (7 kun)
    private const val EPG_MAX_AGE_MS = 7L * 24L * 3600L * 1000L

    // SharedPreferences kalitlari
    private const val PREFS_NAME    = "epg_settings"
    private const val KEY_EPG_URL_1 = "epg_url_1"
    private const val KEY_EPG_URL_2 = "epg_url_2"
    private const val KEY_EPG_URL_3 = "epg_url_3"
    private const val KEY_LAST_UPDATE = "epg_last_update"
    private const val KEY_FIRST_LAUNCH = "epg_first_launch_done"

    fun cacheFile(cacheDir: File): File = File(cacheDir, "epg_cache.xml.gz")

    // Cache yangilanishi kerakmi? (haftada bir yoki birinchi marta)
    fun needsUpdate(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val firstDone = prefs.getBoolean(KEY_FIRST_LAUNCH, false)
        if (!firstDone) return true

        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        return System.currentTimeMillis() - lastUpdate > EPG_MAX_AGE_MS
    }

    fun markUpdated(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .putBoolean(KEY_FIRST_LAUNCH, true)
            .apply()
    }

    // Foydalanuvchi URL larini olish
    fun getUserUrls(context: Context): Triple<String, String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Triple(
            prefs.getString(KEY_EPG_URL_1, "") ?: "",
            prefs.getString(KEY_EPG_URL_2, "") ?: "",
            prefs.getString(KEY_EPG_URL_3, "") ?: ""
        )
    }

    // Foydalanuvchi URL larini saqlash
    fun saveUserUrls(context: Context, url1: String, url2: String, url3: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_EPG_URL_1, url1.trim())
            .putString(KEY_EPG_URL_2, url2.trim())
            .putString(KEY_EPG_URL_3, url3.trim())
            .apply()
    }

    // Yuklash uchun URL ro'yxati: avval asosiy, keyin foydalanuvchi qo'shganlari
    fun getActiveUrls(context: Context): List<String> {
        val (u1, u2, u3) = getUserUrls(context)
        return listOf(BUILTIN_EPG_URL, u1, u2, u3).filter { it.isNotEmpty() }
    }

    fun isCacheFresh(cacheFile: File): Boolean {
        return cacheFile.exists() &&
            (System.currentTimeMillis() - cacheFile.lastModified() < EPG_MAX_AGE_MS)
    }

    fun downloadEpg(
        cacheDir: File,
        epgUrl: String = BUILTIN_EPG_URL,
        onProgress: (Float) -> Unit = {}
    ): File {
        val cacheGzFile = cacheFile(cacheDir)
        val conn = URL(epgUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout    = 30000

        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("EPG HTTP ${conn.responseCode}")
            }

            val fileLength = conn.contentLength.toLong()
            var downloaded = 0L

            BufferedInputStream(conn.inputStream, 262144).use { input ->
                FileOutputStream(cacheGzFile).use { output ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (fileLength > 0L) {
                            onProgress((downloaded.toFloat() / fileLength.toFloat()).coerceIn(0f, 1f))
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
