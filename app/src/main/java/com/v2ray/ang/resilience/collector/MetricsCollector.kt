package com.v2ray.ang.resilience.collector

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.v2ray.ang.resilience.model.NetworkMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlin.math.abs

/**
 * Collects real-time network resilience metrics by probing an HTTP target through the local SOCKS proxy.
 *
 * Maintains a rolling window of recent probe latencies to derive Jitter, Probe Failure Rate (Loss Rate),
 * and tracks consecutive failure counts safely.
 *
 * @param context Android application context used for network capability checks.
 * @param windowSize Size of the sliding window used for calculating moving metrics (default: 10).
 */
class MetricsCollector(
    private val context: Context,
    private val windowSize: Int = 10
) {
    private val probeHistory = ArrayDeque<Long>(windowSize)
    private var consecutiveFailuresCount = 0

    /**
     * Executes a single lightweight HTTP probe through the local proxy and returns a updated [NetworkMetrics] snapshot.
     *
     * @param targetUrl URL to send the HTTP HEAD request to (e.g., "http://www.gstatic.com/generate_204").
     * @param socksPort Local SOCKS5 proxy port exposed by the v2ray/Xray core (e.g., 10808).
     * @param timeoutMs Maximum read/connect timeout per probe in milliseconds (default: 3000ms).
     */
    suspend fun collectMetrics(
        targetUrl: String = "http://www.gstatic.com/generate_204",
        socksPort: Int = 10808,
        timeoutMs: Int = 3000
    ): NetworkMetrics = withContext(Dispatchers.IO) {
        val activeNetworkType = getActiveNetworkType()
        val startTime = System.currentTimeMillis()
        var rttMs = -1L
        var isSuccess = false

        try {
            val url = URL(targetUrl)
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
            val connection = url.openConnection(proxy) as HttpURLConnection

            connection.requestMethod = "HEAD"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.useCaches = false
            connection.instanceFollowRedirects = false

            val responseCode = connection.responseCode
            val elapsedTime = System.currentTimeMillis() - startTime

            if (responseCode in 200..399) {
                rttMs = elapsedTime
                isSuccess = true
            } else {
                rttMs = -1L
                isSuccess = false
            }
            connection.disconnect()
        } catch (e: Exception) {
            rttMs = -1L
            isSuccess = false
        }

        synchronized(probeHistory) {
            if (isSuccess) {
                consecutiveFailuresCount = 0
                probeHistory.addLast(rttMs)
            } else {
                consecutiveFailuresCount++
                probeHistory.addLast(-1L)
            }

            while (probeHistory.size > windowSize) {
                probeHistory.removeFirst()
            }

            val jitter = calculateJitterLocked()
            val lossRate = calculateLossRateLocked()

            NetworkMetrics(
                rttMs = rttMs,
                jitterMs = jitter,
                lossRate = lossRate,
                consecutiveFailures = consecutiveFailuresCount,
                networkType = activeNetworkType,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Calculates Jitter (mean deviation between consecutive valid latency samples) within current window.
     */
    private fun calculateJitterLocked(): Long {
        val validLatencies = probeHistory.filter { it >= 0L }
        if (validLatencies.size < 2) return 0L

        var totalDiff = 0L
        for (i in 0 until validLatencies.size - 1) {
            totalDiff += abs(validLatencies[i + 1] - validLatencies[i])
        }

        return totalDiff / (validLatencies.size - 1)
    }

    /**
     * Calculates Probe Loss Rate in range [0.0..1.0] over the current sample window.
     */
    private fun calculateLossRateLocked(): Float {
        if (probeHistory.isEmpty()) return 0.0f
        val failedCount = probeHistory.count { it < 0L }
        return failedCount.toFloat() / probeHistory.size.toFloat()
    }

    /**
     * Resets internal metrics history window and failure counter.
     */
    fun resetHistory() {
        synchronized(probeHistory) {
            probeHistory.clear()
            consecutiveFailuresCount = 0
        }
    }

    /**
     * Inspects active network capabilities via Android ConnectivityManager.
     */
    private fun getActiveNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "UNKNOWN"

            val network = cm.activeNetwork ?: return "NO_NETWORK"
            val capabilities = cm.getNetworkCapabilities(network) ?: return "NO_NETWORK"

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "OTHER"
            }
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
}
