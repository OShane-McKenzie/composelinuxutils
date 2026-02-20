package pkg.virdin.composelinuxutils

fun DesktopApp.matchesQuery(q: String): Boolean {
    return name.lowercase().contains(q)
            || genericName?.lowercase()?.contains(q) == true
            || comment?.lowercase()?.contains(q) == true
            || keywords.any { it.lowercase().contains(q) }
            || categories.any { it.lowercase().contains(q) }
}

/**
 * Higher score = better match. Name prefix > name contains > genericName > comment > keyword.
 */
fun DesktopApp.searchScore(q: String): Int {
    var score = 0
    val lname = name.lowercase()
    if (lname == q)                          score += 100
    if (lname.startsWith(q))                 score += 50
    if (lname.contains(q))                   score += 30
    if (genericName?.lowercase()?.contains(q) == true) score += 20
    if (comment?.lowercase()?.contains(q) == true)     score += 10
    if (keywords.any { it.lowercase().contains(q) })   score += 5
    return score
}

// ═══════════════════════════════════════════════════════════════════════
//  STRING UTILITIES
// ═══════════════════════════════════════════════════════════════════════

/**
 * Splits a semicolon-delimited desktop file value, dropping blank entries.
 * The spec allows (and common files use) a trailing semicolon.
 */
fun String.splitSemicolon(): List<String> =
    split(";").map { it.trim() }.filter { it.isNotBlank() }

/**
 * Unescapes desktop file string values per the spec:
 * \s → space, \n → newline, \t → tab, \r → carriage return, \\ → backslash
 */
fun String.unescapeDesktop(): String =
    replace("\\s", " ")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\r", "\r")
        .replace("\\\\", "\\")