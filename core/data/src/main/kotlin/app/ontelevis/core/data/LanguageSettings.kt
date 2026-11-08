package app.ontelevis.core.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App language chosen in Settings: "" follows the system, else a language tag ("en", "fr").
 * Android 13+ applies it through the system per-app language (also visible in system settings).
 * Older devices (Fire OS 6 / 7) apply it in [wrap], from `attachBaseContext`, then recreate.
 */
@Singleton
class LanguageSettings
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val supported: List<String> = listOf("en", "fr")

        fun current(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context
                    .getSystemService(LocaleManager::class.java)
                    .applicationLocales
                    .takeIf { !it.isEmpty }
                    ?.get(0)
                    ?.language
                    .orEmpty()
            } else {
                prefs(context).getString(KEY, "").orEmpty()
            }

        /** Returns true when the caller must recreate the activity itself (before Android 13). */
        fun set(tag: String): Boolean {
            prefs(context).edit { putString(KEY, tag) }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                false
            } else {
                true
            }
        }

        companion object {
            private const val KEY = "app_language"

            private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

            /** For `attachBaseContext` before Android 13: a context in the chosen language. */
            fun wrap(base: Context): Context {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
                val tag = prefs(base).getString(KEY, "").orEmpty()
                if (tag.isEmpty()) return base
                val locale = Locale.forLanguageTag(tag)
                Locale.setDefault(locale)
                val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
                return base.createConfigurationContext(config)
            }
        }
    }
