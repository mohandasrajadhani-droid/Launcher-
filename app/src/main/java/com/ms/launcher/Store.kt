package com.ms.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Something placed on a home page or the dock. */
sealed class HomeItem {
    abstract val id: String
    data class App(override val id: String, val appKey: String) : HomeItem()
    data class Folder(override val id: String, val name: String, val apps: List<String>) : HomeItem()
    data class Shortcut(
        override val id: String, val pkg: String, val shortcutId: String,
        val userSerial: Long, val label: String,
    ) : HomeItem()
}

/** page = DOCK means the item sits in the dock; cell = slot index. */
const val DOCK = -1
data class Placed(val item: HomeItem, val page: Int, val cell: Int)

data class WidgetEntry(val id: Int, val heightDp: Int)

data class LauncherSettings(
    val columns: Int = 4,
    val rows: Int = 5,
    val iconSizeDp: Int = 54,
    val showLabels: Boolean = true,
    val showDock: Boolean = true,
    val dockSlots: Int = 5,
    val showClock: Boolean = true,
    val clock24h: Boolean = false,
    val showSearchBar: Boolean = true,
    val dim: Float = 0.10f,
    val swipeDownNotifications: Boolean = true,
    val doubleTapLock: Boolean = false,
    val notificationDots: Boolean = true,
    val lockLayout: Boolean = false,
    val pages: Int = 2,
)

fun newId(): String = UUID.randomUUID().toString().take(8)

/** All launcher data, saved on the phone (SharedPreferences, JSON). */
class LauncherStore private constructor(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("ms_launcher", Context.MODE_PRIVATE)

    var settings by mutableStateOf(LauncherSettings()); private set
    var placed by mutableStateOf<List<Placed>>(emptyList()); private set
    var hidden by mutableStateOf<Set<String>>(emptySet()); private set
    var renames by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var launchCounts by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var widgets by mutableStateOf<List<WidgetEntry>>(emptyList()); private set
    var initialized by mutableStateOf(false); private set

    companion object {
        @Volatile private var inst: LauncherStore? = null
        fun get(ctx: Context): LauncherStore =
            inst ?: synchronized(this) { inst ?: LauncherStore(ctx).also { inst = it } }
    }

    init { load() }

    // ---------- settings ----------
    fun updateSettings(f: (LauncherSettings) -> LauncherSettings) { settings = f(settings); save() }

    fun updateGrid(cols: Int, rows: Int) {
        settings = settings.copy(columns = cols, rows = rows)
        reflow()
    }

    fun setDockSlots(n: Int) {
        settings = settings.copy(dockSlots = n)
        reflow()
    }

    /** Move anything that no longer fits (smaller grid / dock) into free cells. */
    private fun reflow() {
        val cells = settings.columns * settings.rows
        val fits = { p: Placed -> if (p.page == DOCK) p.cell < settings.dockSlots else p.cell < cells }
        val keep = placed.filter(fits).toMutableList()
        var pages = settings.pages
        for (o in placed.filterNot(fits)) {
            var slot: Pair<Int, Int>? = null
            loop@ for (p in 0 until pages) for (c in 0 until cells) {
                if (keep.none { it.page == p && it.cell == c }) { slot = p to c; break@loop }
            }
            if (slot == null) { pages++; slot = (pages - 1) to 0 }
            keep += o.copy(page = slot.first, cell = slot.second)
        }
        settings = settings.copy(pages = pages)
        placed = keep
        save()
    }

    fun markInitialized() { initialized = true; save() }

    // ---------- pages ----------
    fun addPage() = updateSettings { it.copy(pages = it.pages + 1) }

    fun removeEmptyPages() {
        val used = (0 until settings.pages).filter { p -> placed.any { it.page == p } }
        if (used.size == settings.pages) return
        val map = used.withIndex().associate { (newIdx, old) -> old to newIdx }
        placed = placed.map { if (it.page == DOCK) it else it.copy(page = map[it.page] ?: 0) }
        settings = settings.copy(pages = maxOf(1, used.size))
        save()
    }

    // ---------- home items ----------
    fun itemAt(page: Int, cell: Int): Placed? = placed.firstOrNull { it.page == page && it.cell == cell }

    private fun firstFree(startPage: Int): Pair<Int, Int>? {
        val cells = settings.columns * settings.rows
        val order = (startPage until settings.pages) + (0 until startPage)
        for (p in order) for (c in 0 until cells) if (itemAt(p, c) == null) return p to c
        return null
    }

    fun placeAt(item: HomeItem, page: Int, cell: Int): Boolean {
        if (itemAt(page, cell) != null) return false
        placed = placed + Placed(item, page, cell); save(); return true
    }

    fun addToHome(item: HomeItem, startPage: Int = 0) {
        val slot = firstFree(startPage) ?: run {
            settings = settings.copy(pages = settings.pages + 1)
            (settings.pages - 1) to 0
        }
        placed = placed + Placed(item, slot.first, slot.second)
        save()
    }

    fun addToDock(item: HomeItem): Boolean {
        val free = (0 until settings.dockSlots).firstOrNull { itemAt(DOCK, it) == null } ?: return false
        placed = placed + Placed(item, DOCK, free); save(); return true
    }

    fun remove(itemId: String) { placed = placed.filterNot { it.item.id == itemId }; save() }

    /** Drop an item on a cell: move, swap, make a folder, or add into a folder. */
    fun move(itemId: String, page: Int, cell: Int) {
        val src = placed.firstOrNull { it.item.id == itemId } ?: return
        if (src.page == page && src.cell == cell) return
        val target = itemAt(page, cell)
        val s = src.item
        val t = target?.item
        placed = when {
            t == null -> placed.map { if (it.item.id == itemId) it.copy(page = page, cell = cell) else it }
            s is HomeItem.App && t is HomeItem.App ->
                if (s.appKey == t.appKey) placed.filterNot { it.item.id == itemId }
                else placed.filterNot { it.item.id == itemId || it.item.id == t.id } +
                        Placed(HomeItem.Folder(newId(), "Folder", listOf(t.appKey, s.appKey)), page, cell)
            s is HomeItem.App && t is HomeItem.Folder ->
                placed.filterNot { it.item.id == itemId }.map {
                    if (it.item.id == t.id) it.copy(item = t.copy(apps = (t.apps + s.appKey).distinct())) else it
                }
            else -> placed.map {
                when (it.item.id) {
                    itemId -> it.copy(page = page, cell = cell)
                    t.id -> it.copy(page = src.page, cell = src.cell)
                    else -> it
                }
            }
        }
        save()
    }

    fun renameFolder(folderId: String, name: String) {
        placed = placed.map {
            val f = it.item
            if (f is HomeItem.Folder && f.id == folderId) it.copy(item = f.copy(name = name)) else it
        }
        save()
    }

    fun removeFromFolder(folderId: String, appKey: String, toHome: Boolean) {
        val pl = placed.firstOrNull { it.item.id == folderId } ?: return
        val f = pl.item as? HomeItem.Folder ?: return
        val left = f.apps - appKey
        placed = when (left.size) {
            0 -> placed.filterNot { it.item.id == folderId }
            1 -> placed.map { if (it.item.id == folderId) it.copy(item = HomeItem.App(newId(), left[0])) else it }
            else -> placed.map { if (it.item.id == folderId) it.copy(item = f.copy(apps = left)) else it }
        }
        save()
        if (toHome) addToHome(HomeItem.App(newId(), appKey), maxOf(0, pl.page))
    }

    fun ungroup(folderId: String) {
        val pl = placed.firstOrNull { it.item.id == folderId } ?: return
        val f = pl.item as? HomeItem.Folder ?: return
        placed = placed.map {
            if (it.item.id == folderId) it.copy(item = HomeItem.App(newId(), f.apps.first())) else it
        }
        save()
        f.apps.drop(1).forEach { addToHome(HomeItem.App(newId(), it), maxOf(0, pl.page)) }
    }

    fun resetLayout() { placed = emptyList(); settings = settings.copy(pages = 2); initialized = false; save() }

    // ---------- apps ----------
    fun hide(key: String) { hidden = hidden + key; save() }
    fun unhide(key: String) { hidden = hidden - key; save() }
    fun rename(key: String, name: String?) {
        renames = if (name.isNullOrBlank()) renames - key else renames + (key to name)
        save()
    }
    fun recordLaunch(key: String) { launchCounts = launchCounts + (key to ((launchCounts[key] ?: 0) + 1)); save() }

    // ---------- widgets ----------
    fun addWidget(id: Int, heightDp: Int) { widgets = widgets + WidgetEntry(id, heightDp); save() }
    fun removeWidget(id: Int) { widgets = widgets.filterNot { it.id == id }; save() }
    fun setWidgetHeight(id: Int, h: Int) {
        widgets = widgets.map { if (it.id == id) it.copy(heightDp = h.coerceIn(60, 700)) else it }; save()
    }
    fun moveWidget(id: Int, delta: Int) {
        val i = widgets.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in widgets.indices) return
        widgets = widgets.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
        save()
    }

    // ---------- persistence ----------
    private fun itemToJson(i: HomeItem): JSONObject = when (i) {
        is HomeItem.App -> JSONObject().put("t", "app").put("id", i.id).put("k", i.appKey)
        is HomeItem.Folder -> JSONObject().put("t", "folder").put("id", i.id).put("n", i.name).put("a", JSONArray(i.apps))
        is HomeItem.Shortcut -> JSONObject().put("t", "sc").put("id", i.id).put("p", i.pkg)
            .put("s", i.shortcutId).put("u", i.userSerial).put("l", i.label)
    }

    private fun itemFromJson(o: JSONObject): HomeItem? = when (o.optString("t")) {
        "app" -> HomeItem.App(o.getString("id"), o.getString("k"))
        "folder" -> {
            val a = o.getJSONArray("a")
            HomeItem.Folder(o.getString("id"), o.optString("n", "Folder"), List(a.length()) { a.getString(it) })
        }
        "sc" -> HomeItem.Shortcut(o.getString("id"), o.getString("p"), o.getString("s"), o.getLong("u"), o.optString("l"))
        else -> null
    }

    private fun settingsToJson(s: LauncherSettings) = JSONObject()
        .put("columns", s.columns).put("rows", s.rows).put("iconSizeDp", s.iconSizeDp)
        .put("showLabels", s.showLabels).put("showDock", s.showDock).put("dockSlots", s.dockSlots)
        .put("showClock", s.showClock).put("clock24h", s.clock24h).put("showSearchBar", s.showSearchBar)
        .put("dim", s.dim.toDouble()).put("swipeDownNotifications", s.swipeDownNotifications)
        .put("doubleTapLock", s.doubleTapLock).put("notificationDots", s.notificationDots)
        .put("lockLayout", s.lockLayout).put("pages", s.pages)

    private fun settingsFromJson(o: JSONObject): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            columns = o.optInt("columns", d.columns), rows = o.optInt("rows", d.rows),
            iconSizeDp = o.optInt("iconSizeDp", d.iconSizeDp), showLabels = o.optBoolean("showLabels", d.showLabels),
            showDock = o.optBoolean("showDock", d.showDock), dockSlots = o.optInt("dockSlots", d.dockSlots),
            showClock = o.optBoolean("showClock", d.showClock), clock24h = o.optBoolean("clock24h", d.clock24h),
            showSearchBar = o.optBoolean("showSearchBar", d.showSearchBar),
            dim = o.optDouble("dim", d.dim.toDouble()).toFloat(),
            swipeDownNotifications = o.optBoolean("swipeDownNotifications", d.swipeDownNotifications),
            doubleTapLock = o.optBoolean("doubleTapLock", d.doubleTapLock),
            notificationDots = o.optBoolean("notificationDots", d.notificationDots),
            lockLayout = o.optBoolean("lockLayout", d.lockLayout), pages = maxOf(1, o.optInt("pages", d.pages)),
        )
    }

    private fun save() {
        val pl = JSONArray()
        placed.forEach { pl.put(JSONObject().put("i", itemToJson(it.item)).put("p", it.page).put("c", it.cell)) }
        val w = JSONArray()
        widgets.forEach { w.put(JSONObject().put("id", it.id).put("h", it.heightDp)) }
        prefs.edit()
            .putString("settings", settingsToJson(settings).toString())
            .putString("placed", pl.toString())
            .putString("hidden", JSONArray(hidden.toList()).toString())
            .putString("renames", JSONObject(renames as Map<*, *>).toString())
            .putString("counts", JSONObject(launchCounts as Map<*, *>).toString())
            .putString("widgets", w.toString())
            .putBoolean("initialized", initialized)
            .apply()
    }

    private fun load() {
        try {
            prefs.getString("settings", null)?.let { settings = settingsFromJson(JSONObject(it)) }
            prefs.getString("placed", null)?.let { s ->
                val a = JSONArray(s)
                placed = (0 until a.length()).mapNotNull { idx ->
                    val o = a.getJSONObject(idx)
                    itemFromJson(o.getJSONObject("i"))?.let { Placed(it, o.getInt("p"), o.getInt("c")) }
                }
            }
            prefs.getString("hidden", null)?.let { s ->
                val a = JSONArray(s); hidden = (0 until a.length()).map { a.getString(it) }.toSet()
            }
            prefs.getString("renames", null)?.let { s ->
                val o = JSONObject(s); renames = o.keys().asSequence().associateWith { o.getString(it) }
            }
            prefs.getString("counts", null)?.let { s ->
                val o = JSONObject(s); launchCounts = o.keys().asSequence().associateWith { o.getInt(it) }
            }
            prefs.getString("widgets", null)?.let { s ->
                val a = JSONArray(s)
                widgets = (0 until a.length()).map { val o = a.getJSONObject(it); WidgetEntry(o.getInt("id"), o.getInt("h")) }
            }
            initialized = prefs.getBoolean("initialized", false)
        } catch (e: Exception) {
            // Corrupt data: start fresh rather than crash the home screen
        }
    }
}
