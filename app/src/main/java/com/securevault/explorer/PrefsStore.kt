package com.securevault.explorer

import android.content.Context
import android.content.SharedPreferences

/** Thin wrapper over SharedPreferences. Only non-secret settings (theme, view mode, timers …) live here. */
class PrefsStore(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("sv_prefs", Context.MODE_PRIVATE)

    fun str(key: String, def: String): String = sp.getString(key, def) ?: def
    fun bool(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)
    fun int(key: String, def: Int): Int = sp.getInt(key, def)
    fun long(key: String, def: Long): Long = sp.getLong(key, def)
    fun raw(key: String): String? = sp.getString(key, null)

    fun put(key: String, v: String) = sp.edit().putString(key, v).apply()
    fun put(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    fun put(key: String, v: Int) = sp.edit().putInt(key, v).apply()
    fun put(key: String, v: Long) = sp.edit().putLong(key, v).apply()
    fun remove(key: String) = sp.edit().remove(key).apply()
}
