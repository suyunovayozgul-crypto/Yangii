package com.signal.iptv

data class Channel(
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "",
    val tvgId: String = "",
    // Ba'zi kanallar faqat maxsus User-Agent/Referer bilan ochiladi (playlist ichida
    // #EXTVLCOPT yoki #EXTHTTP orqali berilgan bo'ladi). Bo'sh bo'lsa, standart brauzer
    // User-Agent ishlatiladi. Aynan shu narsa "boshqa dasturda ochadi, bunda ochmaydi"
    // holatlarining eng keng tarqalgan sababi.
    val userAgent: String = "",
    val referer: String = ""
)
