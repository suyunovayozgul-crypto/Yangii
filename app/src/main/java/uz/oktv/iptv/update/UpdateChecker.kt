package uz.oktv.iptv.update

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Result of a successful "does a newer release exist" check.
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String
)

/**
 * Checks a GitHub repository's Releases for a version newer than the one
 * currently installed, using the public GitHub REST API (no auth token
 * needed for public repos, but rate-limited to ~60 requests/hour per IP -
 * plenty for an app that checks once per launch).
 *
 * How this expects releases to be tagged:
 *   - Tag name (or release name) must contain the numeric versionCode,
 *     e.g. tag "v28" or "release-28" for versionCode 28. This avoids
 *     fragile string comparison of version names like "1.27" vs "1.9".
 *   - Exactly one asset attached to the release must be a ".apk" file.
 *
 * REPO must be set to your own "owner/repo" before this does anything useful.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    // TODO: replace with your actual GitHub "owner/repo", e.g. "ismoil123/Yangii"
    private const val REPO = "suyunovayozgul-crypto/Yangii"

    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /**
     * Blocking network call - always run this off the main thread
     * (e.g. from a coroutine on Dispatchers.IO).
     *
     * Returns null if no update is available, the repo isn't configured yet,
     * or the check failed for any reason (no internet, API error, etc.) -
     * callers should treat null as "nothing to do", not as an error to show
     * the user, since a failed update check should never block using the app.
     */
    fun checkForUpdate(currentVersionCode: Int): UpdateInfo? {
        if (REPO == "OWNER/REPO") {
            Log.w(TAG, "UpdateChecker.REPO is not configured - skipping update check")
            return null
        }

        return try {
            val json = fetchJson(API_URL) ?: return null

            val tagName = json.optString("tag_name", "")
            val body = json.optString("body", "")
            val remoteVersionCode = extractVersionCode(tagName)
                ?: extractVersionCode(json.optString("name", ""))

            if (remoteVersionCode == null) {
                Log.w(TAG, "Could not find a numeric version code in tag '$tagName'")
                return null
            }

            if (remoteVersionCode <= currentVersionCode) {
                // Already up to date.
                return null
            }

            val apkUrl = findApkAssetUrl(json.optJSONArray("assets"))
            if (apkUrl == null) {
                Log.w(TAG, "Release $tagName has no .apk asset attached")
                return null
            }

            UpdateInfo(
                versionName = tagName,
                versionCode = remoteVersionCode,
                downloadUrl = apkUrl,
                releaseNotes = body
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            null
        }
    }

    private fun findApkAssetUrl(assets: JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url", null.toString())
                    .takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun extractVersionCode(text: String): Int? =
        Regex("""\d+""").find(text)?.value?.toIntOrNull()

    private fun fetchJson(url: String): JSONObject? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub API returned HTTP ${connection.responseCode}")
                return null
            }

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}
