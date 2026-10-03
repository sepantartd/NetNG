package com.v2ray.ang.resilience

import android.content.Context
import com.v2ray.ang.resilience.collector.MetricsCollector
import com.v2ray.ang.resilience.evaluator.HealthEvaluator
import com.v2ray.ang.resilience.model.ConnectionHealthState
import com.v2ray.ang.resilience.model.NetworkMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Central orchestrator for the Network Resilience Engine.
 *
 * Periodically executes network probes via [MetricsCollector], evaluates connection state using [HealthEvaluator],
 * and emits state updates as reactive [StateFlow] streams for UI and background services.
 *
 * @param context Android application context.
 * @param collector Custom or default [MetricsCollector] instance.
 * @param evaluator Custom or default [HealthEvaluator] instance.
 */
class ResilienceManager(
    private val context: Context,
    private val collector: MetricsCollector = MetricsCollector(context),
    private val evaluator: HealthEvaluator = HealthEvaluator()
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var monitoringJob: Job? = null

    private val _healthState = MutableStateFlow(ConnectionHealthState.HEALTHY)
    val healthState: StateFlow<ConnectionHealthState> = _healthState.asStateFlow()

    private val _latestMetrics = MutableStateFlow(NetworkMetrics())
    val latestMetrics: StateFlow<NetworkMetrics> = _latestMetrics.asStateFlow()

    @Volatile
    private var isMonitoring = false

    /**
     * Starts continuous background probing through the local proxy SOCKS port.
     *
     * Adjusts polling intervals adaptively based on the current health state to optimize battery usage.
     *
     * @param socksPort Local SOCKS port exposed by the proxy core (default: 10808).
     * @param targetUrl Endpoint used for lightweight probing.
     */
    fun startMonitoring(
        socksPort: Int = 10808,
        targetUrl: String = "http://www.gstatic.com/generate_204"
    ) {
        if (isMonitoring) return
        isMonitoring = true
        collector.resetHistory()

        monitoringJob = scope.launch {
            while (isActive && isMonitoring) {
                val metrics = collector.collectMetrics(
                    targetUrl = targetUrl,
                    socksPort = socksPort
                )
                val newState = evaluator.evaluate(metrics)

                _latestMetrics.value = metrics
                _healthState.value = newState

                val delayMs = when (newState) {
                    ConnectionHealthState.HEALTHY -> 5000L
                    ConnectionHealthState.DEGRADED -> 3000L
                    ConnectionHealthState.UNSTABLE -> 2000L
                    ConnectionHealthState.DEAD -> 2000L
                }

                delay(delayMs)
            }
        }
    }

    /**
     * Stops background monitoring, cancels scheduled jobs, and resets state to defaults.
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
        collector.resetHistory()
        _healthState.value = ConnectionHealthState.HEALTHY
        _latestMetrics.value = NetworkMetrics()
    }
}
