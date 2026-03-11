package com.example.clawpaw.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 外部执行命令/动作的日志，供主界面「执行日志」区域展示。
 * 由 ADB、WebSocket、HTTP 触发时写入，线程安全。
 */
data class CommandLogEntry(
    val time: String,
    val source: String,
    val action: String
)

object CommandLog {
    private const val MAX_ENTRIES = 200
    private val lock = Any()
    private val list = mutableListOf<CommandLogEntry>()
    private val _entries = MutableStateFlow<List<CommandLogEntry>>(emptyList())
    val entries: StateFlow<List<CommandLogEntry>> = _entries.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun addEntry(source: String, action: String) {
        val time = timeFormat.format(Date())
        synchronized(lock) {
            list.add(CommandLogEntry(time, source, action))
            if (list.size > MAX_ENTRIES) list.removeAt(0)
            _entries.value = list.toList()
        }
    }
}
