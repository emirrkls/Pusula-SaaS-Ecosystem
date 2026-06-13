package com.pusula.service.util

/**
 * Masks secrets that OkHttp BODY logging would otherwise print (auth JSON, Google idToken, etc.).
 */
object SensitiveHttpLogRedactor {
    private val tokenField = Regex(""""(token|accessToken|refreshToken|idToken)"\s*:\s*"[^"]*"""")
    private val passwordField = Regex(""""(password|clientSecret)"\s*:\s*"[^"]*"""")

    fun redact(line: String): String =
        line
            .replace(tokenField) { m -> """"${m.groupValues[1]}":"***"""" }
            .replace(passwordField) { m -> """"${m.groupValues[1]}":"***"""" }
}
