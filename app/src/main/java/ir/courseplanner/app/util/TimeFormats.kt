package ir.courseplanner.app.util

private val TIME_REGEX = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")

/** Converts Persian/Arabic-Indic digits to Latin so users can type ۰۸:۰۰ naturally. */
fun normalizeDigits(input: String): String {
    val sb = StringBuilder(input.length)
    for (ch in input) {
        sb.append(
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            }
        )
    }
    return sb.toString()
}

fun isValidTimeFormat(time: String): Boolean =
    TIME_REGEX.matches(normalizeDigits(time).trim())

/**
 * Parses a free-text exam range such as "09:00 - 12:00" (also accepts "تا" and
 * en/em dashes). Returns null when the text is non-blank but malformed.
 * Blank input means "no exam time" and yields ("", "").
 */
fun parseExamTimeRange(input: String): Pair<String, String>? {
    val normalized = normalizeDigits(input).trim()
    if (normalized.isEmpty()) return "" to ""
    val withDashes = normalized
        .replace("تا", "-")
        .replace("–", "-")
        .replace("—", "-")
    val parts = withDashes.split("-")
    if (parts.size != 2) return null
    val start = parts[0].trim()
    val end = parts[1].trim()
    if (!isValidTimeFormat(start) || !isValidTimeFormat(end)) return null
    return start to end
}
