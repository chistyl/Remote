package com.chi.tvremote.adb

/**
 * Allocates client-side ADB local IDs. IDs are positive, non-zero 32-bit values
 * and are not reused while a stream is still registered.
 */
internal class AdbStreamIdAllocator(
    initialId: Int = 1
) {
    private var nextId: Int = initialId.coerceAtLeast(1)
    private val active = HashSet<Int>()

    @Synchronized
    fun acquire(): Int {
        val start = nextId
        do {
            val candidate = nextId
            nextId = if (nextId == Int.MAX_VALUE) 1 else nextId + 1
            if (active.add(candidate)) return candidate
        } while (nextId != start)
        throw IllegalStateException("ADB local stream ID space exhausted")
    }

    @Synchronized
    fun release(id: Int): Boolean = active.remove(id)

    @Synchronized
    fun isActive(id: Int): Boolean = id in active
}
