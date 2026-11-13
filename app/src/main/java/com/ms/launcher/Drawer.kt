package com.ms.launcher

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    act: MainActivity,
    apps: List<AppEntry>,
    focusSearch: Boolean,
    dots: (String) -> Int,
    onClose: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onRename: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val store = act.store
    val s = store.settings
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(focusSearch) {
        if (focusSearch) { delay(300); try { focus.requestFocus() } catch (_: Exception) {} }
    }

    fun labelOf(a: AppEntry) = store.renames[a.key] ?: a.label
    val sorted = remember(apps, store.renames) { apps.sortedBy { labelOf(it).lowercase() } }
    val filtered = remember(sorted, query) {
        val q = query.trim()
        if (q.isEmpty()) sorted
        else sorted.filter { labelOf(it).contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
            .sortedBy { !labelOf(it).startsWith(q, ignoreCase = true) }
    }
    val frequent = remember(apps, store.launchCounts) {
        val byKey = apps.associateBy { it.key }
        store.launchCounts.entries.sortedByDescending { it.value }.mapNotNull { byKey[it.key] }.take(s.columns)
    }
    val showFrequent = query.isBlank() && frequent.isNotEmpty()
    val headerItems = if (showFrequent) 2 + frequent.size else 0
    val letters = remember(sorted) { sorted.map { letterOf(labelOf(it)) }.distinct() }

    // Pull down at the top of the list to close the drawer
    val close by rememberUpdatedState(onClose)
    val pull = remember { mutableFloatStateOf(0f) }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) {
                    pull.floatValue += available.y
                    if (pull.floatValue > 220f) { pull.floatValue = 0f; close() }
                } else if (available.y < 0f) pull.floatValue = 0f
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity { pull.floatValue = 0f; return Velocity.Zero }
        }
    }

    @Composable
    fun DrawerItem(a: AppEntry, menuKey: String) {
        Box {
            AppIconView(
                a.icon, labelOf(a), s.iconSizeDp.dp, true, dots(a.packageName),
                Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onLaunch(a); query = "" },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuFor = menuKey
                    }
                )
            )
            DropdownMenu(expanded = menuFor == menuKey, onDismissRequest = { menuFor = null }) {
                DrawerAppMenu(a, act, onDismiss = { menuFor = null }, onRename = onRename)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xF20E0E10))
            .pointerInput(Unit) {} // stop touches falling through to the home screen
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("Search ${apps.size} apps") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                    focusedContainerColor = Color.White.copy(alpha = 0.08f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let { onLaunch(it); query = "" } })
            )
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Launcher settings") }
        }

        Row(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(s.columns),
                state = gridState,
                modifier = Modifier.weight(1f).fillMaxHeight().nestedScroll(nested),
                contentPadding = PaddingValues(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (showFrequent) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Frequently used") }
                    items(frequent, key = { "f_" + it.key }) { a -> DrawerItem(a, "f_" + a.key) }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("All apps") }
                }
                items(filtered, key = { it.key }) { a -> DrawerItem(a, a.key) }
                if (query.isNotBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (filtered.isEmpty()) Text("No apps match “$query”", color = Color.White.copy(alpha = 0.6f))
                            OutlinedButton(onClick = {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW,
                                            Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (_: Exception) {}
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Search the web for “$query”") }
                            OutlinedButton(onClick = {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query)))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) {}
                            }, modifier = Modifier.fillMaxWidth()) { Text("Search Play Store") }
                        }
                    }
                }
            }
            if (query.isBlank() && letters.size > 1) {
                AlphabetBar(letters) { l ->
                    val idx = filtered.indexOfFirst { letterOf(labelOf(it)) == l }
                    if (idx >= 0) scope.launch { gridState.scrollToItem(idx + headerItems) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp))
}

/** A–Z strip on the right: tap or slide to jump. */
@Composable
fun AlphabetBar(letters: List<String>, onLetter: (String) -> Unit) {
    val pick by rememberUpdatedState(onLetter)
    var active by remember { mutableStateOf<String?>(null) }
    var h by remember { mutableIntStateOf(1) }
    fun at(y: Float) {
        val i = (y / h * letters.size).toInt().coerceIn(0, letters.size - 1)
        if (letters[i] != active) { active = letters[i]; pick(letters[i]) }
    }
    Column(
        Modifier
            .width(26.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .onSizeChanged { h = maxOf(1, it.height) }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { at(it.y) },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null }
                ) { ch, _ -> ch.consume(); at(ch.position.y) }
            }
            .pointerInput(letters) { detectTapGestures { at(it.y); active = null } },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach {
            Text(
                it, fontSize = if (it == active) 14.sp else 11.sp,
                fontWeight = if (it == active) FontWeight.Bold else FontWeight.Normal,
                color = if (it == active) Color.White else Color.White.copy(alpha = 0.55f)
            )
        }
    }
}
