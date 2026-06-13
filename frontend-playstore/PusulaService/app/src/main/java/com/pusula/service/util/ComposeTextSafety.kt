package com.pusula.service.util

/**
 * SkParagraph / Compose text layout can native-crash on some devices when UTF-16 is malformed
 * (lone surrogates) or contains NUL / stray ISO control characters from bad API data.
 */
fun String?.safeForComposeText(fallback: String = ""): String {
    if (this == null) return fallback
    if (isEmpty()) return fallback
    val out = StringBuilder(length)
    var i = 0
    while (i < length) {
        val c = this[i]
        when {
            Character.isHighSurrogate(c) -> {
                if (i + 1 < length && Character.isLowSurrogate(this[i + 1])) {
                    out.append(c).append(this[i + 1])
                    i += 2
                } else {
                    i++
                }
            }
            Character.isLowSurrogate(c) -> i++
            c == '\u0000' -> i++
            Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t' -> i++
            else -> {
                out.append(c)
                i++
            }
        }
    }
    val result = out.toString().trim()
    return if (result.isEmpty() && fallback.isNotEmpty()) fallback else result
}
