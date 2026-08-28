package com.signal.iptv

import android.content.Context

/**
 * Foydalanuvchi sozlashi mumkin bo'lgan sozlamalar (hozircha faqat EPG havolasi).
 *
 * Muammo: avval EPG manzili kodga qattiq yozilgan edi (umumiy, uchinchi tomon
 * manbasi) — bu manbada ko'p mahalliy/o'zbek kanallar mavjud emas edi, shu
 * sababli "EPG bor lekin chiqmayapti" holati yuzaga kelgan (foydalanuvchi
 * Televizo'da BOSHQA, to'g'ri ishlaydigan EPG havolasini qo'ygan edi, lekin
 * bu ilovada uni o'zgartirish imkoni yo'q edi). Endi Sozlamalar ekranidan
 * xohlagan EPG (XMLTV, .xml yoki .xml.gz) havolasini qo'yish mumkin.
 */
object AppPrefs {

    const val DEFAULT_EPG_URL = "https://iptvx.one/epg/epg_lite.xml.gz"

    private const val PREFS_NAME = "mirovoy_prefs"
    private const val KEY_EPG_URL = "epg_url"

    fun epgUrl(context: Context): String {
        val saved = prefs(context).getString(KEY_EPG_URL, null)
        return if (saved.isNullOrBlank()) DEFAULT_EPG_URL else saved.trim()
    }

    fun setEpgUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_EPG_URL, url.trim()).apply()
    }

    fun resetEpgUrl(context: Context) {
        prefs(context).edit().remove(KEY_EPG_URL).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
