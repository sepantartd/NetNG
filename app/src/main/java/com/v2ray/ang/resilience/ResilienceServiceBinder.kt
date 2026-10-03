package com.v2ray.ang.resilience

import android.content.Context
import android.content.Intent
import com.v2ray.ang.resilience.model.ConnectionHealthState
import com.v2ray.ang.resilience.model.NetworkMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Binds the [ResilienceManager] to the Android Service lifecycle.
 * Broadcasts health state and diagnostics metrics via system intents to update UI components
 * and notifications dynamically without tight coupling.
 */
class ResilienceServiceBinder(
    private val context: Context,
    private val resilienceManager: ResilienceManager = ResilienceManager(context)
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var isBound = false

    companion object {
        const val ACTION_RESILIENCE_UPDATE = "com.v2ray.ang.action.RESILIENCE_UPDATE"
        const val EXTRA_HEALTH_STATE = "extra_health_state"
        const val EXTRA_LATENCY_MS = "extra_latency_ms"
        const val EXTRA_JITTER_MS = "extra_jitter_ms"
        const val EXTRA_LOSS_RATE = "extra_loss_rate"
        const val EXTRA_CONSECUTIVE_FAILURES = "extra_consecutive_failures"
        const val EXTRA_NETWORK_TYPE = "extra_network_type"
    }

    /**
     * Starts monitoring resilience metrics and observing state changes.
     */
    fun start(socksPort: Int = 10808) {
        if (isBound) return
        isBound = true

        resilienceManager.startMonitoring(socksPort = socksPort)

        resilienceManager.healthState.onEach { state ->
            broadcastState(state, resilienceManager.latestMetrics.value)
        }.launchIn(scope)

        resilienceManager.latestMetrics.onEach { metrics ->
            broadcastState(resilienceManager.healthState.value, metrics)
        }.launchIn(scope)
    }

    /**
     * Stops background resilience monitoring and releases observation scopes.
     */
    fun stop() {
        if (!isBound) return
        isBound = false
        resilienceManager.stopMonitoring()
    }

    private fun broadcastState(state: ConnectionHealthState, metrics: NetworkMetrics) {
        val intent = Intent(ACTION_RESILIENCE_UPDATE).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_HEALTH_STATE, state.name)
            putExtra(EXTRA_LATENCY_MS, metrics.rttMs)
            putExtra(EXTRA_JITTER_MS, metrics.jitterMs)
            putExtra(EXTRA_LOSS_RATE, metrics.lossRate)
            putExtra(EXTRA_CONSECUTIVE_FAILURES, metrics.consecutiveFailures)
            putExtra(EXTRA_NETWORK_TYPE, metrics.networkType)
        }
        context.sendBroadcast(intent)
    }
}
