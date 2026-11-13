package com.ms.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.MediaStore
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    lateinit var repo: AppRepository; private set
    lateinit var store: LauncherStore; private set
    lateinit var awm: AppWidgetManager; private set
    lateinit var widgetHost: AppWidgetHost; private set

    /** Bumped when Home is pressed while the launcher is already showing. */
    var homeTick by mutableIntStateOf(0); private set
    /** Bumped every time the launcher comes back to the front. */
    var resumeTick by mutableIntStateOf(0); private set

    private var pendingWidget: Pair<Int, AppWidgetProviderInfo>? = null

    private val pkgCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach { changed(it) }
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach { changed(it) }
    }

    private fun changed(pkg: String) {
        repo.invalidate(pkg)
        lifecycleScope.launch { repo.refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        repo = AppRepository(this)
        store = LauncherStore.get(this)
        awm = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, HOST_ID)
        repo.la.registerCallback(pkgCallback, Handler(Looper.getMainLooper()))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { LauncherRoot(this) }
        }
        lifecycleScope.launch { repo.refresh(); ensureDefaults() }
    }

    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening() } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try { widgetHost.stopListening() } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        lifecycleScope.launch { repo.refresh() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeTick++
    }

    override fun onDestroy() {
        try { repo.la.unregisterCallback(pkgCallback) } catch (_: Exception) {}
        super.onDestroy()
    }

    // ---------------- first run: fill dock + a few home icons ----------------
    fun ensureDefaults() {
        if (store.initialized || repo.apps.isEmpty()) return
        val mine = repo.apps.filter { !it.isWork }
        fun byPkg(vararg p: String) = p.firstNotNullOfOrNull { pk -> mine.firstOrNull { it.packageName == pk } }
        fun byIntent(i: Intent) = try {
            packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?.let { pk -> mine.firstOrNull { it.packageName == pk } }
        } catch (e: Exception) { null }

        val dock = listOfNotNull(
            byIntent(Intent(Intent.ACTION_DIAL)) ?: byPkg("com.samsung.android.dialer", "com.google.android.dialer"),
            byPkg(Telephony.Sms.getDefaultSmsPackage(this) ?: "", "com.samsung.android.messaging", "com.google.android.apps.messaging"),
            byPkg("com.whatsapp", "com.whatsapp.w4b"),
            byIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
                ?: byPkg("com.android.chrome", "com.sec.android.app.sbrowser"),
            byIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                ?: byPkg("com.sec.android.app.camera", "com.google.android.GoogleCamera"),
        ).distinctBy { it.key }.take(store.settings.dockSlots)

        val home = listOfNotNull(
            byPkg("com.sec.android.gallery3d", "com.google.android.apps.photos"),
            byPkg("com.android.vending"),
            byPkg("com.google.android.youtube"),
            byPkg("com.google.android.gm"),
            byPkg("com.google.android.apps.maps"),
            byPkg("com.samsung.android.calendar", "com.google.android.calendar"),
            byPkg("com.sec.android.app.popupcalculator", "com.google.android.calculator"),
            byPkg("com.android.settings"),
        ).filter { h -> dock.none { it.key == h.key } }.distinctBy { it.key }

        dock.forEachIndexed { i, a -> store.placeAt(HomeItem.App(newId(), a.key), DOCK, i) }
        val s = store.settings
        val start = (s.rows - 2).coerceAtLeast(0) * s.columns
        home.take(2 * s.columns).forEachIndexed { i, a -> store.placeAt(HomeItem.App(newId(), a.key), 0, start + i) }
        store.markInitialized()
    }

    // ---------------- widgets ----------------
    fun startAddWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidget = id to info
        val bound = try { awm.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) } catch (e: Exception) { false }
        if (bound) configureWidget(id, info)
        else {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
                REQ_BIND
            )
        }
    }

    private fun configureWidget(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
                return
            } catch (_: Exception) {}
        }
        finishWidget(id, info)
    }

    private fun finishWidget(id: Int, info: AppWidgetProviderInfo) {
        val h = (info.minHeight / resources.displayMetrics.density).toInt().coerceAtLeast(110) + 16
        store.addWidget(id, h)
        pendingWidget = null
    }

    @Deprecated("Uses the platform widget flow")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val (id, info) = pendingWidget ?: return
        val ok = resultCode == RESULT_OK
        when (requestCode) {
            REQ_BIND -> if (ok) configureWidget(id, info) else dropPending(id)
            REQ_CONFIGURE -> if (ok) finishWidget(id, info) else dropPending(id)
        }
    }

    private fun dropPending(id: Int) {
        try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
        pendingWidget = null
    }

    fun removeWidget(id: Int) {
        try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
        store.removeWidget(id)
    }

    companion object {
        const val HOST_ID = 0x4D53
        const val REQ_BIND = 11
        const val REQ_CONFIGURE = 12
    }
}
