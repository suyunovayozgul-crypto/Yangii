package com.signal.iptv

data class Channel(
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "",
    val tvgId: String = "",
    // Ba'zi IPTV serverlari faqat o'ziga xos User-Agent yoki Referer bilan
    // kelgan so'rovlarni qabul qiladi (playlist shu ma'lumotni #EXTVLCOPT
    // qatorlarida yoki URL oxiridagi |User-Agent=...&Referer=... ko'rinishida
    // beradi). Bo'sh bo'lsa, umumiy standart brauzer sarlavhalari ishlatiladi.
    val userAgent: String = "",
    val referrer: String = ""
)
