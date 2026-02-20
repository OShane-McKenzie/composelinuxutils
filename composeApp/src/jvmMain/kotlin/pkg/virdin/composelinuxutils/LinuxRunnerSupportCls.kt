package pkg.virdin.composelinuxutils


// ═══════════════════════════════════════════════════════════════════════
//  RESULT TYPES
// ═══════════════════════════════════════════════════════════════════════

data class RunResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val success: Boolean get() = exitCode == 0
    val output: String get() = stdout.trim()
    val error: String get() = stderr.trim()

    override fun toString(): String =
        "RunResult(exitCode=$exitCode, stdout='${stdout.trim()}', stderr='${stderr.trim()}')"
}

sealed class ElevationMethod {
    /** sudo — most common, prompts via stdin or askpass */
    object Sudo : ElevationMethod()
    /** pkexec — PolicyKit, shows a GUI password dialog */
    object Pkexec : ElevationMethod()
    /** su — classic, prompts for root password */
    object Su : ElevationMethod()
}

// ═══════════════════════════════════════════════════════════════════════
//  LAUNCH OPTIONS
// ═══════════════════════════════════════════════════════════════════════

data class LaunchOptions(
    /** Override working directory. Null = use DesktopApp.workingDirectory or home. */
    val workingDirectory: String? = null,
    /** Extra environment variables to inject. */
    val environment: Map<String, String> = emptyMap(),
    /** If true, wraps the command in a terminal emulator (forces CLI apps to have a window). */
    val forceTerminal: Boolean = false,
    /** Override which terminal emulator to use. Null = auto-detect. */
    val terminalOverride: String? = null
)