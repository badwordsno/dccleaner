package com.dccleaner.app.util

object SensitiveLogSanitizer {
    private val assignmentPatterns = listOf(
        Regex(pattern = "(?i)\\b(authorization)\\b\\s*[:=]\\s*(?:Bearer|Basic)\\s+[^\\s,;&]+"),
        Regex(
            pattern = "(?i)\\b(cookie|session|authorization|password|passwd|pwd|token|captcha(?:[-_ ]?token)?|g-recaptcha-response|ci_t|api[-_ ]?key|2captcha[-_ ]?key)\\b\\s*[:=]\\s*[^\\s,;&]+"
        ),
        Regex(pattern = "(?i)\\b(PHPSESSID=)[^;\\s]+")
    )

    fun sanitize(message: String): String =
        assignmentPatterns.fold(message) { sanitized, pattern ->
            pattern.replace(sanitized) { match ->
                val prefix = match.groups[1]?.value.orEmpty()
                when {
                    prefix.endsWith("=", ignoreCase = true) -> "${prefix}<redacted>"
                    else -> "${prefix}=<redacted>"
                }
            }
        }
}
