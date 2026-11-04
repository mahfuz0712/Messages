package com.mux.phone.messages

import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf

/** Bumped every time the app comes back to the front, so status lines re-check themselves. */
val resumeTick = mutableIntStateOf(0)

class MainActivity : ComponentActivity() {
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            SmsRepo.changed(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        try {
            contentResolver.registerContentObserver(Uri.parse("content://sms"), true, observer)
        } catch (e: Exception) {
        }
        SmsRepo.reload(this)
        if (savedInstanceState == null) handle(intent)
        setContent { MessagesRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** Notification tap, "sms:" link, or text shared from another app. */
    private fun handle(i: Intent?) {
        i ?: return
        when {
            i.hasExtra("thread") -> Nav.openThread(i.getLongExtra("thread", -1L), i.getStringExtra("addr") ?: "")
            i.action == Intent.ACTION_SEND -> Nav.openNew("", i.getStringExtra(Intent.EXTRA_TEXT) ?: "")
            i.data != null && (i.action == Intent.ACTION_SENDTO || i.action == Intent.ACTION_VIEW) -> {
                val num = i.data?.schemeSpecificPart?.substringBefore('?')?.trim() ?: ""
                val body = i.getStringExtra("sms_body") ?: i.data?.getQueryParameter("body") ?: ""
                if (num.isNotEmpty()) {
                    Nav.openThread(SmsRepo.findThread(num) ?: -1L, num, body)
                } else {
                    Nav.openNew("", body)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Contacts.clear()
        SmsRepo.reload(this)
        resumeTick.intValue = resumeTick.intValue + 1
    }

    override fun onDestroy() {
        try { contentResolver.unregisterContentObserver(observer) } catch (e: Exception) { }
        super.onDestroy()
    }
}
