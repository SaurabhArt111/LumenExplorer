package com.lumen.explorer

import android.content.Context
import android.content.SharedPreferences

/** Thin typed wrapper over SharedPreferences. */
object PrefsStore {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE) }
    fun has(k: String) = sp.contains(k)
    fun str(k: String, d: String) = sp.getString(k, d) ?: d
    fun int(k: String, d: Int) = sp.getInt(k, d)
    fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    fun float(k: String, d: Float) = sp.getFloat(k, d)
    fun long(k: String, d: Long) = sp.getLong(k, d)
    fun putAll(vararg kv: Pair<String, Any>) {
        val e = sp.edit()
        for ((k, v) in kv) when (v) {
            is String -> e.putString(k, v); is Int -> e.putInt(k, v); is Boolean -> e.putBoolean(k, v)
            is Float -> e.putFloat(k, v); is Long -> e.putLong(k, v)
        }
        e.apply()
    }
}
