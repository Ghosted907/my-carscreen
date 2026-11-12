package com.ghosted907.t1launcher

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Language selection for T1's bilingual launcher controls. */
object JctLang {
    const val KEY = "app_language"
    const val SYSTEM = "system"
    const val AR = "ar"
    const val EN = "en"
    val choices = listOf(AR, EN, SYSTEM)

    /** Read directly from the prefs file so it also works inside attachBaseContext. */
    fun saved(context: Context): String =
        context.getSharedPreferences(JctStore.FILE, Context.MODE_PRIVATE).getString(KEY, AR) ?: AR

    /** Resolve the system option to the current device language. */
    fun language(context: Context): String {
        val s = saved(context)
        return if (s == SYSTEM) Resources.getSystem().configuration.locales[0].language else s
    }

    /** Only Arabic and English text exist; any other system language falls back to English. */
    fun english(context: Context): Boolean = language(context) != AR

    /** Apply the choice to Android resources before the activity is created. */
    fun wrap(base: Context): Context {
        val locale = Locale(if (english(base)) EN else AR)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Save the choice and redraw the activity. */
    fun set(activity: Activity, value: String) {
        activity.getSharedPreferences(JctStore.FILE, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
        activity.recreate()
    }

    fun label(value: String, english: Boolean): String = when (value) {
        AR -> "العربية"
        EN -> "English"
        else -> if (english) "As in the system" else "كما في النظام"
    }
}

/** Pick the Arabic or English text for the current app language. */
fun tr(context: Context, ar: String, en: String): String = if (JctLang.english(context)) en else ar

@Composable
fun tr(ar: String, en: String): String = tr(LocalContext.current, ar, en)
