package com.stress.test

import com.stress.test.config.StressTestConfig
import com.stress.test.engine.StressTestEngine
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import java.io.File

/**
 * Main entry point for the stress testing framework.
 */
object StressTestEngineKt {
    private val logger = LoggerFactory.getLogger(StressTestEngineKt::class.java)
    
    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("Starting stress test engine")
        
        try {
            // Check for config path from system property or command line args
            val configPath = System.getProperty("config") ?: if (args.isNotEmpty()) args[0] else null
            val config = if (configPath != null) {
                logger.info("Using configuration from: $configPath")
                StressTestConfig.fromFile(configPath)
            } else {
                logger.info("Using default configuration")
                StressTestConfig.default()
            }
            
            logger.info("Initializing stress test engine")
            val engine = StressTestEngine(config)
            
            // We're now using dedicated simulation files in src/test/scala
            // instead of generating them dynamically
            
            logger.info("Running stress test")
            
            val result = engine.runTest()
            
            logger.info("Stress test completed")
            logger.info("Total requests: ${result.totalRequests}")
            logger.info("Success rate: ${String.format("%.2f", result.successRate)}%")
            logger.info("Mean response time: ${String.format("%.2f", result.meanResponseTimeMs)} ms")
            logger.info("Requests per second: ${String.format("%.2f", result.requestsPerSecond)}")
            logger.info("Reports saved to: ${config.reportDirectory}")
            
        } catch (e: Exception) {
            logger.error("Error running stress test", e)
            System.exit(1)
        }
    }
}
