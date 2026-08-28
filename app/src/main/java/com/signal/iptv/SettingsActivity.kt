package com.signal.iptv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Til sozlamasi. AppCompatDelegate.setApplicationLocales orqali butun ilova
 * (barcha ekranlar) tanlangan tilga o'zgaradi va ilova qayta ochilganda ham
 * saqlanib qoladi (AndroidManifest'dagi autoStoreLocales tufayli) — alohida
 * SharedPreferences yozish shart emas.
 *
 * EPG havolasi: MUHIM TUZATISH — bu ekranda EPG maydoni bor edi, lekin
 * hech qanday kodga ulanmagan edi (na o'qir, na saqlar edi), shu sababli
 * foydalanuvchi bu yerga o'z EPG havolasini kiritsa ham hech narsa
 * o'zgarmasdi va ilova doim standart (chet el kanallariga mo'ljallangan)
 * EPG manbasidan foydalanaverardi. Endi bu maydon AppPrefs orqali haqiqatan
 * ham saqlanadi va MainActivity/PlayerActivity aynan shu qiymatni o'qiydi.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var langUz: TextView
    private lateinit var langRu: TextView
    private lateinit var epgUrlField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        langUz = findViewById(R.id.settingsLangUz)
        langRu = findViewById(R.id.settingsLangRu)
        epgUrlField = findViewById(R.id.settingsEpgUrl)

        findViewById<ImageButton>(R.id.settingsBackBtn).setOnClickListener { finish() }

        langUz.setOnClickListener { applyLocale("uz") }
        langRu.setOnClickListener { applyLocale("ru") }

        epgUrlField.setText(AppPrefs.epgUrl(this))

        findViewById<Button>(R.id.settingsEpgSave).setOnClickListener {
            val url = epgUrlField.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                Toast.makeText(this, R.string.settings_epg_empty, Toast.LENGTH_SHORT).show()
            } else {
                AppPrefs.setEpgUrl(this, url)
                Toast.makeText(this, R.string.settings_epg_saved, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.settingsEpgReset).setOnClickListener {
            AppPrefs.resetEpgUrl(this)
            epgUrlField.setText(AppPrefs.epgUrl(this))
            Toast.makeText(this, R.string.settings_epg_saved, Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.settingsTelegramRow).setOnClickListener {
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
