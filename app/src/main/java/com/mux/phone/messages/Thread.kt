package com.mux.phone.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How many SMS a text will use: 160 / 153 characters, or 70 / 67 when it has Bangla or other non-English letters. */
fun smsParts(t: String): Int {
    if (t.isEmpty()) return 0
    val uni = t.any { it.code > 127 }
    val single = if (uni) 70 else 160
    val multi = if (uni) 67 else 153
    return if (t.length <= single) 1 else (t.length + multi - 1) / multi
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: Msg, onLong: () -> Unit) {
    val c = Hud.colors
    val shape = HudShape(8.dp)
    val out = !m.incoming
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = if (out) Alignment.End else Alignment.Start) {
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .background(if (out) c.primary.copy(alpha = 0.16f) else c.panel.copy(alpha = 0.9f), shape)
                .border(1.dp, if (m.failed) c.danger else if (out) c.primary.copy(alpha = 0.6f) else c.dim, shape)
                .combinedClickable(onClick = {}, onLongClick = onLong)
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            HudText(m.body, color = c.text, size = 14.sp)
        }
        val status = when {
            m.failed -> "  ✕ FAILED"
            m.pending -> "  … SENDING"
            out -> "  ✓"
            else -> ""
        }
        HudText(fmtTime(m.date) + status, color = if (m.failed) c.danger else c.dim, size = 9.sp)
    }
}

@Composable
fun ThreadScreen(thread: Long, addr: String, draft: String, onBack: () -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    var tid by remember { mutableLongStateOf(thread) }
    var msgs by remember { mutableStateOf<List<Msg>>(emptyList()) }
    var text by remember { mutableStateOf(draft) }
    var menu by remember { mutableStateOf<Msg?>(null) }
    val listState = rememberLazyListState()
    val tick = SmsRepo.tick
    val title = Contacts.name(ctx, addr).ifEmpty { addr }
    val isDef = isDefaultSms(ctx)

    LaunchedEffect(SmsRepo.convos, addr) {
        if (tid < 0) SmsRepo.findThread(addr)?.let { tid = it }
    }
    LaunchedEffect(tid, tick) {
        if (tid >= 0) {
            val list = withContext(Dispatchers.IO) { SmsRepo.thread(ctx, tid) }
            msgs = list
            if (list.any { it.incoming && !it.read }) {
                withContext(Dispatchers.IO) { SmsRepo.markRead(ctx, tid) }
            }
            Notify.cancel(ctx, tid, addr)
        }
    }
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size - 1)
    }

    menu?.let { m ->
        HudDialog(fmtFull(m.date), onDismiss = { menu = null }) {
            HudButton("COPY", Modifier.fillMaxWidth().height(40.dp), KeyKind.OP, fontSize = 12.sp) {
                Util.copy(ctx, m.body); menu = null
            }
            HudButton("FORWARD", Modifier.fillMaxWidth().height(40.dp), KeyKind.OP, fontSize = 12.sp) {
                menu = null; Nav.openNew("", m.body)
            }
            if (m.failed) {
                HudButton("SEND AGAIN", Modifier.fillMaxWidth().height(40.dp), KeyKind.ACCENT, fontSize = 12.sp) {
                    if (Sender.send(ctx, addr, m.body)) SmsRepo.deleteMsg(ctx, m.id)
                    menu = null
                }
            }
            HudButton("DELETE", Modifier.fillMaxWidth().height(40.dp), KeyKind.DANGER, fontSize = 12.sp) {
                if (!SmsRepo.deleteMsg(ctx, m.id)) Util.toast(ctx, "SET AS DEFAULT SMS APP FIRST")
                menu = null
            }
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            HudButton("◂", Modifier.width(46.dp).height(40.dp), KeyKind.ACCENT, fontSize = 16.sp) { onBack() }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                HudText(title, color = c.primary, size = 15.sp, bold = true, maxLines = 1)
                if (title != addr) HudText(addr, color = c.dim, size = 10.sp, maxLines = 1)
            }
            HudButton("CALL", Modifier.width(66.dp).height(40.dp), KeyKind.OP, fontSize = 11.sp) {
                Util.open(ctx, "tel:" + android.net.Uri.encode(addr))
            }
        }
        if (!isDef) {
            HudText(
                "○ NOT DEFAULT SMS APP: SENT TEXTS ARE NOT SAVED HERE (SETUP TAB)",
                color = c.accent, size = 10.sp, modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (msgs.isEmpty()) {
                EmptyNote(if (tid < 0) "NEW CONVERSATION. WRITE BELOW." else "LOADING...")
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(msgs, key = { it.id }) { m -> Bubble(m) { menu = m } }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val parts = smsParts(text)
        if (parts > 0) {
            HudText("${text.length} CHARS · $parts SMS", color = c.dim, size = 9.sp)
            Spacer(Modifier.height(2.dp))
        }
        val shape = HudShape(8.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.weight(1f).heightIn(min = 46.dp, max = 130.dp)
                    .background(c.primary.copy(alpha = 0.06f), shape)
                    .border(1.dp, c.primary.copy(alpha = 0.5f), shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) HudText("MESSAGE", color = c.dim, size = 13.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    maxLines = 5,
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            HudButton("SEND", Modifier.width(76.dp).height(46.dp), KeyKind.ACCENT, enabled = text.isNotBlank(), fontSize = 12.sp) {
                if (Sender.send(ctx, addr, text.trim())) {
                    text = ""
                    if (Prefs.haptic) Util.vibrate(ctx)
                }
            }
        }
    }
}

@Composable
fun NewScreen(onBack: () -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    var to by remember { mutableStateOf(Nav.addr) }
    var sugg by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val body = Nav.draft

    fun go(number: String) {
        val n = number.trim()
        if (n.isEmpty()) return
        Nav.openThread(SmsRepo.findThread(n) ?: -1L, n, body)
    }

    LaunchedEffect(to) {
        sugg = withContext(Dispatchers.IO) { Contacts.search(ctx, to.trim()) }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            HudButton("◂", Modifier.width(46.dp).height(40.dp), KeyKind.ACCENT, fontSize = 16.sp) { onBack() }
            Spacer(Modifier.width(8.dp))
            HudText("NEW MESSAGE", color = c.primary, size = 15.sp, bold = true)
        }
        Spacer(Modifier.height(8.dp))
        HudField(to, "TO: NAME OR NUMBER", Modifier.fillMaxWidth().height(46.dp), onDone = { go(to) }) { to = it }
        Spacer(Modifier.height(6.dp))
        HudButton("WRITE TO " + (to.trim().ifEmpty { "..." }) + " ▸", Modifier.fillMaxWidth().height(44.dp), KeyKind.ACCENT, enabled = to.isNotBlank(), fontSize = 12.sp) { go(to) }
        Spacer(Modifier.height(8.dp))
        if (body.isNotEmpty()) {
            HudText("TEXT: " + body.replace('\n', ' '), color = c.dim, size = 10.sp, maxLines = 2)
            Spacer(Modifier.height(6.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(sugg, key = { it.second }) { (name, number) ->
                HudItem(Modifier.fillMaxWidth(), onClick = { go(number) }) {
                    HudText(name.ifEmpty { number }, color = c.text, size = 13.sp, bold = true, maxLines = 1)
                    HudText(number, color = c.dim, size = 11.sp)
                }
            }
        }
    }
}
