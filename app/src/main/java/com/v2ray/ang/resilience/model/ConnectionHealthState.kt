package com.v2ray.ang.resilience.model

/**
 * Represents the high-level health state of the proxy connection.
 * Used by the Resilience Engine to trigger adaptive actions or notify the UI.
 */
enum class ConnectionHealthState(
    val level: Int,
    val displayText: String,
    val description: String
) {
    /**
     * Connection is optimal. Low latency, negligible probe loss, zero consecutive failures.
     */
    HEALTHY(
        level = 0,
        displayText = "Healthy",
        description = "Connection is stable with low latency and no loss."
    ),

    /**
     * Connection is functional but showing signs of strain (elevated latency, minor jitter/loss).
     */
    DEGRADED(
        level = 1,
        displayText = "Degraded",
        description = "Connection is experiencing higher latency or minor packet loss."
    ),

    /**
     * Connection is severely impaired (high jitter, high probe failure rate, intermittent drops).
     */
    UNSTABLE(
        level = 2,
        displayText = "Unstable",
        description = "Connection has frequent probe failures or severe latency spikes."
    ),

    /**
     * Connection is completely non-responsive or repeated consecutive probes have failed.
     */
    DEAD(
        level = 3,
        displayText = "Dead",
        description = "Connection is unresponsive. Traffic cannot flow."
    );

    /**
     * Returns true if the connection requires active intervention or recovery attempt.
     */
    fun requiresIntervention(): Boolean {
        return this == UNSTABLE || this == DEAD
    }
}
