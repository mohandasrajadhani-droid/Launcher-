# MS Launcher

A simple Android home-screen launcher (Kotlin + Jetpack Compose), built automatically by GitHub Actions — no Android Studio needed.

## Features
- Replaces the phone's Home screen
- Clock and date
- Search box to filter apps
- 4-column app grid, sorted A–Z, shows the phone wallpaper behind it
- Tap to open an app, long-press for App Info (uninstall, permissions)
- Refreshes the app list whenever you return Home
- 6 built-in black & white wallpapers (silk waves, bubbles, particles, eclipse leaf, lotus) — tap the ⚙ icon or long-press the clock to switch; option to use the phone's own wallpaper, or also apply to the lock screen

To add more wallpapers: put a portrait `.jpg` (about 1080×2340) in `app/src/main/res/drawable-nodpi/` with a lowercase name like `wp_myname.jpg`, then add one line to `BUILT_IN_WALLPAPERS` in `Wallpapers.kt`.

## Get the APK
1. Push this folder to a GitHub repository (branch `main`).
2. Open the repo's **Actions** tab → **Build APK** → wait for the green tick (about 3–5 minutes).
3. Open the finished run → **Artifacts** → download **MSLauncher-apk** (a zip containing `app-debug.apk`).
4. Unzip on your phone and install the APK (allow "Install unknown apps" when asked).

You can also start a build manually: Actions → Build APK → **Run workflow**.

## Set it as your home screen
Press the Home button and pick **MS Launcher → Always**, or go to
Settings → Apps → Default apps → Home app → MS Launcher.

To go back, choose your old launcher in the same setting.

## Project layout
```
.github/workflows/build.yml   GitHub Actions build
app/src/main/AndroidManifest.xml   HOME intent filter + package visibility
app/src/main/java/com/ms/launcher/MainActivity.kt   all launcher code
app/src/main/res/values/themes.xml   transparent wallpaper theme
```
