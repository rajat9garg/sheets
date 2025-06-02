package com.stress.test.model

import java.time.Instant

/**
 * Represents the aggregated results of a stress test run.
 */
data class StressTestResult(
    val startTime: Instant,
    val endTime: Instant,
    val durationMs: Long,
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val meanResponseTimeMs: Double,
    val p50ResponseTimeMs: Double,
    val p95ResponseTimeMs: Double,
    val p99ResponseTimeMs: Double,
    val minResponseTimeMs: Long,
    val maxResponseTimeMs: Long,
    val requestsPerSecond: Double,
    val scenarioResults: List<ScenarioResult>
) {
    /**
     * Calculate the success rate as a percentage.
     */
    val successRate: Double
        get() = if (totalRequests > 0) (successfulRequests.toDouble() / totalRequests) * 100 else 0.0
}

/**
 * Represents the results for a specific test scenario.
 */
data class ScenarioResult(
    val name: String,
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val meanResponseTimeMs: Double,
    val p50ResponseTimeMs: Double,
    val p95ResponseTimeMs: Double,
    val p99ResponseTimeMs: Double,
    val minResponseTimeMs: Long,
    val maxResponseTimeMs: Long,
    val requestsPerSecond: Double
) {
    /**
     * Calculate the success rate as a percentage.
     */
    val successRate: Double
        get() = if (totalRequests > 0) (successfulRequests.toDouble() / totalRequests) * 100 else 0.0
}
