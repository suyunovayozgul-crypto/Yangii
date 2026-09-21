package uz.oktv.iptv.utils

import uz.oktv.iptv.model.AppLang

object Strings {
    private val translations = mapOf(
        "digital_tv"        to mapOf(AppLang.RU to "Цифровое ТВ",              AppLang.UZ to "Raqamli TV",               AppLang.EN to "Digital TV",          AppLang.UK to "Цифрове ТБ",             AppLang.TR to "Dijital TV"),
        "vod"               to mapOf(AppLang.RU to "Медиатека (Фильмы)",       AppLang.UZ to "Mediateka (Filmlar)",      AppLang.EN to "VOD / Cinema",        AppLang.UK to "Медіатека (Фільми)",     AppLang.TR to "Sinema / VOD"),
        "favorites"         to mapOf(AppLang.RU to "Избранное",                AppLang.UZ to "Saralanganlar",            AppLang.EN to "Favorites",           AppLang.UK to "Вибране",                AppLang.TR to "Favoriler"),
        "categories"        to mapOf(AppLang.RU to "Категории",                AppLang.UZ to "Kategoriyalar",            AppLang.EN to "Categories",          AppLang.UK to "Категорії",              AppLang.TR to "Kategoriler"),
        "servers"           to mapOf(AppLang.RU to "Выбор сервера",            AppLang.UZ to "Serverni tanlash",         AppLang.EN to "Server Selection",    AppLang.UK to "Вибір сервера",          AppLang.TR to "Sunucu Seçimi"),
        "playback_settings" to mapOf(AppLang.RU to "Настройки плеера",         AppLang.UZ to "Pleyer sozlamalari",       AppLang.EN to "Playback Settings",   AppLang.UK to "Налаштування плеєра",    AppLang.TR to "Oynatıcı Ayarları"),
        "language"          to mapOf(AppLang.RU to "Язык интерфейса",          AppLang.UZ to "Interfeys tili",           AppLang.EN to "Language",            AppLang.UK to "Мова інтерфейсу",        AppLang.TR to "Dil Seçimi"),
        "speedtest"         to mapOf(AppLang.RU to "Проверка скорости",        AppLang.UZ to "Tezlikni tekshirish",      AppLang.EN to "Speed Test",          AppLang.UK to "Перевірка швидкості",    AppLang.TR to "Hız Testi"),
        "support"           to mapOf(AppLang.RU to "Поддержка",                AppLang.UZ to "Qo'llab-quvvatlash",      AppLang.EN to "Support",             AppLang.UK to "Підтримка",              AppLang.TR to "Destek"),
        "logout"            to mapOf(AppLang.RU to "Сменить плейлист",         AppLang.UZ to "Playlistni almashtirish", AppLang.EN to "Change Playlist",     AppLang.UK to "Змінити плейлист",       AppLang.TR to "Yayın Listesini Değiştir"),
        "slow_codec_warning" to mapOf(AppLang.RU to "Этот канал использует устаревший формат", AppLang.UZ to "Bu kanal eski formatda uzatilmoqda", AppLang.EN to "This channel uses an old broadcast format", AppLang.UK to "Цей канал використовує застарілий формат", AppLang.TR to "Bu kanal eski bir yayın formatı kullanıyor"),
        "update_epg"        to mapOf(AppLang.RU to "Обновить телепрограмму (EPG)", AppLang.UZ to "Teledasturni yangilash (EPG)", AppLang.EN to "Update EPG Schedule", AppLang.UK to "Оновити телепрограму (EPG)", AppLang.TR to "Yayın Akışını Güncelle"),
        "menu"              to mapOf(AppLang.RU to "МЕНЮ",                     AppLang.UZ to "MENYU",                   AppLang.EN to "MENU",                AppLang.UK to "МЕНЮ",                   AppLang.TR to "MENÜ"),
        "all"               to mapOf(AppLang.RU to "Все",                      AppLang.UZ to "Barchasi",                AppLang.EN to "All",                 AppLang.UK to "Всі",                    AppLang.TR to "Tümü"),
        "live"              to mapOf(AppLang.RU to "Прямой эфир",              AppLang.UZ to "Jonli efir",              AppLang.EN to "Live Stream",         AppLang.UK to "Прямий ефір",            AppLang.TR to "Canlı Yayın"),
        "epg_schedule"      to mapOf(AppLang.RU to "ТЕЛЕПРОГРАММА НА НЕДЕЛЮ", AppLang.UZ to "HAFTALIK TELEDASTUR",     AppLang.EN to "WEEKLY EPG SCHEDULE", AppLang.UK to "ТИЖНЕВА ТЕЛЕПРОГРАМА",   AppLang.TR to "HAFTALIK YAYIN AKIŞI"),
        "select_category"   to mapOf(AppLang.RU to "ВЫБОР КАТЕГОРИИ",         AppLang.UZ to "KATEGORIYA TANLASH",      AppLang.EN to "SELECT CATEGORY",     AppLang.UK to "ВИБІР КАТЕГОРІЇ",        AppLang.TR to "KATEGORİ SEÇİN"),
        "speed_test_title"  to mapOf(AppLang.RU to "Тест скорости сети Mirovoy TV", AppLang.UZ to "Mirovoy TV tarmoq tezligi testi", AppLang.EN to "Mirovoy TV Speed Test", AppLang.UK to "Тест швидкості мережі Mirovoy TV", AppLang.TR to "Mirovoy TV Hız Testi"),
        "ping"              to mapOf(AppLang.RU to "Пинг",                     AppLang.UZ to "Ping",                    AppLang.EN to "Ping",                AppLang.UK to "Пінг",                   AppLang.TR to "Ping"),
        "speed"             to mapOf(AppLang.RU to "Скорость",                 AppLang.UZ to "Tezlik",                  AppLang.EN to "Speed",               AppLang.UK to "Швидкість",              AppLang.TR to "Hız"),
        "start_test"        to mapOf(AppLang.RU to "НАЧАТЬ ТЕСТ",              AppLang.UZ to "TESTNI BOSHLASH",         AppLang.EN to "START TEST",          AppLang.UK to "ПОЧАТИ ТЕСТ",            AppLang.TR to "TESTİ BAŞLAT"),
        "audio_tracks"      to mapOf(AppLang.RU to "ЗВУКОВЫЕ ДОРОЖКИ (АУДИО)", AppLang.UZ to "OVOZ YO'LLARI (AUDIO)",  AppLang.EN to "AUDIO TRACKS",        AppLang.UK to "ЗВУКОВІ ДОРІЖКИ (АУДІО)", AppLang.TR to "SES PARÇALARI"),
        "global_search"     to mapOf(AppLang.RU to "Глобальный поиск",         AppLang.UZ to "Umumiy qidiruv",          AppLang.EN to "Global Search",       AppLang.UK to "Глобальний пошук",       AppLang.TR to "Genel Arama"),
        "epg_url_settings"  to mapOf(AppLang.RU to "EPG URL sozlamalari",      AppLang.UZ to "EPG URL sozlamalari",     AppLang.EN to "EPG URL Settings",    AppLang.UK to "Налаштування EPG URL",   AppLang.TR to "EPG URL Ayarları"),
        "football_schedule" to mapOf(AppLang.RU to "Futbol jadvali",           AppLang.UZ to "Futbol jadvali",          AppLang.EN to "Football Schedule",   AppLang.UK to "Розклад футболу",        AppLang.TR to "Futbol Programı"),

        // HomeScreen
        "home_live"         to mapOf(AppLang.RU to "Jonli Efir",               AppLang.UZ to "Jonli Efir",              AppLang.EN to "Live TV",             AppLang.UK to "Живий ефір",             AppLang.TR to "Canlı TV"),
        "home_movies"       to mapOf(AppLang.RU to "Kinolar",                  AppLang.UZ to "Kinolar",                 AppLang.EN to "Movies",              AppLang.UK to "Фільми",                 AppLang.TR to "Filmler"),
        "home_sport"        to mapOf(AppLang.RU to "Sport",                    AppLang.UZ to "Sport",                   AppLang.EN to "Sport",               AppLang.UK to "Спорт",                  AppLang.TR to "Spor"),
        "home_series"       to mapOf(AppLang.RU to "Seriallar",                AppLang.UZ to "Seriallar",               AppLang.EN to "Series",              AppLang.UK to "Серіали",                AppLang.TR to "Diziler"),
        "home_playlist"     to mapOf(AppLang.RU to "Playlist",                 AppLang.UZ to "Playlist",                AppLang.EN to "Playlist",            AppLang.UK to "Плейліст",               AppLang.TR to "Çalma Listesi"),
        "home_settings"     to mapOf(AppLang.RU to "Настройки",                AppLang.UZ to "Sozlamalar",              AppLang.EN to "Settings",            AppLang.UK to "Налаштування",           AppLang.TR to "Ayarlar"),
        "home_refresh"      to mapOf(AppLang.RU to "Обновить",                 AppLang.UZ to "Yangilash",               AppLang.EN to "Refresh",             AppLang.UK to "Оновити",                AppLang.TR to "Yenile"),
        "home_exit"         to mapOf(AppLang.RU to "Выход",                    AppLang.UZ to "Chiqish",                 AppLang.EN to "Exit",                AppLang.UK to "Вийти",                  AppLang.TR to "Çıkış"),
        "home_channels"     to mapOf(AppLang.RU to "каналов",                  AppLang.UZ to "kanal",                   AppLang.EN to "channels",            AppLang.UK to "каналів",                AppLang.TR to "kanal"),
        "home_now_sport"    to mapOf(AppLang.RU to "⚽ Сейчас в эфире — Спорт", AppLang.UZ to "⚽ Hozir efirda — Sport", AppLang.EN to "⚽ Live Now — Sport",  AppLang.UK to "⚽ Зараз в ефірі — Спорт", AppLang.TR to "⚽ Şimdi Yayında — Spor"),
        "home_now_movies"   to mapOf(AppLang.RU to "🎬 Сейчас в эфире — Кино", AppLang.UZ to "🎬 Hozir efirda — Kino", AppLang.EN to "🎬 Live Now — Movies", AppLang.UK to "🎬 Зараз в ефірі — Кіно", AppLang.TR to "🎬 Şimdi Yayında — Film"),
        "home_live_badge"   to mapOf(AppLang.RU to "🔴 ЭФИР",                  AppLang.UZ to "🔴 JONLI",                AppLang.EN to "🔴 LIVE",             AppLang.UK to "🔴 ЕФІР",                AppLang.TR to "🔴 CANLI"),
        "home_iptv_player"  to mapOf(AppLang.RU to "IPTV PLAYER",              AppLang.UZ to "IPTV PLAYER",             AppLang.EN to "IPTV PLAYER",         AppLang.UK to "IPTV ПЛЕЄР",             AppLang.TR to "IPTV OYNATICI"),

        // Football
        "football_title"    to mapOf(AppLang.RU to "⚽ Футбол",                  AppLang.UZ to "⚽ Futbol",                AppLang.EN to "⚽ Football",          AppLang.UK to "⚽ Футбол",               AppLang.TR to "⚽ Futbol"),
        "fb_standings"      to mapOf(AppLang.RU to "📊 Турнирная таблица",       AppLang.UZ to "📊 Turnir jadvali",       AppLang.EN to "📊 Standings",         AppLang.UK to "📊 Турнірна таблиця",    AppLang.TR to "📊 Puan Tablosu"),
        "fb_scorers"        to mapOf(AppLang.RU to "⚽ Бомбардиры",              AppLang.UZ to "⚽ Gol urganlar",         AppLang.EN to "⚽ Top Scorers",        AppLang.UK to "⚽ Бомбардири",           AppLang.TR to "⚽ Gol Krallığı"),
        "fb_today"          to mapOf(AppLang.RU to "📅 Сегодня",                 AppLang.UZ to "📅 Bugun",                AppLang.EN to "📅 Today",             AppLang.UK to "📅 Сьогодні",             AppLang.TR to "📅 Bugün"),
        "fb_upcoming"       to mapOf(AppLang.RU to "📆 Ближайшие матчи",         AppLang.UZ to "📆 Keyingi o'yinlar",    AppLang.EN to "📆 Upcoming Matches",  AppLang.UK to "📆 Найближчі матчі",     AppLang.TR to "📆 Yaklaşan Maçlar"),
        "fb_no_data"        to mapOf(AppLang.RU to "Данные не найдены",           AppLang.UZ to "Ma'lumot topilmadi",      AppLang.EN to "No data found",        AppLang.UK to "Дані не знайдено",        AppLang.TR to "Veri bulunamadı"),
        "fb_no_today"       to mapOf(AppLang.RU to "Сегодня матчей нет",          AppLang.UZ to "Bugun o'yin yo'q",        AppLang.EN to "No matches today",     AppLang.UK to "Сьогодні матчів немає",   AppLang.TR to "Bugün maç yok"),
        "fb_no_upcoming"    to mapOf(AppLang.RU to "Ближайших матчей нет",        AppLang.UZ to "Keyingi o'yinlar yo'q",  AppLang.EN to "No upcoming matches",  AppLang.UK to "Найближчих матчів немає", AppLang.TR to "Yaklaşan maç yok"),
        "fb_channel"        to mapOf(AppLang.RU to "📺 Смотреть на",             AppLang.UZ to "📺 Tomosha qilish",      AppLang.EN to "📺 Watch on",          AppLang.UK to "📺 Дивитись на",          AppLang.TR to "📺 İzle"),
        "fb_no_channel"     to mapOf(AppLang.RU to "Канал не найден",             AppLang.UZ to "Kanal topilmadi",         AppLang.EN to "Channel not found",    AppLang.UK to "Канал не знайдено",       AppLang.TR to "Kanal bulunamadı"),
        "fb_team"           to mapOf(AppLang.RU to "Команда",                     AppLang.UZ to "Jamoa",                   AppLang.EN to "Team",                 AppLang.UK to "Команда",                 AppLang.TR to "Takım"),
        "fb_goals"          to mapOf(AppLang.RU to "Голы",                        AppLang.UZ to "Gollar",                  AppLang.EN to "Goals",                AppLang.UK to "Голи",                    AppLang.TR to "Goller"),
        "fb_played"         to mapOf(AppLang.RU to "О",                           AppLang.UZ to "O",                       AppLang.EN to "P",                    AppLang.UK to "І",                       AppLang.TR to "O"),
        "fb_diff"           to mapOf(AppLang.RU to "Раз",                         AppLang.UZ to "Far",                     AppLang.EN to "GD",                   AppLang.UK to "Різн",                    AppLang.TR to "Av"),
        "fb_points"         to mapOf(AppLang.RU to "О.",                          AppLang.UZ to "O'.",                     AppLang.EN to "Pts",                  AppLang.UK to "О.",                      AppLang.TR to "Puan"),

        // Kategoriya
        "cat_all"           to mapOf(AppLang.RU to "Все",                         AppLang.UZ to "Barchasi",                AppLang.EN to "All",                  AppLang.UK to "Всі",                     AppLang.TR to "Tümü"),
        "cat_favorites"     to mapOf(AppLang.RU to "❤️ Избранное",               AppLang.UZ to "❤️ Saralanganlar",       AppLang.EN to "❤️ Favorites",         AppLang.UK to "❤️ Вибране",             AppLang.TR to "❤️ Favoriler"),

        // HomeScreen sport
        "home_next_sport"   to mapOf(AppLang.RU to "📆 Ближайшие матчи (EPG)",   AppLang.UZ to "📆 Keyingi o'yinlar (EPG)", AppLang.EN to "📆 Upcoming Matches (EPG)", AppLang.UK to "📆 Найближчі матчі (EPG)", AppLang.TR to "📆 Yaklaşan Maçlar (EPG)"),
        "football_standings" to mapOf(AppLang.RU to "Турнирная таблица", AppLang.UZ to "Turnirnoma",       AppLang.EN to "Standings",    AppLang.UK to "Таблиця",          AppLang.TR to "Puan Tablosu"),
        "football_scorers"   to mapOf(AppLang.RU to "Бомбардиры",        AppLang.UZ to "Gol uruvchilar",   AppLang.EN to "Top Scorers",  AppLang.UK to "Бомбардири",       AppLang.TR to "Gol Krallığı"),
        "football_today"     to mapOf(AppLang.RU to "Сегодня",            AppLang.UZ to "Bugun",            AppLang.EN to "Today",        AppLang.UK to "Сьогодні",         AppLang.TR to "Bugün"),
        "football_upcoming"  to mapOf(AppLang.RU to "Ближайшие (7 дней)", AppLang.UZ to "Keyingi (7 kun)", AppLang.EN to "Upcoming (7d)", AppLang.UK to "Найближчі (7 днів)", AppLang.TR to "Yaklaşan (7 gün)"),
        "football_no_games"  to mapOf(AppLang.RU to "O'yinlar topilmadi", AppLang.UZ to "O'yinlar topilmadi", AppLang.EN to "No games found", AppLang.UK to "Матчів не знайдено", AppLang.TR to "Maç bulunamadı")
    )

    fun get(key: String, lang: AppLang): String {
        return translations[key]?.get(lang) ?: translations[key]?.get(AppLang.RU) ?: key
    }
}
