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
        "logout" to mapOf(AppLang.RU to "Сброс токена / Выход", AppLang.UZ to "Tokenni o'chirish / Chiqish", AppLang.EN to "Reset Token / Logout", AppLang.UK to "Скидання токена / Вихід", AppLang.TR to "Token Sıfırla / Çıkış"),
        "update_epg" to mapOf(AppLang.RU to "Обновить телепрограмму (EPG)", AppLang.UZ to "Teledasturni yangilash (EPG)", AppLang.EN to "Update EPG Schedule", AppLang.UK to "Оновити телепрограму (EPG)", AppLang.TR to "Yayın Akışını Güncelle"),
        "menu" to mapOf(AppLang.RU to "МЕНЮ", AppLang.UZ to "MENYU", AppLang.EN to "MENU", AppLang.UK to "МЕНЮ", AppLang.TR to "MENÜ"),
        "all" to mapOf(AppLang.RU to "Все", AppLang.UZ to "Barchasi", AppLang.EN to "All", AppLang.UK to "Всі", AppLang.TR to "Tümü"),
        "live" to mapOf(AppLang.RU to "Прямой эфир", AppLang.UZ to "Jonli efir", AppLang.EN to "Live Stream", AppLang.UK to "Прямий ефір", AppLang.TR to "Canlı Yayın"),
        "epg_schedule" to mapOf(AppLang.RU to "ТЕЛЕПРОГРАММА НА НЕДЕЛЮ", AppLang.UZ to "HAFTALIK TELEDASTUR", AppLang.EN to "WEEKLY EPG SCHEDULE", AppLang.UK to "ТИЖНЕВА ТЕЛЕПРОГРАМА", AppLang.TR to "HAFTALIK YAYIN AKIŞI"),
        "select_category" to mapOf(AppLang.RU to "ВЫБОР КАТЕГОРИИ", AppLang.UZ to "KATEGORIYA TANLASH", AppLang.EN to "SELECT CATEGORY", AppLang.UK to "ВИБІР КАТЕГОРІЇ", AppLang.TR to "KATEGORİ SEÇİN"),
        "speed_test_title" to mapOf(AppLang.RU to "Тест скорости сети OK TV", AppLang.UZ to "OK TV tarmoq tezligi testi", AppLang.EN to "OK TV Speed Test", AppLang.UK to "Тест швидкості мережі OK TV", AppLang.TR to "OK TV Hız Testi"),
        "ping" to mapOf(AppLang.RU to "Пинг", AppLang.UZ to "Ping", AppLang.EN to "Ping", AppLang.UK to "Пінг", AppLang.TR to "Ping"),
        "speed" to mapOf(AppLang.RU to "Скорость", AppLang.UZ to "Tezlik", AppLang.EN to "Speed", AppLang.UK to "Швидкість", AppLang.TR to "Hız"),
        "start_test" to mapOf(AppLang.RU to "НАЧАТЬ ТЕСТ", AppLang.UZ to "TESTNI BOSHLASH", AppLang.EN to "START TEST", AppLang.UK to "ПОЧАТИ ТЕСТ", AppLang.TR to "TESTİ BAŞLAT"),
        "audio_tracks" to mapOf(AppLang.RU to "ЗВУКОВЫЕ ДОРОЖКИ (АУДИО)", AppLang.UZ to "OVOZ YO'LLARI (AUDIO)", AppLang.EN to "AUDIO TRACKS", AppLang.UK to "ЗВУКОВі ДОРОЖКИ (АУДІО)", AppLang.TR to "SES PARÇALARI")
    )

    fun get(key: String, lang: AppLang): String {
        return translations[key]?.get(lang) ?: translations[key]?.get(AppLang.RU) ?: key
    }
}
