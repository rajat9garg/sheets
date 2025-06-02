package com.stress.test.engine

import com.stress.test.config.StressTestConfig
import com.stress.test.model.StressTestResult
import com.stress.test.model.ScenarioResult
import com.stress.test.reporter.StressTestReporter
import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.protocol.HttpProtocolBuilder
import java.nio.file.Paths
import java.io.File
import java.util.UUID

/**
 * Main engine for executing stress tests.
 * Uses Gatling under the hood for the actual load testing.
 */
class StressTestEngine(private val config: StressTestConfig) {
    
    /**
     * Run the stress test with the provided configuration.
     * @return StressTestResult with aggregated metrics
     */
    fun runTest(): StressTestResult {
        val props = GatlingPropertiesBuilder()
            .resultsDirectory(config.reportDirectory)
            .simulationClass("com.stress.test.simulation.PaymentInitiateSimulation")
            .build()
            
        val result = Gatling.fromMap(props)
        
        // Process and return results
        return StressTestReporter.generateReport(Paths.get(config.reportDirectory))
    }
    
    /**
     * Creates a Gatling simulation class.
     * Note: This is a simplified implementation as we're using the Gatling Gradle plugin
     * which handles the simulation execution differently.
     */
    fun createSimulationClass(): String {
        val simulationContent = """
            package com.stress.test.simulation
            
            import io.gatling.core.Predef._
            import io.gatling.http.Predef._
            import scala.concurrent.duration._
            import java.util.UUID
            import scala.language.postfixOps
            
            class StressTestSimulation extends Simulation {
              
              val httpProtocol = http
                .baseUrl("${config.baseUrl}")
                .acceptHeader("application/json")
                .contentTypeHeader("application/json")
                .userAgentHeader("StressTest/1.0")
            
              // Add custom headers from configuration
              ${generateHeadersCode()}
                
              ${generateScenarios()}
              
              setUp(
                ${generateSetup()}
              ).protocols(httpProtocol)
               .maxDuration(${config.durationSeconds}.seconds)
            }
        """.trimIndent()
        
        return simulationContent
    }
    
    /**
     * Generate Gatling headers configuration code.
     */
    private fun generateHeadersCode(): String {
        if (config.headers.isEmpty()) {
            return ""
        }
        
        return config.headers.entries.joinToString("\n") { (name, value) -> 
            "  .header(\"$name\", \"$value\")"
        }
    }
    
    /**
     * Generate Gatling scenario definitions based on configuration.
     */
    private fun generateScenarios(): String {
        return config.scenarios.joinToString("\n\n") { scenarioConfig ->
            val requestBody = if (scenarioConfig.body != null) {
                "StringBody(\"\"\"${scenarioConfig.body.replace("\"\"\"", "\\\"\\\"\\\"")}\"\"\")".trimIndent()
            } else {
                ""
            }
            
            // Generate dynamic headers code if required
            val dynamicHeadersCode = if (scenarioConfig.dynamicHeaders) {
                """
                .header("Idempotency-Key", session => UUID.randomUUID().toString)
                """
            } else {
                ""
            }
            
            val requestDefinition = when (scenarioConfig.method.uppercase()) {
                "GET" -> "http(\"${scenarioConfig.name}\").get(\"${scenarioConfig.endpoint}\")"
                "POST" -> {
                    if (scenarioConfig.body != null) {
                        "http(\"${scenarioConfig.name}\").post(\"${scenarioConfig.endpoint}\").body($requestBody)"
                    } else {
                        "http(\"${scenarioConfig.name}\").post(\"${scenarioConfig.endpoint}\")"
                    }
                }
                "PUT" -> {
                    if (scenarioConfig.body != null) {
                        "http(\"${scenarioConfig.name}\").put(\"${scenarioConfig.endpoint}\").body($requestBody)"
                    } else {
                        "http(\"${scenarioConfig.name}\").put(\"${scenarioConfig.endpoint}\")"
                    }
                }
                "DELETE" -> "http(\"${scenarioConfig.name}\").delete(\"${scenarioConfig.endpoint}\")"
                else -> throw IllegalArgumentException("Unsupported HTTP method: ${scenarioConfig.method}")
            }
            
            """
            val ${scenarioConfig.name.replace(" ", "")}Scenario = scenario("${scenarioConfig.name}")
              .exec(
                $requestDefinition
                  $dynamicHeadersCode
                  .check(status.is(${scenarioConfig.expectedStatus}))
              )
            """.trimIndent()
        }
    }
    
    /**
     * Generate Gatling setup code based on configuration.
     */
    private fun generateSetup(): String {
        return config.scenarios.joinToString(",\n") { scenarioConfig ->
            val weight = scenarioConfig.weight
            val users = config.concurrentUsers * weight / 100
            
            """
            ${scenarioConfig.name.replace(" ", "")}Scenario.inject(
              rampUsers($users).during(${config.rampUpSeconds}.seconds)
            )
            """.trimIndent()
        }
    }
}
