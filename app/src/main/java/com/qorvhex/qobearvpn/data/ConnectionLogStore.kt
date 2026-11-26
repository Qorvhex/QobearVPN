package com.qorvhex.qobearvpn.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(val timestamp: String, val tag: String, val message: String, val isError: Boolean = false)

object ConnectionLogStore {
    private const val MAX_ENTRIES = 250
    private val entries = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val mutable = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = mutable.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun append(tag: String, message: String, isError: Boolean = false) {
        val clean = sanitize(message)
        if (clean.isBlank()) return
        if (entries.size >= MAX_ENTRIES) entries.removeAt(0)
        entries.add(
            LogEntry(
                timestamp = timeFormatter.format(Date()),
                tag = tag,
                message = clean,
                isError = isError,
            )
        )
        mutable.value = entries.toList()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        mutable.value = emptyList()
    }

    private fun sanitize(msg: String): String {
        val lower = msg.lowercase(Locale.ROOT)
        for (secret in listOf("password", "passwd", "token", "private", "-----begin", "<ca>", "<key>")) {
            if (secret in lower) return "[redacted sensitive line]"
        }
        return msg.trim().take(300)
    }
}
