package com.stress.test.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.File
import java.util.*

/**
 * Configuration for stress testing.
 */
data class StressTestConfig(
    val baseUrl: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val maxConnections: Int,
    val concurrentUsers: Int,
    val rampUpSeconds: Int,
    val durationSeconds: Int,
    val reportDirectory: String,
    val headers: Map<String, String>,
    val scenarios: List<ScenarioConfig>
) {
    companion object {
        /**
         * Load configuration from a file.
         */
        fun fromFile(path: String): StressTestConfig {
            val configFile = File(path)
            if (!configFile.exists()) {
                throw IllegalArgumentException("Configuration file not found: $path")
            }
            
            val config = ConfigFactory.parseFile(configFile)
            
            // Check if the config has a stress-test section or is directly the stress-test section
            return if (config.hasPath("stress-test")) {
                fromConfig(config.getConfig("stress-test"))
            } else {
                // Assume the whole file is the stress-test config
                fromConfig(config)
            }
        }

        /**
         * Load configuration from a Config object.
         */
        private fun fromConfig(config: Config): StressTestConfig {
            val baseUrl = config.getString("base-url")
            val connectTimeoutMs = config.getInt("connect-timeout-ms")
            val readTimeoutMs = config.getInt("read-timeout-ms")
            val maxConnections = config.getInt("max-connections")
            val concurrentUsers = config.getInt("concurrent-users")
            val rampUpSeconds = config.getInt("ramp-up-seconds")
            val durationSeconds = config.getInt("duration-seconds")
            val reportDirectory = config.getString("report-directory")
            
            val headers = if (config.hasPath("headers")) {
                val headersConfig = config.getConfig("headers")
                headersConfig.entrySet().associate { it.key to headersConfig.getString(it.key) }
            } else {
                emptyMap()
            }
            
            val scenarios = if (config.hasPath("scenarios")) {
                config.getConfigList("scenarios").map { scenarioConfig ->
                    val name = scenarioConfig.getString("name")
                    val endpoint = scenarioConfig.getString("endpoint")
                    val method = scenarioConfig.getString("method")
                    val weight = if (scenarioConfig.hasPath("weight")) scenarioConfig.getInt("weight") else 100
                    val expectedStatus = if (scenarioConfig.hasPath("expected-status")) 
                        scenarioConfig.getInt("expected-status") else 200
                    val body = if (scenarioConfig.hasPath("body")) scenarioConfig.getString("body") else null
                    val dynamicHeaders = if (scenarioConfig.hasPath("dynamic-headers")) 
                        scenarioConfig.getBoolean("dynamic-headers") else false
                    
                    ScenarioConfig(name, endpoint, method, weight, expectedStatus, body, dynamicHeaders)
                }
            } else {
                emptyList()
            }
            
            return StressTestConfig(
                baseUrl,
                connectTimeoutMs,
                readTimeoutMs,
                maxConnections,
                concurrentUsers,
                rampUpSeconds,
                durationSeconds,
                reportDirectory,
                headers,
                scenarios
            )
        }

        /**
         * Create a default configuration.
         */
        fun default(): StressTestConfig {
            return StressTestConfig(
                baseUrl = "http://localhost:8080",
                connectTimeoutMs = 5000,
                readTimeoutMs = 30000,
                maxConnections = 50,
                concurrentUsers = 10,
                rampUpSeconds = 10,
                durationSeconds = 60,
                reportDirectory = "build/reports/gatling",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Accept" to "application/json"
                ),
                scenarios = listOf(
                    ScenarioConfig(
                        name = "Default GET Request",
                        endpoint = "/health",
                        method = "GET",
                        weight = 100,
                        expectedStatus = 200,
                        body = null,
                        dynamicHeaders = false
                    )
                )
            )
        }
    }
}

/**
 * Configuration for a test scenario.
 */
data class ScenarioConfig(
    val name: String,
    val endpoint: String,
    val method: String,
    val weight: Int,
    val expectedStatus: Int,
    val body: String?,
    val dynamicHeaders: Boolean
)
