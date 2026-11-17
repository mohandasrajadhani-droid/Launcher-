package com.ms.launcher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow

/** Live notification counts per package (package name -> count). Memory only, never saved. */
object Dots {
    val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
}

/**
 * Notification dots.
 * PRIVACY: only the notification key, package name and flags are read. Title, text, extras,
 * people, images and actions are never accessed. Nothing is written to storage, logged or sent.
 * The component is disabled in the manifest and only enabled after the user agrees to the
 * in-app disclosure (see setPrivacyComponents).
 */
class DotService : NotificationListenerService() {
    private val keyToPkg = HashMap<String, String>()

    override fun onListenerConnected() {
        if (!LauncherStore.get(this).settings.notificationDots) {
            requestUnbind(); return
        }
        keyToPkg.clear()
        try {
            activeNotifications?.forEach { if (counts(it)) keyToPkg[it.key] = it.packageName }
        } catch (_: Exception) {}
        publish()
    }

    override fun onListenerDisconnected() {
        keyToPkg.clear()
        Dots.counts.value = emptyMap()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (counts(sbn)) keyToPkg[sbn.key] = sbn.packageName else keyToPkg.remove(sbn.key)
        publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        keyToPkg.remove(sbn.key)
        publish()
    }

    private fun counts(sbn: StatusBarNotification): Boolean =
        sbn.isClearable && !sbn.isOngoing && (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0

    private fun publish() {
        Dots.counts.value = keyToPkg.values.groupingBy { it }.eachCount()
    }
}

/**
 * Optional accessibility service, only declared in the "lock" edition.
 * PRIVACY: used only for performGlobalAction(LOCK_SCREEN / NOTIFICATIONS). It is configured
 * (res/xml/lock_service.xml) to receive no events from other apps and cannot read window content.
 */
class LockService : AccessibilityService() {
    override fun onServiceConnected() {
        if (!BuildConfig.LOCK_FEATURE || !LauncherStore.get(this).settings.doubleTapLock) {
            disableSelf(); return
        }
        instance = this
    }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* intentionally ignored */ }
    override fun onInterrupt() {}

    companion object {
        @Volatile var instance: LockService? = null
    }
}

/** Enable the privacy-sensitive services only while the user has the matching feature turned on. */
fun setPrivacyComponents(ctx: Context, s: LauncherSettings) {
    setComponent(ctx, DotService::class.java, s.notificationDots)
    if (s.notificationDots) {
        try { NotificationListenerService.requestRebind(ComponentName(ctx, DotService::class.java)) } catch (_: Exception) {}
    } else {
        Dots.counts.value = emptyMap()
    }
    if (BuildConfig.LOCK_FEATURE) {
        setComponent(ctx, LockService::class.java, s.doubleTapLock)
        if (!s.doubleTapLock) LockService.instance?.disableSelf()
    }
}

private fun setComponent(ctx: Context, cls: Class<*>, enabled: Boolean) {
    val pm = ctx.packageManager
    val cn = ComponentName(ctx, cls)
    val want = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    try {
        if (pm.getComponentEnabledSetting(cn) != want) pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP)
    } catch (_: Exception) {}
}

fun lockScreen(ctx: Context) {
    val svc = LockService.instance
    if (BuildConfig.LOCK_FEATURE && svc != null && Build.VERSION.SDK_INT >= 28) {
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        return
    }
    Toast.makeText(ctx, "Double-tap to lock is off. Turn it on in Launcher settings → Gestures.", Toast.LENGTH_LONG).show()
}

/**
 * Opens the notification shade. Uses the accessibility service if the user enabled it, otherwise
 * StatusBarManager.expandNotificationsPanel (needs the normal EXPAND_STATUS_BAR permission).
 * That method is not in the public SDK, so it is called defensively and fails silently.
 */
@SuppressLint("WrongConstant")
fun openNotifications(ctx: Context) {
    if (BuildConfig.LOCK_FEATURE &&
        LockService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) == true) return
    try {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    } catch (_: Throwable) {}
}

fun openSettings(ctx: Context, action: String) {
    try { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

fun isDefaultLauncher(ctx: Context): Boolean {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName == ctx.packageName
}

fun isLockServiceEnabled(ctx: Context): Boolean {
    if (!BuildConfig.LOCK_FEATURE) return false
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return s.split(':').any { it.startsWith(ctx.packageName + "/") }
}

/**
 * Handles "Add to home screen" requests from other apps (Chrome pages, WhatsApp chats...).
 * This activity must be exported for the system to reach it, so nothing in the incoming
 * intent is trusted: after accepting, the shortcut is re-read from the system and only a
 * shortcut that Android confirms is pinned is added, using the system's own label.
 */
class PinShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { handle() } catch (_: Exception) {}
        finish()
    }

    private fun handle() {
        if (intent?.action != LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) return
        val la = getSystemService(LauncherApps::class.java)
        if (!la.hasShortcutHostPermission()) return          // only the default home app may accept
        val req = la.getPinItemRequest(intent) ?: return
        if (!req.isValid || req.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return
        val requested = req.shortcutInfo ?: return
        if (!req.accept()) return

        val verified = la.getShortcuts(
            LauncherApps.ShortcutQuery()
                .setPackage(requested.`package`)
                .setShortcutIds(listOf(requested.id))
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED),
            requested.userHandle
        )?.firstOrNull { it.isPinned && it.isEnabled } ?: return

        val serial = getSystemService(android.os.UserManager::class.java).getSerialNumberForUser(verified.userHandle)
        val label = (verified.shortLabel ?: "").toString().trim().take(40)
        LauncherStore.get(this).addToHome(HomeItem.Shortcut(newId(), verified.`package`, verified.id, serial, label))
        Toast.makeText(this, "Added to home screen", Toast.LENGTH_SHORT).show()
    }
}
