package com.mux.phone.messages

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsManager

/** Sending SMS. The text is saved in the phone's SMS database only when this app is the default SMS app. */
object Sender {
    private fun manager(ctx: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()

    fun send(ctx: Context, to: String, body: String): Boolean {
        val num = to.trim()
        if (num.isEmpty() || body.isBlank()) return false
        if (!hasPerm(ctx, Manifest.permission.SEND_SMS)) {
            Util.toast(ctx, "ALLOW SMS PERMISSION FIRST (SETUP TAB)")
            return false
        }
        var uri: Uri? = null
        if (isDefaultSms(ctx)) {
            try {
                val v = ContentValues().apply {
                    put("address", num)
                    put("body", body)
                    put("date", System.currentTimeMillis())
                    put("read", 1)
                    put("seen", 1)
                    put("type", 4)
                }
                uri = ctx.contentResolver.insert(Telephony.Sms.CONTENT_URI, v)
            } catch (e: Exception) {
            }
        }
        try {
            val sm = manager(ctx)
            val parts = sm.divideMessage(body)
            var sent: ArrayList<PendingIntent>? = null
            if (uri != null) {
                sent = ArrayList()
                for (i in parts.indices) {
                    val si = Intent(ctx, NotifyReceiver::class.java).setAction(NotifyReceiver.SENT).setData(uri)
                    sent.add(
                        PendingIntent.getBroadcast(
                            ctx, (uri.lastPathSegment?.toIntOrNull() ?: 0) * 16 + i, si,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                }
            }
            sm.sendMultipartTextMessage(num, null, parts, sent, null)
        } catch (e: Exception) {
            setType(ctx, uri, 5)
            Util.toast(ctx, "SEND FAILED")
            return false
        }
        if (uri == null) Util.toast(ctx, "SENT (NOT SAVED: SET AS DEFAULT SMS APP)")
        return true
    }

    fun setType(ctx: Context, uri: Uri?, type: Int) {
        if (uri == null) return
        try {
            ctx.contentResolver.update(uri, ContentValues().apply { put("type", type) }, null, null)
        } catch (e: Exception) {
        }
    }
}

object Notify {
    private const val CH = "messages"

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (nm.getNotificationChannel(CH) == null) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(CH, "Messages", android.app.NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }

    private fun idOf(thread: Long, addr: String): Int =
        if (thread >= 0) 1000 + (thread % 100000).toInt() else addr.hashCode()

    fun incoming(ctx: Context, addr: String, body: String, thread: Long) {
        if (!Prefs.notify) return
        try {
            channel(ctx)
            val id = idOf(thread, addr)
            val who = Contacts.name(ctx, addr).ifEmpty { addr }
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open = PendingIntent.getActivity(
                ctx, id,
                Intent(ctx, MainActivity::class.java).putExtra("thread", thread).putExtra("addr", addr)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags
            )
            val read = PendingIntent.getBroadcast(
                ctx, id,
                Intent(ctx, NotifyReceiver::class.java).setAction(NotifyReceiver.READ)
                    .putExtra("thread", thread).putExtra("addr", addr),
                flags
            )
            val replyFlags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val reply = PendingIntent.getBroadcast(
                ctx, id,
                Intent(ctx, NotifyReceiver::class.java).setAction(NotifyReceiver.REPLY)
                    .putExtra("thread", thread).putExtra("addr", addr),
                replyFlags
            )
            val ri = androidx.core.app.RemoteInput.Builder("reply").setLabel("Reply").build()
            val n = androidx.core.app.NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(R.drawable.ic_stat_msg)
                .setContentTitle(who)
                .setContentText(body)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setColor(0xFF19E3FF.toInt())
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(androidx.core.app.NotificationCompat.Action.Builder(0, "REPLY", reply).addRemoteInput(ri).build())
                .addAction(androidx.core.app.NotificationCompat.Action.Builder(0, "MARK READ", read).build())
                .build()
            androidx.core.app.NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
        } catch (e: Exception) {
        }
    }

    fun cancel(ctx: Context, thread: Long, addr: String = "") {
        try {
            androidx.core.app.NotificationManagerCompat.from(ctx).cancel(idOf(thread, addr))
        } catch (e: Exception) {
        }
    }
}

/** Receives SMS when this app is the default SMS app. Saves them to the phone's SMS database. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val by = LinkedHashMap<String, StringBuilder>()
        var sentAt = System.currentTimeMillis()
        for (m in msgs) {
            val a = m.originatingAddress ?: m.displayOriginatingAddress ?: "unknown"
            by.getOrPut(a) { StringBuilder() }.append(m.messageBody ?: "")
            sentAt = m.timestampMillis
        }
        for ((a, b) in by) {
            var thread = -1L
            try {
                val v = ContentValues().apply {
                    put("address", a)
                    put("body", b.toString())
                    put("date", System.currentTimeMillis())
                    put("date_sent", sentAt)
                    put("read", 0)
                    put("seen", 0)
                }
                val u = ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, v)
                if (u != null) {
                    ctx.contentResolver.query(u, arrayOf("thread_id"), null, null, null)?.use {
                        if (it.moveToFirst()) thread = it.getLong(0)
                    }
                }
            } catch (e: Exception) {
            }
            Notify.incoming(ctx, a, b.toString(), thread)
        }
    }
}

/**
 * Picture messages (MMS) are not supported. This receiver only exists because Android requires it
 * for the default SMS role; it shows a notice so you know something arrived.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Notify.incoming(ctx, "MMS", "A picture message arrived. Messages cannot show MMS yet.", -1L)
    }
}

/** "Reply with message" from the incoming call screen. */
class RespondService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val num = intent?.data?.schemeSpecificPart?.substringBefore('?')
            val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
            if (!num.isNullOrEmpty() && !text.isNullOrBlank()) Sender.send(this, num, text)
        } catch (e: Exception) {
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

class NotifyReceiver : BroadcastReceiver() {
    companion object {
        const val SENT = "com.mux.phone.messages.SENT"
        const val READ = "com.mux.phone.messages.READ"
        const val REPLY = "com.mux.phone.messages.REPLY"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val thread = intent.getLongExtra("thread", -1L)
        val addr = intent.getStringExtra("addr") ?: ""
        when (intent.action) {
            SENT -> Sender.setType(ctx, intent.data, if (resultCode == Activity.RESULT_OK) 2 else 5)
            READ -> {
                SmsRepo.markRead(ctx, thread)
                Notify.cancel(ctx, thread, addr)
            }
            REPLY -> {
                val text = androidx.core.app.RemoteInput.getResultsFromIntent(intent)?.getCharSequence("reply")?.toString()
                if (!text.isNullOrBlank() && addr.isNotEmpty()) Sender.send(ctx, addr, text)
                SmsRepo.markRead(ctx, thread)
                Notify.cancel(ctx, thread, addr)
            }
        }
    }
}
