package com.thuvstu.personalencyclopedia.util

inline fun <T> timed(tag: String, label: String, block: () -> T): T {
    val start = System.currentTimeMillis()
    val result = block()
    AppLogger.d(tag, "$label: ${System.currentTimeMillis() - start}ms")
    return result
}
