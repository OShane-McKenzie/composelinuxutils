package pkg.virdin.composelinuxutils

import java.io.File

/**
 * Provides friendly, named access to every standard freedesktop.org / XDG path
 * on a Linux system — both base directories (XDG Base Directory Specification)
 * and user-facing directories (XDG User Directories / xdg-user-dirs).
 *
 * All properties are lazy and read environment variables at access time,
 * so they reflect the actual runtime environment.
 *
 * Spec references:
 *  - Base dirs:  https://specifications.freedesktop.org/basedir/latest/
 *  - User dirs:  https://www.freedesktop.org/wiki/Software/xdg-user-dirs/
 */
object LinuxPaths {

    val home: String get() = System.getProperty("user.home") ?: "/root"

    // ═══════════════════════════════════════════════════════════════════════
    //  INTERNAL HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /** Reads an env var; returns [default] if unset, empty, or not absolute. */
    private fun xdgEnv(variable: String, default: String): String {
        val value = System.getenv(variable)?.takeIf { it.isNotBlank() && it.startsWith("/") }
        return value ?: default
    }

    /** Reads a colon-separated env var into a list of absolute paths. */
    private fun xdgEnvList(variable: String, default: List<String>): List<String> {
        val value = System.getenv(variable)?.takeIf { it.isNotBlank() } ?: return default
        val paths = value.split(":").filter { it.startsWith("/") }  // spec: ignore relative paths
        return paths.ifEmpty { default }
    }

    /**
     * Parses ~/.config/user-dirs.dirs to get user directory paths.
     * The file is shell-format: XDG_MUSIC_DIR="$HOME/Music"
     * We resolve $HOME substitution manually — no shell eval needed.
     */
    private val userDirs: Map<String, String> by lazy {
        val file = File("$configHome/user-dirs.dirs")
        if (!file.exists()) return@lazy emptyMap()

        val map = mutableMapOf<String, String>()
        file.readLines().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#") || !line.contains("=")) return@forEach
            val (key, value) = line.split("=", limit = 2)
            // Strip surrounding quotes, resolve $HOME
            val path = value.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .replace("\$HOME", home)
                .replace("~", home)
                .trimEnd('/')
            if (path.startsWith("/")) map[key.trim()] = path
        }
        map
    }

    /** Looks up a user dir from the parsed file, falling back to [default]. */
    private fun userDir(key: String, default: String): String =
        userDirs[key] ?: default

    // ═══════════════════════════════════════════════════════════════════════
    //  XDG BASE DIRECTORIES
    //  https://specifications.freedesktop.org/basedir/latest/
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Where user-specific data files should be stored.
     * $XDG_DATA_HOME, default: ~/.local/share
     */
    val dataHome: String get() = xdgEnv("XDG_DATA_HOME", "$home/.local/share")

    /**
     * Where user-specific configuration files should be stored.
     * $XDG_CONFIG_HOME, default: ~/.config
     */
    val configHome: String get() = xdgEnv("XDG_CONFIG_HOME", "$home/.config")

    /**
     * Where user-specific state files should be stored (persists across restarts,
     * but not important enough to be in dataHome). Logs, history, undo stacks etc.
     * $XDG_STATE_HOME, default: ~/.local/state
     */
    val stateHome: String get() = xdgEnv("XDG_STATE_HOME", "$home/.local/state")

    /**
     * Where user-specific non-essential cached data should be stored.
     * $XDG_CACHE_HOME, default: ~/.cache
     */
    val cacheHome: String get() = xdgEnv("XDG_CACHE_HOME", "$home/.cache")

    /**
     * Where user-specific runtime files should live (sockets, pipes, locks).
     * Only valid for the duration of the login session.
     * $XDG_RUNTIME_DIR — no default; may be null if not set.
     */
    val runtimeDir: String? get() =
        System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() && it.startsWith("/") }

    /**
     * System-wide data search path (in addition to [dataHome]).
     * $XDG_DATA_DIRS, default: [/usr/local/share, /usr/share]
     */
    val dataDirs: List<String> get() = xdgEnvList(
        "XDG_DATA_DIRS", listOf("/usr/local/share", "/usr/share")
    )

    /**
     * System-wide config search path (in addition to [configHome]).
     * $XDG_CONFIG_DIRS, default: [/etc/xdg]
     */
    val configDirs: List<String> get() = xdgEnvList(
        "XDG_CONFIG_DIRS", listOf("/etc/xdg")
    )

    /**
     * Full data search path: [dataHome] + [dataDirs], in priority order.
     */
    val allDataDirs: List<String> get() = listOf(dataHome) + dataDirs

    /**
     * Full config search path: [configHome] + [configDirs], in priority order.
     */
    val allConfigDirs: List<String> get() = listOf(configHome) + configDirs

    /**
     * User-specific executable files. Not an official XDG variable but
     * universally adopted. Equivalent of /usr/local/bin for the user.
     */
    val userBin: String get() = "$home/.local/bin"

    // ═══════════════════════════════════════════════════════════════════════
    //  XDG USER DIRECTORIES
    //  Read from ~/.config/user-dirs.dirs (set by xdg-user-dirs-update)
    // ═══════════════════════════════════════════════════════════════════════

    /** The user's desktop directory. Default: ~/Desktop */
    val desktop: String get() = userDir("XDG_DESKTOP_DIR", "$home/Desktop")

    /** The user's downloads directory. Default: ~/Downloads */
    val downloads: String get() = userDir("XDG_DOWNLOAD_DIR", "$home/Downloads")

    /** The user's documents directory. Default: ~/Documents */
    val documents: String get() = userDir("XDG_DOCUMENTS_DIR", "$home/Documents")

    /** The user's music directory. Default: ~/Music */
    val music: String get() = userDir("XDG_MUSIC_DIR", "$home/Music")

    /** The user's pictures directory. Default: ~/Pictures */
    val pictures: String get() = userDir("XDG_PICTURES_DIR", "$home/Pictures")

    /** The user's videos directory. Default: ~/Videos */
    val videos: String get() = userDir("XDG_VIDEOS_DIR", "$home/Videos")

    /** The user's templates directory. Default: ~/Templates */
    val templates: String get() = userDir("XDG_TEMPLATES_DIR", "$home/Templates")

    /** The user's public share directory. Default: ~/Public */
    val publicShare: String get() = userDir("XDG_PUBLICSHARE_DIR", "$home/Public")

    // ═══════════════════════════════════════════════════════════════════════
    //  WELL-KNOWN DERIVED PATHS
    //  Standardized subdirectories under the base dirs above
    // ═══════════════════════════════════════════════════════════════════════

    // ── Applications ─────────────────────────────────────────────────────

    /** User-local .desktop files. Overrides system applications. */
    val userApplications: String get() = "$dataHome/applications"

    /** All application .desktop file directories, in priority order. */
    val allApplicationDirs: List<String> get() = allDataDirs.map { "$it/applications" }

    // ── Autostart ────────────────────────────────────────────────────────

    /** User autostart .desktop files (~/.config/autostart). */
    val userAutostart: String get() = "$configHome/autostart"

    /** All autostart directories, user first then system. */
    val allAutostartDirs: List<String> get() = allConfigDirs.map { "$it/autostart" }

    // ── Icons ────────────────────────────────────────────────────────────

    /** User icon themes directory. */
    val userIcons: String get() = "$dataHome/icons"

    /** Legacy user icon directory, still widely supported. */
    val userIconsLegacy: String get() = "$home/.icons"

    /** All icon theme directories, in XDG priority order. */
    val allIconDirs: List<String> get() =
        listOf(userIcons, userIconsLegacy) + dataDirs.map { "$it/icons" }

    /** Flat fallback icon directory (no theme subdirs). */
    val pixmaps: String get() = "/usr/share/pixmaps"

    // ── Fonts ────────────────────────────────────────────────────────────

    /** User-installed fonts. */
    val userFonts: String get() = "$dataHome/fonts"

    /** All font directories (user + system). */
    val allFontDirs: List<String> get() = listOf(
        userFonts,
        "$home/.fonts",                // legacy, still common
        "/usr/share/fonts",
        "/usr/local/share/fonts",
        "/etc/fonts"
    )

    // ── Menus ────────────────────────────────────────────────────────────

    /** User menu XML files (.menu). */
    val userMenus: String get() = "$configHome/menus"

    /** All menu directories. */
    val allMenuDirs: List<String> get() = allConfigDirs.map { "$it/menus" }

    // ── Themes ───────────────────────────────────────────────────────────

    /** User GTK / desktop themes. */
    val userThemes: String get() = "$dataHome/themes"

    /** Legacy user themes location. */
    val userThemesLegacy: String get() = "$home/.themes"

    /** System themes. */
    val systemThemes: String get() = "/usr/share/themes"

    // ── MIME ─────────────────────────────────────────────────────────────

    /** User MIME type overrides and associations. */
    val userMimeApps: String get() = "$configHome/mimeapps.list"

    /** User MIME type packages directory. */
    val userMimePackages: String get() = "$dataHome/mime/packages"

    /** System MIME database. */
    val systemMimeDatabase: String get() = "/usr/share/mime"

    // ── Cursor themes ─────────────────────────────────────────────────────

    /** User cursor themes. */
    val userCursors: String get() = userIcons // cursors live inside icon dirs

    // ── D-Bus ────────────────────────────────────────────────────────────

    /** User D-Bus service files. */
    val userDbusServices: String get() = "$dataHome/dbus-1/services"

    /** System D-Bus service files. */
    val systemDbusServices: String get() = "/usr/share/dbus-1/services"

    /** User D-Bus session config. */
    val userDbusSession: String get() = "$dataHome/dbus-1/session.d"

    // ── systemd user units ───────────────────────────────────────────────

    /** User systemd unit files. */
    val userSystemdUnits: String get() = "$configHome/systemd/user"

    /** Runtime user systemd units. */
    val runtimeSystemdUnits: String? get() = runtimeDir?.let { "$it/systemd/user" }

    // ── Bash / shell ─────────────────────────────────────────────────────

    /** Bash history file. */
    val bashHistory: String get() = System.getenv("HISTFILE") ?: "$home/.bash_history"

    /** Shell profile. */
    val bashProfile: String get() = "$home/.bash_profile"

    /** Bashrc. */
    val bashrc: String get() = "$home/.bashrc"

    // ── SSH ──────────────────────────────────────────────────────────────

    /** SSH config directory. */
    val sshDir: String get() = "$home/.ssh"

    /** SSH config file. */
    val sshConfig: String get() = "$sshDir/config"

    /** SSH authorized keys. */
    val sshAuthorizedKeys: String get() = "$sshDir/authorized_keys"

    // ── GnuPG ───────────────────────────────────────────────────────────

    /** GnuPG home directory. */
    val gnupgDir: String get() = System.getenv("GNUPGHOME") ?: "$home/.gnupg"

    // ── Trash ────────────────────────────────────────────────────────────

    /** User's XDG trash directory. */
    val trash: String get() = "$dataHome/Trash"

    /** Files inside the trash. */
    val trashFiles: String get() = "$trash/files"

    /** Trash metadata (.trashinfo files). */
    val trashInfo: String get() = "$trash/info"

    // ── Recently used ────────────────────────────────────────────────────

    /**
     * GTK recently used files list (shared between GTK apps).
     * Located in dataHome per XDG state/data conventions.
     */
    val recentFiles: String get() = "$dataHome/recently-used.xbel"

    // ── Thumbnails ───────────────────────────────────────────────────────

    /** Thumbnail cache root. */
    val thumbnails: String get() = "$cacheHome/thumbnails"

    /** Normal (128×128) thumbnails. */
    val thumbnailsNormal: String get() = "$thumbnails/normal"

    /** Large (256×256) thumbnails. */
    val thumbnailsLarge: String get() = "$thumbnails/large"

    /** X-Large (512×512) thumbnails. */
    val thumbnailsXLarge: String get() = "$thumbnails/x-large"

    // ═══════════════════════════════════════════════════════════════════════
    //  SYSTEM-WIDE STANDARD PATHS
    // ═══════════════════════════════════════════════════════════════════════

    /** System application .desktop files. */
    val systemApplications: String get() = "/usr/share/applications"

    /** System-local application .desktop files (distro-local installs). */
    val systemLocalApplications: String get() = "/usr/local/share/applications"

    /** System icon themes. */
    val systemIcons: String get() = "/usr/share/icons"

    /** System fonts. */
    val systemFonts: String get() = "/usr/share/fonts"

    /** System XDG config base (/etc/xdg). */
    val systemConfig: String get() = "/etc/xdg"

    /** System autostart entries. */
    val systemAutostart: String get() = "/etc/xdg/autostart"

    /** System menus. */
    val systemMenus: String get() = "/etc/xdg/menus"

    /** System D-Bus interface definitions. */
    val systemDbusInterfaces: String get() = "/usr/share/dbus-1/interfaces"

    /** System locale data. */
    val systemLocale: String get() = "/usr/share/locale"

    /** System documentation. */
    val systemDoc: String get() = "/usr/share/doc"

    /** System man pages. */
    val systemMan: String get() = "/usr/share/man"

    // ═══════════════════════════════════════════════════════════════════════
    //  FILE ACCESSORS
    //  Convenience methods that return java.io.File instead of String
    // ═══════════════════════════════════════════════════════════════════════

    val dataHomeDir: File            get() = File(dataHome)
    val configHomeDir: File          get() = File(configHome)
    val stateHomeDir: File           get() = File(stateHome)
    val cacheHomeDir: File           get() = File(cacheHome)
    val runtimeDirFile: File?        get() = runtimeDir?.let { File(it) }
    val userBinDir: File             get() = File(userBin)

    val desktopDir: File             get() = File(desktop)
    val downloadsDir: File           get() = File(downloads)
    val documentsDir: File           get() = File(documents)
    val musicDir: File               get() = File(music)
    val picturesDir: File            get() = File(pictures)
    val videosDir: File              get() = File(videos)
    val templatesDir: File           get() = File(templates)
    val publicShareDir: File         get() = File(publicShare)

    val userApplicationsDir: File    get() = File(userApplications)
    val userAutostartDir: File       get() = File(userAutostart)
    val userIconsDir: File           get() = File(userIcons)
    val userFontsDir: File           get() = File(userFonts)
    val userThemesDir: File          get() = File(userThemes)
    val thumbnailsDir: File          get() = File(thumbnails)
    val trashDir: File               get() = File(trash)
    val trashFilesDir: File          get() = File(trashFiles)
    val recentFilesFile: File        get() = File(recentFiles)

    // ═══════════════════════════════════════════════════════════════════════
    //  SEARCH HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Searches all data dirs for a relative path, returning the first match.
     * e.g. findData("applications/firefox.desktop")
     */
    fun findData(relativePath: String): File? =
        allDataDirs.map { File(it, relativePath) }.firstOrNull { it.exists() }

    /**
     * Searches all config dirs for a relative path, returning the first match.
     * e.g. findConfig("mimeapps.list")
     */
    fun findConfig(relativePath: String): File? =
        allConfigDirs.map { File(it, relativePath) }.firstOrNull { it.exists() }

    /**
     * Returns all existing matches for a relative path across all data dirs.
     * Useful when you want to merge results from user + system dirs.
     */
    fun findAllData(relativePath: String): List<File> =
        allDataDirs.map { File(it, relativePath) }.filter { it.exists() }

    /**
     * Returns all existing matches across all config dirs.
     */
    fun findAllConfig(relativePath: String): List<File> =
        allConfigDirs.map { File(it, relativePath) }.filter { it.exists() }

    /**
     * Returns a path inside [dataHome] for the given app name and relative path.
     * Creates parent directories if [createIfMissing] is true.
     *
     * e.g. appData("myapp", "settings.json") → ~/.local/share/myapp/settings.json
     */
    fun appData(appName: String, relativePath: String = "", createIfMissing: Boolean = false): File {
        val dir = File(dataHome, appName)
        if (createIfMissing) dir.mkdirs()
        return if (relativePath.isBlank()) dir else File(dir, relativePath)
    }

    /**
     * Returns a path inside [configHome] for the given app name and relative path.
     * Creates parent directories if [createIfMissing] is true.
     *
     * e.g. appConfig("myapp", "config.json") → ~/.config/myapp/config.json
     */
    fun appConfig(appName: String, relativePath: String = "", createIfMissing: Boolean = false): File {
        val dir = File(configHome, appName)
        if (createIfMissing) dir.mkdirs()
        return if (relativePath.isBlank()) dir else File(dir, relativePath)
    }

    /**
     * Returns a path inside [cacheHome] for the given app name and relative path.
     * Creates parent directories if [createIfMissing] is true.
     *
     * e.g. appCache("myapp", "thumbnails/") → ~/.cache/myapp/thumbnails/
     */
    fun appCache(appName: String, relativePath: String = "", createIfMissing: Boolean = false): File {
        val dir = File(cacheHome, appName)
        if (createIfMissing) dir.mkdirs()
        return if (relativePath.isBlank()) dir else File(dir, relativePath)
    }

    /**
     * Returns a path inside [stateHome] for the given app name and relative path.
     *
     * e.g. appState("myapp", "history.log") → ~/.local/state/myapp/history.log
     */
    fun appState(appName: String, relativePath: String = "", createIfMissing: Boolean = false): File {
        val dir = File(stateHome, appName)
        if (createIfMissing) dir.mkdirs()
        return if (relativePath.isBlank()) dir else File(dir, relativePath)
    }

    /**
     * Dumps all resolved paths as a formatted string — useful for debugging.
     */
    fun dump(): String = buildString {
        appendLine("══ XDG Base Directories ══════════════════════")
        appendLine("dataHome          = $dataHome")
        appendLine("configHome        = $configHome")
        appendLine("stateHome         = $stateHome")
        appendLine("cacheHome         = $cacheHome")
        appendLine("runtimeDir        = ${runtimeDir ?: "(not set)"}")
        appendLine("userBin           = $userBin")
        appendLine("dataDirs          = ${dataDirs.joinToString(":")}")
        appendLine("configDirs        = ${configDirs.joinToString(":")}")
        appendLine()
        appendLine("══ XDG User Directories ══════════════════════")
        appendLine("desktop           = $desktop")
        appendLine("downloads         = $downloads")
        appendLine("documents         = $documents")
        appendLine("music             = $music")
        appendLine("pictures          = $pictures")
        appendLine("videos            = $videos")
        appendLine("templates         = $templates")
        appendLine("publicShare       = $publicShare")
        appendLine()
        appendLine("══ Well-Known Derived Paths ══════════════════")
        appendLine("userApplications  = $userApplications")
        appendLine("userAutostart     = $userAutostart")
        appendLine("userIcons         = $userIcons")
        appendLine("userFonts         = $userFonts")
        appendLine("userThemes        = $userThemes")
        appendLine("trash             = $trash")
        appendLine("thumbnails        = $thumbnails")
        appendLine("recentFiles       = $recentFiles")
        appendLine("userMimeApps      = $userMimeApps")
        appendLine("userDbusServices  = $userDbusServices")
        appendLine("userSystemdUnits  = $userSystemdUnits")
        appendLine("gnupgDir          = $gnupgDir")
        appendLine("sshDir            = $sshDir")
    }
}