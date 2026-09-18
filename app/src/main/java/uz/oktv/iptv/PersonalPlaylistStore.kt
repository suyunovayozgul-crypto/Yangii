package uz.oktv.iptv

import android.content.Context

data class PersonalPlaylist(
    val id: Long,
    val name: String,
    val url: String,
    val epgUrl: String = "",
    val epgEnabled: Boolean = false,
    val enabled: Boolean = true
)

/**
 * Ilova ichiga qattiq o'rnatilgan (hardcoded) plейlistlar — foydalanuvchi
 * bularni ko'ra olmaydi, tahrirlay olmaydi va o'zi qo'lda havola qo'sha
 * olmaydi. Faqat shular orasidan birini TANLASH imkoni beriladi
 * (PersonalPlaylistScreen orqali).
 */
private val BUILTIN_PLAYLISTS = listOf(
    PersonalPlaylist(id = 9001L, name = "Mirovoy TV 1", url = "https://mirovoytv.uz/playlists/c042aeff.m3u8"),
    PersonalPlaylist(id = 9002L, name = "Mirovoy TV 2", url = "https://mirovoytv.uz/playlists/b62e592a.m3u"),
    PersonalPlaylist(id = 9003L, name = "Mirovoy TV 3", url = "https://mirovoytv.uz/playlists/813bc163.m3u"),
    PersonalPlaylist(id = 9004L, name = "Mediatek", url = "https://mirovoytv.uz/playlists/77aa14e5.m3u8")
)

class PersonalPlaylistStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("OKTV_PERSONAL_PLAYLISTS", Context.MODE_PRIVATE)

    private val keyPlaylists = "playlists"
    private val keyActiveId = "active_playlist_id"

    /**
     * Foydalanuvchi hech qachon o'zi playlist qo'sha olmaydi — shuning uchun
     * bu funksiya har doim faqat qattiq o'rnatilgan 3 ta playlistni qaytaradi.
     * Eski versiyalarda saqlanib qolgan (agar bo'lsa) qo'lda kiritilgan
     * yozuvlar butunlay e'tiborga olinmaydi.
     */
    fun load(): List<PersonalPlaylist> {
        if (getActiveId() == null) {
            setActiveId(BUILTIN_PLAYLISTS.first().id)
        }
        return BUILTIN_PLAYLISTS
    }

    fun save(list: List<PersonalPlaylist>) {
        // Qasddan bo'sh — foydalanuvchi tomonidan ro'yxat qayta yozilishining
        // oldini olish uchun. Playlistlar faqat BUILTIN_PLAYLISTS orqali beriladi.
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

