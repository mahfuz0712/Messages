# Messages

Sci-fi HUD (Jarvis style) SMS app for Android 8.0+. Same design language as Dialer, Scanner, Keyboard and Launcher.

- Package: `com.mux.phone.messages` - Version: `1.0.0`

## Features
- Conversation list with unread badges, ALL / UNREAD filter, search by name, number and message text
- Chat screen with bubbles, send status (sending / sent / failed), SMS counter (Bangla-aware: 70 / 67 characters), call button
- Long-press a message: copy, forward, send again, delete. Long-press a conversation: open, mark read, call, delete
- New message with contact suggestions; opens from `sms:` / `smsto:` links and "share text" from other apps
- Default SMS app support: receives and stores new texts, notification with REPLY and MARK READ, "reply with message" from the call screen
- Cyan / amber HUD palette, SETUP tab (permissions, default app, options), SYS tab (info, update check)

## First run
1. SETUP > GRANT PERMISSIONS
2. SETUP > SET AS DEFAULT SMS APP
3. Honor: Settings > Battery > App launch > Messages > Manage manually, all switches on

## Limits
- SMS only. MMS (pictures, group messages) is not supported; a notice appears when one arrives. Keep your old app as default if you need MMS.
- Without being the default SMS app: you can read and send, but new texts, saving sent texts, mark read and delete do not work.
- Single recipient per message. No dual-SIM chooser (the system default SIM is used).

## Build
`gradle assembleDebug` (Gradle 8.9, JDK 17) or the GitHub Action. `app/debug.keystore` is shared with the other apps.
Updates are read from GitHub releases of `mahfuz0712/Messages` (edit `AppInfo.kt`).
