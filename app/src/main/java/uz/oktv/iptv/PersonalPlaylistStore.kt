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
    val enabled: Boolean = true,
    val isBuiltin: Boolean = false  // qattiq yozilgan = true
)

// Qattiq yozilgan — ko'rinadi, tanlanadi, o'chirilmaydi, URL ko'rinmaydi
private val BUILTIN_PLAYLISTS = listOf(
    PersonalPlaylist(id = 9001L, name = "Mirovoy 1", url = "https://mirovoytv.uz/playlists/c042aeff.m3u8", isBuiltin = true),
    PersonalPlaylist(id = 9002L, name = "Mirovoy 2", url = "https://mirovoytv.uz/playlists/b62e592a.m3u",  isBuiltin = true),
    PersonalPlaylist(id = 9003L, name = "Mirovoy 3", url = "https://mirovoytv.uz/playlists/813bc163.m3u",  isBuiltin = true),
    PersonalPlaylist(id = 9004L, name = "Mediatek",  url = "https://mirovoytv.uz/playlists/77aa14e5.m3u8", isBuiltin = true)
)

// Foydalanuvchi qo'sha oladigan maksimal playlist soni
private const val MAX_USER_PLAYLISTS = 2

class PersonalPlaylistStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("OKTV_PERSONAL_PLAYLISTS", Context.MODE_PRIVATE)

    private val keyUserPlaylists = "user_playlists_v2"
    private val keyActiveId      = "active_playlist_id"

    // Barcha playlistlar = qattiq + foydalanuvchi
    fun load(): List<PersonalPlaylist> {
        if (getActiveId() == null) {
            setActiveId(BUILTIN_PLAYLISTS.first().id)
        }
        return BUILTIN_PLAYLISTS + loadUserPlaylists()
    }

    // Foydalanuvchi playlistlarini SharedPreferences dan o'qish
    private fun loadUserPlaylists(): List<PersonalPlaylist> {
        val json = prefs.getString(keyUserPlaylists, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PersonalPlaylist(
                    id         = obj.getLong("id"),
                    name       = obj.getString("name"),
                    url        = obj.getString("url"),
                    epgUrl     = obj.optString("epgUrl", ""),
                    epgEnabled = obj.optBoolean("epgEnabled", false),
                    enabled    = obj.optBoolean("enabled", true),
                    isBuiltin  = false
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Foydalanuvchi playlistlarini saqlash
    private fun saveUserPlaylists(list: List<PersonalPlaylist>) {
        val arr = JSONArray()
        list.filter { !it.isBuiltin }.forEach { p ->
            arr.put(JSONObject().apply {
                put("id",         p.id)
                put("name",       p.name)
                put("url",        p.url)
                put("epgUrl",     p.epgUrl)
                put("epgEnabled", p.epgEnabled)
                put("enabled",    p.enabled)
            })
        }
        prefs.edit().putString(keyUserPlaylists, arr.toString()).apply()
    }

    // Yangi playlist qo'shish (max 2 ta)
    fun add(name: String, url: String, epgUrl: String = "", epgEnabled: Boolean = false): PersonalPlaylist? {
        val userList = loadUserPlaylists()
        if (userList.size >= MAX_USER_PLAYLISTS) return null  // limit

        val item = PersonalPlaylist(
            id         = System.currentTimeMillis(),
            name       = name.trim().ifEmpty { "Mening playlist" },
            url        = url.trim(),
            epgUrl     = epgUrl.trim(),
            epgEnabled = epgEnabled,
            isBuiltin  = false
        )
        saveUserPlaylists(userList + item)

        if (getActiveId() == null) setActiveId(item.id)
        return item
    }

    // O'chirish — faqat foydalanuvchi playlistlari
    fun delete(id: Long) {
        val userList = loadUserPlaylists().filterNot { it.id == id }
        saveUserPlaylists(userList)

        if (getActiveId() == id) {
            setActiveId(load().firstOrNull()?.id)
        }
    }

    fun save(list: List<PersonalPlaylist>) {
        saveUserPlaylists(list.filter { !it.isBuiltin })
    }

    fun update(item: PersonalPlaylist) {
        if (item.isBuiltin) return  // qattiq yozilganni o'zgartirib bo'lmaydi
        val userList = loadUserPlaylists().map { if (it.id == item.id) item else it }
        saveUserPlaylists(userList)
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        val item = load().firstOrNull { it.id == id } ?: return
        if (!item.isBuiltin) update(item.copy(enabled = enabled))
    }

    fun canAddMore(): Boolean = loadUserPlaylists().size < MAX_USER_PLAYLISTS

    fun setActiveId(id: Long?) {
        prefs.edit().apply {
            if (id == null) remove(keyActiveId)
            else putLong(keyActiveId, id)
        }.apply()
    }

    fun getActiveId(): Long? {
        if (!prefs.contains(keyActiveId)) return null
        return prefs.getLong(keyActiveId, -1L).takeIf { it > 0L }
    }

    fun getActive(): PersonalPlaylist? {
        val list = load()
        val activeId = getActiveId()
        return list.firstOrNull { it.id == activeId && it.enabled }
            ?: list.firstOrNull { it.enabled }
    }
}
