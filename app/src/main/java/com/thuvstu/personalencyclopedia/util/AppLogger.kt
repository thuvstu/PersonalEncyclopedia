package com.thuvstu.personalencyclopedia.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * アプリ全体のログを集約するロガー（§2.5 可観測性）。
 * - Logcat 出力
 * - 直近200件のインメモリリングバッファ（UIの「診断」画面で表示可能）
 */
object AppLogger {
    data class LogEntry(
        val time: String,
        val level: String,
        val tag: String,
        val message: String
    )

    private const val MAX_BUFFER = 200
    private val buffer = ConcurrentLinkedDeque<LogEntry>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private fun record(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(timeFmt.format(Date()), level, tag, message)
        buffer.addFirst(entry)
        while (buffer.size > MAX_BUFFER) buffer.pollLast()
        _logs.value = buffer.toList()
        when (level) {
            "E" -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "I" -> Log.i(tag, message)
            else -> Log.d(tag, message)
        }
    }

    fun d(tag: String, msg: String) = record("D", tag, msg)
    fun i(tag: String, msg: String) = record("I", tag, msg)
    fun w(tag: String, msg: String) = record("W", tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) = record("E", tag, msg, t)

    fun clear() {
        buffer.clear()
        _logs.value = emptyList()
    }
}