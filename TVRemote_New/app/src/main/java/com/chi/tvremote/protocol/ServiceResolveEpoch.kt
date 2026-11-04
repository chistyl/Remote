package com.chi.tvremote.protocol

/**
 * Tracks the discovery lifetime of each mDNS service name.
 * A resolve callback started before onServiceLost must stay stale even if
 * the same service name is discovered again before that callback returns.
 */
internal class ServiceResolveEpoch {
    private val epochs = mutableMapOf<String, Long>()

    fun snapshot(serviceName: String): Long = epochs[serviceName] ?: 0L

    fun markLost(serviceName: String) {
        epochs[serviceName] = snapshot(serviceName) + 1L
    }

    fun isCurrent(serviceName: String, capturedEpoch: Long): Boolean =
        snapshot(serviceName) == capturedEpoch

    fun clear() = epochs.clear()
}
