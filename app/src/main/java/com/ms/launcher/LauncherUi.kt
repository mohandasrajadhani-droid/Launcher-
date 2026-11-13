package com.ms.launcher

import android.content.Intent
import android.os.BatteryManager
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LauncherRoot(act: MainActivity) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val store = act.store
    val repo = act.repo
    val s = store.settings
    val apps = repo.apps
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val dotCounts by Dots.counts.collectAsState()
    val dots: (String) -> Int = { pkg -> if (s.notificationDots) dotCounts[pkg] ?: 0 else 0 }

    var wallpaperId by remember { mutableStateOf(WallpaperPrefs.get(ctx)) }
    var drawerOpen by remember { mutableStateOf(false) }
    var drawerSearchFocus by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var homeOptions by remember { mutableStateOf(false) }
    var widgetPicker by remember { mutableStateOf(false) }
    var wallpaperSheet by remember { mutableStateOf(false) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var renameKey by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    // ---- drag & drop state ----
    var drag by remember { mutableStateOf<Placed?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragActive by remember { mutableStateOf(false) }
    val gridBounds = remember { mutableStateMapOf<Int, Rect>() }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    var removeZone by remember { mutableStateOf(Rect.Zero) }
    var uninstallZone by remember { mutableStateOf(Rect.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val dragThreshold = with(density) { 12.dp.toPx() }

    // Page 0 = widgets, pages 1..n = home pages
    val pagerState = rememberPagerState(initialPage = 1) { store.settings.pages + 1 }

    fun closeAll() {
        drawerOpen = false; settingsOpen = false; openFolderId = null; homeOptions = false
        widgetPicker = false; wallpaperSheet = false; menuFor = null
    }

    LaunchedEffect(act.homeTick) {
        if (act.homeTick == 0) return@LaunchedEffect
        val anyOpen = drawerOpen || settingsOpen || openFolderId != null
        closeAll()
        if (!anyOpen) pagerState.animateScrollToPage(1)
    }
    LaunchedEffect(store.placed, repo.loaded) {
        repo.loadShortcutIcons(store.placed.mapNotNull { it.item as? HomeItem.Shortcut })
    }
    LaunchedEffect(repo.loaded) { if (repo.loaded) act.ensureDefaults() }

    BackHandler {
        when {
            settingsOpen -> settingsOpen = false
            drawerOpen -> drawerOpen = false
            openFolderId != null -> openFolderId = null
            else -> scope.launch { pagerState.animateScrollToPage(1) }
        }
    }

    fun launchApp(app: AppEntry) { repo.launch(app); store.recordLaunch(app.key) }

    fun openItem(item: HomeItem) {
        when (item) {
            is HomeItem.App -> appsByKey[item.appKey]?.let { launchApp(it) }
                ?: Toast.makeText(ctx, "App not available", Toast.LENGTH_SHORT).show()
            is HomeItem.Folder -> openFolderId = item.id
            is HomeItem.Shortcut -> repo.startPinned(item)
        }
    }

    fun visible(pl: Placed): Boolean = when (val i = pl.item) {
        is HomeItem.App -> !repo.loaded || appsByKey.containsKey(i.appKey)
        is HomeItem.Folder -> !repo.loaded || i.apps.any { appsByKey.containsKey(it) }
        is HomeItem.Shortcut -> true
    }

    fun targetCell(pos: Offset): Pair<Int, Int>? {
        val st = store.settings
        if (st.showDock && dockBounds.contains(pos)) {
            val slot = ((pos.x - dockBounds.left) / (dockBounds.width / st.dockSlots)).toInt()
            return DOCK to slot.coerceIn(0, st.dockSlots - 1)
        }
        val page = pagerState.currentPage - 1
        val b = gridBounds[page] ?: return null
        if (page < 0 || !b.contains(pos)) return null
        val col = ((pos.x - b.left) / (b.width / st.columns)).toInt().coerceIn(0, st.columns - 1)
        val row = ((pos.y - b.top) / (b.height / st.rows)).toInt().coerceIn(0, st.rows - 1)
        return page to row * st.columns + col
    }

    fun doDrop() {
        val d = drag ?: return
        val pos = dragPos
        if (dragActive) {
            when {
                removeZone.contains(pos) -> {
                    (d.item as? HomeItem.Shortcut)?.let { repo.unpin(it) }
                    store.remove(d.item.id)
                }
                uninstallZone.contains(pos) ->
                    (d.item as? HomeItem.App)?.let { appsByKey[it.appKey] }?.let { repo.uninstall(it) }
                else -> targetCell(pos)?.let { (p, c) -> store.move(d.item.id, p, c) }
            }
        }
        drag = null
        dragActive = false
    }

    // Hold a dragged icon at the screen edge to flip pages (adds a new page past the last one)
    LaunchedEffect(dragActive) {
        while (dragActive) {
            delay(600)
            if (!dragActive) break
            val edge = with(density) { 28.dp.toPx() }
            val cur = pagerState.currentPage
            if (dragPos.x < edge && cur > 1) pagerState.animateScrollToPage(cur - 1)
            else if (dragPos.x > rootSize.width - edge && cur >= 1) {
                if (cur == store.settings.pages) store.addPage()
                pagerState.animateScrollToPage(cur + 1)
            }
        }
    }

    // Wallpaper
    val selectedWp = BUILT_IN_WALLPAPERS.firstOrNull { it.id == wallpaperId }
    val bg by produceState<ImageBitmap?>(null, selectedWp) {
        value = selectedWp?.let { w -> withContext(Dispatchers.IO) { loadWallpaperBitmap(ctx, w.res, 1) } }
    }

    /** One icon on the grid or dock, with tap / hold / drag behaviour. */
    val itemCell: @Composable (Placed, Modifier, Boolean) -> Unit = { pl, mod, showLabel ->
        val item = pl.item
        var origin by remember(item.id) { mutableStateOf(Offset.Zero) }
        val isDragged = drag?.item?.id == item.id && dragActive
        Box(
            mod.onGloballyPositioned { origin = it.positionInRoot() }
                .graphicsLayer { alpha = if (isDragged) 0.25f else 1f }
                .homeItemGestures(
                    key = item.id,
                    onTap = { openItem(item) },
                    onLongPress = { p ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuFor = item.id
                        if (!store.settings.lockLayout) {
                            drag = pl; dragStart = origin + p; dragPos = dragStart; dragActive = false
                            removeZone = Rect.Zero; uninstallZone = Rect.Zero
                        }
                    },
                    onDragMove = { d ->
                        if (drag != null) {
                            dragPos += d
                            if (!dragActive && (dragPos - dragStart).getDistance() > dragThreshold) {
                                dragActive = true; menuFor = null
                            }
                        }
                    },
                    onRelease = { doDrop() },
                    onCancel = { drag = null; dragActive = false },
                ),
            contentAlignment = Alignment.Center
        ) {
            ItemView(item, appsByKey, store, repo.shortcutIcons, dots, showLabel)
            DropdownMenu(expanded = menuFor == item.id, onDismissRequest = { menuFor = null }) {
                HomeItemMenu(item, act, appsByKey, onDismiss = { menuFor = null },
                    onRename = { renameKey = it }, onOpenFolder = { openFolderId = it })
            }
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
        bg?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (selectedWp == null) maxOf(s.dim, 0.2f) else s.dim)))

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .pointerInput(s.swipeDownNotifications) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -120f) { drawerSearchFocus = false; drawerOpen = true }
                            else if (total > 120f && s.swipeDownNotifications) openNotifications(ctx)
                        },
                        onVerticalDrag = { ch, dy -> total += dy; ch.consume() }
                    )
                }
        ) {
            if (s.showClock) ClockHeader(s.clock24h)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                userScrollEnabled = drag == null,
            ) { p ->
                if (p == 0) {
                    WidgetsPage(act, onAdd = { widgetPicker = true })
                } else {
                    val page = p - 1
                    BoxWithConstraints(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .onGloballyPositioned { gridBounds[page] = it.boundsInRoot() }
                            .pointerInput(s.doubleTapLock) {
                                detectTapGestures(
                                    onDoubleTap = if (s.doubleTapLock) ({ _: Offset -> lockScreen(ctx) }) else null,
                                    onLongPress = { homeOptions = true }
                                )
                            }
                    ) {
                        val cw = maxWidth / s.columns
                        val ch = maxHeight / s.rows
                        // drop target highlight
                        if (dragActive && pagerState.currentPage == p) {
                            targetCell(dragPos)?.takeIf { it.first == page }?.let { (_, c) ->
                                Box(
                                    Modifier.offset(x = cw * (c % s.columns), y = ch * (c / s.columns))
                                        .size(cw, ch).padding(4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                )
                            }
                        }
                        store.placed.filter { it.page == page && it.cell < s.columns * s.rows && visible(it) }
                            .forEach { pl ->
                                key(pl.item.id) {
                                    itemCell(
                                        pl,
                                        Modifier.offset(x = cw * (pl.cell % s.columns), y = ch * (pl.cell / s.columns))
                                            .size(cw, ch),
                                        s.showLabels
                                    )
                                }
                            }
                    }
                }
            }

            PageDots(pagerState.pageCount, pagerState.currentPage)

            if (s.showSearchBar) SearchPill { drawerSearchFocus = true; drawerOpen = true }

            if (s.showDock) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .onGloballyPositioned { dockBounds = it.boundsInRoot() }
                        .height(s.iconSizeDp.dp + 24.dp)
                ) {
                    val cw = maxWidth / s.dockSlots
                    store.placed.filter { it.page == DOCK && it.cell < s.dockSlots && visible(it) }.forEach { pl ->
                        key(pl.item.id) {
                            itemCell(pl, Modifier.offset(x = cw * pl.cell).width(cw).fillMaxHeight(), false)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---- drag overlay: drop zones + floating icon ----
        val d = drag
        if (d != null && dragActive) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DropZone("Remove", Icons.Filled.Close, removeZone.contains(dragPos),
                    Modifier.weight(1f).onGloballyPositioned { removeZone = it.boundsInRoot() })
                if (d.item is HomeItem.App) {
                    DropZone("Uninstall", Icons.Filled.Delete, uninstallZone.contains(dragPos),
                        Modifier.weight(1f).onGloballyPositioned { uninstallZone = it.boundsInRoot() })
                }
            }
            val half = with(density) { (s.iconSizeDp.dp / 2).toPx() }
            Box(
                Modifier
                    .offset { IntOffset((dragPos.x - half).roundToInt(), (dragPos.y - half).roundToInt()) }
                    .graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }
            ) {
                ItemView(d.item, appsByKey, store, repo.shortcutIcons, dots, showLabel = false)
            }
        }

        // ---- overlays ----
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut()
        ) {
            AppDrawer(
                act = act,
                apps = apps.filter { it.key !in store.hidden },
                focusSearch = drawerSearchFocus,
                dots = dots,
                onClose = { drawerOpen = false },
                onLaunch = { launchApp(it) },
                onRename = { renameKey = it },
                onSettings = { settingsOpen = true },
            )
        }

        val folder = openFolderId?.let { id -> store.placed.firstOrNull { it.item.id == id }?.item as? HomeItem.Folder }
        if (folder != null) {
            FolderDialog(folder, act, appsByKey, dots, onDismiss = { openFolderId = null }, onLaunch = { launchApp(it) })
        }

        renameKey?.let { key ->
            val app = appsByKey[key]
            RenameDialog(
                current = store.renames[key] ?: app?.label ?: "",
                onSave = { store.rename(key, it); renameKey = null },
                onDismiss = { renameKey = null }
            )
        }

        if (homeOptions) {
            ModalBottomSheet(onDismissRequest = { homeOptions = false }) {
                Column(Modifier.padding(bottom = 24.dp)) {
                    SheetOption("Wallpapers", Icons.Filled.Face) { homeOptions = false; wallpaperSheet = true }
                    SheetOption("Widgets", Icons.Filled.Star) { homeOptions = false; widgetPicker = true }
                    SheetOption("Add a home page", Icons.Filled.Add) {
                        store.addPage(); homeOptions = false
                        scope.launch { pagerState.animateScrollToPage(store.settings.pages) }
                    }
                    SheetOption("Launcher settings", Icons.Filled.Settings) { homeOptions = false; settingsOpen = true }
                }
            }
        }

        if (wallpaperSheet) {
            ModalBottomSheet(onDismissRequest = { wallpaperSheet = false }) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                    Text("Wallpaper", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    WallpaperPicker(wallpaperId) { wallpaperId = it; WallpaperPrefs.set(ctx, it) }
                }
            }
        }

        if (widgetPicker) {
            WidgetPickerSheet(act, onDismiss = { widgetPicker = false }) { info ->
                widgetPicker = false
                act.startAddWidget(info)
                scope.launch { pagerState.animateScrollToPage(0) }
            }
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            SettingsScreen(
                act = act,
                wallpaperId = wallpaperId,
                onWallpaper = { wallpaperId = it; WallpaperPrefs.set(ctx, it) },
                onClose = { settingsOpen = false }
            )
        }
    }
}

@Composable
fun ClockHeader(h24: Boolean) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        while (true) {
            now = Date()
            battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            charging = bm?.isCharging ?: false
            delay(5_000)
        }
    }
    val time = SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Text(
            time, color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Light,
            modifier = Modifier.clickable {
                try { ctx.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }
        )
        val extra = if (battery in 0..100) "   ·   ${if (charging) "⚡" else ""}$battery%" else ""
        Text(
            date + extra, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp,
            modifier = Modifier.clickable {
                try {
                    ctx.startActivity(
                        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        )
    }
}

@Composable
fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until count) {
            val sel = i == current
            Box(
                Modifier.padding(horizontal = 4.dp)
                    .size(if (sel) 8.dp else 6.dp)
                    .clip(if (i == 0) RoundedCornerShape(2.dp) else CircleShape)
                    .background(Color.White.copy(alpha = if (sel) 0.95f else 0.4f))
            )
        }
    }
}

@Composable
fun SearchPill(onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.width(10.dp))
        Text("Search apps", color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
    }
}

@Composable
fun DropZone(label: String, icon: ImageVector, hot: Boolean, modifier: Modifier) {
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (hot) Color(0xCCE53935) else Color.Black.copy(alpha = 0.55f)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SheetOption(text: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
