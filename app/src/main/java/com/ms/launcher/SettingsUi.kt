package com.ms.launcher

import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

@Composable
fun SettingsScreen(act: MainActivity, wallpaperId: String, onWallpaper: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = act.store
    val s = store.settings
    val tick = act.resumeTick
    val isDefault = remember(tick) { isDefaultLauncher(ctx) }
    val dotsOn = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }
    val lockOn = remember(tick) { isLockServiceEnabled(ctx) }
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "" }
    }
    var confirmReset by remember { mutableStateOf(false) }
    var disclosure by remember { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF0E0E10)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Launcher settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

                if (!isDefault) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A30)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("MS Launcher isn't your default home app yet", fontWeight = FontWeight.SemiBold)
                            Text("Needed for app shortcuts and for the Home button to come here.",
                                fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { openSettings(ctx, Settings.ACTION_HOME_SETTINGS) }) { Text("Set as default") }
                        }
                    }
                }

                Section("Wallpaper")
                WallpaperPicker(wallpaperId, onWallpaper)
                SliderRow("Darken wallpaper", "${(s.dim * 100).toInt()}%", s.dim, 0f..0.6f) { v ->
                    store.updateSettings { it.copy(dim = v) }
                }

                Section("Home screen")
                StepperRow("Columns", s.columns, 3..6) { store.updateGrid(it, s.rows) }
                StepperRow("Rows", s.rows, 3..8) { store.updateGrid(s.columns, it) }
                SliderRow("Icon size", "${s.iconSizeDp} dp", s.iconSizeDp.toFloat(), 40f..72f) { v ->
                    store.updateSettings { it.copy(iconSizeDp = v.toInt()) }
                }
                SwitchRow("Show app names", null, s.showLabels) { v -> store.updateSettings { it.copy(showLabels = v) } }
                SwitchRow("Clock, date & battery", null, s.showClock) { v -> store.updateSettings { it.copy(showClock = v) } }
                SwitchRow("24-hour clock", null, s.clock24h) { v -> store.updateSettings { it.copy(clock24h = v) } }
                SwitchRow("Search bar", null, s.showSearchBar) { v -> store.updateSettings { it.copy(showSearchBar = v) } }
                SwitchRow("Dock", null, s.showDock) { v -> store.updateSettings { it.copy(showDock = v) } }
                StepperRow("Dock icons", s.dockSlots, 3..6) { store.setDockSlots(it) }
                SwitchRow("Lock home layout", "Stops icons being moved or removed by accident", s.lockLayout) { v ->
                    store.updateSettings { it.copy(lockLayout = v) }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { store.addPage() }, modifier = Modifier.weight(1f)) { Text("Add page") }
                    OutlinedButton(onClick = { store.removeEmptyPages() }, modifier = Modifier.weight(1f)) { Text("Remove empty pages") }
                }
                Text("Pages: ${s.pages}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))

                Section("Gestures")
                Hint("Swipe up → all apps   ·   Swipe right from home → widgets   ·   Hold an empty spot → options   ·   Hold an icon → menu, then drag to move")
                SwitchRow("Swipe down for notifications", null, s.swipeDownNotifications) { v ->
                    store.updateSettings { it.copy(swipeDownNotifications = v) }
                }
                if (BuildConfig.LOCK_FEATURE) {
                    SwitchRow(
                        "Double-tap empty space to lock",
                        if (!s.doubleTapLock) "Off · optional · uses Accessibility"
                        else if (lockOn) "On" else "Waiting for you to allow it in Accessibility",
                        s.doubleTapLock
                    ) { v ->
                        if (v) disclosure = "lock"
                        else {
                            store.updateSettings { it.copy(doubleTapLock = false) }
                            setPrivacyComponents(ctx, store.settings)
                        }
                    }
                    if (s.doubleTapLock && !lockOn) {
                        Hint("Accessibility → Installed apps → MS Launcher – double-tap to lock → On. If it's greyed out on Samsung: Settings → Apps → MS Launcher → ⋮ → Allow restricted settings.")
                        OutlinedButton(onClick = { openSettings(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("Open Accessibility settings") }
                    }
                } else {
                    Hint("Double-tap to lock is not included in this edition, so the app needs no Accessibility access.")
                }

                Section("Notification counts")
                SwitchRow(
                    "Show notification counts on icons",
                    if (!s.notificationDots) "Off · optional · uses notification access"
                    else if (dotsOn) "On" else "Waiting for you to allow notification access",
                    s.notificationDots
                ) { v ->
                    if (v) disclosure = "dots"
                    else {
                        store.updateSettings { it.copy(notificationDots = false) }
                        setPrivacyComponents(ctx, store.settings)
                    }
                }
                if (s.notificationDots && !dotsOn) {
                    Hint("Turn on “MS Launcher – notification counts”. If it's greyed out: Settings → Apps → MS Launcher → ⋮ → Allow restricted settings.")
                    OutlinedButton(onClick = { openSettings(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text("Open notification access settings") }
                }
                if (!s.notificationDots && dotsOn) {
                    Hint("Notification access is still allowed in Android settings but MS Launcher has switched its listener off. You can also remove the access there.")
                    OutlinedButton(onClick = { openSettings(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text("Remove notification access") }
                }

                Section("Privacy")
                Hint("MS Launcher has no internet permission and sends nothing anywhere. Your layout, hidden apps and app-usage counts stay on this phone only and are excluded from backups.")
                OutlinedButton(onClick = {
                    store.clearLaunchCounts()
                    android.widget.Toast.makeText(ctx, "App usage history cleared", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Clear app usage history (Frequently used)") }

                Section("Hidden apps")
                if (store.hidden.isEmpty()) {
                    Hint("No hidden apps. In the app drawer, hold an app → Hide app.")
                } else {
                    store.hidden.forEach { key ->
                        val a = act.repo.apps.firstOrNull { it.key == key }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            a?.let { Image(it.icon, null, Modifier.size(36.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Text(a?.let { store.renames[it.key] ?: it.label } ?: key.substringBefore("/"),
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { store.unhide(key) }) { Text("Unhide") }
                        }
                    }
                }

                Section("Reset")
                OutlinedButton(onClick = { confirmReset = true }) { Text("Reset home screen layout") }

                Spacer(Modifier.height(20.dp))
                Text("MS Launcher $version", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Prominent disclosure shown BEFORE the user is sent to a sensitive Android permission screen
    disclosure?.let { kind ->
        val isLock = kind == "lock"
        AlertDialog(
            onDismissRequest = { disclosure = null },
            title = { Text(if (isLock) "Allow Accessibility for double-tap to lock?" else "Allow notification access for counts?") },
            text = {
                Text(
                    if (isLock)
                        "MS Launcher uses Android's Accessibility service ONLY to:\n" +
                        "• lock the screen when you double-tap empty space on the home screen\n" +
                        "• open the notification panel when you swipe down\n\n" +
                        "It does NOT read your screen, what you type, passwords, OTPs, banking apps or any other app's content. " +
                        "It is set up to receive no events from other apps. Nothing is stored or sent — the app has no internet access.\n\n" +
                        "Next, Android will show the Accessibility screen. Turn on “MS Launcher – double-tap to lock”. " +
                        "You can turn it off any time here."
                    else
                        "To show a count on app icons, Android must give MS Launcher notification access.\n\n" +
                        "MS Launcher uses ONLY the app name and the number of notifications for each app. " +
                        "It does NOT read, save or send message text, senders, OTPs, passwords or banking details. " +
                        "Counts are kept in memory only and the app has no internet access.\n\n" +
                        "Next, Android will show the Notification access screen. Turn on “MS Launcher – notification counts”. " +
                        "You can turn it off any time here."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.updateSettings { if (isLock) it.copy(doubleTapLock = true) else it.copy(notificationDots = true) }
                    setPrivacyComponents(ctx, store.settings)
                    if (isLock && !lockOn) openSettings(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    if (!isLock && !dotsOn) openSettings(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    disclosure = null
                }) { Text("Agree & continue") }
            },
            dismissButton = { TextButton(onClick = { disclosure = null }) { Text("No thanks") } }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset home screen?") },
            text = { Text("All icons, folders and the dock go back to the default layout. Widgets, hidden apps and settings are kept.") },
            confirmButton = {
                TextButton(onClick = { store.resetLayout(); act.ensureDefaults(); confirmReset = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB8C7FF))
    Spacer(Modifier.height(6.dp))
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f)) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value > range.first) onChange(value - 1) }, enabled = value > range.first) { Text("−", fontSize = 20.sp) }
        Text("$value", fontWeight = FontWeight.SemiBold, modifier = Modifier.width(28.dp))
        TextButton(onClick = { if (value < range.last) onChange(value + 1) }, enabled = value < range.last) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
private fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(title, modifier = Modifier.weight(1f))
            Text(valueText, color = Color.White.copy(alpha = 0.6f))
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
