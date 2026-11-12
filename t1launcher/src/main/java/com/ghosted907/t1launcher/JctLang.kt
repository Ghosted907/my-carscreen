package com.ghosted907.t1launcher

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Port of NaviStart `e0.b0` application language (setting "setLangApp" / "AppLangDefault").
 * Same key "app_language" and the same "system" value; NaviStart applies the choice by giving the
 * activity a Configuration with that Locale (b0.b, p2.e / j0.e attachBaseContext wrapping).
 * T1 is Arabic-first, so the default is "ar" instead of NaviStart's Locale.getDefault().
 */
object JctLang {
    const val KEY = "app_language"
    const val SYSTEM = "system"
    const val AR = "ar"
    const val EN = "en"
    val choices = listOf(AR, EN, SYSTEM)

    /** Read directly from the prefs file so it also works inside attachBaseContext. */
    fun saved(context: Context): String =
        context.getSharedPreferences(JctStore.FILE, Context.MODE_PRIVATE).getString(KEY, AR) ?: AR

    /** b0.a(): the effective language code. */
    fun language(context: Context): String {
        val s = saved(context)
        return if (s == SYSTEM) Resources.getSystem().configuration.locales[0].language else s
    }

    /** Only Arabic and English text exist; any other system language falls back to English. */
    fun english(context: Context): Boolean = language(context) != AR

    /** attachBaseContext wrapper: the activity's Configuration carries the chosen Locale. */
    fun wrap(base: Context): Context {
        val locale = Locale(if (english(base)) EN else AR)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** b0.b(): save and re-apply. */
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
