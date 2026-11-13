package com.ms.launcher

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

val LabelStyle = TextStyle(
    color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1.5f), 4f)
)

fun letterOf(label: String): String {
    val c = label.trim().firstOrNull() ?: return "#"
    return if (c.isLetter()) c.uppercaseChar().toString() else "#"
}

/** Icon + optional label + optional notification count bubble. */
@Composable
fun AppIconView(
    icon: ImageBitmap?, label: String, iconSize: Dp, showLabel: Boolean, dot: Int,
    modifier: Modifier = Modifier, folderIcons: List<ImageBitmap>? = null,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(iconSize)) {
            if (folderIcons != null) FolderIcon(folderIcons, iconSize)
            else if (icon != null) Image(icon, contentDescription = label, modifier = Modifier.fillMaxSize())
            if (dot > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(18.dp)
                        .clip(CircleShape).background(Color(0xFFE53935)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (dot > 9) "9+" else "$dot", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(label, style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp))
        }
    }
}

@Composable
fun FolderIcon(icons: List<ImageBitmap>, size: Dp) {
    val mini = (size - 14.dp) / 2
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color.White.copy(alpha = 0.22f)).padding(5.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 2) {
                    val ic = icons.getOrNull(r * 2 + c)
                    Box(Modifier.size(mini)) { if (ic != null) Image(ic, null, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

/** Draws any home item (app, folder, pinned shortcut). */
@Composable
fun ItemView(
    item: HomeItem, appsByKey: Map<String, AppEntry>, store: LauncherStore,
    shortcutIcons: Map<String, ImageBitmap>, dots: (String) -> Int, showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val s = store.settings
    val size = s.iconSizeDp.dp
    when (item) {
        is HomeItem.App -> {
            val a = appsByKey[item.appKey]
            AppIconView(a?.icon, store.renames[item.appKey] ?: a?.label ?: "", size, showLabel,
                a?.let { dots(it.packageName) } ?: 0, modifier)
        }
        is HomeItem.Folder -> {
            val apps = item.apps.mapNotNull { appsByKey[it] }
            AppIconView(null, item.name, size, showLabel, apps.sumOf { dots(it.packageName) }, modifier,
                folderIcons = apps.map { it.icon })
        }
        is HomeItem.Shortcut -> {
            val icon = shortcutIcons[item.id] ?: appsByKey.values.firstOrNull { it.packageName == item.pkg }?.icon
            AppIconView(icon, item.label, size, showLabel, 0, modifier)
        }
    }
}

/**
 * Tap = open. Hold = menu (+ start drag). Hold and move = drag to a new place.
 * Short swipes still pass through to the page swiping / swipe-up gestures.
 */
@Composable
fun Modifier.homeItemGestures(
    key: Any,
    onTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val move by rememberUpdatedState(onDragMove)
    val release by rememberUpdatedState(onRelease)
    val cancel by rememberUpdatedState(onCancel)
    return this.pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val res: Any? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation() ?: Unit
            }
            if (res != null) {
                if (res is PointerInputChange) { res.consume(); tap() }
                return@awaitEachGesture
            }
            long(down.position)
            val ended = drag(down.id) { ch -> ch.consume(); move(ch.positionChange()) }
            if (ended) release() else cancel()
        }
    }
}

@Composable
fun MenuRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

/** App shortcuts (e.g. "New chat", "Scan QR") shown at the top of a long-press menu. */
@Composable
fun ShortcutItems(app: AppEntry, act: MainActivity, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val repo = act.repo
    val shortcuts = remember(app.key) { repo.shortcutsFor(app) }
    shortcuts.forEach { si ->
        val icon = remember(si.id) { repo.shortcutIcon(si) }
        DropdownMenuItem(
            text = { Text(si.shortLabel?.toString() ?: si.id, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { icon?.let { Image(it, null, Modifier.size(24.dp)) } },
            trailingIcon = {
                IconButton(onClick = {
                    if (repo.pin(si)) {
                        act.store.addToHome(
                            HomeItem.Shortcut(newId(), si.`package`, si.id, repo.userSerial(si.userHandle),
                                si.shortLabel?.toString() ?: "")
                        )
                        Toast.makeText(ctx, "Shortcut added to home screen", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }) { Icon(Icons.Filled.Add, "Add to home screen") }
            },
            onClick = { repo.startShortcut(si); onDismiss() }
        )
    }
    if (shortcuts.isNotEmpty()) HorizontalDivider()
}

/** Long-press menu for an item on the home screen / dock. */
@Composable
fun HomeItemMenu(
    item: HomeItem, act: MainActivity, appsByKey: Map<String, AppEntry>,
    onDismiss: () -> Unit, onRename: (String) -> Unit, onOpenFolder: (String) -> Unit,
) {
    val store = act.store
    val repo = act.repo
    when (item) {
        is HomeItem.App -> {
            val app = appsByKey[item.appKey]
            if (app != null) ShortcutItems(app, act, onDismiss)
            MenuRow("Remove from home", Icons.Filled.Close) { store.remove(item.id); onDismiss() }
            if (app != null) {
                MenuRow("Rename", Icons.Filled.Edit) { onRename(app.key); onDismiss() }
                MenuRow("App info", Icons.Filled.Info) { repo.openInfo(app); onDismiss() }
                MenuRow("Uninstall", Icons.Filled.Delete) { repo.uninstall(app); onDismiss() }
            }
        }
        is HomeItem.Folder -> {
            MenuRow("Open & rename", Icons.Filled.Edit) { onOpenFolder(item.id); onDismiss() }
            MenuRow("Ungroup folder", Icons.Filled.Refresh) { store.ungroup(item.id); onDismiss() }
            MenuRow("Remove folder", Icons.Filled.Close) { store.remove(item.id); onDismiss() }
        }
        is HomeItem.Shortcut -> {
            MenuRow("Remove", Icons.Filled.Close) { repo.unpin(item); store.remove(item.id); onDismiss() }
        }
    }
}

/** Long-press menu for an app in the app drawer. */
@Composable
fun DrawerAppMenu(app: AppEntry, act: MainActivity, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val ctx = LocalContext.current
    val store = act.store
    ShortcutItems(app, act, onDismiss)
    MenuRow("Add to home screen", Icons.Filled.Home) {
        store.addToHome(HomeItem.App(newId(), app.key))
        Toast.makeText(ctx, "Added to home screen", Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    MenuRow("Add to dock", Icons.Filled.Add) {
        if (!store.addToDock(HomeItem.App(newId(), app.key)))
            Toast.makeText(ctx, "Dock is full – remove an icon first", Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    MenuRow("Rename", Icons.Filled.Edit) { onRename(app.key); onDismiss() }
    MenuRow("Hide app", Icons.Filled.Lock) {
        store.hide(app.key)
        Toast.makeText(ctx, "Hidden. Unhide in Launcher settings → Hidden apps", Toast.LENGTH_LONG).show()
        onDismiss()
    }
    MenuRow("App info", Icons.Filled.Info) { act.repo.openInfo(app); onDismiss() }
    MenuRow("Uninstall", Icons.Filled.Delete) { act.repo.uninstall(app); onDismiss() }
}

@Composable
fun RenameDialog(current: String, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(text.trim().ifBlank { null }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onSave(null) }) { Text("Reset to original") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderDialog(
    folder: HomeItem.Folder, act: MainActivity, appsByKey: Map<String, AppEntry>,
    dots: (String) -> Int, onDismiss: () -> Unit, onLaunch: (AppEntry) -> Unit,
) {
    val store = act.store
    val haptic = LocalHapticFeedback.current
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val apps = folder.apps.mapNotNull { appsByKey[it] }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xF21C1C20)) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; store.renameFolder(folder.id, it) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 18.sp, textAlign = TextAlign.Center, color = Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDismiss() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.heightIn(max = 440.dp)
                ) {
                    items(apps, key = { it.key }) { a ->
                        Box {
                            AppIconView(
                                a.icon, store.renames[a.key] ?: a.label, 50.dp, true, dots(a.packageName),
                                Modifier.combinedClickable(
                                    onClick = { onLaunch(a); onDismiss() },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuFor = a.key
                                    }
                                )
                            )
                            DropdownMenu(expanded = menuFor == a.key, onDismissRequest = { menuFor = null }) {
                                MenuRow("Move to home screen", Icons.Filled.Home) {
                                    store.removeFromFolder(folder.id, a.key, toHome = true); menuFor = null
                                }
                                MenuRow("Remove from folder", Icons.Filled.Close) {
                                    store.removeFromFolder(folder.id, a.key, toHome = false); menuFor = null
                                }
                                MenuRow("App info", Icons.Filled.Info) { act.repo.openInfo(a); menuFor = null }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Tip: drag an app onto this folder on the home screen to add it.",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}
