package pkg.virdin.composelinuxutils

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.File

// ═══════════════════════════════════════════════════════════════════════
//  VIRDIN CONFIG MODELS
// ═══════════════════════════════════════════════════════════════════════

@kotlinx.serialization.Serializable
data class VirdinConfig(
    val prefIcons: List<PrefIcon> = emptyList(),
    val defaultIcons: List<DefaultIcon> = emptyList(),
    val userIcons: List<UserIcon> = emptyList()
)

@kotlinx.serialization.Serializable
data class PrefIcon(
    val name: String,
    val matchType: MatchType,
    val path: String
)

@Serializable
data class DefaultIcon(
    val name: String,
    val matchType: MatchType,
    val path: String
)

@Serializable
data class UserIcon(
    val appId: String,
    val matchType: MatchType,
    val path: String
)

@Serializable
enum class MatchType {
    @SerialName("exact")    exact,
    @SerialName("contains") contains,
    @SerialName("default")  default,
    @SerialName("user")     user
}

private val configJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Loads the Virdin config from the standard locations, in priority order:
 *   1. ~/.config/virdin/virdin.json  (user override)
 *   2. /usr/share/virdin/virdin.json (system default)
 *
 * Returns null if neither file exists, so [XdgIconResolver] falls back
 * to pure XDG resolution with no config layer.
 */
object VirdinIconConfig {

    private val configPaths = listOf(
        "${System.getProperty("user.home")}/.config/virdin/virdin.json",
        "/usr/share/virdin/virdin.json"
    )

    private var _config: VirdinConfig? = null
    private var _loaded = false

    /** Returns the config, loading it once on first access. Returns null if no config file found. */
    fun loadIfExists(): VirdinConfig? {
        if (_loaded) return _config
        _loaded = true
        _config = loadFromDisk()
        return _config
    }

    /** Force-reloads the config from disk (call if the file changes at runtime). */
    fun reload(): VirdinConfig? {
        _loaded = false
        return loadIfExists()
    }

    private fun loadFromDisk(): VirdinConfig? {
        for (path in configPaths) {
            val file = File(path)
            if (!file.exists()) continue
            return try {
                configJson.decodeFromString<VirdinConfig>(file.readText())
            } catch (e: Exception) {
                System.err.println("VirdinIconConfig: failed to parse $path — ${e.message}")
                null
            }
        }
        return null
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  DATA TYPES
// ═══════════════════════════════════════════════════════════════════════

enum class IconFormat { SVG, PNG, XPM, UNKNOWN }

enum class DirectoryType { Fixed, Scalable, Threshold }

data class ThemeDirectory(
    val path: String,       // e.g. "48x48/apps" or "scalable/apps"
    val size: Int,          // nominal size in px
    val scale: Int = 1,
    val type: DirectoryType = DirectoryType.Threshold,
    val maxSize: Int = size,
    val minSize: Int = size,
    val threshold: Int = 2,
    val context: String = ""
)

data class IconTheme(
    val name: String,
    val parents: List<String>,          // from Inherits=
    val directories: List<ThemeDirectory>
)

sealed class IconResult {
    data class Found(val path: String, val format: IconFormat) : IconResult()
    object NotFound : IconResult()
}