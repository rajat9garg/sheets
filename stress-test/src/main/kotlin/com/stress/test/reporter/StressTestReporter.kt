package com.stress.test.reporter

import com.stress.test.model.ScenarioResult
import com.stress.test.model.StressTestResult
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import org.slf4j.LoggerFactory

/**
 * Processes Gatling results and generates reports.
 */
object StressTestReporter {
    private val logger = LoggerFactory.getLogger(StressTestReporter::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    
    /**
     * Generate a report from Gatling results.
     * @param resultsDir Directory containing Gatling results
     * @return StressTestResult with aggregated metrics
     */
    fun generateReport(resultsDir: Path): StressTestResult {
        logger.info("Generating stress test report from directory: {}", resultsDir)
        
        // Find the most recent simulation.log file
        val simulationLog = findLatestSimulationLog(resultsDir)
            ?: throw IllegalStateException("No simulation.log found in $resultsDir")
        
        logger.info("Processing simulation log: {}", simulationLog)
        
        // Parse the simulation log and extract metrics
        val metrics = parseSimulationLog(simulationLog)
        
        // Create the result object
        val result = StressTestResult(
            startTime = metrics.startTime,
            endTime = metrics.endTime,
            durationMs = metrics.durationMs,
            totalRequests = metrics.totalRequests,
            successfulRequests = metrics.successfulRequests,
            failedRequests = metrics.failedRequests,
            meanResponseTimeMs = metrics.meanResponseTimeMs,
            p50ResponseTimeMs = metrics.p50ResponseTimeMs,
            p95ResponseTimeMs = metrics.p95ResponseTimeMs,
            p99ResponseTimeMs = metrics.p99ResponseTimeMs,
            minResponseTimeMs = metrics.minResponseTimeMs,
            maxResponseTimeMs = metrics.maxResponseTimeMs,
            requestsPerSecond = metrics.requestsPerSecond,
            scenarioResults = metrics.scenarioResults
        )
        
        // Save the report in different formats
        saveJsonReport(result, resultsDir.resolve("stress-test-report.json"))
        saveYamlReport(result, resultsDir.resolve("stress-test-report.yaml"))
        saveTextReport(result, resultsDir.resolve("stress-test-report.txt"))
        
        return result
    }
    
    /**
     * Find the most recent simulation.log file in the results directory.
     */
    private fun findLatestSimulationLog(resultsDir: Path): File? {
        val resultsDirFile = resultsDir.toFile()
        return resultsDirFile.walkTopDown()
            .filter { file -> file.isFile && file.name == "simulation.log" }
            .maxByOrNull { file -> file.lastModified() }
    }
    
    /**
     * Parse the simulation log file and extract metrics.
     */
    private fun parseSimulationLog(logFile: File): ParsedMetrics {
        // This is a simplified implementation. In a real-world scenario,
        // you would parse the Gatling simulation.log file format.
        // For now, we'll create some dummy data.
        
        val startTime = Instant.now().minusSeconds(60)
        val endTime = Instant.now()
        val durationMs = endTime.toEpochMilli() - startTime.toEpochMilli()
        
        // Create some dummy scenario results
        val scenarioResults = listOf(
            ScenarioResult(
                name = "Scenario 1",
                totalRequests = 1000,
                successfulRequests = 950,
                failedRequests = 50,
                meanResponseTimeMs = 120.0,
                p50ResponseTimeMs = 100.0,
                p95ResponseTimeMs = 200.0,
                p99ResponseTimeMs = 300.0,
                minResponseTimeMs = 50,
                maxResponseTimeMs = 500,
                requestsPerSecond = 16.7
            ),
            ScenarioResult(
                name = "Scenario 2",
                totalRequests = 500,
                successfulRequests = 490,
                failedRequests = 10,
                meanResponseTimeMs = 80.0,
                p50ResponseTimeMs = 70.0,
                p95ResponseTimeMs = 150.0,
                p99ResponseTimeMs = 200.0,
                minResponseTimeMs = 30,
                maxResponseTimeMs = 300,
                requestsPerSecond = 8.3
            )
        )
        
        // Calculate aggregated metrics
        val totalRequests = scenarioResults.sumOf { it.totalRequests }
        val successfulRequests = scenarioResults.sumOf { it.successfulRequests }
        val failedRequests = scenarioResults.sumOf { it.failedRequests }
        val meanResponseTimeMs = scenarioResults.map { it.meanResponseTimeMs }.average()
        val p50ResponseTimeMs = scenarioResults.map { it.p50ResponseTimeMs }.average()
        val p95ResponseTimeMs = scenarioResults.map { it.p95ResponseTimeMs }.average()
        val p99ResponseTimeMs = scenarioResults.map { it.p99ResponseTimeMs }.average()
        val minResponseTimeMs = scenarioResults.minOfOrNull { it.minResponseTimeMs } ?: 0
        val maxResponseTimeMs = scenarioResults.maxOfOrNull { it.maxResponseTimeMs } ?: 0
        val requestsPerSecond = scenarioResults.sumOf { it.requestsPerSecond }
        
        return ParsedMetrics(
            startTime = startTime,
            endTime = endTime,
            durationMs = durationMs,
            totalRequests = totalRequests,
            successfulRequests = successfulRequests,
            failedRequests = failedRequests,
            meanResponseTimeMs = meanResponseTimeMs,
            p50ResponseTimeMs = p50ResponseTimeMs,
            p95ResponseTimeMs = p95ResponseTimeMs,
            p99ResponseTimeMs = p99ResponseTimeMs,
            minResponseTimeMs = minResponseTimeMs,
            maxResponseTimeMs = maxResponseTimeMs,
            requestsPerSecond = requestsPerSecond,
            scenarioResults = scenarioResults
        )
    }
    
    /**
     * Save the report as JSON.
     */
    private fun saveJsonReport(result: StressTestResult, outputPath: Path) {
        try {
            Files.createDirectories(outputPath.parent)
            Files.writeString(
                outputPath,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            )
            logger.info("JSON report saved to: {}", outputPath)
        } catch (e: Exception) {
            logger.error("Failed to save JSON report", e)
        }
    }
    
    /**
     * Save the report as YAML.
     */
    private fun saveYamlReport(result: StressTestResult, outputPath: Path) {
        try {
            Files.createDirectories(outputPath.parent)
            Files.writeString(
                outputPath,
                yamlMapper.writeValueAsString(result),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            )
            logger.info("YAML report saved to: {}", outputPath)
        } catch (e: Exception) {
            logger.error("Failed to save YAML report", e)
        }
    }
    
    /**
     * Save the report as a formatted text file.
     */
    private fun saveTextReport(result: StressTestResult, outputPath: Path) {
        try {
            Files.createDirectories(outputPath.parent)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
            
            val report = StringBuilder()
            report.appendLine("=== STRESS TEST REPORT ===")
            report.appendLine("Start Time: ${formatter.format(result.startTime)}")
            report.appendLine("End Time: ${formatter.format(result.endTime)}")
            report.appendLine("Duration: ${result.durationMs / 1000.0} seconds")
            report.appendLine()
            report.appendLine("=== OVERALL METRICS ===")
            report.appendLine("Total Requests: ${result.totalRequests}")
            report.appendLine("Successful Requests: ${result.successfulRequests}")
            report.appendLine("Failed Requests: ${result.failedRequests}")
            report.appendLine("Success Rate: ${String.format("%.2f", result.successRate)}%")
            report.appendLine("Mean Response Time: ${String.format("%.2f", result.meanResponseTimeMs)} ms")
            report.appendLine("50th Percentile: ${String.format("%.2f", result.p50ResponseTimeMs)} ms")
            report.appendLine("95th Percentile: ${String.format("%.2f", result.p95ResponseTimeMs)} ms")
            report.appendLine("99th Percentile: ${String.format("%.2f", result.p99ResponseTimeMs)} ms")
            report.appendLine("Min Response Time: ${result.minResponseTimeMs} ms")
            report.appendLine("Max Response Time: ${result.maxResponseTimeMs} ms")
            report.appendLine("Requests Per Second: ${String.format("%.2f", result.requestsPerSecond)}")
            report.appendLine()
            
            report.appendLine("=== SCENARIO METRICS ===")
            result.scenarioResults.forEach { scenario ->
                report.appendLine("Scenario: ${scenario.name}")
                report.appendLine("  Total Requests: ${scenario.totalRequests}")
                report.appendLine("  Successful Requests: ${scenario.successfulRequests}")
                report.appendLine("  Failed Requests: ${scenario.failedRequests}")
                report.appendLine("  Success Rate: ${String.format("%.2f", scenario.successRate)}%")
                report.appendLine("  Mean Response Time: ${String.format("%.2f", scenario.meanResponseTimeMs)} ms")
                report.appendLine("  50th Percentile: ${String.format("%.2f", scenario.p50ResponseTimeMs)} ms")
                report.appendLine("  95th Percentile: ${String.format("%.2f", scenario.p95ResponseTimeMs)} ms")
                report.appendLine("  99th Percentile: ${String.format("%.2f", scenario.p99ResponseTimeMs)} ms")
                report.appendLine("  Min Response Time: ${scenario.minResponseTimeMs} ms")
                report.appendLine("  Max Response Time: ${scenario.maxResponseTimeMs} ms")
                report.appendLine("  Requests Per Second: ${String.format("%.2f", scenario.requestsPerSecond)}")
                report.appendLine()
            }
            
            Files.writeString(
                outputPath,
                report.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            )
            logger.info("Text report saved to: {}", outputPath)
        } catch (e: Exception) {
            logger.error("Failed to save text report", e)
        }
    }
    
    /**
     * Helper class for storing parsed metrics.
     */
    private data class ParsedMetrics(
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
    )
}
