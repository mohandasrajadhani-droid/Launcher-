package com.ms.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap
)

class MainActivity : ComponentActivity() {

    // Bumped every time the launcher comes back to the front,
    // so newly installed / removed apps show up.
    private var refreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LauncherScreen(
                    refreshKey = refreshKey,
                    loadApps = { loadApps() },
                    onOpen = { openApp(it) },
                    onInfo = { openAppInfo(it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
    }

    private fun loadApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun openApp(app: AppInfo) {
        packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
    }

    private fun openAppInfo(app: AppInfo) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${app.packageName}"))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    refreshKey: Int,
    loadApps: () -> List<AppInfo>,
    onOpen: (AppInfo) -> Unit,
    onInfo: (AppInfo) -> Unit
) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(Date()) }
    var wallpaperId by remember { mutableStateOf(WallpaperPrefs.get(ctx)) }
    var showPicker by remember { mutableStateOf(false) }

    // Home screen must never close on Back
    BackHandler { query = "" }

    LaunchedEffect(refreshKey) {
        apps = withContext(Dispatchers.Default) { loadApps() }
    }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(10_000) }
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    // Load the chosen background (null = use phone's system wallpaper)
    val selected = BUILT_IN_WALLPAPERS.firstOrNull { it.id == wallpaperId }
    val background by produceState<ImageBitmap?>(null, selected) {
        value = selected?.let { w -> withContext(Dispatchers.IO) { loadWallpaperBitmap(ctx, w.res, 1) } }
    }

    Box(Modifier.fillMaxSize()) {
        background?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (selected == null) 0.35f else 0.10f))
                .systemBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Clock + date (long-press to change wallpaper)
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { showPicker = true })
                    .padding(top = 24.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        SimpleDateFormat("hh:mm", Locale.getDefault()).format(now),
                        color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light
                    )
                    Text(
                        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
                        color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp
                    )
                }
                IconButton(onClick = { showPicker = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Wallpapers",
                        tint = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(20.dp))

            // Search
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
                    focusedContainerColor = Color.Black.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            // App grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.combinedClickable(
                            onClick = { onOpen(app); query = "" },
                            onLongClick = { onInfo(app) }
                        )
                    ) {
                        Image(app.icon, contentDescription = app.label, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            app.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            WallpaperPicker(
                currentId = wallpaperId,
                onPick = { id ->
                    wallpaperId = id
                    WallpaperPrefs.set(ctx, id)
                }
            )
        }
    }
}

@Composable
fun WallpaperPicker(currentId: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val thumbs by produceState<Map<String, ImageBitmap>>(emptyMap()) {
        value = withContext(Dispatchers.IO) {
            BUILT_IN_WALLPAPERS.associate { it.id to loadWallpaperBitmap(ctx, it.res, 6) }
        }
    }
    val current = BUILT_IN_WALLPAPERS.firstOrNull { it.id == currentId }

    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        Text("Wallpaper", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(BUILT_IN_WALLPAPERS, key = { it.id }) { w ->
                val isSel = w.id == currentId
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(width = 96.dp, height = 192.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                BorderStroke(if (isSel) 3.dp else 1.dp,
                                    if (isSel) Color.White else Color.White.copy(alpha = 0.25f)),
                                RoundedCornerShape(14.dp)
                            )
                            .background(Color.Black)
                            .clickable { onPick(w.id) }
                    ) {
                        thumbs[w.id]?.let {
                            Image(it, contentDescription = w.name, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(w.name, fontSize = 12.sp)
                }
            }
            item {
                val isSel = currentId == SYSTEM_WALLPAPER
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 96.dp, height = 192.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                BorderStroke(if (isSel) 3.dp else 1.dp,
                                    if (isSel) Color.White else Color.White.copy(alpha = 0.25f)),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onPick(SYSTEM_WALLPAPER) }
                    ) {
                        Text("Phone\nwallpaper", textAlign = TextAlign.Center, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("System", fontSize = 12.sp)
                }
            }
        }

        if (current != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { setLockScreenWallpaper(ctx, current.res) }.isSuccess
                        }
                        Toast.makeText(ctx,
                            if (ok) "Lock screen updated" else "Couldn't set lock screen",
                            Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Also use on lock screen") }
        }
    }
}
