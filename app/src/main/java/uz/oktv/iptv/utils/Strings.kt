package uz.oktv.iptv.utils

import uz.oktv.iptv.model.AppLang

object Strings {
    private val translations = mapOf(
        "digital_tv" to mapOf(AppLang.RU to "Цифровое ТВ", AppLang.UZ to "Raqamli TV", AppLang.EN to "Digital TV", AppLang.UK to "Цифрове ТБ", AppLang.TR to "Dijital TV"),
        "vod" to mapOf(AppLang.RU to "Медиатека (Фильмы)", AppLang.UZ to "Mediateka (Filmlar)", AppLang.EN to "VOD / Cinema", AppLang.UK to "Медіатека (Фільми)", AppLang.TR to "Sinema / VOD"),
        "favorites" to mapOf(AppLang.RU to "Избранное", AppLang.UZ to "Saralanganlar", AppLang.EN to "Favorites", AppLang.UK to "Вибране", AppLang.TR to "Favoriler"),
        "categories" to mapOf(AppLang.RU to "Категории", AppLang.UZ to "Kategoriyalar", AppLang.EN to "Categories", AppLang.UK to "Категорії", AppLang.TR to "Kategoriler"),
        "servers" to mapOf(AppLang.RU to "Выбор сервера", AppLang.UZ to "Serverni tanlash", AppLang.EN to "Server Selection", AppLang.UK to "Вибір сервера", AppLang.TR to "Sunucu Seçimi"),
        "playback_settings" to mapOf(AppLang.RU to "Настройки плеера", AppLang.UZ to "Pleyer sozlamalari", AppLang.EN to "Playback Settings", AppLang.UK to "Налаштування плеєра", AppLang.TR to "Oynatıcı Ayarları"),
        "language" to mapOf(AppLang.RU to "Язык интерфейса", AppLang.UZ to "Interfeys tili", AppLang.EN to "Language", AppLang.UK to "Мова інтерфейсу", AppLang.TR to "Dil Seçimi"),
        "speedtest" to mapOf(AppLang.RU to "Проверка скорости", AppLang.UZ to "Tezlikni tekshirish", AppLang.EN to "Speed Test", AppLang.UK to "Перевірка швидкості", AppLang.TR to "Hız Testi"),
        "support" to mapOf(AppLang.RU to "Поддержка", AppLang.UZ to "Qo'llab-quvvatlash", AppLang.EN to "Support", AppLang.UK to "Підтримка", AppLang.TR to "Destek"),
        "logout" to mapOf(AppLang.RU to "Сменить плейлист", AppLang.UZ to "Playlistni almashtirish", AppLang.EN to "Change Playlist", AppLang.UK to "Змінити плейлист", AppLang.TR to "Yayın Listesini Değiştir"),
        "slow_codec_warning" to mapOf(AppLang.RU to "Этот канал использует устаревший формат вещания — на некоторых ТВ видео может идти медленнее обычного", AppLang.UZ to "Bu kanal eski formatda uzatilmoqda — ba'zi televizorlarda video sekinroq ishlashi mumkin", AppLang.EN to "This channel uses an old broadcast format — video may run slower on some TVs", AppLang.UK to "Цей канал використовує застарілий формат — на деяких ТБ відео може йти повільніше", AppLang.TR to "Bu kanal eski bir yayın formatı kullanıyor — bazı TV'lerde görüntü daha yavaş akabilir"),
        "update_epg" to mapOf(AppLang.RU to "Обновить телепрограмму (EPG)", AppLang.UZ to "Teledasturni yangilash (EPG)", AppLang.EN to "Update EPG Schedule", AppLang.UK to "Оновити телепрограму (EPG)", AppLang.TR to "Yayın Akışını Güncelle"),
        "menu" to mapOf(AppLang.RU to "МЕНЮ", AppLang.UZ to "MENYU", AppLang.EN to "MENU", AppLang.UK to "МЕНЮ", AppLang.TR to "MENÜ"),
        "all" to mapOf(AppLang.RU to "Все", AppLang.UZ to "Barchasi", AppLang.EN to "All", AppLang.UK to "Всі", AppLang.TR to "Tümü"),
        "live" to mapOf(AppLang.RU to "Прямой эфир", AppLang.UZ to "Jonli efir", AppLang.EN to "Live Stream", AppLang.UK to "Прямий ефір", AppLang.TR to "Canlı Yayın"),
        "epg_schedule" to mapOf(AppLang.RU to "ТЕЛЕПРОГРАММА НА НЕДЕЛЮ", AppLang.UZ to "HAFTALIK TELEDASTUR", AppLang.EN to "WEEKLY EPG SCHEDULE", AppLang.UK to "ТИЖНЕВА ТЕЛЕПРОГРАМА", AppLang.TR to "HAFTALIK YAYIN AKIŞI"),
        "select_category" to mapOf(AppLang.RU to "ВЫБОР КАТЕГОРИИ", AppLang.UZ to "KATEGORIYA TANLASH", AppLang.EN to "SELECT CATEGORY", AppLang.UK to "ВИБІР КАТЕГОРІЇ", AppLang.TR to "KATEGORİ SEÇİN"),
        "speed_test_title" to mapOf(AppLang.RU to "Тест скорости сети Mirovoy TV", AppLang.UZ to "Mirovoy TV tarmoq tezligi testi", AppLang.EN to "Mirovoy TV Speed Test", AppLang.UK to "Тест швидкості мережі Mirovoy TV", AppLang.TR to "Mirovoy TV Hız Testi"),
        "ping" to mapOf(AppLang.RU to "Пинг", AppLang.UZ to "Ping", AppLang.EN to "Ping", AppLang.UK to "Пінг", AppLang.TR to "Ping"),
        "speed" to mapOf(AppLang.RU to "Скорость", AppLang.UZ to "Tezlik", AppLang.EN to "Speed", AppLang.UK to "Швидкість", AppLang.TR to "Hız"),
        "start_test" to mapOf(AppLang.RU to "НАЧАТЬ ТЕСТ", AppLang.UZ to "TESTNI BOSHLASH", AppLang.EN to "START TEST", AppLang.UK to "ПОЧАТИ ТЕСТ", AppLang.TR to "TESTİ BAŞLAT"),
        "audio_tracks" to mapOf(AppLang.RU to "ЗВУКОВЫЕ ДОРОЖКИ (АУДИО)", AppLang.UZ to "OVOZ YO'LLARI (AUDIO)", AppLang.EN to "AUDIO TRACKS", AppLang.UK to "ЗВУКОВі ДОРОЖКИ (АУДІО)", AppLang.TR to "SES PARÇALARI"),
        "home_subscription" to mapOf(AppLang.RU to "ПОДПИСКА Mirovoy TV", AppLang.UZ to "OBUNA Mirovoy TV", AppLang.EN to "SUBSCRIPTION Mirovoy TV", AppLang.UK to "ПІДПИСКА Mirovoy TV", AppLang.TR to "ABONELİK Mirovoy TV"),
        "home_tariff" to mapOf(AppLang.RU to "ТАРИФ", AppLang.UZ to "TARIF", AppLang.EN to "PLAN", AppLang.UK to "ТАРИФ", AppLang.TR to "TARİFE"),
        "home_server" to mapOf(AppLang.RU to "СЕРВЕР", AppLang.UZ to "SERVER", AppLang.EN to "SERVER", AppLang.UK to "СЕРВЕР", AppLang.TR to "SUNUCU"),
        "home_expires" to mapOf(AppLang.RU to "ИСТЕКАЕТ", AppLang.UZ to "TUGAYDI", AppLang.EN to "EXPIRES", AppLang.UK to "ЗАКІНЧУЄТЬСЯ", AppLang.TR to "BİTİŞ"),
        "home_screens" to mapOf(AppLang.RU to "ЭКРАНЫ", AppLang.UZ to "EKRANLAR", AppLang.EN to "SCREENS", AppLang.UK to "ЕКРАНИ", AppLang.TR to "EKRANLAR"),
        "home_live" to mapOf(AppLang.RU to "Jonli Efir", AppLang.UZ to "Jonli Efir", AppLang.EN to "Live TV", AppLang.UK to "Живий ефір", AppLang.TR to "Canlı TV"),
        "home_movies" to mapOf(AppLang.RU to "Kinolar", AppLang.UZ to "Kinolar", AppLang.EN to "Movies", AppLang.UK to "Фільми", AppLang.TR to "Filmler"),
        "home_sport" to mapOf(AppLang.RU to "Sport", AppLang.UZ to "Sport", AppLang.EN to "Sport", AppLang.UK to "Спорт", AppLang.TR to "Spor"),
        "home_series" to mapOf(AppLang.RU to "Seriallar", AppLang.UZ to "Seriallar", AppLang.EN to "Series", AppLang.UK to "Серіали", AppLang.TR to "Diziler"),
        "home_playlist" to mapOf(AppLang.RU to "Playlist", AppLang.UZ to "Playlist", AppLang.EN to "Playlist", AppLang.UK to "Плейліст", AppLang.TR to "Çalma Listesi"),
        "home_settings" to mapOf(AppLang.RU to "Sozlamalar", AppLang.UZ to "Sozlamalar", AppLang.EN to "Settings", AppLang.UK to "Налаштування", AppLang.TR to "Ayarlar"),
        "home_refresh" to mapOf(AppLang.RU to "Yangilash", AppLang.UZ to "Yangilash", AppLang.EN to "Refresh", AppLang.UK to "Оновити", AppLang.TR to "Yenile"),
        "home_exit" to mapOf(AppLang.RU to "Chiqish", AppLang.UZ to "Chiqish", AppLang.EN to "Exit", AppLang.UK to "Вийти", AppLang.TR to "Çıkış"),
        "home_now_sport" to mapOf(AppLang.RU to "⚽ Сейчас в эфире — Спорт", AppLang.UZ to "⚽ Hozir efirda — Sport", AppLang.EN to "⚽ Live Now — Sport", AppLang.UK to "⚽ Зараз в ефірі — Спорт", AppLang.TR to "⚽ Şimdi Yayında — Spor"),
        "home_now_movies" to mapOf(AppLang.RU to "🎬 Сейчас в эфире — Кино", AppLang.UZ to "🎬 Hozir efirda — Kino", AppLang.EN to "🎬 Live Now — Movies", AppLang.UK to "🎬 Зараз в ефірі — Кіно", AppLang.TR to "🎬 Şimdi Yayında — Film"),
        "home_channels" to mapOf(AppLang.RU to "каналов", AppLang.UZ to "kanal", AppLang.EN to "channels", AppLang.UK to "каналів", AppLang.TR to "kanal"),
        "home_live_badge" to mapOf(AppLang.RU to "🔴 ЭФИР", AppLang.UZ to "🔴 JONLI", AppLang.EN to "🔴 LIVE", AppLang.UK to "🔴 ЕФІР", AppLang.TR to "🔴 CANLI")
    )

    fun get(key: String, lang: AppLang): String {
        return translations[key]?.get(lang) ?: translations[key]?.get(AppLang.RU) ?: key
    }
}
