@file:Suppress("KDocUnresolvedReference", "unused")

package pkg.virdin.composelinuxutils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File


// ═══════════════════════════════════════════════════════════════════════
//  RUNNER
// ═══════════════════════════════════════════════════════════════════════

/**
 * Runs system commands — both regular and elevated — and launches desktop apps.
 *
 * Elevated commands use [elevationMethod] to gain root privileges.
 * The password prompt is handled by the elevation tool itself:
 *  - [ElevationMethod.Sudo]   → sudo reads from stdin; you supply the password via [sudoPassword]
 *    or it falls back to the user's cached sudo session if still valid.
 *  - [ElevationMethod.Pkexec] → PolicyKit shows its own native GUI dialog; no password needed here.
 *  - [ElevationMethod.Su]     → su reads root password from stdin via [sudoPassword].
 *
 * All long-running operations are suspend functions — call them from a coroutine.
 */
class LinuxRunner(
    val elevationMethod: ElevationMethod = ElevationMethod.Pkexec
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // ── Internal execution core ──────────────────────────────────────────

    /**
     * Runs a command list as a process, optionally in a working directory
     * and with extra environment variables.
     *
     * @param command        Full command + args as a list, e.g. ["ls", "-la", "/tmp"]
     * @param workingDir     Working directory for the process
     * @param env            Extra env vars merged into the current environment
     * @param stdinInput     Optional text written to the process's stdin (used for sudo -S)
     * @param detach         If true, the process is launched and we return immediately
     *                       with a synthetic RunResult(0). Use for GUI app launches.
     */
    private suspend fun exec(
        command: List<String>,
        workingDir: File? = null,
        env: Map<String, String> = emptyMap(),
        stdinInput: String? = null,
        detach: Boolean = false
    ): RunResult = withContext(Dispatchers.IO) {
        val pb = ProcessBuilder(command)
            .redirectErrorStream(false)
            .apply {
                workingDir?.let { directory(it) }
                if (env.isNotEmpty()) environment().putAll(env)
            }

        val process = pb.start()

        if (stdinInput != null) {
            process.outputStream.bufferedWriter().use { it.write(stdinInput) }
        }

        if (detach) {
            // Don't wait — the GUI app runs on its own
            return@withContext RunResult(0, "", "")
        }

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        RunResult(exitCode, stdout, stderr)
    }

    // ── Elevation wrapper ────────────────────────────────────────────────

    /**
     * Wraps [command] with the configured elevation method.
     *
     * For [ElevationMethod.Sudo], pass the user's password as [password].
     * It is written to stdin with the `-S` flag (reads password from stdin).
     * If [password] is null and there's a valid sudo session cached, it will
     * still work — sudo won't prompt if the timestamp is still valid.
     *
     * For [ElevationMethod.Pkexec], [password] is ignored — PolicyKit handles
     * the authentication UI itself.
     */
    private fun elevate(command: List<String>): List<String> = when (elevationMethod) {
        ElevationMethod.Sudo   -> listOf("sudo", "-S", "--") + command
        ElevationMethod.Pkexec -> listOf("pkexec") + command
        ElevationMethod.Su     -> listOf("su", "-c", command.joinToString(" ") { shellQuote(it) })
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PUBLIC API — GENERAL COMMANDS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Runs a shell command string via bash.
     * For complex pipelines or redirects that need a real shell.
     *
     * e.g. run("ls -la /tmp | grep log")
     */
    suspend fun run(shellCommand: String, workingDir: File? = null): RunResult =
        exec(listOf("bash", "-c", shellCommand), workingDir)

    /**
     * Runs a command as a list of args — safer than shell strings, no injection risk.
     *
     * e.g. run("ls", "-la", "/tmp")
     */
    suspend fun run(vararg args: String, workingDir: File? = null): RunResult =
        exec(args.toList(), workingDir)

    /**
     * Runs a command with elevated privileges.
     * For [ElevationMethod.Sudo] or [ElevationMethod.Su], provide [password].
     * For [ElevationMethod.Pkexec], [password] is ignored.
     *
     * e.g. runElevated(password = "secret", "pacman", "-Syu")
     */
    suspend fun runElevated(
        password: String? = null,
        vararg args: String,
        workingDir: File? = null
    ): RunResult {
        val elevated = elevate(args.toList())
        val stdin = when (elevationMethod) {
            ElevationMethod.Sudo -> password?.let { "$it\n" }
            ElevationMethod.Su   -> password?.let { "$it\n" }
            ElevationMethod.Pkexec -> null
        }
        return exec(elevated, workingDir, stdinInput = stdin)
    }

    /**
     * Runs a shell string with elevated privileges.
     *
     * e.g. runElevatedShell("secret", "echo hello > /etc/test")
     */
    suspend fun runElevatedShell(
        password: String? = null,
        shellCommand: String,
        workingDir: File? = null
    ): RunResult = runElevated(
        password = password,
        args = arrayOf("bash", "-c", shellCommand),
        workingDir = workingDir
    )

    // ═══════════════════════════════════════════════════════════════════════
    //  PUBLIC API — COMMON OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    // ── Package management ───────────────────────────────────────────────

    /** Install packages — auto-detects pacman / apt / dnf / zypper. */
    suspend fun installPackages(password: String? = null, vararg packages: String): RunResult {
        val pm = detectPackageManager() ?: return RunResult(-1, "", "No supported package manager found")
        val cmd = when (pm) {
            "pacman" -> arrayOf("pacman", "-S", "--noconfirm") + packages
            "apt"    -> arrayOf("apt-get", "install", "-y") + packages
            "dnf"    -> arrayOf("dnf", "install", "-y") + packages
            "zypper" -> arrayOf("zypper", "install", "-y") + packages
            else     -> return RunResult(-1, "", "Unknown package manager: $pm")
        }
        return runElevated(password, *cmd)
    }

    /** Remove packages. */
    suspend fun removePackages(password: String? = null, vararg packages: String): RunResult {
        val pm = detectPackageManager() ?: return RunResult(-1, "", "No supported package manager found")
        val cmd = when (pm) {
            "pacman" -> arrayOf("pacman", "-R", "--noconfirm") + packages
            "apt"    -> arrayOf("apt-get", "remove", "-y") + packages
            "dnf"    -> arrayOf("dnf", "remove", "-y") + packages
            "zypper" -> arrayOf("zypper", "remove", "-y") + packages
            else     -> return RunResult(-1, "", "Unknown package manager: $pm")
        }
        return runElevated(password, *cmd)
    }

    /** Update the system. */
    suspend fun updateSystem(password: String? = null): RunResult {
        val pm = detectPackageManager() ?: return RunResult(-1, "", "No supported package manager found")
        val cmd = when (pm) {
            "pacman" -> arrayOf("pacman", "-Syu", "--noconfirm")
            "apt"    -> arrayOf("bash", "-c", "apt-get update && apt-get upgrade -y")
            "dnf"    -> arrayOf("dnf", "upgrade", "-y")
            "zypper" -> arrayOf("zypper", "update", "-y")
            else     -> return RunResult(-1, "", "Unknown package manager: $pm")
        }
        return runElevated(password, *cmd)
    }

    /** Check if a package is installed. */
    suspend fun isPackageInstalled(packageName: String): Boolean {
        val pm = detectPackageManager() ?: return false
        val result = when (pm) {
            "pacman" -> run("pacman", "-Q", packageName)
            "apt"    -> run("dpkg", "-s", packageName)
            "dnf"    -> run("rpm", "-q", packageName)
            else     -> return false
        }
        return result.success
    }

    // ── File operations ──────────────────────────────────────────────────

    /** Copy a file, elevated if [elevated] is true. */
    suspend fun copyFile(
        source: String, destination: String,
        elevated: Boolean = false, password: String? = null
    ): RunResult = if (elevated)
        runElevated(password, "cp", "-r", source, destination)
    else
        run("cp", "-r", source, destination)

    /** Move a file, elevated if needed. */
    suspend fun moveFile(
        source: String, destination: String,
        elevated: Boolean = false, password: String? = null
    ): RunResult = if (elevated)
        runElevated(password, "mv", source, destination)
    else
        run("mv", source, destination)

    /** Delete a file or directory, elevated if needed. */
    suspend fun deleteFile(
        path: String,
        elevated: Boolean = false, password: String? = null
    ): RunResult = if (elevated)
        runElevated(password, "rm", "-rf", path)
    else
        run("rm", "-rf", path)

    /** Create a directory (and parents) at [path]. */
    suspend fun makeDirectory(
        path: String,
        elevated: Boolean = false, password: String? = null
    ): RunResult = if (elevated)
        runElevated(password, "mkdir", "-p", path)
    else
        run("mkdir", "-p", path)

    /** Change ownership of a path. */
    suspend fun changeOwner(
        path: String, owner: String,
        password: String? = null
    ): RunResult = runElevated(password, "chown", "-R", owner, path)

    /** Change permissions on a path. */
    suspend fun changePermissions(
        path: String, mode: String,
        elevated: Boolean = false, password: String? = null
    ): RunResult = if (elevated)
        runElevated(password, "chmod", "-R", mode, path)
    else
        run("chmod", "-R", mode, path)

    /** Create a symbolic link. */
    suspend fun createSymlink(
        target: String, link: String,
        elevated: Boolean = false, password: String? = null
    ): RunResult = if (elevated)
        runElevated(password, "ln", "-sf", target, link)
    else
        run("ln", "-sf", target, link)

    // ── systemd ──────────────────────────────────────────────────────────

    /** Start a systemd service. */
    suspend fun startService(name: String, password: String? = null): RunResult =
        runElevated(password, "systemctl", "start", name)

    /** Stop a systemd service. */
    suspend fun stopService(name: String, password: String? = null): RunResult =
        runElevated(password, "systemctl", "stop", name)

    /** Restart a systemd service. */
    suspend fun restartService(name: String, password: String? = null): RunResult =
        runElevated(password, "systemctl", "restart", name)

    /** Enable a service to start on boot. */
    suspend fun enableService(name: String, password: String? = null): RunResult =
        runElevated(password, "systemctl", "enable", name)

    /** Disable a service from starting on boot. */
    suspend fun disableService(name: String, password: String? = null): RunResult =
        runElevated(password, "systemctl", "disable", name)

    /** Check if a systemd service is currently active. */
    suspend fun isServiceActive(name: String): Boolean =
        run("systemctl", "is-active", "--quiet", name).success

    /** Check if a systemd service is enabled. */
    suspend fun isServiceEnabled(name: String): Boolean =
        run("systemctl", "is-enabled", "--quiet", name).success

    /** Start a user-level systemd service (no elevation needed). */
    suspend fun startUserService(name: String): RunResult =
        run("systemctl", "--user", "start", name)

    /** Stop a user-level systemd service. */
    suspend fun stopUserService(name: String): RunResult =
        run("systemctl", "--user", "stop", name)

    // ── Networking ───────────────────────────────────────────────────────

    /** Get all IP addresses for all interfaces. */
    suspend fun ipAddresses(): RunResult = run("ip", "addr", "show")

    /** Ping a host [count] times. */
    suspend fun ping(host: String, count: Int = 4): RunResult =
        run("ping", "-c", count.toString(), host)

    /** Show current network connections. */
    suspend fun networkConnections(): RunResult = run("ss", "-tulnp")

    /** Flush DNS cache (systemd-resolved). */
    suspend fun flushDns(password: String? = null): RunResult =
        runElevated(password, "systemd-resolve", "--flush-caches")

    // ── System info ──────────────────────────────────────────────────────

    /** Get disk usage for a path (or all if null). */
    suspend fun diskUsage(path: String? = null): RunResult =
        run("df", "-h", *(if (path != null) arrayOf(path) else arrayOf()))

    /** Get directory size. */
    suspend fun directorySize(path: String): RunResult =
        run("du", "-sh", path)

    /** List running processes. */
    suspend fun processes(): RunResult = run("ps", "aux")

    /** Kill a process by PID. */
    suspend fun killProcess(pid: Int, force: Boolean = false, password: String? = null): RunResult {
        val sig = if (force) "-9" else "-15"
        return if (password != null)
            runElevated(password, "kill", sig, pid.toString())
        else
            run("kill", sig, pid.toString())
    }

    /** Get kernel/OS info. */
    suspend fun systemInfo(): RunResult = run("uname", "-a")

    /** Get memory usage. */
    suspend fun memoryUsage(): RunResult = run("free", "-h")

    /** Get CPU info. */
    suspend fun cpuInfo(): RunResult = run("cat", "/proc/cpuinfo")

    /** Get system uptime. */
    suspend fun uptime(): RunResult = run("uptime", "-p")

    /** List USB devices. */
    suspend fun usbDevices(): RunResult = run("lsusb")

    /** List PCI devices. */
    suspend fun pciDevices(): RunResult = run("lspci")

    /** Get current logged-in users. */
    suspend fun whoIsLoggedIn(): RunResult = run("who")

    /** Get hostname. */
    suspend fun hostname(): RunResult = run("hostname")

    /** Get OS release info. */
    suspend fun osRelease(): RunResult = run("cat", "/etc/os-release")

    // ── User management ──────────────────────────────────────────────────

    /** Add a user to a group. */
    suspend fun addUserToGroup(
        username: String, group: String, password: String? = null
    ): RunResult = runElevated(password, "usermod", "-aG", group, username)

    /** List groups for a user. */
    suspend fun userGroups(username: String = System.getProperty("user.name") ?: ""): RunResult =
        run("groups", username)

    /** Get current user name. */
    fun currentUser(): String = System.getProperty("user.name") ?: run {
        ProcessBuilder("whoami").start().inputStream.bufferedReader().readText().trim()
    }

    // ── Clipboard ────────────────────────────────────────────────────────

    /** Copy text to clipboard (requires xclip or wl-clipboard). */
    suspend fun copyToClipboard(text: String): RunResult {
        return if (isWayland()) {
            val process = ProcessBuilder("wl-copy").start()
            process.outputStream.bufferedWriter().use { it.write(text) }
            RunResult(process.waitFor(), "", "")
        } else {
            val process = ProcessBuilder("xclip", "-selection", "clipboard").start()
            process.outputStream.bufferedWriter().use { it.write(text) }
            RunResult(process.waitFor(), "", "")
        }
    }

    /** Read text from clipboard. */
    suspend fun readClipboard(): RunResult = if (isWayland())
        run("wl-paste")
    else
        run("xclip", "-selection", "clipboard", "-o")

    // ── Desktop integration ───────────────────────────────────────────────

    /** Open a file or URL with the default application (xdg-open). */
    suspend fun openFile(path: String): RunResult = run("xdg-open", path)

    /** Open a URL in the default browser. */
    suspend fun openUrl(url: String): RunResult = run("xdg-open", url)

    /** Open a directory in the default file manager. */
    suspend fun openDirectory(path: String): RunResult = run("xdg-open", path)

    /**
     * Send a desktop notification.
     * Requires libnotify (notify-send).
     *
     * @param summary  Title of the notification
     * @param body     Body text
     * @param icon     Icon name or path (optional)
     * @param urgency  low / normal / critical
     * @param timeout  Timeout in milliseconds (0 = no timeout)
     */
    suspend fun notify(
        summary: String,
        body: String = "",
        icon: String? = null,
        urgency: String = "normal",
        timeout: Int = 3000
    ): RunResult {
        val args = mutableListOf("notify-send", summary)
        if (body.isNotBlank()) args.add(body)
        if (icon != null) args.addAll(listOf("-i", icon))
        args.addAll(listOf("-u", urgency))
        args.addAll(listOf("-t", timeout.toString()))
        return exec(args)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DESKTOP APP LAUNCHER
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Launches a [DesktopApp] respecting its Exec=, Path=, Terminal=, and
     * DBusActivatable= fields from the desktop entry spec.
     *
     * Field codes (%f, %F, %u, %U, %d, %D, %n, %N, %k, %v, %m) are stripped
     * unless [uris] or [files] are provided, in which case %u/%f are substituted.
     *
     * @param app      The [DesktopApp] to launch
     * @param uris     URIs to pass to the app (substituted for %u / %U field codes)
     * @param files    File paths to pass to the app (substituted for %f / %F)
     * @param options  Launch options — working dir, extra env, terminal override
     */
    suspend fun launch(
        app: DesktopApp,
        uris: List<String> = emptyList(),
        files: List<String> = emptyList(),
        options: LaunchOptions = LaunchOptions()
    ): RunResult {
        val exec = app.exec ?: return RunResult(-1, "", "No Exec= key in desktop file for ${app.name}")

        // D-Bus activation — hand off to dbus-send
        if (app.dbusActivatable) {
            val busName = app.startupWmClass ?: app.id.removeSuffix(".desktop")
            return run(
                "dbus-send", "--session", "--print-reply",
                "--dest=org.freedesktop.DBus",
                "/org/freedesktop/DBus",
                "org.freedesktop.DBus.StartServiceByName",
                "string:$busName", "uint32:0"
            )
        }

        // Resolve field codes
        val resolvedExec = resolveFieldCodes(exec, app, uris, files)

        // Split into argv (respecting quoted strings)
        val argv = splitExec(resolvedExec).toMutableList()
        if (argv.isEmpty()) return RunResult(-1, "", "Empty Exec= after field code resolution")

        // Working directory: options override → desktop file Path= → user home
        val workDir = File(
            options.workingDirectory
                ?: app.workingDirectory
                ?: LinuxPaths.home
        ).takeIf { it.exists() }

        // Terminal wrapping — Terminal=true or forced by options
        val finalArgv = if (app.isCli || options.forceTerminal) {
            val terminal = options.terminalOverride ?: detectTerminalEmulator()
                ?: return RunResult(-1, "", "No terminal emulator found for CLI app ${app.name}")
            buildTerminalCommand(terminal, argv)
        } else {
            argv
        }

        return exec(
            command = finalArgv,
            workingDir = workDir,
            env = options.environment,
            detach = !app.isCli   // GUI apps detach; CLI apps we wait for
        )
    }

    /**
     * Launches a specific [DesktopAction] from a [DesktopApp]
     * (e.g. "New Window", "New Private Window").
     */
    suspend fun launchAction(
        app: DesktopApp,
        action: DesktopAction,
        options: LaunchOptions = LaunchOptions()
    ): RunResult {
        val exec = action.exec
            ?: return RunResult(-1, "", "Action '${action.name}' has no Exec= key")
        val resolved = resolveFieldCodes(exec, app, emptyList(), emptyList())
        val argv = splitExec(resolved)
        val workDir = File(options.workingDirectory ?: app.workingDirectory ?: LinuxPaths.home)
            .takeIf { it.exists() }
        return exec(argv, workDir, options.environment, detach = true)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Resolves XDG Exec field codes per the desktop entry spec section 7.
     *
     * Supported substitutions:
     *   %f → first file path
     *   %F → all file paths (space-separated)
     *   %u → first URI
     *   %U → all URIs (space-separated)
     *   %i → "--icon <iconname>" if Icon= is set
     *   %c → app Name (translated)
     *   %k → desktop file path
     *   %%  → literal %
     *
     * Deprecated/removed codes (%d,%D,%n,%N,%v,%m) are stripped silently.
     */
    private fun resolveFieldCodes(
        exec: String,
        app: DesktopApp,
        uris: List<String>,
        files: List<String>
    ): String {
        return exec
            .replace("%%", "\u0000")            // protect literal %%
            .replace("%f", files.firstOrNull()?.shellQuoted() ?: "")
            .replace("%F", files.joinToString(" ") { it.shellQuoted() })
            .replace("%u", uris.firstOrNull()?.shellQuoted() ?: "")
            .replace("%U", uris.joinToString(" ") { it.shellQuoted() })
            .replace("%i", app.icon?.let { "--icon ${shellQuote(it)}" } ?: "")
            .replace("%c", shellQuote(app.name))
            .replace("%k", shellQuote(app.desktopFilePath))
            .replace(Regex("%[dDnNvm]"), "")    // deprecated, strip
            .replace("\u0000", "%")             // restore literal %
            .trim()
    }

    /**
     * Splits an Exec= string into argv, respecting double-quoted segments.
     * e.g. `code --new-window "some path"` → ["code", "--new-window", "some path"]
     */
    private fun splitExec(exec: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in exec) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ' ' && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        args.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) args.add(current.toString())
        return args
    }

    /**
     * Wraps an argv in a terminal emulator command.
     * Handles the most common emulators and their differing `-e` / `--` conventions.
     */
    private fun buildTerminalCommand(terminal: String, argv: List<String>): List<String> {
        val exec = argv.joinToString(" ") { shellQuote(it) }
        return when {
            terminal.endsWith("konsole")      -> listOf(terminal, "-e") + argv
            terminal.endsWith("gnome-terminal") -> listOf(terminal, "--") + argv
            terminal.endsWith("xfce4-terminal") -> listOf(terminal, "-e", exec)
            terminal.endsWith("alacritty")    -> listOf(terminal, "-e") + argv
            terminal.endsWith("kitty")        -> listOf(terminal) + argv
            terminal.endsWith("foot")         -> listOf(terminal) + argv
            terminal.endsWith("wezterm")      -> listOf(terminal, "start", "--") + argv
            terminal.endsWith("tilix")        -> listOf(terminal, "-e", exec)
            terminal.endsWith("xterm")        -> listOf(terminal, "-e", exec)
            terminal.endsWith("uxterm")       -> listOf(terminal, "-e", exec)
            else                              -> listOf(terminal, "-e") + argv
        }
    }

    /**
     * Finds an available terminal emulator by checking common ones in order
     * of preference: respects $TERMINAL env var first, then checks PATH.
     */
    private fun detectTerminalEmulator(): String? {
        System.getenv("TERMINAL")?.takeIf { it.isNotBlank() }?.let { return it }

        val candidates = listOf(
            "konsole", "gnome-terminal", "xfce4-terminal", "alacritty",
            "kitty", "foot", "wezterm", "tilix", "xterm", "uxterm", "rxvt"
        )
        return candidates.firstOrNull { which(it) != null }
    }

    /** Detects the installed package manager. */
    private fun detectPackageManager(): String? {
        return listOf("pacman", "apt-get", "dnf", "zypper")
            .firstOrNull { which(it) != null }
            ?.replace("-get", "")   // normalize apt-get → apt
            ?.let { if (it == "ap") "apt" else it }
            ?: listOf("pacman", "apt", "dnf", "zypper").firstOrNull { which(it) != null }
    }

    /** Returns the full path of a binary if it's on PATH, null otherwise. */
    private fun which(binary: String): String? {
        val path = System.getenv("PATH") ?: return null
        return path.split(":").firstNotNullOfOrNull { dir ->
            File(dir, binary).takeIf { it.canExecute() }?.absolutePath
        }
    }

    /** Detects if we're running under Wayland. */
    private fun isWayland(): Boolean =
        System.getenv("WAYLAND_DISPLAY") != null ||
        System.getenv("XDG_SESSION_TYPE")?.lowercase() == "wayland"

    private val home: String get() = System.getProperty("user.home") ?: "/root"

    // ═══════════════════════════════════════════════════════════════════════
    //  STATIC HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    companion object {
        /** Shell-quote a single argument (single-quote wrapping, escape internal quotes). */
        fun shellQuote(arg: String): String {
            if (arg.isEmpty()) return "''"
            if (arg.none { it in " \t\n\"'\\$`|&;<>(){}#~*?[]!" }) return arg
            return "'" + arg.replace("'", "'\\''") + "'"
        }
    }
}

// Extension for cleaner internal use
private fun String.shellQuoted(): String = LinuxRunner.shellQuote(this)