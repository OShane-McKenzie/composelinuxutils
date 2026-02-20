package pkg.virdin.composelinuxutils
import java.io.File

// ═══════════════════════════════════════════════════════════════════════
//  XDG ICON RESOLVER
// ═══════════════════════════════════════════════════════════════════════

class XdgIconResolver(
    /**
     * Optional Virdin config for custom icon overrides.
     * When provided, resolution follows this priority chain:
     *   userIcons → prefIcons → XDG theme → defaultIcons
     * When null, falls back to standard XDG resolution only.
     */
    private val virdinConfig: VirdinConfig? = VirdinIconConfig.loadIfExists()
) {

    companion object {
        private val EXTENSIONS = listOf("svg", "png", "xpm")
        private const val HICOLOR = "hicolor"
    }

    // Theme parse cache — avoids re-reading index.theme on repeated lookups
    private val themeCache = mutableMapOf<String, IconTheme?>()

    // Stable list of XDG base dirs for this session
    val baseDirs: List<String> by lazy { resolveBaseDirs() }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Resolves the raw value of the `Icon=` key from a .desktop file,
     * following the full Virdin + XDG priority chain:
     *
     *   1. userIcons  — per-app path overrides keyed by [appId] (highest priority)
     *   2. prefIcons  — preferred icon overrides matched by name (exact or contains)
     *   3. XDG theme  — full icon theme spec lookup (theme chain, size matching, hicolor)
     *   4. defaultIcons — catch-all fallback path from config
     *
     * Steps 1, 2, 4 only apply when a [VirdinConfig] was provided to the constructor.
     * If a config path doesn't exist on disk it silently falls through to the next step.
     *
     * @param iconValue  Raw Icon= value — absolute path or theme icon name
     * @param appId      Desktop file ID (e.g. "code.desktop") used for userIcons matching
     * @param iconSize   Preferred size in px for XDG lookup
     * @param iconScale  HiDPI scale factor
     * @param themeName  Override theme name; defaults to auto-detected system theme
     */
    fun resolveFromDesktopValue(
        iconValue: String,
        iconSize: Int = 48,
        iconScale: Int = 1,
        appId: String = "",
        themeName: String = detectCurrentTheme()
    ): IconResult {
        val config = virdinConfig

        // ── 1. userIcons ─────────────────────────────────────────────────
        if (config != null && appId.isNotBlank()) {
            val userIcon = config.userIcons.firstOrNull { it.appId == appId }
            absolutePathResult(userIcon?.path)?.let { return it }
        }

        // ── 2. prefIcons ─────────────────────────────────────────────────
        if (config != null) {
            val iconName = iconValue.trim()
                .substringAfterLast("/")
                .removeSuffix(".svg").removeSuffix(".png").removeSuffix(".xpm")

            val prefIcon = config.prefIcons.firstOrNull { pref ->
                when (pref.matchType) {
                    MatchType.exact    -> pref.name.equals(iconName, ignoreCase = true)
                    MatchType.contains -> iconName.contains(pref.name, ignoreCase = true)
                            || pref.name.contains(iconName, ignoreCase = true)
                    else               -> false
                }
            }
            absolutePathResult(prefIcon?.path)?.let { return it }
        }

        // ── 3. XDG theme lookup ──────────────────────────────────────────
        val xdgResult = resolveXdg(iconValue, iconSize, iconScale, themeName)
        if (xdgResult is IconResult.Found) return xdgResult

        // ── 4. defaultIcons ──────────────────────────────────────────────
        if (config != null) {
            val defaultIcon = config.defaultIcons.firstOrNull { it.matchType == MatchType.default }
            absolutePathResult(defaultIcon?.path)?.let { return it }
        }

        return IconResult.NotFound
    }

    /**
     * Internal XDG-only resolution — the original resolveFromDesktopValue logic.
     * Called as step 3 in the priority chain above.
     */
    private fun resolveXdg(
        iconValue: String,
        iconSize: Int,
        iconScale: Int,
        themeName: String
    ): IconResult {
        val trimmed = iconValue.trim()

        if (trimmed.startsWith("/")) {
            val file = File(trimmed)
            return if (file.exists()) {
                IconResult.Found(trimmed, trimmed.substringAfterLast(".").toFormat())
            } else {
                val stem = File(trimmed).nameWithoutExtension
                resolve(stem, iconSize, iconScale, themeName)
            }
        }

        val iconName = trimmed
            .removeSuffix(".png")
            .removeSuffix(".svg")
            .removeSuffix(".xpm")

        return resolve(iconName, iconSize, iconScale, themeName)
    }

    /**
     * Returns an [IconResult.Found] if [path] is non-null and the file exists on disk,
     * null otherwise so the caller can fall through to the next source.
     */
    private fun absolutePathResult(path: String?): IconResult.Found? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return IconResult.Found(path, path.substringAfterLast(".").toFormat())
    }

    /**
     * Resolve an icon by name, following the full XDG icon theme spec:
     *  1. Search current theme (exact size match)
     *  2. Walk Inherits= chain recursively
     *  3. Search hicolor (mandatory fallback theme)
     *  4. Closest size match in current theme
     *  5. Flat /usr/share/pixmaps fallback
     *
     * @param iconName   Name as found in .desktop file (e.g. "firefox", "folder-open")
     * @param iconSize   Preferred size in pixels (default 48)
     * @param iconScale  HiDPI scale factor (default 1)
     * @param themeName  Override theme; defaults to the system's current theme
     */
    fun resolve(
        iconName: String,
        iconSize: Int = 48,
        iconScale: Int = 1,
        themeName: String = detectCurrentTheme()
    ): IconResult {
        val visited = mutableSetOf<String>()

        // Phase 1 & 2: theme chain with exact size matching
        val exactResult = searchThemeChain(iconName, iconSize, iconScale, themeName, visited, exactOnly = true)
        if (exactResult != null) return exactResult

        // Phase 3: hicolor exact match (if not already visited)
        if (HICOLOR !in visited) {
            val hicolorExact = searchThemeChain(iconName, iconSize, iconScale, HICOLOR, visited, exactOnly = true)
            if (hicolorExact != null) return hicolorExact
        }

        // Phase 4: closest size match across all visited themes
        val closestResult = searchThemeChain(iconName, iconSize, iconScale, themeName, mutableSetOf(), exactOnly = false)
        if (closestResult != null) return closestResult

        // Phase 5: flat fallback directories (no theme structure)
        return searchFlatFallback(iconName)
    }

    /**
     * Returns all theme names found across all base directories.
     */
    fun availableThemes(): List<String> {
        return baseDirs
            .flatMap { base ->
                File(base).listFiles()
                    ?.filter { File("$it/index.theme").exists() }
                    ?.map { it.name }
                    ?: emptyList()
            }
            .distinct()
            .sorted()
    }

    /** Clears theme cache — call after theme changes at runtime */
    fun clearCache() = themeCache.clear()

    // ── Theme chain search ───────────────────────────────────────────────

    private fun searchThemeChain(
        iconName: String,
        iconSize: Int,
        iconScale: Int,
        themeName: String,
        visited: MutableSet<String>,
        exactOnly: Boolean
    ): IconResult? {
        if (themeName in visited) return null
        visited.add(themeName)

        val theme = loadTheme(themeName) ?: return null

        // Search this theme
        val result = searchInTheme(iconName, iconSize, iconScale, theme, exactOnly)
        if (result != null) return result

        // Recurse into parent themes
        for (parent in theme.parents) {
            val parentResult = searchThemeChain(iconName, iconSize, iconScale, parent, visited, exactOnly)
            if (parentResult != null) return parentResult
        }

        return null
    }

    private fun searchInTheme(
        iconName: String,
        iconSize: Int,
        iconScale: Int,
        theme: IconTheme,
        exactOnly: Boolean
    ): IconResult? {
        var bestPath: String? = null
        var bestFormat = IconFormat.UNKNOWN
        var bestDistance = Int.MAX_VALUE

        for (baseDir in baseDirs) {
            for (dir in theme.directories) {
                val distance = sizeDistance(dir, iconSize, iconScale)
                if (exactOnly && distance != 0) continue
                if (!exactOnly && distance >= bestDistance) continue

                for (ext in EXTENSIONS) {
                    val path = "$baseDir/${theme.name}/${dir.path}/$iconName.$ext"
                    if (File(path).exists()) {
                        bestPath = path
                        bestFormat = ext.toFormat()
                        bestDistance = distance
                        if (distance == 0) break
                    }
                }
            }
        }

        return bestPath?.let { IconResult.Found(it, bestFormat) }
    }

    private fun searchFlatFallback(iconName: String): IconResult {
        val flatDirs = baseDirs + listOf("/usr/share/pixmaps", "/usr/local/share/pixmaps")
        for (dir in flatDirs) {
            for (ext in EXTENSIONS) {
                val path = "$dir/$iconName.$ext"
                if (File(path).exists()) return IconResult.Found(path, ext.toFormat())
            }
        }
        return IconResult.NotFound
    }

    // ── index.theme parsing ──────────────────────────────────────────────

    private fun loadTheme(name: String): IconTheme? {
        if (themeCache.containsKey(name)) return themeCache[name]

        for (baseDir in baseDirs) {
            val indexFile = File("$baseDir/$name/index.theme")
            if (indexFile.exists()) {
                val theme = parseIndexTheme(indexFile.readText(), name)
                themeCache[name] = theme
                return theme
            }
        }

        themeCache[name] = null
        return null
    }

    private fun parseIndexTheme(content: String, themeName: String): IconTheme {
        // Parse into sections: sectionName -> (key -> value)
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var current = ""

        for (raw in content.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("[") && line.endsWith("]") -> {
                    current = line.removeSurrounding("[", "]")
                    sections.getOrPut(current) { mutableMapOf() }
                }
                line.contains("=") && !line.startsWith("#") && current.isNotEmpty() -> {
                    val (k, v) = line.split("=", limit = 2).map { it.trim() }
                    sections[current]!![k] = v
                }
            }
        }

        val meta = sections["Icon Theme"] ?: return IconTheme(themeName, emptyList(), emptyList())

        val parents = meta["Inherits"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        // KDE themes (breeze, breeze-dark, etc.) use "ScaledDirectories" for HiDPI
        // variants in addition to the standard "Directories" key. We merge both.
        val standardDirs = meta["Directories"]
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: emptyList()
        val scaledDirs = meta["ScaledDirectories"]
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: emptyList()
        val dirNames = (standardDirs + scaledDirs).distinct()

        val directories = dirNames.mapNotNull { dirName ->
            val d = sections[dirName] ?: return@mapNotNull null
            val size = d["Size"]?.toIntOrNull() ?: return@mapNotNull null

            // FollowsColorScheme=true is a KDE extension: these directories
            // contain recolored SVG icons that adapt to the current color scheme.
            // We include them normally — the file path is still valid; the icon
            // just looks correct in the matching color scheme environment.
            // There's no need to skip them; KDE apps handle recoloring at paint
            // time via Qt, which we can't replicate. Loading the file still gives
            // a usable icon.
            ThemeDirectory(
                path      = dirName,
                size      = size,
                scale     = d["Scale"]?.toIntOrNull() ?: 1,
                context   = d["Context"] ?: "",
                type      = when (d["Type"]) {
                    "Fixed"    -> DirectoryType.Fixed
                    "Scalable" -> DirectoryType.Scalable
                    else       -> DirectoryType.Threshold
                },
                maxSize   = d["MaxSize"]?.toIntOrNull() ?: size,
                minSize   = d["MinSize"]?.toIntOrNull() ?: size,
                threshold = d["Threshold"]?.toIntOrNull() ?: 2
            )
        }

        return IconTheme(name = themeName, parents = parents, directories = directories)
    }

    // ── XDG size distance algorithm (per spec) ───────────────────────────

    /**
     * Returns 0 for an exact match, positive integer for distance.
     * Lower is better during closest-match fallback.
     */
    private fun sizeDistance(dir: ThemeDirectory, iconSize: Int, iconScale: Int): Int {
        val target = iconSize * iconScale
        val dirScaled = dir.size * dir.scale
        return when (dir.type) {
            DirectoryType.Fixed ->
                Math.abs(dirScaled - target)

            DirectoryType.Scalable -> {
                val minScaled = dir.minSize * dir.scale
                val maxScaled = dir.maxSize * dir.scale
                when {
                    target < minScaled -> minScaled - target
                    target > maxScaled -> target - maxScaled
                    else               -> 0
                }
            }

            DirectoryType.Threshold -> {
                val lo = (dir.size - dir.threshold) * dir.scale
                val hi = (dir.size + dir.threshold) * dir.scale
                when {
                    target < lo -> lo - target
                    target > hi -> target - hi
                    else        -> 0
                }
            }
        }
    }

    // ── Environment / theme detection ────────────────────────────────────

    private fun resolveBaseDirs(): List<String> {
        val home = System.getProperty("user.home") ?: ""
        val dirs = mutableListOf<String>()

        // XDG_DATA_HOME (user-specific, highest priority)
        val xdgDataHome = System.getenv("XDG_DATA_HOME")
            ?.takeIf { it.isNotBlank() }
            ?: "$home/.local/share"
        dirs.add("$xdgDataHome/icons")

        // Legacy location, still widely honoured
        dirs.add("$home/.icons")

        // XDG_DATA_DIRS (system-wide, colon-separated)
        val xdgDataDirs = System.getenv("XDG_DATA_DIRS")
            ?.takeIf { it.isNotBlank() }
            ?: "/usr/local/share:/usr/share"
        xdgDataDirs.split(":").forEach { dirs.add("$it/icons") }

        // ── KDE / Plasma-specific locations ──────────────────────────────
        //
        // KDE can install icons through its own package manager (Plasma Store /
        // Discover) into locations that sit outside XDG_DATA_DIRS on some distros.

        // User-installed via Plasma Store / knsrc
        dirs.add("$home/.local/share/icons")           // usually covered by XDG but explicit is safer

        // Some distros (openSUSE, Arch w/ KDE) put extra icon sets here
        dirs.add("/usr/share/pixmaps")                 // flat, also used as fallback
        dirs.add("/opt/kde/share/icons")               // legacy KDE3-era path, rare but exists
        dirs.add("/usr/local/kde/share/icons")         // local KDE installs

        // Flatpak-installed apps export their icons here, and some Flatpak
        // icon themes also land here
        dirs.add("$home/.local/share/flatpak/exports/share/icons")
        dirs.add("/var/lib/flatpak/exports/share/icons")

        // Snap packages expose icons here
        dirs.add("/var/lib/snapd/desktop/icons")

        return dirs.filter { it.isNotBlank() }.distinct()
    }

    fun detectCurrentTheme(): String {
        // GNOME / most modern DEs using GSettings
        runCatching {
            val proc = ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "icon-theme")
                .redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            val name = out.removeSurrounding("'")
            if (name.isNotBlank() && name != "''") return name
        }

        // KDE Plasma — check in priority order:
        //
        // 1. User's own kdeglobals (~/.config/kdeglobals)
        //    Icon theme lives under the [Icons] group, key = "Theme"
        //    (not at root level — common mistake)
        runCatching {
            readKdeglobalsIconTheme(File("${System.getProperty("user.home")}/.config/kdeglobals"))
                ?.let { return it }
        }

        // 2. System-level KDE default (~/.config/kdedefaults/kdeglobals)
        //    Plasma writes fallback defaults here; lower priority than user config
        runCatching {
            readKdeglobalsIconTheme(File("${System.getProperty("user.home")}/.config/kdedefaults/kdeglobals"))
                ?.let { return it }
        }

        // 3. System-wide KDE defaults (/etc/xdg/kdeglobals)
        //    Distro-level Plasma defaults (e.g. KDE Neon, Kubuntu ship their theme here)
        runCatching {
            readKdeglobalsIconTheme(File("/etc/xdg/kdeglobals"))
                ?.let { return it }
        }

        // XFCE
        runCatching {
            val f = File("${System.getProperty("user.home")}/.config/xfce4/xfconf/xfce-perchannel-xml/xsettings.xml")
            Regex("""name="IconThemeName"[^>]*value="([^"]+)"""").find(f.readText())
                ?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        // GTK-3 settings.ini
        runCatching {
            val f = File("${System.getProperty("user.home")}/.config/gtk-3.0/settings.ini")
            Regex("""(?m)^gtk-icon-theme-name\s*=\s*(.+)$""").find(f.readText())
                ?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        // GTK-4 settings.ini
        runCatching {
            val f = File("${System.getProperty("user.home")}/.config/gtk-4.0/settings.ini")
            Regex("""(?m)^gtk-icon-theme-name\s*=\s*(.+)$""").find(f.readText())
                ?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        // GTK-2 .gtkrc
        runCatching {
            val f = File("${System.getProperty("user.home")}/.gtkrc-2.0")
            Regex("""gtk-icon-theme-name\s*=\s*"([^"]+)"""").find(f.readText())
                ?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        return HICOLOR  // universal safe fallback
    }

    /**
     * Parses a kdeglobals INI file and extracts the icon theme name.
     *
     * kdeglobals is a standard INI file. The icon theme is stored as:
     *
     *   [Icons]
     *   Theme=Papirus
     *
     * It must NOT be read as a flat key=value file — the [Icons] group
     * must be found first, otherwise you'd match a "Theme=" key from
     * a completely different group (e.g. [KDE] or [ColorScheme]).
     */
    private fun readKdeglobalsIconTheme(file: File): String? {
        if (!file.exists()) return null
        var inIconsGroup = false
        for (raw in file.readLines()) {
            val line = raw.trim()
            when {
                line.startsWith("[") -> inIconsGroup = line == "[Icons]"
                inIconsGroup && line.startsWith("Theme=") -> {
                    val name = line.removePrefix("Theme=").trim()
                    if (name.isNotBlank()) return name
                }
            }
        }
        return null
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun String.toFormat() = when (lowercase()) {
        "svg" -> IconFormat.SVG
        "png" -> IconFormat.PNG
        "xpm" -> IconFormat.XPM
        else  -> IconFormat.UNKNOWN
    }
}
