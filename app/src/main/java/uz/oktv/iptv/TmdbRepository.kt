package uz.oktv.iptv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TmdbMovie(
    val title: String,
    val posterUrl: String,
    val year: String,
    val rating: String,
    val overview: String
)

object TmdbRepository {
    private const val API_KEY = "c307e4dfd6998099d3ef15b881b2c7ca"
    private const val BASE_URL = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

    fun fetchNowPlaying(lang: String = "ru-RU"): List<TmdbMovie> {
        return try {
            val url = URL("$BASE_URL/movie/now_playing?api_key=$API_KEY&language=$lang&page=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val results = json.getJSONArray("results")

            (0 until minOf(results.length(), 6)).map { i ->
                val movie = results.getJSONObject(i)
                TmdbMovie(
                    title     = movie.optString("title", ""),
                    posterUrl = IMAGE_BASE + movie.optString("poster_path", ""),
                    year      = movie.optString("release_date", "").take(4),
                    rating    = String.format("%.1f", movie.optDouble("vote_average", 0.0)),
                    overview  = movie.optString("overview", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun fetchTrending(lang: String = "ru-RU"): List<TmdbMovie> {
        return try {
            val url = URL("$BASE_URL/trending/movie/week?api_key=$API_KEY&language=$lang")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val results = json.getJSONArray("results")

            (0 until minOf(results.length(), 6)).map { i ->
                val movie = results.getJSONObject(i)
                TmdbMovie(
                    title     = movie.optString("title", movie.optString("name", "")),
                    posterUrl = IMAGE_BASE + movie.optString("poster_path", ""),
                    year      = movie.optString("release_date", movie.optString("first_air_date", "")).take(4),
                    rating    = String.format("%.1f", movie.optDouble("vote_average", 0.0)),
                    overview  = movie.optString("overview", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
