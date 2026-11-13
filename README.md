# MS Launcher

A full Android home-screen launcher (Kotlin + Jetpack Compose), built automatically by GitHub Actions — no Android Studio needed.

## Features

**Home screen**
- Multiple home pages with page dots; hold an icon at the screen edge to move it to the next page (a new page is added if needed)
- Hold an icon → menu (app shortcuts, rename, app info, uninstall, remove); keep holding and drag to move it
- Drop an app on another app → creates a folder; drop on a folder → adds to it
- Drag to the top **Remove** / **Uninstall** zones
- Dock (3–6 icons) and a search bar
- Clock, date and battery at the top — tap the time for alarms, the date for the calendar
- Hold an empty spot → Wallpapers, Widgets, Add page, Launcher settings
- First run fills the dock (Phone, Messages, WhatsApp, Browser, Camera) and a few common apps

**App drawer** (swipe up)
- Search box — press Enter to open the top result, or search the web / Play Store
- Frequently used apps row
- A–Z side bar for quick jumping
- Hold an app → app shortcuts, add to home, add to dock, rename, hide, info, uninstall
- Work-profile apps included (with badge)
- Pull down at the top to close

**Widgets** — swipe right from the first home page. Add any Android widget, then Edit to reorder, resize or remove.

**Other**
- App shortcuts (e.g. WhatsApp "New chat"), pinnable to home with the + button
- "Add to home screen" from other apps (Chrome pages, WhatsApp chats, contacts) is supported
- Notification count dots on icons (needs notification access)
- Swipe down → notification panel; double-tap empty space → lock screen (needs the accessibility permission)
- Hide apps, rename apps
- 6 built-in black & white wallpapers or the phone's own wallpaper; darken slider; apply to lock screen
- Settings: grid columns/rows, icon size, labels, clock, 24-hour, search bar, dock, lock layout, pages, reset

## Get the APK
1. Every push to `main` builds automatically (Actions tab → **Build APK**, about 3 minutes).
2. Open the finished run → **Artifacts** → download **MSLauncher-apk** → unzip → install `app-debug.apk`.
3. Updating: install the new APK over the old one — your layout is kept.

## First-time setup
1. Press Home → choose **MS Launcher** → **Always** (or Settings → Apps → Default apps → Home app).
2. Optional — notification dots: Launcher settings → Notification dots → Grant notification access.
3. Optional — double-tap to lock: Launcher settings → Gestures → turn on, then enable MS Launcher in Accessibility.
   On Samsung, if these options are greyed out: Settings → Apps → MS Launcher → ⋮ → **Allow restricted settings**, then try again.

## Adding wallpapers
Put a portrait `.jpg` (about 1080×2340) in `app/src/main/res/drawable-nodpi/` with a lowercase name like `wp_myname.jpg`, then add one line to `BUILT_IN_WALLPAPERS` in `Wallpapers.kt`.

## Code layout
```
MainActivity.kt   activity, first-run defaults, widget binding
LauncherUi.kt     home pages, dock, drag & drop, gestures, clock
Drawer.kt         app drawer, search, A–Z bar
Components.kt     icons, folders, menus, rename/folder dialogs
WidgetsUi.kt      widgets page + widget picker
SettingsUi.kt     settings screen
Apps.kt           installed apps, launching, app shortcuts
Store.kt          saved layout & settings
Services.kt       notification dots, lock service, pinned shortcuts
Wallpapers.kt     built-in wallpapers + picker
```
