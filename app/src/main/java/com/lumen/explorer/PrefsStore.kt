package com.lumen.explorer

import android.content.Context
import android.content.SharedPreferences

object PrefsStore {
    private lateinit var sp: SharedPreferences
    fun init(c: Context) { if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE) }
    fun str(k: String, d: String): String = sp.getString(k, d) ?: d
    fun int(k: String, d: Int): Int = sp.getInt(k, d)
    fun bool(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)
    fun float(k: String, d: Float): Float = sp.getFloat(k, d)
    fun long(k: String, d: Long): Long = sp.getLong(k, d)
    fun list(k: String): List<String> = sp.getString(k, "")?.split('\u0001')?.filter { it.isNotEmpty() } ?: emptyList()
    fun edit(block: SharedPreferences.Editor.() -> Unit) { val e = sp.edit(); e.block(); e.apply() }
    fun putList(e: SharedPreferences.Editor, k: String, v: List<String>) { e.putString(k, v.joinToString("\u0001")) }
}
