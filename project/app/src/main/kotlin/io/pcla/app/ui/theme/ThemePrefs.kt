package io.pcla.app.ui.theme

import android.content.Context

/** Persisted choice between the lime-dark skin and PCL's light-blue skin. */
object ThemePrefs {
    private const val FILE = "pcla-prefs"
    private const val KEY_LIGHT = "pcl_light"

    fun isLight(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_LIGHT, false)

    fun setLight(context: Context, light: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_LIGHT, light).apply()
    }
}
