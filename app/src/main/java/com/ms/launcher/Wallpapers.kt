package com.ms.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Built-in home-screen backgrounds. Add a new .jpg to res/drawable-nodpi and list it here. */
data class Wallpaper(val id: String, val name: String, @DrawableRes val res: Int)

val BUILT_IN_WALLPAPERS = listOf(
    Wallpaper("silk_bubbles", "Silk & Bubbles", R.drawable.wp_silk_bubbles),
    Wallpaper("silk_flow", "Silk Flow", R.drawable.wp_silk_flow),
    Wallpaper("waves_bubbles", "Waves", R.drawable.wp_waves_bubbles),
    Wallpaper("particle_wave", "Particles", R.drawable.wp_particle_wave),
    Wallpaper("eclipse_leaf", "Eclipse Leaf", R.drawable.wp_eclipse_leaf),
    Wallpaper("lotus", "Lotus", R.drawable.wp_lotus),
)

/** "system" = show the phone's own wallpaper behind the launcher. */
const val SYSTEM_WALLPAPER = "system"

object WallpaperPrefs {
    private const val PREFS = "launcher_prefs"
    private const val KEY = "wallpaper_id"

    fun get(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, BUILT_IN_WALLPAPERS.first().id) ?: BUILT_IN_WALLPAPERS.first().id

    fun set(ctx: Context, id: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, id).apply()
    }
}

/** Decode a wallpaper at reduced size. sample=1 for full screen, 6 for thumbnails. */
fun loadWallpaperBitmap(ctx: Context, @DrawableRes res: Int, sample: Int): ImageBitmap {
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeResource(ctx.resources, res, opts).asImageBitmap()
}

/** Also apply the image as the phone's lock screen wallpaper. */
fun setLockScreenWallpaper(ctx: Context, @DrawableRes res: Int) {
    ctx.resources.openRawResource(res).use { stream ->
        WallpaperManager.getInstance(ctx).setStream(stream, null, true, WallpaperManager.FLAG_LOCK)
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

    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(BUILT_IN_WALLPAPERS, key = { it.id }) { w ->
                WallpaperTile(w.name, w.id == currentId, { onPick(w.id) }) {
                    thumbs[w.id]?.let {
                        Image(it, contentDescription = w.name, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize())
                    }
                }
            }
            item {
                WallpaperTile("System", currentId == SYSTEM_WALLPAPER, { onPick(SYSTEM_WALLPAPER) }) {
                    Text("Phone\nwallpaper", textAlign = TextAlign.Center, fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        if (current != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { setLockScreenWallpaper(ctx, current.res) }.isSuccess
                        }
                        Toast.makeText(ctx, if (ok) "Lock screen updated" else "Couldn't set lock screen",
                            Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Also use on lock screen") }
        }
    }
}

@Composable
private fun WallpaperTile(
    name: String, selected: Boolean, onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 84.dp, height = 170.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    BorderStroke(if (selected) 3.dp else 1.dp,
                        if (selected) Color.White else Color.White.copy(alpha = 0.25f)),
                    RoundedCornerShape(14.dp)
                )
                .background(Color.Black)
                .clickable(onClick = onClick),
            content = content
        )
        Spacer(Modifier.height(6.dp))
        Text(name, fontSize = 12.sp)
    }
}
