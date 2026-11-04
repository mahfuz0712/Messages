package com.mux.phone.messages

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class Msg(
    val id: Long,
    val thread: Long,
    val addr: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean
) {
    val incoming: Boolean get() = type == 1
    val failed: Boolean get() = type == 5
    val pending: Boolean get() = type == 4 || type == 6
}

class Convo(
    val thread: Long,
    val addr: String,
    val name: String,
    val last: Msg,
    val unread: Int,
    val count: Int
) {
    val title: String get() = if (name.isNotEmpty()) name else addr
}

fun hasPerm(ctx: Context, p: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

fun smsPerms(): Array<String> = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_CONTACTS
)

fun isDefaultSms(ctx: Context): Boolean = try {
    Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
} catch (e: Exception) {
    false
}

/** "14:05" today, "03 Nov" this year, "03/11/25" older. */
fun fmtTime(ms: Long): String {
    val now = Calendar.getInstance()
    val t = Calendar.getInstance().apply { timeInMillis = ms }
    val pattern = when {
        now.get(Calendar.YEAR) != t.get(Calendar.YEAR) -> "dd/MM/yy"
        now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR) -> "HH:mm"
        else -> "dd MMM"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
}

fun fmtFull(ms: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

object Contacts {
    private val cache = ConcurrentHashMap<String, String>()

    fun clear() = cache.clear()

    /** Contact name for a number, or "" when unknown / no permission. */
    fun name(ctx: Context, addr: String): String {
        cache[addr]?.let { return it }
        var n = ""
        if (hasPerm(ctx, Manifest.permission.READ_CONTACTS)) {
            try {
                val u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(addr))
                ctx.contentResolver.query(u, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) n = it.getString(0) ?: ""
                }
            } catch (e: Exception) {
            }
        }
        cache[addr] = n
        return n
    }

    /** (name, number) pairs whose name or number contains [q]. */
    fun search(ctx: Context, q: String): List<Pair<String, String>> {
        if (q.isBlank() || !hasPerm(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        try {
            val p = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val nameCol = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            val numCol = ContactsContract.CommonDataKinds.Phone.NUMBER
            ctx.contentResolver.query(
                p, arrayOf(nameCol, numCol), "$nameCol LIKE ? OR $numCol LIKE ?",
                arrayOf("%$q%", "%$q%"), "$nameCol ASC"
            )?.use {
                val seen = HashSet<String>()
                while (it.moveToNext() && out.size < 8) {
                    val nm = it.getString(0) ?: ""
                    val no = it.getString(1) ?: continue
                    if (seen.add(no.filter { ch -> ch.isDigit() || ch == '+' })) out.add(nm to no)
                }
            }
        } catch (e: Exception) {
        }
        return out
    }
}

object SmsRepo {
    var convos by mutableStateOf<List<Convo>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    /** Bumped when the SMS database changes, so open screens reload. */
    var tick by mutableIntStateOf(0)

    private var busy = false
    private var again = false
    private val main = Handler(Looper.getMainLooper())

    fun changed(ctx: Context) {
        tick += 1
        reload(ctx)
    }

    fun reload(ctx: Context) {
        val app = ctx.applicationContext
        if (!hasPerm(app, Manifest.permission.READ_SMS)) {
            convos = emptyList()
            loaded = true
            return
        }
        if (busy) {
            again = true
            return
        }
        busy = true
        Thread {
            val list = try { query(app) } catch (e: Exception) { emptyList() }
            main.post {
                convos = list
                loaded = true
                busy = false
                if (again) {
                    again = false
                    reload(app)
                }
            }
        }.start()
    }

    private fun msgOf(c: android.database.Cursor): Msg = Msg(
        id = c.getLong(0),
        thread = c.getLong(1),
        addr = c.getString(2) ?: "",
        body = c.getString(3) ?: "",
        date = c.getLong(4),
        type = c.getInt(5),
        read = c.getInt(6) != 0
    )

    private val cols = arrayOf("_id", "thread_id", "address", "body", "date", "type", "read")

    private fun query(ctx: Context): List<Convo> {
        val last = LinkedHashMap<Long, Msg>()
        val counts = HashMap<Long, Int>()
        val unread = HashMap<Long, Int>()
        ctx.contentResolver.query(Telephony.Sms.CONTENT_URI, cols, null, null, "date DESC")?.use { c ->
            var n = 0
            while (c.moveToNext() && n < 12000) {
                n++
                val m = msgOf(c)
                if (m.addr.isEmpty()) continue
                if (!last.containsKey(m.thread)) last[m.thread] = m
                counts[m.thread] = (counts[m.thread] ?: 0) + 1
                if (m.incoming && !m.read) unread[m.thread] = (unread[m.thread] ?: 0) + 1
            }
        }
        return last.values.map {
            Convo(it.thread, it.addr, Contacts.name(ctx, it.addr), it, unread[it.thread] ?: 0, counts[it.thread] ?: 1)
        }
    }

    /** All messages of one conversation, oldest first. Call off the main thread. */
    fun thread(ctx: Context, id: Long): List<Msg> {
        val out = ArrayList<Msg>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, cols, "thread_id=?", arrayOf(id.toString()), "date ASC"
            )?.use { c -> while (c.moveToNext()) out.add(msgOf(c)) }
        } catch (e: Exception) {
        }
        return out
    }

    /** Messages whose text contains [q], newest first. Call off the main thread. */
    fun searchBodies(ctx: Context, q: String): List<Msg> {
        val out = ArrayList<Msg>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, cols, "body LIKE ?", arrayOf("%$q%"), "date DESC"
            )?.use { c -> while (c.moveToNext() && out.size < 40) out.add(msgOf(c)) }
        } catch (e: Exception) {
        }
        return out
    }

    /** The conversation id for a number (needs default-SMS rights to create a new one). */
    fun threadIdFor(ctx: Context, addr: String): Long = try {
        Telephony.Threads.getOrCreateThreadId(ctx, addr)
    } catch (e: Exception) {
        -1L
    }

    fun findThread(addr: String): Long? {
        val digits = addr.filter { it.isDigit() }
        if (digits.length < 6) return convos.firstOrNull { it.addr.equals(addr, true) }?.thread
        val tail = digits.takeLast(9)
        return convos.firstOrNull { it.addr.filter { ch -> ch.isDigit() }.endsWith(tail) }?.thread
    }

    fun markRead(ctx: Context, thread: Long) {
        if (!isDefaultSms(ctx)) return
        try {
            val v = ContentValues().apply { put("read", 1); put("seen", 1) }
            ctx.contentResolver.update(Telephony.Sms.CONTENT_URI, v, "thread_id=? AND read=0", arrayOf(thread.toString()))
        } catch (e: Exception) {
        }
    }

    fun deleteThread(ctx: Context, thread: Long): Boolean {
        if (!isDefaultSms(ctx)) return false
        return try {
            ctx.contentResolver.delete(Telephony.Sms.CONTENT_URI, "thread_id=?", arrayOf(thread.toString()))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteMsg(ctx: Context, id: Long): Boolean {
        if (!isDefaultSms(ctx)) return false
        return try {
            ctx.contentResolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
