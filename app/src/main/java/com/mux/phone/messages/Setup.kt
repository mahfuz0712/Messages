package com.mux.phone.messages

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Status(ok: Boolean, onText: String, offText: String) {
    val c = Hud.colors
    HudText(if (ok) "● $onText" else "○ $offText", color = if (ok) c.good else c.danger, size = 13.sp, bold = true)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Hint(text: String) {
    HudText(text, color = Hud.colors.dim, size = 10.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Btn(label: String, kind: KeyKind = KeyKind.OP, onClick: () -> Unit) {
    HudButton(label, Modifier.fillMaxWidth().height(42.dp), kind, fontSize = 12.sp, onClick = onClick)
}

private fun go(ctx: Context, action: String) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Util.toast(ctx, "CANNOT OPEN THIS SETTING")
    }
}

private fun onOff(b: Boolean) = if (b) "ON" else "OFF"

@Composable
fun SetupScreen(onAsk: () -> Unit, onDefault: () -> Unit) {
    val ctx = LocalContext.current
    val tick = resumeTick.intValue
    val isDef = remember(tick) { isDefaultSms(ctx) }
    val sms = remember(tick) {
        hasPerm(ctx, Manifest.permission.READ_SMS) && hasPerm(ctx, Manifest.permission.SEND_SMS) &&
            hasPerm(ctx, Manifest.permission.RECEIVE_SMS)
    }
    val contacts = remember(tick) { hasPerm(ctx, Manifest.permission.READ_CONTACTS) }
    val notif = remember(tick) {
        Build.VERSION.SDK_INT < 33 || hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HudPanel(Modifier.fillMaxWidth(), "1 · PERMISSIONS") {
            Status(sms, "SMS ACCESS GRANTED", "SMS ACCESS MISSING")
            Status(contacts, "CONTACT NAMES ALLOWED", "CONTACT NAMES OFF (NUMBERS ONLY)")
            Status(notif, "NOTIFICATIONS ALLOWED", "NOTIFICATIONS OFF")
            Btn("GRANT PERMISSIONS", KeyKind.ACCENT) { onAsk() }
            Btn("APP SETTINGS", KeyKind.FN) { Util.openAppSettings(ctx) }
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "2 · DEFAULT SMS APP") {
            Status(isDef, "MESSAGES IS YOUR SMS APP", "NOT THE DEFAULT SMS APP YET")
            Btn("SET AS DEFAULT SMS APP", KeyKind.ACCENT) { onDefault() }
            Btn("DEFAULT APPS SETTINGS", KeyKind.FN) { go(ctx, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) }
            Hint("Only the default SMS app can receive new texts, save sent texts, mark as read and delete. Without it you can still read old messages and send.")
            Hint("MMS (picture / group messages) is NOT supported yet. If you rely on MMS, keep your old app as default. To go back, open DEFAULT APPS SETTINGS and choose the old app.")
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "3 · OPTIONS") {
            SettingRow("PALETTE", "cyan or amber HUD colors", if (Prefs.amber) "AMBER" else "CYAN") {
                Prefs.amber = !Prefs.amber; Prefs.commit()
            }
            SettingRow("NOTIFICATIONS", "show a notification for new texts", onOff(Prefs.notify)) {
                Prefs.notify = !Prefs.notify; Prefs.commit()
            }
            SettingRow("HAPTIC", "vibrate after sending", onOff(Prefs.haptic)) {
                Prefs.haptic = !Prefs.haptic; Prefs.commit()
            }
        }
        Spacer(Modifier.height(8.dp))
        HudPanel(Modifier.fillMaxWidth(), "4 · HONOR / BATTERY") {
            Btn("BATTERY OPTIMIZATION", KeyKind.OP) { go(ctx, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
            Hint("Honor: Settings > Battery > App launch > Messages > Manage manually > turn on all three switches. Otherwise new-text notifications can be late.")
        }
        Spacer(Modifier.height(8.dp))
    }
}
