package pkg.virdin.composelinuxutils

/** The `Type=` value from the desktop entry spec. */
enum class DesktopEntryType { Application, Link, Directory, Unknown }

/**
 * Represents a parsed `.desktop` file with all standard XDG keys.
 *
 * Fields map 1-to-1 to the keys defined in the Desktop Entry Specification:
 * https://specifications.freedesktop.org/desktop-entry/latest/recognized-keys.html
 *
 * Localized keys (Name, GenericName, Comment, Keywords) are resolved to the
 * best match for the current system locale at parse time, falling back to
 * the unlocalized value.
 */
data class DesktopApp(

    // ── Identity ─────────────────────────────────────────────────────────

    /** Absolute path to the .desktop file itself. */
    val desktopFilePath: String,

    /**
     * The desktop entry ID — the filename relative to the applications/ dir,
     * with path separators replaced by '-'. Used for deduplication.
     * e.g. "org.gnome.gedit.desktop"
     */
    val id: String,

    // ── Required keys ────────────────────────────────────────────────────

    /** Type=  (Application / Link / Directory) */
    val type: DesktopEntryType,

    /** Name=  Specific name, e.g. "Mozilla Firefox". Locale-resolved. */
    val name: String,

    // ── Display / metadata ───────────────────────────────────────────────

    /** GenericName=  e.g. "Web Browser". Locale-resolved. */
    val genericName: String? = null,

    /** Comment=  Tooltip / short description. Locale-resolved. */
    val comment: String? = null,

    /**
     * Icon=  Raw value from the desktop file — either an absolute path or
     * a theme icon name. Pass directly to XdgIconResolver.resolveFromDesktopValue().
     */
    val icon: String? = null,

    /** Keywords=  Extra search terms. Locale-resolved, semicolon-separated in file. */
    val keywords: List<String> = emptyList(),

    /** Categories=  e.g. ["Network", "WebBrowser"]. Useful for grouping. */
    val categories: List<String> = emptyList(),

    /** Version=  Spec version the file targets, e.g. "1.5". */
    val specVersion: String? = null,

    // ── Visibility ───────────────────────────────────────────────────────

    /**
     * NoDisplay=  true means "installed but intentionally hidden from menus".
     * You almost always want to filter these out when showing an app launcher.
     */
    val noDisplay: Boolean = false,

    /**
     * Hidden=  Equivalent to the file not existing — the user "deleted" it.
     * Always filter these out.
     */
    val hidden: Boolean = false,

    /** OnlyShowIn=  Only show in these desktop environments. */
    val onlyShowIn: List<String> = emptyList(),

    /** NotShowIn=  Never show in these desktop environments. */
    val notShowIn: List<String> = emptyList(),

    // ── Execution ────────────────────────────────────────────────────────

    /**
     * Exec=  The command to run, possibly with field codes (%f, %u, %F, %U…).
     * Strip field codes before passing to a shell.
     */
    val exec: String? = null,

    /**
     * TryExec=  Path or name of executable used to check if the app is installed.
     * If specified and the binary can't be found in PATH, the entry should be hidden.
     */
    val tryExec: String? = null,

    /** Path=  Working directory to run the program in. */
    val workingDirectory: String? = null,

    /**
     * Terminal=  Whether the app runs inside a terminal emulator.
     *
     * This is the definitive spec-level indicator for CLI apps. When true,
     * the desktop environment wraps the Exec command in a terminal (e.g. xterm,
     * konsole, gnome-terminal). We expose this as [isCli] for clarity.
     */
    val isCli: Boolean = false,

    /** DBusActivatable=  If true, launch via D-Bus instead of Exec. */
    val dbusActivatable: Boolean = false,

    // ── Actions ──────────────────────────────────────────────────────────

    /**
     * Actions=  Named jump-list actions (e.g. "New Window", "New Private Window").
     * Each action has its own [Desktop Action] section in the file.
     */
    val actions: List<DesktopAction> = emptyList(),

    // ── MIME / file handling ─────────────────────────────────────────────

    /** MimeType=  MIME types this app can open. */
    val mimeTypes: List<String> = emptyList(),

    // ── Startup ──────────────────────────────────────────────────────────

    /** StartupNotify=  Whether the app supports startup notification. */
    val startupNotify: Boolean? = null,

    /** StartupWMClass=  WM_CLASS hint for window matching. */
    val startupWmClass: String? = null,

    // ── GPU / window hints ───────────────────────────────────────────────

    /** PrefersNonDefaultGPU=  Hint to run on discrete GPU if available. */
    val prefersNonDefaultGpu: Boolean = false,

    /** SingleMainWindow=  App only supports one window at a time. */
    val singleMainWindow: Boolean = false,

    // ── Link-type only ───────────────────────────────────────────────────

    /** URL=  Only present when Type=Link. */
    val url: String? = null,

    // ── Source tracking ──────────────────────────────────────────────────

    /** Whether this entry came from a user-local directory (vs system-wide). */
    val isUserLocal: Boolean = false,

    /** Raw key→value map of any unrecognized / vendor-extension keys (X-*). */
    val extras: Map<String, String> = emptyMap()
)

/**
 * A named action from an `[Desktop Action <name>]` section.
 */
data class DesktopAction(
    val id: String,
    val name: String,
    val icon: String? = null,
    val exec: String? = null
)