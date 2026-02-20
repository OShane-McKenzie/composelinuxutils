package pkg.virdin.composelinuxutils

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.decodeToSvgPainter
import java.io.File
import java.io.InputStream

// ═══════════════════════════════════════════════════════════════════════
//  PAINTER LOADING
// ═══════════════════════════════════════════════════════════════════════


/**
 * Loads a Compose [Painter] from a resolved icon file path.
 * SVG files are loaded via Compose's SVG support; everything else as bitmap.
 *
 * @param recolorSvgWith  If provided, monochrome/FollowsColorScheme SVG icons
 *                        will be recolored with this color. Pass your theme's
 *                        icon color (e.g. onSurface). Null = no recoloring.
 */
fun loadIconPainter(
    result: IconResult.Found,
    density: Density = Density(1f),
    recolorSvgWith: Color? = null
): Painter? {
    return try {
        val file = File(result.path)
        if (!file.exists()) return null

        val ext = file.extension.lowercase()

        when {
            ext == "svg" || result.format == IconFormat.SVG -> {
                val svgText = file.readText()
                val processed = if (recolorSvgWith != null && isMonochromeSvg(svgText)) {
                    recolorSvg(svgText, recolorSvgWith)
                } else {
                    svgText
                }
                processed.byteInputStream().use { loadSvgPainter(it, density) }
            }
            ext == "xpm" || result.format == IconFormat.XPM -> {
                loadXpmPainter(file, density)
            }
            else -> {
                try {
                    file.inputStream().use { BitmapPainter(loadImageBitmap(it)) }
                } catch (_: Exception) {
                    try {
                        file.inputStream().use { loadSvgPainter(it, density) }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Detects whether an SVG is monochrome / FollowsColorScheme by checking if it
 * has no explicit fill or stroke colors other than black, white, or none.
 *
 * KDE's FollowsColorScheme icons typically have no fill attributes at all,
 * or use currentColor, or only use black (#000, #000000, black).
 */
private fun isMonochromeSvg(svgText: String): Boolean {
    // If it explicitly declares colors beyond black/white, it's polychrome
    val colorPattern = Regex("""(?:fill|stroke)\s*=\s*["']([^"']+)["']""")
    val styleColorPattern = Regex("""(?:fill|stroke)\s*:\s*([^;}"']+)""")

    val declaredColors = (colorPattern.findAll(svgText) + styleColorPattern.findAll(svgText))
        .map { it.groupValues[1].trim().lowercase() }
        .filter { it != "none" && it != "inherit" && it != "currentcolor" && it.isNotBlank() }
        .toSet()

    val monochromeValues = setOf("black", "#000", "#000000", "#fff", "#ffffff", "white")
    return declaredColors.isEmpty() || declaredColors.all { it in monochromeValues }
}

/**
 * Injects a color into a monochrome SVG so it renders visibly regardless of
 * the background. Adds a fill and stroke attribute to the root <svg> element,
 * and also injects a CSS style block so nested elements inherit it.
 */
private fun recolorSvg(svgText: String, color: Color): String {
    val hex = "#%02x%02x%02x".format(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )

    // Inject a <style> block right after the opening <svg ...> tag
    // so it overrides any existing fill/stroke without modifying the structure
    val styleBlock = """<style>svg,path,rect,circle,polygon,polyline,ellipse,line,use{fill:$hex;stroke:$hex}</style>"""

    return if (svgText.contains("<style", ignoreCase = true)) {
        // There's already a style block — prepend our rule inside it
        svgText.replaceFirst(
            Regex("""(<style[^>]*>)""", RegexOption.IGNORE_CASE),
            "$1svg,path,rect,circle,polygon,polyline,ellipse,line,use{fill:$hex;stroke:$hex}"
        )
    } else {
        // No style block — inject one after the opening svg tag
        svgText.replaceFirst(
            Regex("""(<svg[^>]*>)""", RegexOption.IGNORE_CASE),
            "$1$styleBlock"
        )
    }
}

/**
 * XPM is a text-based format Skia doesn't support natively.
 * We shell out to ImageMagick's `convert` if it's on PATH to produce a PNG in memory.
 * Returns null silently if ImageMagick is not available.
 */
private fun loadXpmPainter(file: File, density: Density): Painter? {
    return try {
        val process = ProcessBuilder("convert", file.absolutePath, "png:-")
            .redirectErrorStream(true)
            .start()
        val bytes = process.inputStream.readBytes()
        process.waitFor()
        if (bytes.isEmpty()) return null
        BitmapPainter(loadImageBitmap(bytes.inputStream()))
    } catch (_: Exception) {
        null
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  COMPOSE INTEGRATION
// ═══════════════════════════════════════════════════════════════════════

/**
 * Composition local providing an app-wide [XdgIconResolver].
 * Provide it near the root of your composition so all [SystemIcon]
 * calls share the same theme cache.
 *
 * Usage:
 *   CompositionLocalProvider(LocalIconResolver provides XdgIconResolver()) {
 *       MyApp()
 *   }
 */
val LocalIconResolver = staticCompositionLocalOf { XdgIconResolver() }

/**
 * Displays a system icon resolved via the full priority chain:
 * userIcons → prefIcons → XDG theme → defaultIcons
 *
 * @param iconValue          Raw Icon= value from the .desktop file
 * @param appId              Desktop file ID for userIcons matching (e.g. "code.desktop")
 * @param size               Display size
 * @param scale              HiDPI scale factor
 * @param themeName          Override the auto-detected theme name
 * @param recolorMonochrome  Color for monochrome KDE SVG icons. Defaults to onSurface.
 * @param modifier           Applied to the Image or fallback Box
 * @param fallback           Shown while loading or if the icon cannot be found
 */
@Composable
fun SystemIcon(
    iconValue: String,
    appId: String = "",
    size: Dp = 48.dp,
    scale: Int = 1,
    contentScale: ContentScale = ContentScale.FillBounds,
    shape: Shape = RoundedCornerShape(5),
    themeName: String? = null,
    recolorMonochrome: Color? = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {
        Box(modifier.size(size).background(Color.Transparent))
    }
) {
    val resolver = LocalIconResolver.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var painter by remember(iconValue, appId, size, scale, themeName, recolorMonochrome) {
        mutableStateOf<Painter?>(null)
    }
    var loaded by remember(iconValue, appId, size, scale, themeName, recolorMonochrome) {
        mutableStateOf(false)
    }

    LaunchedEffect(iconValue, appId, size, scale, themeName, recolorMonochrome) {
        painter = null
        loaded = false

        val px = with(density) { size.toPx().toInt() }

        val result = withContext(Dispatchers.IO) {
            if (themeName != null)
                resolver.resolveFromDesktopValue(iconValue, px, scale, appId, themeName)
            else
                resolver.resolveFromDesktopValue(iconValue, px, scale, appId)
        }

        painter = withContext(Dispatchers.IO) {
            when (result) {
                is IconResult.Found -> loadIconPainter(result, density, recolorMonochrome)
                is IconResult.NotFound -> null
            }
        }

        loaded = true
    }

    val p = painter
    if (p != null) {
        Image(
            painter = p,
            contentScale = contentScale,
            contentDescription = iconValue,
            modifier = modifier.size(size).clip(shape)
        )
    } else {
        fallback()
    }
}

/**
 * Returns the resolved [Painter] for an icon, or null while loading / if not found.
 * Useful when you need the painter directly for custom rendering.
 */
@Composable
fun rememberSystemIconPainter(
    iconValue: String,
    appId: String = "",
    size: Dp = 48.dp,
    scale: Int = 1,
    themeName: String? = null,
    recolorMonochrome: Color? = MaterialTheme.colorScheme.onSurface,
    resolver: XdgIconResolver = LocalIconResolver.current
): Painter? {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var painter by remember(iconValue, appId, size, scale, themeName) { mutableStateOf<Painter?>(null) }

    LaunchedEffect(iconValue, appId, size, scale, themeName) {
        painter = null
        val px = with(density) { size.toPx().toInt() }

        val result = withContext(Dispatchers.IO) {
            if (themeName != null)
                resolver.resolveFromDesktopValue(iconValue, px, scale, appId, themeName)
            else
                resolver.resolveFromDesktopValue(iconValue, px, scale, appId)
        }

        painter = withContext(Dispatchers.IO) {
            (result as? IconResult.Found)?.let { loadIconPainter(it, density, recolorMonochrome) }
        }
    }

    return painter
}