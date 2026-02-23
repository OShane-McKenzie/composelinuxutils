package pkg.virdin.composelinuxutils

import java.io.File
import java.nio.charset.Charset

// ═══════════════════════════════════════════════════════════════════════
//  RESULT TYPE
// ═══════════════════════════════════════════════════════════════════════

/**
 * Wraps the outcome of a file operation.
 * Avoids throwing exceptions — callers can handle failure explicitly.
 */
sealed class FileResult<out T> {
    data class Success<T>(val value: T) : FileResult<T>()
    data class Failure(val message: String, val cause: Exception? = null) : FileResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    /** Returns the value or null if this is a Failure. */
    fun getOrNull(): T? = (this as? Success)?.value

    /** Returns the value or throws the underlying exception. */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw cause ?: Exception(message)
    }

    /** Returns the value or a [default] if this is a Failure. */
    fun getOrDefault(default: @UnsafeVariance T): T = getOrNull() ?: default

    /** Runs [block] if this is a Success. */
    fun onSuccess(block: (T) -> Unit): FileResult<T> {
        if (this is Success) block(value)
        return this
    }

    /** Runs [block] if this is a Failure. */
    fun onFailure(block: (message: String, cause: Exception?) -> Unit): FileResult<T> {
        if (this is Failure) block(message, cause)
        return this
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  LINUX FILES
// ═══════════════════════════════════════════════════════════════════════

/**
 * Utility object for reading and writing files on Linux.
 *
 * All operations return [FileResult] rather than throwing exceptions,
 * so callers handle failure explicitly without try/catch at every call site.
 *
 * Write operations create parent directories automatically unless
 * [createParents] is set to false.
 */
object LinuxFiles {

    // ═══════════════════════════════════════════════════════════════════════
    //  READ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Reads the entire content of a file as a String.
     *
     * e.g. LinuxFiles.readText("/etc/os-release")
     */
    fun readText(path: String, charset: Charset = Charsets.UTF_8): FileResult<String> =
        readText(File(path), charset)

    fun readText(file: File, charset: Charset = Charsets.UTF_8): FileResult<String> =
        runCatching(file) { it.readText(charset) }

    /**
     * Reads all lines of a file into a List, stripping line endings.
     */
    fun readLines(path: String, charset: Charset = Charsets.UTF_8): FileResult<List<String>> =
        readLines(File(path), charset)

    fun readLines(file: File, charset: Charset = Charsets.UTF_8): FileResult<List<String>> =
        runCatching(file) { it.readLines(charset) }

    /**
     * Reads the raw bytes of a file.
     * Useful for binary files — images, archives, etc.
     */
    fun readBytes(path: String): FileResult<ByteArray> = readBytes(File(path))

    fun readBytes(file: File): FileResult<ByteArray> =
        runCatching(file) { it.readBytes() }

    /**
     * Reads a file and parses each non-blank line with [transform],
     * skipping lines that return null.
     *
     * e.g. read a CSV into a list of data objects:
     * LinuxFiles.readParsed("/data/apps.csv") { line -> line.split(",") }
     */
    fun <T> readParsed(
        path: String,
        charset: Charset = Charsets.UTF_8,
        transform: (String) -> T?
    ): FileResult<List<T>> = readParsed(File(path), charset, transform)

    fun <T> readParsed(
        file: File,
        charset: Charset = Charsets.UTF_8,
        transform: (String) -> T?
    ): FileResult<List<T>> = runCatching(file) {
        it.readLines(charset)
            .filter { line -> line.isNotBlank() }
            .mapNotNull(transform)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  WRITE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Writes [content] to a file, replacing any existing content.
     * Creates parent directories automatically.
     */
    fun writeText(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        createParents: Boolean = true
    ): FileResult<Unit> = writeText(File(path), content, charset, createParents)

    fun writeText(
        file: File,
        content: String,
        charset: Charset = Charsets.UTF_8,
        createParents: Boolean = true
    ): FileResult<Unit> = runCatching(file, requireExists = false) {
        if (createParents) it.parentFile?.mkdirs()
        it.writeText(content, charset)
    }

    /**
     * Appends [content] to an existing file, or creates it if it doesn't exist.
     */
    fun appendText(
        path: String,
        content: String,
        charset: Charset = Charsets.UTF_8,
        createParents: Boolean = true
    ): FileResult<Unit> = appendText(File(path), content, charset, createParents)

    fun appendText(
        file: File,
        content: String,
        charset: Charset = Charsets.UTF_8,
        createParents: Boolean = true
    ): FileResult<Unit> = runCatching(file, requireExists = false) {
        if (createParents) it.parentFile?.mkdirs()
        it.appendText(content, charset)
    }

    /**
     * Writes [lines] to a file, each separated by the system line separator.
     * Replaces any existing content.
     */
    fun writeLines(
        path: String,
        lines: List<String>,
        charset: Charset = Charsets.UTF_8,
        createParents: Boolean = true
    ): FileResult<Unit> = writeLines(File(path), lines, charset, createParents)

    fun writeLines(
        file: File,
        lines: List<String>,
        charset: Charset = Charsets.UTF_8,
        createParents: Boolean = true
    ): FileResult<Unit> = runCatching(file, requireExists = false) {
        if (createParents) it.parentFile?.mkdirs()
        it.writeText(lines.joinToString(System.lineSeparator()), charset)
    }

    /**
     * Writes raw [bytes] to a file. Replaces any existing content.
     * Useful for binary output — images, archives, etc.
     */
    fun writeBytes(
        path: String,
        bytes: ByteArray,
        createParents: Boolean = true
    ): FileResult<Unit> = writeBytes(File(path), bytes, createParents)

    fun writeBytes(
        file: File,
        bytes: ByteArray,
        createParents: Boolean = true
    ): FileResult<Unit> = runCatching(file, requireExists = false) {
        if (createParents) it.parentFile?.mkdirs()
        it.writeBytes(bytes)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DIRECTORY OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Lists all files directly inside [path] (non-recursive).
     * Returns an empty list if the directory is empty or doesn't exist.
     */
    fun list(path: String): FileResult<List<File>> = list(File(path))

    fun list(dir: File): FileResult<List<File>> =
        runCatching(dir, requireExists = false) {
            it.listFiles()?.toList() ?: emptyList()
        }

    /**
     * Lists all files recursively under [path].
     * Optionally filtered by [predicate].
     */
    fun listRecursive(
        path: String,
        predicate: (File) -> Boolean = { true }
    ): FileResult<List<File>> = listRecursive(File(path), predicate)

    fun listRecursive(
        dir: File,
        predicate: (File) -> Boolean = { true }
    ): FileResult<List<File>> = runCatching(dir, requireExists = false) {
        it.walkTopDown().filter { f -> f.isFile && predicate(f) }.toList()
    }

    /**
     * Creates a directory and all missing parent directories.
     */
    fun createDirectory(path: String): FileResult<Unit> = createDirectory(File(path))

    fun createDirectory(dir: File): FileResult<Unit> =
        runCatching(dir, requireExists = false) { it.mkdirs() }

    /**
     * Deletes a file or directory. If [recursive] is true (default), removes
     * directories and all their contents. Safe — returns Failure rather than
     * throwing if the path doesn't exist.
     */
    fun delete(path: String, recursive: Boolean = true): FileResult<Unit> =
        delete(File(path), recursive)

    fun delete(file: File, recursive: Boolean = true): FileResult<Unit> =
        runCatching(file, requireExists = false) {
            if (!it.exists()) return@runCatching     // already gone — not an error
            if (recursive && it.isDirectory) it.deleteRecursively()
            else it.delete()
        }

    /**
     * Checks whether a file or directory exists at [path].
     */
    fun exists(path: String): Boolean = File(path).exists()

    /**
     * Returns the size of a file in bytes, or the total size of a directory tree.
     */
    fun sizeBytes(path: String): FileResult<Long> = sizeBytes(File(path))

    fun sizeBytes(file: File): FileResult<Long> = runCatching(file) {
        if (it.isDirectory) it.walkTopDown().sumOf { f -> if (f.isFile) f.length() else 0L }
        else it.length()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  XDG-AWARE HELPERS
    //  Convenience wrappers that use LinuxPaths so you never hardcode paths
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Reads a file from the app's config directory.
     * Searches [LinuxPaths.allConfigDirs] in priority order (user before system).
     *
     * e.g. LinuxFiles.readConfig("virdin", "virdin.json")
     * → tries ~/.config/virdin/virdin.json, then /etc/xdg/virdin/virdin.json
     */
    fun readConfig(appName: String, fileName: String): FileResult<String> {
        val file = LinuxPaths.findConfig("$appName/$fileName")
            ?: return FileResult.Failure("Config file not found: $appName/$fileName")
        return readText(file)
    }

    /**
     * Writes a file to the user's app config directory (~/.config/[appName]/[fileName]).
     * Creates the directory if it doesn't exist.
     */
    fun writeConfig(appName: String, fileName: String, content: String): FileResult<Unit> {
        val file = LinuxPaths.appConfig(appName, fileName, createIfMissing = true)
        return writeText(file, content)
    }

    /**
     * Reads a file from the app's data directory.
     * Searches [LinuxPaths.allDataDirs] in priority order.
     *
     * e.g. LinuxFiles.readData("virdin", "icons/default.png")
     */
    fun readDataBytes(appName: String, relativePath: String): FileResult<ByteArray> {
        val file = LinuxPaths.findData("$appName/$relativePath")
            ?: return FileResult.Failure("Data file not found: $appName/$relativePath")
        return readBytes(file)
    }

    /**
     * Writes a file to the user's app data directory (~/.local/share/[appName]/[relativePath]).
     */
    fun writeData(appName: String, relativePath: String, content: String): FileResult<Unit> {
        val file = LinuxPaths.appData(appName, relativePath, createIfMissing = true)
        return writeText(file, content)
    }

    /**
     * Reads a file from the app's cache directory (~/.cache/[appName]/[relativePath]).
     */
    fun readCache(appName: String, relativePath: String): FileResult<String> {
        val file = LinuxPaths.appCache(appName, relativePath)
        return readText(file)
    }

    /**
     * Writes a file to the app's cache directory. Creates the directory if needed.
     */
    fun writeCache(appName: String, relativePath: String, content: String): FileResult<Unit> {
        val file = LinuxPaths.appCache(appName, relativePath, createIfMissing = true)
        return writeText(file, content)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INTERNAL
    // ═══════════════════════════════════════════════════════════════════════

    private inline fun <T> runCatching(
        file: File,
        requireExists: Boolean = true,
        block: (File) -> T
    ): FileResult<T> {
        if (requireExists && !file.exists())
            return FileResult.Failure("File not found: ${file.absolutePath}")
        return try {
            FileResult.Success(block(file))
        } catch (e: SecurityException) {
            FileResult.Failure("Permission denied: ${file.absolutePath}", e)
        } catch (e: Exception) {
            FileResult.Failure("File operation failed: ${e.message}", e)
        }
    }
}