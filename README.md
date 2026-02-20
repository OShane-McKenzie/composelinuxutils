# Virdin Compose Linux Utils

A Kotlin/JVM library for **Compose for Desktop** that provides first-class Linux desktop integration, following freedesktop.org standards throughout. Built for developers who want to interact with the Linux desktop environment in a native, spec-compliant way without reinventing the wheel.

---
[![](https://jitpack.io/v/OShane-McKenzie/composelinuxutils.svg)](https://jitpack.io/#OShane-McKenzie/composelinuxutils)
---
## What's included

| Module | Description |
|---|---|
| `XdgIconResolver` | Resolves Linux icon names to painters following the full XDG Icon Theme spec |
| `InstalledAppsProvider` | Parses all `.desktop` files on the system and provides a searchable app list |
| `LinuxPaths` | Named access to every standard XDG base directory and user directory |
| `LinuxRunner` | Runs commands, elevates with pkexec/sudo/su, and launches desktop apps |

---

## Requirements

- Kotlin JVM (Compose for Desktop)
- Linux
- `kotlinx.serialization` (for icon config parsing)

---

## Installation

```kotlin
// Add JitPack repository to your settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
// settings.gradle.kts
// ... your repo config

// build.gradle.kts
dependencies {
    implementation("com.github.OShane-McKenzie:composelinuxutils:1.0.1-ALPHA")
}
```

---

## XdgIconResolver

Resolves icon names to Compose `Painter` objects following the full [XDG Icon Theme Specification](https://specifications.freedesktop.org/icon-theme-spec/latest/), including theme inheritance chains, size matching, hicolor fallback, KDE `ScaledDirectories`, and monochrome SVG recoloring for KDE Plasma icons.

Resolution priority when a `virdin.json` config is present:
```
userIcons → prefIcons → XDG theme → defaultIcons
```

### Basic usage

```kotlin
// Provide a single resolver instance at the root of your composition
val resolver = remember { XdgIconResolver() }

CompositionLocalProvider(LocalIconResolver provides resolver) {
    MyApp()
}
```

### `SystemIcon` composable

Resolves and displays any icon by the raw `Icon=` value from a `.desktop` file, handles both absolute paths and theme icon names automatically.

```kotlin
// Theme icon name
SystemIcon(iconValue = "firefox", size = 48.dp)

// Absolute path
SystemIcon(iconValue = "/usr/share/icons/hicolor/48x48/apps/code.png", size = 48.dp)

// With appId for config-based overrides (see virdin.json below)
SystemIcon(
    iconValue = app.icon ?: "application-x-executable",
    appId     = app.id,
    size      = 48.dp
)

// Monochrome KDE plasma icons are auto-recolored to match your theme.
// Override the recolor color or disable it:
SystemIcon(iconValue = "plasma-discover", recolorMonochrome = Color.White)
SystemIcon(iconValue = "plasma-discover", recolorMonochrome = null) // raw SVG
```

### `rememberSystemIconPainter`

For when you need the `Painter` directly to use in custom composables.

```kotlin
val painter = rememberSystemIconPainter(
    iconValue = "folder-open",
    appId     = "org.kde.dolphin.desktop",
    size      = 64.dp
)
if (painter != null) {
    Image(painter, "Dolphin", Modifier.clip(CircleShape).size(64.dp))
}
```

### Direct resolver use (non-Compose)

```kotlin
val resolver = XdgIconResolver()

val result = resolver.resolveFromDesktopValue(
    iconValue = "firefox",
    iconSize  = 48,
    appId     = "firefox.desktop"
)

when (result) {
    is IconResult.Found    -> println("Found at: ${result.path}")
    is IconResult.NotFound -> println("Not found")
}

// List all installed themes
resolver.availableThemes().forEach { println(it) }

// Clear cache after a theme change
resolver.clearCache()
```

### `virdin.json` icon config

Place at `~/.config/virdin/virdin.json` (user) or `/usr/share/virdin/virdin.json` (system). User config takes precedence.

```json
{
    "prefIcons": [
        { "name": "ark",         "matchType": "exact",    "path": "/usr/share/virdin/icons/archive.png" },
        { "name": "spectacle",   "matchType": "exact",    "path": "/usr/share/virdin/icons/screenshot.png" },
        { "name": "xterm",       "matchType": "contains", "path": "/usr/share/virdin/icons/terminal.png" }
    ],
    "defaultIcons": [
        { "name": "default",     "matchType": "default",  "path": "/usr/share/virdin/icons/default.png" }
    ],
    "userIcons": [
        { "appId": "code.desktop", "matchType": "user",   "path": "/home/user/.local/share/virdin/icons/code.png" }
    ]
}
```

| `matchType` | Behaviour |
|---|---|
| `exact` | Icon name must exactly match |
| `contains` | Either string contains the other |
| `user` | Matched by desktop file ID (`appId`), highest priority |
| `default` | Used as the final fallback if nothing else resolves |

```kotlin
// Force-reload config at runtime
VirdinIconConfig.reload()
resolver.clearCache()
```

---

## InstalledAppsProvider

Scans all XDG application directories and parses `.desktop` files into typed `DesktopApp` objects. Follows the [Desktop Entry Specification](https://specifications.freedesktop.org/desktop-entry/latest/) throughout, deduplication, locale resolution, `OnlyShowIn`/`NotShowIn` filtering, and `TryExec` validation are all handled.

### Get all apps

```kotlin
val provider = remember { InstalledAppsProvider() }
val apps     = remember { provider.getApps() }

// Include apps marked NoDisplay=true
val allApps  = provider.getApps(includeNoDisplay = true)

// Skip TryExec validation (faster, but may include uninstalled entries)
val fast     = provider.getApps(checkTryExec = false)
```

### Search

Ranked by relevance, exact name match scores highest, then name prefix, name contains, generic name, comment, and keywords.

```kotlin
val results = provider.search("text editor")
val results = provider.search("browser", apps = apps)
```

### Filter by category

```kotlin
val audioApps  = provider.filterByCategory(listOf("AudioVideo", "Audio"))
val devTools   = provider.filterByCategory(listOf("Development"))
val categories = provider.allCategories()  // all available categories
```

### CLI apps

Apps where `Terminal=true` in the desktop file, the definitive spec-level indicator.

```kotlin
val cliApps = provider.getCliApps()
```

### The `DesktopApp` data class

Every field maps 1-to-1 to a key in the Desktop Entry Specification.

```kotlin
data class DesktopApp(
    val id: String,                  // "code.desktop"
    val name: String,                // "Visual Studio Code" (locale-resolved)
    val genericName: String?,        // "Text Editor"
    val comment: String?,            // "Code Editing. Redefined."
    val icon: String?,               // raw Icon= value, pass to SystemIcon
    val exec: String?,               // "code %F"
    val isCli: Boolean,              // Terminal=true
    val categories: List<String>,    // ["TextEditor", "Development", "IDE"]
    val keywords: List<String>,      // ["vscode"]
    val mimeTypes: List<String>,     // ["application/x-code-workspace"]
    val actions: List<DesktopAction>,// ["New Empty Window"]
    val noDisplay: Boolean,
    val isUserLocal: Boolean,        // installed to ~/.local/share/applications
    val dbusActivatable: Boolean,
    val desktopFilePath: String,     // "/usr/share/applications/code.desktop"
    // ... and all other standard keys
)
```

### Using with `SystemIcon`

```kotlin
@Composable
fun AppItem(app: DesktopApp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SystemIcon(
            iconValue = app.icon ?: "application-x-executable",
            appId     = app.id,
            size      = 48.dp
        )
        Text(app.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (app.isCli) Badge { Text("CLI") }
    }
}
```

### Diagnose missing apps

```kotlin
// Prints every directory scanned, every parse failure, and why each app was filtered
println(provider.diagnose())
```

---

## LinuxPaths

A single object providing named access to every standard freedesktop.org path on the system. All properties read the appropriate environment variables at access time and apply spec-compliant defaults when they are unset.

### XDG base directories

```kotlin
LinuxPaths.dataHome          // ~/.local/share  (XDG_DATA_HOME)
LinuxPaths.configHome        // ~/.config       (XDG_CONFIG_HOME)
LinuxPaths.cacheHome         // ~/.cache        (XDG_CACHE_HOME)
LinuxPaths.stateHome         // ~/.local/state  (XDG_STATE_HOME)
LinuxPaths.runtimeDir        // /run/user/1000  (XDG_RUNTIME_DIR, nullable)
LinuxPaths.dataDirs          // ["/usr/local/share", "/usr/share"] (XDG_DATA_DIRS)
LinuxPaths.configDirs        // ["/etc/xdg"]    (XDG_CONFIG_DIRS)
LinuxPaths.allDataDirs       // dataHome + dataDirs, full search path
LinuxPaths.allConfigDirs     // configHome + configDirs, full search path
LinuxPaths.userBin           // ~/.local/bin
```

### XDG user directories

Read from `~/.config/user-dirs.dirs`.

```kotlin
LinuxPaths.desktop           // ~/Desktop
LinuxPaths.downloads         // ~/Downloads
LinuxPaths.documents         // ~/Documents
LinuxPaths.music             // ~/Music
LinuxPaths.pictures          // ~/Pictures
LinuxPaths.videos            // ~/Videos
LinuxPaths.templates         // ~/Templates
LinuxPaths.publicShare       // ~/Public
```

### Well-known derived paths

```kotlin
LinuxPaths.userApplications  // ~/.local/share/applications
LinuxPaths.userAutostart     // ~/.config/autostart
LinuxPaths.userIcons         // ~/.local/share/icons
LinuxPaths.userFonts         // ~/.local/share/fonts
LinuxPaths.userThemes        // ~/.local/share/themes
LinuxPaths.userMimeApps      // ~/.config/mimeapps.list
LinuxPaths.userDbusServices  // ~/.local/share/dbus-1/services
LinuxPaths.userSystemdUnits  // ~/.config/systemd/user
LinuxPaths.trash             // ~/.local/share/Trash
LinuxPaths.thumbnails        // ~/.cache/thumbnails
LinuxPaths.recentFiles       // ~/.local/share/recently-used.xbel
LinuxPaths.sshDir            // ~/.ssh
LinuxPaths.gnupgDir          // ~/.gnupg  (or $GNUPGHOME)
LinuxPaths.bashHistory       // ~/.bash_history (or $HISTFILE)
```

### `File` accessors

Every path has a corresponding `File`-returning property:

```kotlin
LinuxPaths.downloadsDir.listFiles()
LinuxPaths.configHomeDir.exists()
LinuxPaths.runtimeDirFile?.let { ... }  // nullable
```

### App-specific helpers

```kotlin
// ~/.config/myapp/settings.json  (creates parent dirs if missing)
val config = LinuxPaths.appConfig("myapp", "settings.json", createIfMissing = true)

// ~/.local/share/myapp/data.db
val data = LinuxPaths.appData("myapp", "data.db")

// ~/.cache/myapp/
val cache = LinuxPaths.appCache("myapp", createIfMissing = true)

// ~/.local/state/myapp/history.log
val state = LinuxPaths.appState("myapp", "history.log")
```

### Search helpers

```kotlin
// First match across all data dirs (user takes priority over system)
LinuxPaths.findData("applications/code.desktop")

// All matches (useful for merging user + system config)
LinuxPaths.findAllConfig("autostart/")

// Debug dump of all resolved paths
println(LinuxPaths.dump())
```

---

## LinuxRunner

Runs system commands and launches desktop applications. All blocking operations are `suspend` functions, call them from a coroutine or `LaunchedEffect`.

### Setup

```kotlin
// pkexec, shows a native PolicyKit GUI dialog (recommended for desktop apps)
val runner = LinuxRunner()

// sudo, password supplied programmatically via stdin
val runner = LinuxRunner(ElevationMethod.Sudo)

// su, uses root password
val runner = LinuxRunner(ElevationMethod.Su)
```

### Running commands

```kotlin
// Shell string (bash -c)
val result = runner.run("ls -la /tmp | grep log")

// Arg list (safer, no injection risk)
val result = runner.run("ls", "-la", "/tmp")

// Elevated, pkexec shows its own GUI dialog, no password needed
val result = runner.runElevated("cp", "-r", "/src", "/dst")

// Elevated with sudo, password supplied via stdin
val result = runner.runElevated(password = "mypassword", "pacman", "-Syu")

// Check result
if (result.success) println(result.output)
else println("Error: ${result.error}")
```

### Package management

Auto-detects pacman / apt / dnf / zypper.

```kotlin
runner.installPackages(password, "firefox", "vlc")
runner.removePackages(password, "vlc")
runner.updateSystem(password)
runner.isPackageInstalled("firefox")   // Boolean, no elevation needed
```

### systemd services

```kotlin
runner.startService("nginx", password)
runner.stopService("nginx", password)
runner.restartService("nginx", password)
runner.enableService("nginx", password)
runner.disableService("nginx", password)
runner.isServiceActive("nginx")        // Boolean
runner.isServiceEnabled("nginx")       // Boolean

// User-level services (no elevation)
runner.startUserService("pipewire")
runner.stopUserService("pipewire")
```

### File operations

```kotlin
runner.copyFile("/src/file", "/dst/file")
runner.moveFile("/src/file", "/dst/file", elevated = true, password = password)
runner.deleteFile("/tmp/junk", elevated = false)
runner.makeDirectory("/opt/myapp", elevated = true, password = password)
runner.changeOwner("/opt/myapp", "user:user", password)
runner.changePermissions("/opt/myapp", "755")
runner.createSymlink("/usr/bin/myapp", "/opt/myapp/bin/myapp", elevated = true, password)
```

### System info

```kotlin
runner.diskUsage()                // df -h
runner.diskUsage("/home")         // df -h /home
runner.memoryUsage()              // free -h
runner.cpuInfo()                  // /proc/cpuinfo
runner.uptime()                   // uptime -p
runner.processes()                // ps aux
runner.systemInfo()               // uname -a
runner.osRelease()                // /etc/os-release
runner.usbDevices()               // lsusb
runner.pciDevices()               // lspci
runner.whoIsLoggedIn()            // who
runner.hostname()
runner.currentUser()              // String, non-suspend
```

### Networking

```kotlin
runner.ping("8.8.8.8", count = 4)
runner.ipAddresses()
runner.networkConnections()
runner.flushDns(password)
```

### Desktop integration

```kotlin
runner.openFile("/home/user/document.pdf")   // xdg-open
runner.openUrl("https://example.com")
runner.openDirectory(LinuxPaths.downloads)

runner.notify(
    summary = "Build complete",
    body    = "Your project compiled successfully",
    icon    = "dialog-information",
    urgency = "normal",
    timeout = 4000
)

runner.copyToClipboard("hello world")   // wl-copy on Wayland, xclip on X11
runner.readClipboard()
```

### Launching desktop apps

`launch()` respects the full Desktop Entry Specification, `Exec=` field codes, `Path=`, `Terminal=`, `DBusActivatable=`, and named actions.

```kotlin
// Basic launch
runner.launch(app)

// With URIs or files (substituted into %u / %f field codes)
runner.launch(app, uris  = listOf("https://example.com"))
runner.launch(app, files = listOf("/home/user/photo.jpg"))

// Launch a named action (e.g. "New Window", "New Private Window")
val newWindow = app.actions.first { it.name == "New Empty Window" }
runner.launchAction(app, newWindow)

// Custom launch options
runner.launch(
    app = app,
    options = LaunchOptions(
        workingDirectory = LinuxPaths.documents,
        environment      = mapOf("MY_VAR" to "value"),
        forceTerminal    = true,          // wrap in terminal even if not a CLI app
        terminalOverride = "alacritty"    // use specific terminal
    )
)
```

---

## Specs implemented

- [XDG Base Directory Specification](https://specifications.freedesktop.org/basedir/latest/)
- [XDG Desktop Entry Specification](https://specifications.freedesktop.org/desktop-entry/latest/)
- [XDG Icon Theme Specification](https://specifications.freedesktop.org/icon-theme-spec/latest/)
- [XDG User Directories](https://www.freedesktop.org/wiki/Software/xdg-user-dirs/)
- [Desktop Menu Specification](https://specifications.freedesktop.org/menu-spec/latest/) (categories)

---

## License

This project is licensed under the GPL v3 License - see the [LICENSE](LICENSE) file for details.
