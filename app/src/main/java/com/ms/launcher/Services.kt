package com.ms.launcher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
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

/** Live notification counts per package, filled by DotService. */
object Dots {
    val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
}

class DotService : NotificationListenerService() {
    override fun onListenerConnected() = publish()
    override fun onListenerDisconnected() { Dots.counts.value = emptyMap() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    private fun publish() {
        Dots.counts.value = try {
            activeNotifications.orEmpty()
                .filter { it.isClearable && !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
                .groupingBy { it.packageName }
                .eachCount()
        } catch (e: Exception) { emptyMap() }
    }
}

/** Optional accessibility service: used only for double-tap-to-lock and opening notifications. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile var instance: LockService? = null
    }
}

fun lockScreen(ctx: Context) {
    val svc = LockService.instance
    if (svc != null && Build.VERSION.SDK_INT >= 28) {
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        return
    }
    Toast.makeText(ctx, "Turn on MS Launcher in Accessibility settings to use double-tap to lock", Toast.LENGTH_LONG).show()
    openSettings(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
}

@SuppressLint("WrongConstant")
fun openNotifications(ctx: Context) {
    if (LockService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) == true) return
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
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return s.split(':').any { it.startsWith(ctx.packageName + "/") }
}

/** Handles "Add to home screen" requests from other apps (e.g. Chrome, WhatsApp chats). */
class PinShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val la = getSystemService(LauncherApps::class.java)
        val req = try { la.getPinItemRequest(intent) } catch (e: Exception) { null }
        if (req != null && req.isValid && req.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val si = req.shortcutInfo
            if (si != null && req.accept()) {
                val serial = getSystemService(android.os.UserManager::class.java).getSerialNumberForUser(si.userHandle)
                LauncherStore.get(this).addToHome(
                    HomeItem.Shortcut(newId(), si.`package`, si.id, serial, si.shortLabel?.toString() ?: "")
                )
                Toast.makeText(this, "Added to home screen", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
