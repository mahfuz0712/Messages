package com.mux.phone.messages

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConvoRow(cv: Convo, onClick: () -> Unit, onLong: () -> Unit) {
    val c = Hud.colors
    val shape = HudShape(8.dp)
    val unread = cv.unread > 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(c.primary.copy(alpha = if (unread) 0.12f else 0.04f), shape)
            .border(1.dp, c.primary.copy(alpha = if (unread) 0.6f else 0.25f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).border(1.dp, c.primary.copy(alpha = 0.6f), HudShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            HudText(cv.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#", color = c.primary, size = 16.sp, bold = true)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            HudText(cv.title, color = if (unread) c.primary else c.text, size = 14.sp, bold = unread, maxLines = 1)
            val prefix = if (cv.last.incoming) "" else "▸ "
            HudText(prefix + cv.last.body.replace('\n', ' '), color = c.dim, size = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            HudText(fmtTime(cv.last.date), color = c.dim, size = 10.sp)
            if (unread) HudText("● " + cv.unread, color = c.accent, size = 11.sp, bold = true)
        }
    }
}

@Composable
fun ChatsScreen(onAsk: () -> Unit, onSetup: () -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val tick = resumeTick.intValue
    val canRead = remember(tick) { hasPerm(ctx, Manifest.permission.READ_SMS) }
    val isDef = remember(tick) { isDefaultSms(ctx) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyUnread by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Convo?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<Msg>>(emptyList()) }

    val q = query.trim()
    val all = SmsRepo.convos
    val unreadCount = all.count { it.unread > 0 }
    val shown = all.filter {
        (!onlyUnread || it.unread > 0) &&
            (q.isEmpty() || it.title.contains(q, true) || it.addr.contains(q, true) || it.last.body.contains(q, true))
    }

    LaunchedEffect(q, SmsRepo.tick) {
        matches = if (q.length >= 2 && canRead) withContext(Dispatchers.IO) { SmsRepo.searchBodies(ctx, q) } else emptyList()
    }

    menu?.let { cv ->
        HudDialog(cv.title.uppercase(), onDismiss = { menu = null; confirm = false }) {
            if (!confirm) {
                HudButton("OPEN", Modifier.fillMaxWidth().height(40.dp), KeyKind.ACCENT, fontSize = 12.sp) {
                    menu = null; Nav.openThread(cv.thread, cv.addr)
                }
                if (cv.unread > 0) {
                    HudButton("MARK AS READ", Modifier.fillMaxWidth().height(40.dp), KeyKind.OP, fontSize = 12.sp) {
                        if (isDef) SmsRepo.markRead(ctx, cv.thread) else Util.toast(ctx, "SET AS DEFAULT SMS APP FIRST")
                        menu = null
                    }
                }
                HudButton("CALL", Modifier.fillMaxWidth().height(40.dp), KeyKind.OP, fontSize = 12.sp) {
                    Util.open(ctx, "tel:" + android.net.Uri.encode(cv.addr)); menu = null
                }
                HudButton("DELETE CONVERSATION", Modifier.fillMaxWidth().height(40.dp), KeyKind.DANGER, fontSize = 12.sp) {
                    if (isDef) confirm = true else Util.toast(ctx, "SET AS DEFAULT SMS APP FIRST")
                }
            } else {
                HudText("DELETE ALL " + cv.count + " MESSAGES WITH " + cv.title.uppercase() + "?", color = c.danger, size = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(44.dp)) {
                    HudButton("CANCEL", Modifier.weight(1f).height(44.dp), KeyKind.OP, fontSize = 12.sp) { confirm = false; menu = null }
                    HudButton("DELETE", Modifier.weight(1f).height(44.dp), KeyKind.DANGER, fontSize = 12.sp) {
                        SmsRepo.deleteThread(ctx, cv.thread)
                        confirm = false; menu = null
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!canRead) {
            PermissionPanel("Allow SMS access so Messages can show and send your text messages.", onAsk)
            return@Column
        }
        HudField(query, "⌕  SEARCH NAMES, NUMBERS, TEXT", Modifier.fillMaxWidth().height(44.dp)) { query = it }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HudButton("ALL " + all.size, Modifier.weight(1f).height(34.dp), KeyKind.OP, selected = !onlyUnread, fontSize = 10.sp) { onlyUnread = false }
            HudButton("UNREAD " + unreadCount, Modifier.weight(1f).height(34.dp), KeyKind.ACCENT, selected = onlyUnread, fontSize = 10.sp) { onlyUnread = true }
        }
        if (!isDef) {
            Spacer(Modifier.height(6.dp))
            HudText(
                "○ NOT THE DEFAULT SMS APP: NEW TEXTS STILL ARRIVE IN YOUR OLD APP. TAP HERE TO FIX",
                color = c.accent, size = 10.sp, modifier = Modifier.fillMaxWidth().clickable { onSetup() }
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty() && matches.isEmpty()) {
                EmptyNote(if (!SmsRepo.loaded) "LOADING..." else if (q.isNotEmpty()) "NOTHING FOUND" else "NO MESSAGES YET")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { "c" + it.thread }) { cv ->
                        ConvoRow(cv, onClick = { Nav.openThread(cv.thread, cv.addr) }, onLong = { menu = cv })
                    }
                    if (matches.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            HudText("MESSAGE MATCHES (" + matches.size + ")", color = c.primary, size = 10.sp)
                            Spacer(Modifier.height(4.dp))
                        }
                        items(matches, key = { "m" + it.id }) { m ->
                            HudItem(Modifier.fillMaxWidth(), onClick = { Nav.openThread(m.thread, m.addr) }) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    HudText(Contacts.name(ctx, m.addr).ifEmpty { m.addr }, color = c.text, size = 12.sp, bold = true, maxLines = 1, modifier = Modifier.weight(1f))
                                    HudText(fmtTime(m.date), color = c.dim, size = 10.sp)
                                }
                                HudText(m.body.replace('\n', ' '), color = c.dim, size = 11.sp, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        HudButton("+ NEW MESSAGE", Modifier.fillMaxWidth().height(46.dp), KeyKind.ACCENT, fontSize = 13.sp) { Nav.openNew() }
    }
}
