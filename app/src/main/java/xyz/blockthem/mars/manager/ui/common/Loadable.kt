package xyz.blockthem.mars.manager.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/** Content state for screens that load a single resource. */
data class Loadable<T>(
    val data: T? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** Formats an RFC 3339 timestamp from the API as a local date. */
fun formatApiDate(timestamp: String?): String? {
    if (timestamp.isNullOrBlank()) return null
    return try {
        val instant = Instant.parse(timestamp)
        if (instant.epochSecond <= 0) null else dateFormatter.format(instant.atZone(ZoneId.systemDefault()))
    } catch (_: DateTimeParseException) {
        null
    }
}
