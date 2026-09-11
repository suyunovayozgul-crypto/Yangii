package uz.oktv.iptv.model

const val APP_VERSION_NAME = "v1.29 PRO"

data class M3UChannel(
    val id: Int,
    val tvgId: String,
    val name: String,
    val group: String,
    val logo: String,
    val url: String
)

data class EpgProgram(
    val startTimeMs: Long,
    val stopTimeMs: Long,
    val startTimeStr: String,
    val stopTimeStr: String,
    val dateStr: String,
    val title: String,
    val desc: String
)

data class KinopoiskData(
    val rating: String? = null,
    val posterUrl: String? = null,
    val year: String? = null,
    val description: String? = null,
    val genres: String? = null
)

data class ServerNode(
    val id: String,
    val name: String,
    val host: String
)

val OKTV_SERVERS = listOf(
    ServerNode("auto", "AUTO (Оптимальный)", "s1.oktv.uz"),
    ServerNode("s1", "Сервер 1 (Ташкент)", "s1.oktv.uz"),
    ServerNode("s2", "Сервер 2 (Ташкент Резерв)", "s2.oktv.uz"),
    ServerNode("de", "Сервер 3 (Германия / EU)", "de.oktv.uz")
)

enum class PlayerEngine { EXO2_MOD, EXO2, STANDART }
enum class AppScreenState { CHECKING, AUTH, LOADING_SYSTEM, MAIN_PLAYER }
enum class AppContentMode { TV, VOD }

enum class AppLang(val code: String, val title: String) {
    RU("ru", "Русский"),
    UZ("uz", "O'zbekcha"),
    EN("en", "English"),
    UK("uk", "Українська"),
    TR("tr", "Türkçe")
}
