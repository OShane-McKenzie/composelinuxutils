package pkg.virdin.composelinuxutils

import java.io.File
import java.util.Locale
/**
 * Scans all XDG application directories and returns parsed [DesktopApp] entries.
 *
 * Search path (highest to lowest priority, matching the spec):
 *   $XDG_DATA_HOME/applications          (~/.local/share/applications)
 *   $XDG_DATA_DIRS/applications          (/usr/local/share, /usr/share, …)
 *
 * Deduplication: if the same desktop ID (filename) appears in multiple
 * directories, the highest-priority occurrence wins — exactly as the spec
 * mandates for the application menu.
 *
 * The current desktop environment is detected via $XDG_CURRENT_DESKTOP so
 * that OnlyShowIn / NotShowIn filtering works correctly.
 */
class InstalledAppsProvider {

    private val home = System.getProperty("user.home") ?: ""
    private val currentDesktops: Set<String> by lazy { detectCurrentDesktops() }
    private val systemLocale: String by lazy { Locale.getDefault().toLanguageTag().replace("-", "_") }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Returns all installed applications, deduped by desktop ID.
     *
     * @param includeNoDisplay  Include apps with NoDisplay=true (default false)
     * @param includeHidden     Include apps with Hidden=true (almost never wanted)
     * @param filterForCurrentDesktop  Apply OnlyShowIn/NotShowIn filtering
     * @param checkTryExec      Skip entries whose TryExec binary isn't on PATH
     */
    fun getApps(
        includeNoDisplay: Boolean = false,
        includeHidden: Boolean = false,
        filterForCurrentDesktop: Boolean = true,
        checkTryExec: Boolean = true
    ): List<DesktopApp> {
        // Parse everything first, then deduplicate by ID keeping highest-priority occurrence.
        // Doing dedup BEFORE filtering means a hidden user-local entry correctly shadows
        // a visible system entry with the same ID (which is the spec's intent).
        val allParsed = mutableMapOf<String, DesktopApp>()  // id -> first-seen (highest priority)

        for ((dir, isUserLocal) in applicationDirs()) {
            val folder = File(dir)
            if (!folder.isDirectory) continue

            collectDesktopFiles(folder).forEach { file ->
                val app = parseDesktopFile(file, dir, isUserLocal) ?: return@forEach
                allParsed.putIfAbsent(app.id, app)  // first occurrence wins (highest priority dir)
            }
        }

        return allParsed.values
            .filter { app ->
                if (!includeHidden && app.hidden) return@filter false
                if (!includeNoDisplay && app.noDisplay) return@filter false
                if (app.type != DesktopEntryType.Application) return@filter false
                if (filterForCurrentDesktop && !isVisibleOnCurrentDesktop(app)) return@filter false
                if (checkTryExec && !isTryExecSatisfied(app)) return@filter false
                true
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Diagnose why apps are being dropped. Useful during development.
     * Call this and print the result to see exactly what is filtered and why.
     */
    fun diagnose(): String {
        val sb = StringBuilder()
        val allParsed = mutableMapOf<String, DesktopApp>()

        for ((dir, _) in applicationDirs()) {
            val folder = File(dir)
            if (!folder.isDirectory) { sb.appendLine("DIR NOT FOUND: $dir"); continue }
            val files = collectDesktopFiles(folder)
            sb.appendLine("DIR: $dir (${files.size} .desktop files)")
            files.forEach { file ->
                val app = parseDesktopFile(file, dir, false)
                if (app == null) sb.appendLine("  PARSE FAIL: ${file.name}")
                else allParsed.putIfAbsent(app.id, app)
            }
        }

        sb.appendLine("\nTotal parsed (deduped): ${allParsed.size}")
        sb.appendLine("Filtered breakdown:")

        var shown = 0
        allParsed.values.sortedBy { it.name }.forEach { app ->
            val reasons = mutableListOf<String>()
            if (app.hidden) reasons += "Hidden=true"
            if (app.noDisplay) reasons += "NoDisplay=true"
            if (app.type != DesktopEntryType.Application) reasons += "Type=${app.type}"
            if (!isVisibleOnCurrentDesktop(app)) reasons += "OnlyShowIn=${app.onlyShowIn} NotShowIn=${app.notShowIn}"
            if (!isTryExecSatisfied(app)) reasons += "TryExec not found (${app.tryExec})"

            if (reasons.isEmpty()) shown++
            else sb.appendLine("  FILTERED [${app.id}] \"${app.name}\": ${reasons.joinToString()}")
        }
        sb.appendLine("\nFinal visible: $shown / ${allParsed.size}")
        return sb.toString()
    }

    /**
     * Search installed apps by [query], matching against name, generic name,
     * comment, and keywords. Case-insensitive. Returns apps where any field
     * contains the query as a substring.
     */
    fun search(
        query: String,
        apps: List<DesktopApp> = getApps()
    ): List<DesktopApp> {
        if (query.isBlank()) return apps
        val q = query.trim().lowercase()
        return apps.filter { it.matchesQuery(q) }
            .sortedWith(compareByDescending { it.searchScore(q) })
    }

    /**
     * Filter apps by one or more [categories] (e.g. "Network", "AudioVideo").
     * An app matches if it has ANY of the given categories.
     */
    fun filterByCategory(
        categories: List<String>,
        apps: List<DesktopApp> = getApps()
    ): List<DesktopApp> {
        val cats = categories.map { it.lowercase() }.toSet()
        return apps.filter { app ->
            app.categories.any { it.lowercase() in cats }
        }
    }

    /**
     * Returns only apps where [DesktopApp.isCli] is true — i.e. Terminal=true
     * in the desktop file.
     */
    fun getCliApps(apps: List<DesktopApp> = getApps()): List<DesktopApp> =
        apps.filter { it.isCli }

    /**
     * Returns all unique category values across all apps.
     */
    fun allCategories(apps: List<DesktopApp> = getApps()): List<String> =
        apps.flatMap { it.categories }.distinct().sorted()

    // ── Directory resolution ─────────────────────────────────────────────

    /**
     * Returns (directoryPath, isUserLocal) pairs in descending priority order.
     */
    fun applicationDirs(): List<Pair<String, Boolean>> {
        val dirs = mutableListOf<Pair<String, Boolean>>()

        // User-local (highest priority)
        val xdgDataHome = System.getenv("XDG_DATA_HOME")
            ?.takeIf { it.isNotBlank() }
            ?: "$home/.local/share"
        dirs.add("$xdgDataHome/applications" to true)

        // System-wide — Flatpak and Snap inject their paths into XDG_DATA_DIRS
        // at login, so we don't need to hardcode their locations separately.
        val xdgDataDirs = System.getenv("XDG_DATA_DIRS")
            ?.takeIf { it.isNotBlank() }
            ?: "/usr/local/share:/usr/share"
        xdgDataDirs.split(":").forEach { dataDir ->
            dirs.add("$dataDir/applications" to false)
        }

        return dirs
    }

    // ── Desktop file collection ──────────────────────────────────────────

    /**
     * Recursively collects all .desktop files under [dir], including
     * subdirectories (the spec allows apps/ to have subdirs, with the subdir
     * name prefixed to the desktop ID using '-').
     */
    private fun collectDesktopFiles(dir: File): List<File> {
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "desktop" }
            .sortedBy { it.name }   // deterministic order; prevents random dedup behaviour
            .toList()
    }

    // ── Desktop file parser ──────────────────────────────────────────────

    private fun parseDesktopFile(file: File, baseDir: String, isUserLocal: Boolean): DesktopApp? {
        val lines = try {
            file.readLines(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }

        // Parse into sections
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var currentSection = ""

        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isBlank() || line.startsWith("#") -> Unit
                line.startsWith("[") && line.endsWith("]") -> {
                    currentSection = line.removeSurrounding("[", "]")
                    sections.getOrPut(currentSection) { mutableMapOf() }
                }
                line.contains("=") && currentSection.isNotEmpty() -> {
                    val eqIdx = line.indexOf('=')
                    val key = line.substring(0, eqIdx).trim()
                    val value = line.substring(eqIdx + 1).trim()
                    // Only keep first occurrence (spec: no duplicate keys per group)
                    sections[currentSection]!!.putIfAbsent(key, value)
                }
            }
        }

        val entry = sections["Desktop Entry"] ?: return null

        // Derive the desktop ID from the file path relative to the base dir
        val id = deriveDesktopId(file, baseDir)

        val type = when (entry["Type"]) {
            "Application" -> DesktopEntryType.Application
            "Link"        -> DesktopEntryType.Link
            "Directory"   -> DesktopEntryType.Directory
            else          -> DesktopEntryType.Unknown
        }

        // Locale-resolved fields
        val name        = resolveLocalized(entry, "Name") ?: return null  // Name is required
        val genericName = resolveLocalized(entry, "GenericName")
        val comment     = resolveLocalized(entry, "Comment")
        val keywords    = resolveLocalized(entry, "Keywords")
            ?.splitSemicolon() ?: emptyList()

        // Parse actions
        val actionIds = entry["Actions"]?.splitSemicolon() ?: emptyList()
        val actions = actionIds.mapNotNull { actionId ->
            val sec = sections["Desktop Action $actionId"] ?: return@mapNotNull null
            DesktopAction(
                id   = actionId,
                name = resolveLocalized(sec, "Name") ?: actionId,
                icon = sec["Icon"],
                exec = sec["Exec"]
            )
        }

        // Collect unrecognized / extension keys (X-*)
        val knownKeys = setOf(
            "Type", "Version", "Name", "GenericName", "NoDisplay", "Comment",
            "Icon", "Hidden", "OnlyShowIn", "NotShowIn", "DBusActivatable",
            "TryExec", "Exec", "Path", "Terminal", "Actions", "MimeType",
            "Categories", "Implements", "Keywords", "StartupNotify",
            "StartupWMClass", "URL", "PrefersNonDefaultGPU", "SingleMainWindow"
        )
        val extras = entry.entries
            .filter { (k, _) ->
                val bare = k.substringBefore("[")   // strip locale suffix
                bare !in knownKeys
            }
            .associate { (k, v) -> k to v }

        return DesktopApp(
            desktopFilePath    = file.absolutePath,
            id                 = id,
            type               = type,
            name               = name,
            genericName        = genericName,
            comment            = comment,
            icon               = entry["Icon"],
            keywords           = keywords,
            categories         = entry["Categories"]?.splitSemicolon() ?: emptyList(),
            specVersion        = entry["Version"],
            noDisplay          = entry["NoDisplay"]?.toBoolean() ?: false,
            hidden             = entry["Hidden"]?.toBoolean() ?: false,
            onlyShowIn         = entry["OnlyShowIn"]?.splitSemicolon() ?: emptyList(),
            notShowIn          = entry["NotShowIn"]?.splitSemicolon() ?: emptyList(),
            exec               = entry["Exec"],
            tryExec            = entry["TryExec"],
            workingDirectory   = entry["Path"],
            isCli              = entry["Terminal"]?.toBoolean() ?: false,
            dbusActivatable    = entry["DBusActivatable"]?.toBoolean() ?: false,
            actions            = actions,
            mimeTypes          = entry["MimeType"]?.splitSemicolon() ?: emptyList(),
            startupNotify      = entry["StartupNotify"]?.toBoolean(),
            startupWmClass     = entry["StartupWMClass"],
            prefersNonDefaultGpu = entry["PrefersNonDefaultGPU"]?.toBoolean() ?: false,
            singleMainWindow   = entry["SingleMainWindow"]?.toBoolean() ?: false,
            url                = entry["URL"],
            isUserLocal        = isUserLocal,
            extras             = extras
        )
    }

    // ── Locale resolution ────────────────────────────────────────────────

    /**
     * Resolves a localizable key to the best value for the current locale.
     *
     * The spec matching order for locale `lang_COUNTRY@MODIFIER`:
     *   1. lang_COUNTRY@MODIFIER
     *   2. lang_COUNTRY
     *   3. lang@MODIFIER
     *   4. lang
     *   5. unlocalized key
     */
    private fun resolveLocalized(section: Map<String, String>, key: String): String? {
        val locale = systemLocale  // e.g. "en_GB" or "de_DE"

        // Build candidate suffixes in priority order
        val lang     = locale.substringBefore("_").substringBefore("@")
        val country  = locale.substringAfter("_", "").substringBefore("@")
        val modifier = locale.substringAfter("@", "")

        val candidates = buildList {
            if (country.isNotEmpty() && modifier.isNotEmpty()) add("${lang}_${country}@$modifier")
            if (country.isNotEmpty())                           add("${lang}_${country}")
            if (modifier.isNotEmpty())                          add("${lang}@$modifier")
            if (lang.isNotEmpty())                              add(lang)
        }

        for (suffix in candidates) {
            val v = section["$key[$suffix]"]
            if (!v.isNullOrBlank()) return v.unescapeDesktop()
        }

        return section[key]?.unescapeDesktop()
    }

    // ── Visibility filtering ─────────────────────────────────────────────

    private fun isVisibleOnCurrentDesktop(app: DesktopApp): Boolean {
        if (app.onlyShowIn.isNotEmpty()) {
            // Must match at least one of the OnlyShowIn values
            return app.onlyShowIn.any { it in currentDesktops }
        }
        if (app.notShowIn.isNotEmpty()) {
            // Must not match any NotShowIn value
            return app.notShowIn.none { it in currentDesktops }
        }
        return true
    }

    private fun detectCurrentDesktops(): Set<String> {
        val env = System.getenv("XDG_CURRENT_DESKTOP") ?: return emptySet()
        return env.split(":").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    // ── TryExec check ────────────────────────────────────────────────────

    private fun isTryExecSatisfied(app: DesktopApp): Boolean {
        val tryExec = app.tryExec ?: return true  // not specified = satisfied
        if (tryExec.startsWith("/")) return File(tryExec).canExecute()
        return findOnPath(tryExec) != null
    }

    private fun findOnPath(name: String): String? {
        val path = System.getenv("PATH") ?: return null
        return path.split(":").firstNotNullOfOrNull { dir ->
            val f = File(dir, name)
            if (f.canExecute()) f.absolutePath else null
        }
    }

    // ── Desktop ID derivation ─────────────────────────────────────────────

    /**
     * Derives the desktop ID per spec:
     * The ID is the path relative to the applications/ dir, with '/' replaced by '-'.
     * e.g. file at /usr/share/applications/kde/dolphin.desktop
     *      base dir /usr/share/applications
     *      id = "kde-dolphin.desktop"
     */
    private fun deriveDesktopId(file: File, baseDir: String): String {
        // Use absolutePath (not canonicalPath) on both sides to avoid symlink
        // resolution mismatches where baseDir and file.parent diverge after
        // resolving symlinks differently on some distros.
        val base = File(baseDir).absolutePath.trimEnd('/')
        val filePath = file.absolutePath
        return if (filePath.startsWith("$base/")) {
            filePath.removePrefix("$base/").replace('/', '-')
        } else {
            file.name
        }
    }
}