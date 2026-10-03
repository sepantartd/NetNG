package com.v2ray.ang.resilience.evaluator

import com.v2ray.ang.resilience.model.ConnectionHealthState
import com.v2ray.ang.resilience.model.NetworkMetrics

/**
 * Evaluates raw [NetworkMetrics] snapshots against predefined resilience thresholds
 * to determine the overall [ConnectionHealthState].
 *
 * @property healthyLatencyMaxMs Latency ceiling for HEALTHY state (default: 300ms).
 * @property degradedLatencyMaxMs Latency ceiling for DEGRADED state (default: 800ms).
 * @property healthyJitterMaxMs Jitter ceiling for HEALTHY state (default: 50ms).
 * @property degradedJitterMaxMs Jitter ceiling for DEGRADED state (default: 200ms).
 * @property healthyLossRateMax Probe loss rate ceiling for HEALTHY state (default: 0.05 / 5%).
 * @property degradedLossRateMax Probe loss rate ceiling for DEGRADED state (default: 0.25 / 25%).
 * @property unstableLossRateMax Probe loss rate ceiling for UNSTABLE state (default: 0.60 / 60%).
 * @property consecutiveFailuresDead Threshold of consecutive probe failures triggering DEAD state (default: 3).
 */
class HealthEvaluator(
    private val healthyLatencyMaxMs: Long = 300L,
    private val degradedLatencyMaxMs: Long = 800L,
    private val healthyJitterMaxMs: Long = 50L,
    private val degradedJitterMaxMs: Long = 200L,
    private val healthyLossRateMax: Float = 0.05f,
    private val degradedLossRateMax: Float = 0.25f,
    private val unstableLossRateMax: Float = 0.60f,
    private val consecutiveFailuresDead: Int = 3
) {

    /**
     * Evaluates a given [NetworkMetrics] snapshot and returns its classified [ConnectionHealthState].
     */
    fun evaluate(metrics: NetworkMetrics): ConnectionHealthState {
        // Priority 1: Check for DEAD state
        if (metrics.consecutiveFailures >= consecutiveFailuresDead ||
            metrics.lossRate >= unstableLossRateMax
        ) {
            return ConnectionHealthState.DEAD
        }

        // Priority 2: Check for UNSTABLE state
        if (metrics.consecutiveFailures > 0 ||
            metrics.rttMs > degradedLatencyMaxMs ||
            metrics.jitterMs > degradedJitterMaxMs ||
            metrics.lossRate >= degradedLossRateMax
        ) {
            return ConnectionHealthState.UNSTABLE
        }

        // Priority 3: Check for DEGRADED state
        if (metrics.rttMs > healthyLatencyMaxMs ||
            metrics.jitterMs > healthyJitterMaxMs ||
            metrics.lossRate >= healthyLossRateMax
        ) {
            return ConnectionHealthState.DEGRADED
        }

        // Priority 4: Default to HEALTHY
        return ConnectionHealthState.HEALTHY
    }
}
