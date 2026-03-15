package pkg.virdin.composelinuxutils

import androidx.compose.runtime.toMutableStateList
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Executes the given block if the receiver is not null
 * Returns the receiver to allow chaining
 */
inline fun <T> T?.ifNotNull(block: (T) -> Unit): T? {
    if (this != null) {
        block(this)
    }
    return this
}

/**
 * Executes the given block if the receiver is not empty
 * Supports Collections, Maps, CharSequences, Arrays, and custom isEmpty implementations
 * Returns the receiver to allow chaining
 */
inline fun <T> T?.ifNotEmpty(block: (T) -> Unit): T? {
    when (this) {
        null -> return null
        is String -> if(isNotEmpty()) block(this)
        is Collection<*> -> if (isNotEmpty()) block(this)
        is Map<*, *> -> if (isNotEmpty()) block(this)
        is CharSequence -> if (isNotEmpty()) block(this)
        is Array<*> -> if (isNotEmpty()) block(this)
        is List<*> -> if (isNotEmpty()) block(this)
    }
    return this
}

/**
 * Executes the given block if the receiver is empty
 * Supports Collections, Maps, CharSequences, Arrays, and custom isEmpty implementations
 * Returns the receiver to allow chaining
 */
inline fun <T> T?.ifEmpty(block: (T) -> Unit): T? {
    when (this) {
        null -> return null
        is String -> if(isEmpty()) block(this)
        is Collection<*> -> if (isEmpty()) block(this)
        is Map<*, *> -> if (isEmpty()) block(this)
        is CharSequence -> if (isEmpty()) block(this)
        is Array<*> -> if (isEmpty()) block(this)
        is List<*> -> if (isNotEmpty()) block(this)
    }
    return this
}

/**
 * Executes the given block if the receiver is null
 * Returns the receiver to allow chaining
 */
inline fun <T> T?.ifNull(block: () -> Unit): T? {
    if (this == null) {
        block()
    }
    return this
}

/**
 * Executes success block if receiver is not null, null block if null
 * Returns the receiver to allow chaining
 */
inline fun <T> T?.ifNullOrNot(
    onNull: () -> Unit = {},
    onNotNull: (T) -> Unit = {}
): T? {
    if (this != null) {
        onNotNull(this)
    } else {
        onNull()
    }
    return this
}

inline fun Boolean.ifTrueOrNot(
    onFalse: (Boolean) -> Unit = {},
    onTrue: (Boolean) -> Unit = {}
): Boolean {
    if (this) {
        onTrue.invoke(true)
    }else{
        onFalse.invoke(false)
    }
    return this
}

/**
 * Executes success block if receiver is not null, null block if null, error block if error
 * Returns the receiver to allow chaining
 */
inline fun <T> T?.tryIfNullOrNot(
    nullBlock: () -> Unit = {},
    errorBlock:(Exception)->Unit,
    notNullBlock: (T) -> Unit
): T? {
    try {
        if (this != null) {
            notNullBlock(this)
        } else {
            nullBlock()
        }
    }catch (e:Exception){
        errorBlock(e)
    }
    return this
}

inline fun <T> T?.tryIfNullOrNotTriplets(
    allBlocks: () -> Triple<(T) -> Unit, () -> Unit, (Exception) -> Unit>
): T? {
    val (notNullBlock, nullBlock, errorBlock) = allBlocks()
    try {
        if (this != null) {
            notNullBlock(this)
        } else {
            nullBlock()
        }
    } catch (e: Exception) {
        errorBlock(e)
    }
    return this
}


/**
 * Extension function that checks if the receiver object is equal to another object.
 * This version allows comparison with any type, but will return false if types don't match.
 *
 * @param other The object to compare with the receiver
 * @return true if the objects are equal, false otherwise
 */
fun <T> T.isSameAs(other: Any?): Boolean {
    // If either is null, use standard equality check
    if (this == null || other == null) {
        return this == other
    }

    // Get runtime class safely
    val thisClass = this!!::class
    val otherClass = other::class

    // If types don't match, they can't be the same
    if (thisClass != otherClass) {
        return false
    }

    // Otherwise check equality
    return this == other
}

fun Int.isNegative():Boolean{
    return this < 0
}


@OptIn(ExperimentalTime::class)
fun getDateTimeAsMillis():Long{
    return Clock.System.now().epochSeconds
}

/**
 * Checks if the [String] is a valid email address.
 *
 * This extension function uses a regular expression to validate the format of an email address.
 * It checks for a typical `local-part@domain` structure.
 *
 * @return `true` if the string matches the email format, `false` otherwise.
 */
fun String.isValidEmail(): Boolean {
    // Regular expression pattern for validating email addresses
    val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\$")
    // Return whether the email matches the pattern
    return this.matches(emailRegex)
}

fun Long.toMinutesAndSeconds(): String {
    val totalSeconds = this / 1000  // Convert to seconds first
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60

    // Use Kotlin's string template with padStart() for zero padding
    return "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
}


/**
 * Replaces an element in the list or adds it if it doesn't exist.
 *
 * This function finds the first element in the `MutableList` that has the same key
 * as the `updated` element. The key is determined by the `selector` lambda.
 *
 * - If an element with a matching key is found, it is replaced with the `updated` element,
 *   and the function returns `true`.
 * - If no element with a matching key is found, the `updated` element is added to the end
 *   of the list, and the function returns `false`.
 *
 * This is useful for "upsert" (update or insert) operations on a list of objects,
 * where uniqueness is defined by a specific property (e.g., an ID).
 *
 * @param T The type of elements in the list.
 * @param K The type of the key used for comparison.
 * @param selector A lambda function that extracts a key `K` from an element `T`.
 * @param updated The new or updated element to be placed in the list.
 * @return `true` if an existing element was replaced, `false` if a new element was added.
 */
inline fun <T, K> MutableList<T>.replaceBy(selector: (T) -> K, updated: T): Boolean {
    val key = selector(updated)
    val index = indexOfFirst { selector(it) == key }
    return if (index != -1) {
        this[index] = updated
        true
    } else {
        this.add(updated)
        false
    }
}