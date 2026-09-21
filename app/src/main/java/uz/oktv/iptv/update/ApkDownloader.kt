package uz.oktv.iptv.update

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads an update APK into the app's own private external-files folder
 * (no storage permission needed - this is app-scoped storage). The file is
 * only reachable by the system installer once AppUpdateInstaller wraps it in
 * a FileProvider content:// URI, see file_paths.xml ("updates/").
 */
object ApkDownloader {

    /** Where the downloaded update lives; must match file_paths.xml. */
    fun updateFile(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        return File(dir, "update.apk")
    }

    /**
     * Blocking download - always run off the main thread.
     * [onProgress] receives 0..100 and may be called from this same background
     * thread; hop to the main thread yourself if you use it to update UI.
     *
     * Returns the downloaded file, or null if the download failed (caller
     * should show a simple "download failed, try again" message).
     */
    fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit = {}
    ): File? {
        val target = updateFile(context)
        // Never let a half-written file from an interrupted previous attempt
        // get handed to the installer.
        if (target.exists()) target.delete()

        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val totalBytes = connection.contentLength
            var downloaded = 0

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            onProgress((downloaded * 100L / totalBytes).toInt())
                        }
                    }
                }
            }

            // Serverning o'zi ulanishni erta uzib qo'yishi mumkin — bunda
            // input.read() xatosiz -1 qaytaradi, lekin fayl chala qoladi.
            // Shuni ushlab, chala APK o'rnatilishining oldini olamiz.
            if (totalBytes > 0 && downloaded < totalBytes) {
                target.delete()
                return null
            }

            target
        } catch (e: Exception) {
            target.delete()
            null
        } finally {
            connection.disconnect()
        }
    }
}
