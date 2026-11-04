package com.mux.phone.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TABS = listOf("CHATS", "SETUP", "SYS")

@Composable
fun MessagesRoot() {
    val ctx = LocalContext.current
    MuxTheme(if (Prefs.amber) AmberPalette else CyanPalette) {
        val c = Hud.colors
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var update by remember { mutableStateOf<UpdateInfo?>(null) }

        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            Contacts.clear()
            SmsRepo.reload(ctx)
            resumeTick.intValue = resumeTick.intValue + 1
        }
        val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            resumeTick.intValue = resumeTick.intValue + 1
            SmsRepo.reload(ctx)
        }
        val askPerms: () -> Unit = {
            val list = ArrayList(smsPerms().toList())
            if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
            permLauncher.launch(list.toTypedArray())
        }
        val askDefault: () -> Unit = {
            try {
                val i = if (Build.VERSION.SDK_INT >= 29) {
                    (ctx.getSystemService(RoleManager::class.java)).createRequestRoleIntent(RoleManager.ROLE_SMS)
                } else {
                    Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                        .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ctx.packageName)
                }
                roleLauncher.launch(i)
            } catch (e: Exception) {
                Util.toast(ctx, "CANNOT OPEN THE DEFAULT APP DIALOG")
            }
        }

        LaunchedEffect(Unit) { if (isOnline(ctx)) update = checkUpdate(ctx).second }
        BackHandler(Nav.mode != "LIST") { Nav.close() }

        update?.let { u ->
            UpdateDialog(
                u, appVersion(ctx),
                onUpdate = { Util.open(ctx, u.apkUrl); update = null },
                onCancel = { update = null }
            )
        }

        HudBackground(Modifier.fillMaxSize()) {
            when (Nav.mode) {
                "THREAD" -> key(Nav.thread, Nav.addr) { ThreadScreen(Nav.thread, Nav.addr, Nav.draft) { Nav.close() } }
                "NEW" -> NewScreen { Nav.close() }
                else -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            HudText("◈ MESSAGES", color = c.primary, size = 16.sp, bold = true)
                            HudText(TABS[tab] + if (resumeTick.intValue >= 0 && isDefaultSms(ctx)) " · DEFAULT SMS APP" else "", color = c.dim, size = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            0 -> ChatsScreen(askPerms) { tab = 1 }
                            1 -> SetupScreen(askPerms, askDefault)
                            else -> SystemScreen(false) { update = it }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().height(46.dp)) {
                        TABS.forEachIndexed { i, t ->
                            HudButton(
                                label = t,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                kind = KeyKind.OP,
                                selected = i == tab,
                                fontSize = 11.sp
                            ) { tab = i }
                        }
                    }
                }
            }
        }
    }
}
