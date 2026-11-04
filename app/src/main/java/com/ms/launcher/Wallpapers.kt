package com.ms.launcher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

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
        WallpaperManager.getInstance(ctx)
            .setStream(stream, null, true, WallpaperManager.FLAG_LOCK)
    }
}
