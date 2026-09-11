package uz.oktv.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PersonalPlaylist(
    val id: Long,
    val name: String,
    val url: String,
    val epgUrl: String = "",
    val epgEnabled: Boolean = false,
    val enabled: Boolean = true
)

class PersonalPlaylistStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("OKTV_PERSONAL_PLAYLISTS", Context.MODE_PRIVATE)

    private val keyPlaylists = "playlists"
    private val keyActiveId = "active_playlist_id"

    fun load(): List<PersonalPlaylist> {
        val raw = prefs.getString(keyPlaylists, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<PersonalPlaylist>()

            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)

                result += PersonalPlaylist(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    url = o.optString("url"),
                    epgUrl = o.optString("epgUrl"),
                    epgEnabled = o.optBoolean("epgEnabled", false),
                    enabled = o.optBoolean("enabled", true)
                )
            }

            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(list: List<PersonalPlaylist>) {
        val array = JSONArray()

        list.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("url", item.url)
                    put("epgUrl", item.epgUrl)
                    put("epgEnabled", item.epgEnabled)
                    put("enabled", item.enabled)
                }
            )
        }

        prefs.edit()
            .putString(keyPlaylists, array.toString())
            .apply()
    }

    fun add(
        name: String,
        url: String,
        epgUrl: String = "",
        epgEnabled: Boolean = false
    ): PersonalPlaylist {
        val item = PersonalPlaylist(
            id = System.currentTimeMillis(),
            name = name.trim().ifEmpty { "Плейлист" },
            url = url.trim(),
            epgUrl = epgUrl.trim(),
            epgEnabled = epgEnabled
        )

        val list = load().toMutableList()
        list.add(item)
        save(list)

        if (getActiveId() == null) {
            setActiveId(item.id)
        }

        return item
    }

    fun update(item: PersonalPlaylist) {
        val list = load().map {
            if (it.id == item.id) item else it
        }

        save(list)
    }

    fun delete(id: Long) {
        val list = load().filterNot { it.id == id }
        save(list)

        if (getActiveId() == id) {
            setActiveId(list.firstOrNull()?.id)
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        update(
            load().firstOrNull { it.id == id }?.copy(enabled = enabled)
                ?: return
        )
    }

    fun setActiveId(id: Long?) {
        prefs.edit()
            .apply {
                if (id == null) {
                    remove(keyActiveId)
                } else {
                    putLong(keyActiveId, id)
                }
            }
            .apply()
    }

    fun getActiveId(): Long? {
        if (!prefs.contains(keyActiveId)) return null

        return prefs.getLong(keyActiveId, -1L)
            .takeIf { it > 0L }
    }

    fun getActive(): PersonalPlaylist? {
        val list = load()
        val activeId = getActiveId()

        return list.firstOrNull { it.id == activeId && it.enabled }
            ?: list.firstOrNull { it.enabled }
    }
}
