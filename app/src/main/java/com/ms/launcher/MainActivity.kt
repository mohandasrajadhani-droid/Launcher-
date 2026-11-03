package com.ms.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(
    refreshKey: Int,
    loadApps: () -> List<AppInfo>,
    onOpen: (AppInfo) -> Unit,
    onInfo: (AppInfo) -> Unit
) {
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(Date()) }

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

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Clock + date
        Spacer(Modifier.height(24.dp))
        Text(
            SimpleDateFormat("hh:mm", Locale.getDefault()).format(now),
            color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light
        )
        Text(
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
            color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp
        )
        Spacer(Modifier.height(20.dp))

        // Search
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
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
