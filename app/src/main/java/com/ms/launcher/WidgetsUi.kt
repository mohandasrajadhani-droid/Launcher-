package com.ms.launcher

import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Left-most page: a scrollable stack of real Android widgets. */
@Composable
fun WidgetsPage(act: MainActivity, onAdd: () -> Unit) {
    val store = act.store
    var editing by remember { mutableStateOf(false) }
    val widthDp = LocalConfiguration.current.screenWidthDp - 24

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Widgets", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            if (store.widgets.isNotEmpty()) {
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done" else "Edit") }
            }
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Add")
            }
        }
        if (store.widgets.isEmpty()) {
            Text(
                "Tap Add to place widgets here — weather, calendar, notes, music, clock and more.\n\n" +
                    "You can also long-press an empty spot on any home page and choose Widgets.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
        store.widgets.forEach { w ->
            key(w.id) {
                val info = remember(w.id) { act.awm.getAppWidgetInfo(w.id) }
                Box(
                    Modifier.fillMaxWidth().height(w.heightDp.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.15f))
                ) {
                    if (info != null) {
                        AndroidView(
                            factory = { c ->
                                act.widgetHost.createView(c, w.id, info).apply {
                                    @Suppress("DEPRECATION")
                                    try { updateAppWidgetSize(Bundle(), widthDp, w.heightDp, widthDp, w.heightDp) } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("Widget no longer available", color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.Center))
                    }
                }
                if (editing || info == null) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { store.moveWidget(w.id, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { store.moveWidget(w.id, 1) }) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                        TextButton(onClick = { store.setWidgetHeight(w.id, w.heightDp - 40) }) { Text("Smaller") }
                        TextButton(onClick = { store.setWidgetHeight(w.id, w.heightDp + 40) }) { Text("Taller") }
                        IconButton(onClick = { act.removeWidget(w.id) }) { Icon(Icons.Filled.Delete, "Remove", tint = Color(0xFFEF5350)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(act: MainActivity, onDismiss: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val groups = remember {
        act.awm.installedProviders
            .groupBy { it.provider.packageName }
            .map { (pkg, list) ->
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                Triple(pkg, label, list)
            }
            .sortedBy { it.second.lowercase() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Add a widget", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxHeight(0.85f)) {
            groups.forEach { (pkg, label, list) ->
                item(key = "h_$pkg") {
                    Text(label, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
                }
                items(list, key = { it.provider.flattenToString() }) { info -> WidgetChoice(info, onPick) }
            }
        }
    }
}

@Composable
private fun WidgetChoice(info: AppWidgetProviderInfo, onPick: (AppWidgetProviderInfo) -> Unit) {
    val ctx = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            try {
                (info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0))?.let { d ->
                    val w = if (d.intrinsicWidth > 0) d.intrinsicWidth.coerceAtMost(600) else 200
                    val h = if (d.intrinsicHeight > 0) d.intrinsicHeight.coerceAtMost(600) else 200
                    d.toBitmap(w, h).asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }
    val dens = ctx.resources.displayMetrics.density
    val cellsW = ((info.minWidth / dens + 30) / 70).toInt().coerceAtLeast(1)
    val cellsH = ((info.minHeight / dens + 30) / 70).toInt().coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().clickable { onPick(info) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 104.dp, height = 76.dp).clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            preview?.let {
                Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(info.loadLabel(ctx.packageManager) ?: "Widget", fontSize = 15.sp)
            Text("$cellsW × $cellsH", fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
        }
    }
}
