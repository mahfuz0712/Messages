package com.mux.phone.messages

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Prefs {
    private var sp: SharedPreferences? = null
    var amber by mutableStateOf(false)
    var notify by mutableStateOf(true)
    var haptic by mutableStateOf(true)

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("mux_messages", Context.MODE_PRIVATE)
        sp = p
        amber = p.getBoolean("amber", false)
        notify = p.getBoolean("notify", true)
        haptic = p.getBoolean("haptic", true)
    }

    fun commit() {
        sp?.edit()?.putBoolean("amber", amber)?.putBoolean("notify", notify)?.putBoolean("haptic", haptic)?.apply()
    }
}

/** Which screen is open on top of the tabs: the list, one conversation, or a new message. */
object Nav {
    var mode by mutableStateOf("LIST")
    var thread by mutableLongStateOf(-1L)
    var addr by mutableStateOf("")
    var draft by mutableStateOf("")

    fun openThread(t: Long, a: String, d: String = "") {
        thread = t; addr = a; draft = d; mode = "THREAD"
    }

    fun openNew(number: String = "", body: String = "") {
        addr = number; draft = body; thread = -1L; mode = "NEW"
    }

    fun close() {
        mode = "LIST"
    }
}
