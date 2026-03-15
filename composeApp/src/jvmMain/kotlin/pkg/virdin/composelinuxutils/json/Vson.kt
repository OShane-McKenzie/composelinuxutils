package pkg.virdin.composelinuxutils.json

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import java.io.File

object Vson {
    val json = SerializationConfig.json
    val jsonScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─────────────────────────────────────────────
    // ENCODE — Object → String
    // ─────────────────────────────────────────────

    /**
     * Encodes [value] to a JSON string using the inferred serializer for [T].
     *
     * ```kotlin
     * val str = Vson.encode(myUser) // → "{\"name\":\"Alice\"}"
     * ```
     */
    inline fun <reified T> encode(value: T): String =
        json.encodeToString(serializer<T>(), value)

    /**
     * Encodes [value] using an explicit [strategy].
     * Use when the serializer cannot be inferred (e.g. abstract/generic base types).
     */
    fun <T> encode(value: T, strategy: SerializationStrategy<T>): String =
        json.encodeToString(strategy, value)

    /**
     * Encodes [value] to an indented, human-readable JSON string.
     *
     * ```kotlin
     * val pretty = Vson.encodePretty(myUser)
     * ```
     */
    inline fun <reified T> encodePretty(value: T): String =
        SerializationConfig.prettyJson.encodeToString(serializer<T>(), value)

    /**
     * Encodes [value] to a [JsonElement] tree instead of a raw string.
     * Useful for inspecting or modifying the structure before final serialization.
     */
    inline fun <reified T> encodeToElement(value: T): JsonElement =
        json.encodeToJsonElement(value)

    /**
     * Attempts to encode [value]. Returns **null** on any failure instead of throwing.
     */
    inline fun <reified T> encodeOrNull(value: T): String? =
        runCatching { encode(value) }.getOrNull()

    // ─────────────────────────────────────────────
    // DECODE — String → Object
    // ─────────────────────────────────────────────

    /**
     * Decodes a JSON [string] into an object of type [T].
     *
     * ```kotlin
     * val user: User = Vson.decode("{\"name\":\"Alice\"}")
     * ```
     */
    inline fun <reified T> decode(string: String): T =
        json.decodeFromString(serializer<T>(), string)

    /**
     * Decodes a JSON [string] using an explicit [strategy].
     */
    fun <T> decode(string: String, strategy: DeserializationStrategy<T>): T =
        json.decodeFromString(strategy, string)

    /**
     * Decodes a [JsonElement] tree into an object of type [T].
     */
    inline fun <reified T> decodeFromElement(element: JsonElement): T =
        json.decodeFromJsonElement(element)

    /**
     * Decodes a raw JSON string into a [JsonElement].
     * Useful for dynamic or unknown schemas.
     *
     * ```kotlin
     * val element = Vson.parseElement(raw)
     * val name = element.jsonObject["name"]?.jsonPrimitive?.content
     * ```
     */
    fun parseElement(string: String): JsonElement =
        json.parseToJsonElement(string)

    /**
     * Parses a JSON string and returns a [JsonObject], or null if it is not an object.
     */
    fun parseObject(string: String): JsonObject? =
        parseElement(string) as? JsonObject

    /**
     * Parses a JSON string and returns a [JsonArray], or null if it is not an array.
     */
    fun parseArray(string: String): JsonArray? =
        parseElement(string) as? JsonArray

    /**
     * Attempts to decode [string] into [T]. Returns **null** on any failure instead of
     * throwing. Ideal for untrusted or external input.
     *
     * ```kotlin
     * val user: User? = Vson.decodeOrNull(untrustedJson)
     * ```
     */
    inline fun <reified T> decodeOrNull(string: String): T? =
        runCatching { decode<T>(string) }.getOrNull()

    // ─────────────────────────────────────────────
    // FILE — Read / Write JSON to disk
    // ─────────────────────────────────────────────

    /**
     * Encodes [value] as JSON and writes it to [file].
     * Parent directories are created automatically if missing.
     */
    inline fun <reified T> encodeToFile(value: T, file: File) {
        file.parentFile?.mkdirs()
        file.writeText(encode(value))
    }

    /**
     * Reads [file] and decodes its entire contents into [T].
     */
    inline fun <reified T> decodeFromFile(file: File): T =
        decode(file.readText())

    /**
     * Reads [file] and decodes its contents into [T].
     * Returns **null** on any failure (missing file, bad JSON, type mismatch, etc).
     */
    inline fun <reified T> decodeFromFileOrNull(file: File): T? =
        runCatching { decodeFromFile<T>(file) }.getOrNull()

    // ─────────────────────────────────────────────
    // ASYNC — Suspend variants (for coroutines / ViewModels)
    // ─────────────────────────────────────────────

    /**
     * Suspending encode — offloads work to [Dispatchers.IO].
     */
    suspend inline fun <reified T> encodeAsync(value: T): String =
        withContext(Dispatchers.IO) { encode(value) }

    /**
     * Suspending decode — offloads work to [Dispatchers.IO].
     */
    suspend inline fun <reified T> decodeAsync(string: String): T =
        withContext(Dispatchers.IO) { decode(string) }

    /**
     * Suspending file write — runs on [Dispatchers.IO].
     */
    suspend inline fun <reified T> encodeToFileAsync(value: T, file: File) =
        withContext(Dispatchers.IO) { encodeToFile(value, file) }

    /**
     * Suspending file read + decode — runs on [Dispatchers.IO].
     */
    suspend inline fun <reified T> decodeFromFileAsync(file: File): T =
        withContext(Dispatchers.IO) { decodeFromFile(file) }

    // ─────────────────────────────────────────────
    // CALLBACKS — Fire-and-forget with Result delivery on Main
    // ─────────────────────────────────────────────

    /**
     * Encodes [value] on IO and delivers the JSON string (or error) to [onResult] on Main.
     *
     * ```kotlin
     * Vson.encode(myUser) { result ->
     *     result.onSuccess { json -> log(json) }
     *     result.onFailure { e -> showError(e) }
     * }
     * ```
     */
    inline fun <reified T> encodeCatching(
        value: T,
        crossinline onResult: (Result<String>) -> Unit
    ) {
        jsonScope.launch {
            val result = runCatching { encode(value) }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /**
     * Decodes [string] on IO and delivers the typed result (or error) to [onResult] on Main.
     *
     * ```kotlin
     * Vson.decode<User>(jsonString) { result ->
     *     result.onSuccess { user -> updateUi(user) }
     *     result.onFailure { e -> showError(e) }
     * }
     * ```
     */
    inline fun <reified T> decodeCatching(
        string: String,
        crossinline onResult: (Result<T>) -> Unit
    ) {
        jsonScope.launch {
            val result = runCatching { decode<T>(string) }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /**
     * Reads and decodes [file] on IO, delivering the result to [onResult] on Main.
     */
    inline fun <reified T> decodeFromFileCatching(
        file: File,
        crossinline onResult: (Result<T>) -> Unit
    ) {
        jsonScope.launch {
            val result = runCatching { decodeFromFile<T>(file) }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /**
     * Encodes [value] and writes it to [file] on IO, delivering success/failure to [onResult] on Main.
     */
    inline fun <reified T> encodeToFileCatching(
        value: T,
        file: File,
        crossinline onResult: (Result<Unit>) -> Unit
    ) {
        jsonScope.launch {
            val result = runCatching { encodeToFile(value, file) }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }
}