package com.v2ray.ang.resilience.model

/**
 * Snapshot of collected network resilience metrics at a given point in time.
 *
 * @property rttMs Round-trip time in milliseconds (-1 if probe timed out/failed).
 * @property jitterMs Calculated variation in latency between consecutive probes in milliseconds.
 * @property lossRate Estimated probe failure rate in range [0.0..1.0] over the sample window.
 * @property consecutiveFailures Number of consecutive failed probes up to this point.
 * @property networkType Active network transport type (e.g., "WIFI", "CELLULAR", "VPN", "UNKNOWN").
 * @property timestamp Unix timestamp in milliseconds when this metric was recorded.
 */
data class NetworkMetrics(
    val rttMs: Long = -1L,
    val jitterMs: Long = 0L,
    val lossRate: Float = 0.0f,
    val consecutiveFailures: Int = 0,
    val networkType: String = "UNKNOWN",
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Helper check to determine if this specific metric snapshot represents a successful probe.
     */
    val isProbeSuccessful: Boolean
        get() = rttMs >= 0L
}
