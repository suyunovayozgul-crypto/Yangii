package com.signal.iptv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Til sozlamasi. AppCompatDelegate.setApplicationLocales orqali butun ilova
 * (barcha ekranlar) tanlangan tilga o'zgaradi va ilova qayta ochilganda ham
 * saqlanib qoladi (AndroidManifest'dagi autoStoreLocales tufayli) — alohida
 * SharedPreferences yozish shart emas.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var langUz: TextView
    private lateinit var langRu: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        langUz = findViewById(R.id.settingsLangUz)
        langRu = findViewById(R.id.settingsLangRu)

        findViewById<ImageButton>(R.id.settingsBackBtn).setOnClickListener { finish() }

        langUz.setOnClickListener { applyLocale("uz") }
        langRu.setOnClickListener { applyLocale("ru") }

        // Reklamasiz/qo'shimcha kanallar so'ragan foydalanuvchilar to'g'ridan-to'g'ri
        // Telegram kanaliga o'tishi uchun — alohida qidirib topish shart emas.
        findViewById<TextView>(R.id.settingsTelegram).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.TELEGRAM_URL)))
        }

        refreshSelection()
    }

    private fun applyLocale(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        // setApplicationLocales activity'larni o'zi qayta yaratadi (recreate),
        // shu joyda qo'shimcha ishlov berish shart emas.
    }

    private fun refreshSelection() {
        val current = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (current.isEmpty) "uz" else current[0]?.language ?: "uz"
        langUz.isSelected = currentTag.startsWith("uz")
        langRu.isSelected = currentTag.startsWith("ru")
    }
}
