package com.ms.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** One launchable app (an app can appear twice if it is also in a Work profile). */
data class AppEntry(
    val key: String,              // "package/.Activity#userSerial"
    val packageName: String,
    val component: ComponentName,
    val label: String,
    val user: UserHandle,
    val isWork: Boolean,
    val icon: ImageBitmap,
)

private const val SC_ALL = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED

/** Reads installed apps (all profiles), launches them, and handles app shortcuts. */
class AppRepository(private val ctx: Context) {
    val la: LauncherApps = ctx.getSystemService(LauncherApps::class.java)
    private val um: UserManager = ctx.getSystemService(UserManager::class.java)
    private val density = ctx.resources.displayMetrics.densityDpi
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var shortcutIcons by mutableStateOf<Map<String, ImageBitmap>>(emptyMap()); private set

    suspend fun refresh() {
        apps = withContext(Dispatchers.Default) { loadApps() }
        loaded = true
    }

    fun invalidate(pkg: String) {
        iconCache.keys.removeAll { it.startsWith("$pkg/") }
    }

    private fun loadApps(): List<AppEntry> {
        val me = Process.myUserHandle()
        val out = ArrayList<AppEntry>()
        val profiles = try { la.profiles } catch (e: Exception) { listOf(me) }
        for (user in profiles) {
            val serial = um.getSerialNumberForUser(user)
            val list = try { la.getActivityList(null, user) } catch (e: Exception) { emptyList() }
            for (info in list) {
                val cn = info.componentName
                if (cn.packageName == ctx.packageName) continue
                val key = "${cn.flattenToShortString()}#$serial"
                val icon = iconCache.getOrPut(key) {
                    info.getBadgedIcon(density).toBitmap(144, 144).asImageBitmap()
                }
                out += AppEntry(key, cn.packageName, cn, info.label.toString(), user, user != me, icon)
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    fun userSerial(u: UserHandle): Long = um.getSerialNumberForUser(u)
    private fun userFor(serial: Long): UserHandle? = um.getUserForSerialNumber(serial)

    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun launch(app: AppEntry) {
        try { la.startMainActivity(app.component, app.user, null, null) }
        catch (e: Exception) { toast("Can't open ${app.label}") }
    }

    fun openInfo(app: AppEntry) {
        try { la.startAppDetailsActivity(app.component, app.user, null, null) }
        catch (e: Exception) { toast("Can't open app info") }
    }

    /**
     * Opens the system App info page, where the user taps Uninstall.
     * This avoids the REQUEST_DELETE_PACKAGES permission entirely.
     */
    fun uninstall(app: AppEntry) {
        openInfo(app)
        toast("Tap Uninstall to remove ${app.label}")
    }

    /** App shortcuts only work while MS Launcher is the default home app. */
    fun canUseShortcuts(): Boolean = try { la.hasShortcutHostPermission() } catch (e: Exception) { false }

    fun shortcutsFor(app: AppEntry): List<ShortcutInfo> {
        if (!canUseShortcuts()) return emptyList()
        return try {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setActivity(app.component)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
            (la.getShortcuts(q, app.user) ?: emptyList())
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(5)
        } catch (e: Exception) { emptyList() }
    }

    fun shortcutIcon(si: ShortcutInfo): ImageBitmap? = try {
        la.getShortcutIconDrawable(si, density)?.toBitmap(96, 96)?.asImageBitmap()
    } catch (e: Exception) { null }

    fun startShortcut(si: ShortcutInfo) {
        try { la.startShortcut(si, null, null) } catch (e: Exception) { toast("Shortcut unavailable") }
    }

    fun startPinned(sc: HomeItem.Shortcut) {
        val u = userFor(sc.userSerial) ?: return
        try { la.startShortcut(sc.pkg, sc.shortcutId, null, null, u) }
        catch (e: Exception) { toast("Shortcut unavailable") }
    }

    private fun pinnedIds(pkg: String, user: UserHandle): List<String> = try {
        la.getShortcuts(
            LauncherApps.ShortcutQuery().setPackage(pkg)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), user
        )?.map { it.id } ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun pin(si: ShortcutInfo): Boolean = try {
        la.pinShortcuts(si.`package`, (pinnedIds(si.`package`, si.userHandle) + si.id).distinct(), si.userHandle)
        true
    } catch (e: Exception) { false }

    fun unpin(sc: HomeItem.Shortcut) {
        val u = userFor(sc.userSerial) ?: return
        try { la.pinShortcuts(sc.pkg, pinnedIds(sc.pkg, u) - sc.shortcutId, u) } catch (_: Exception) {}
    }

    suspend fun loadShortcutIcons(items: List<HomeItem.Shortcut>) {
        if (items.isEmpty() || !canUseShortcuts()) return
        shortcutIcons = withContext(Dispatchers.Default) {
            items.mapNotNull { sc ->
                val u = userFor(sc.userSerial) ?: return@mapNotNull null
                val si = try {
                    la.getShortcuts(
                        LauncherApps.ShortcutQuery().setPackage(sc.pkg)
                            .setShortcutIds(listOf(sc.shortcutId)).setQueryFlags(SC_ALL), u
                    )?.firstOrNull()
                } catch (e: Exception) { null } ?: return@mapNotNull null
                shortcutIcon(si)?.let { sc.id to it }
            }.toMap()
        }
    }
}
