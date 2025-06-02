# Stress Testing Module

A generic, reusable stress testing framework built with Kotlin and Gatling for performance testing REST APIs.

## Overview

This module provides a configurable framework for conducting stress tests against any REST API. It is designed to be:

- **Generic**: Can be used with any REST API
- **Configurable**: All test parameters can be customized via configuration files
- **Portable**: Can be easily copied to other repositories
- **Extensible**: Designed to be extended with custom scenarios and reporting

## Features

- HTTP/HTTPS endpoint stress testing
- Configurable concurrency levels and ramp-up periods
- Multiple scenario support with weighted distribution
- Detailed performance metrics (response times, throughput, error rates)
- Multiple report formats (JSON, YAML, text)
- Command-line interface for easy integration with CI/CD pipelines

## Usage

### Configuration

Create a custom configuration file based on the template in `src/main/resources/application.conf`:

```hocon
stress-test {
  baseUrl = "http://your-api-host:port"
  durationSeconds = 60
  rampUpSeconds = 10
  concurrentUsers = 100
  reportDirectory = "reports"
  
  http {
    connectTimeoutMs = 5000
    readTimeoutMs = 30000
    maxConnections = 1000
  }
  
  headers {
    "Content-Type" = "application/json"
    "Accept" = "application/json"
    "User-Id" = "test-user"
  }
  
  scenarios = [
    {
      name = "Your Scenario Name"
      weight = 50  # Percentage of total load
      endpoint = "/your/endpoint"
      method = "GET|POST|PUT|DELETE"
      body = """
        {
          "your": "json payload"
        }
      """
      expectedStatus = 200
    }
  ]
}
```

### Running Tests

#### From Gradle

```bash
./gradlew :stress-test:run --args="path/to/your/config.conf"
```

#### Using the JAR

Build the fat JAR:

```bash
./gradlew :stress-test:stressTestJar
```

Run the JAR:

```bash
java -jar stress-test/build/libs/stress-test-0.0.1-SNAPSHOT-stress-test.jar path/to/your/config.conf
```

## Adapting for Other Repositories

To use this module in another repository:

1. Copy the entire `stress-test` directory to your target repository
2. Add the module to your `settings.gradle.kts`:
   ```kotlin
   include("stress-test")
   ```
3. Create a custom configuration file for your specific API
4. Run the tests as described above

## Extending the Framework

### Adding Custom Scenarios

To add custom scenarios beyond the basic HTTP requests:

1. Create a new class that extends `io.gatling.javaapi.core.Simulation`
2. Implement your custom scenario logic
3. Update the `StressTestEngine` to use your custom simulation

### Custom Reporting

To add custom reporting formats:

1. Extend the `StressTestReporter` class
2. Implement your custom report generation logic
3. Update the `generateReport` method to include your custom format

## Dependencies

- Kotlin 1.9.x
- Gatling 3.10.x
- Typesafe Config 1.4.x
- Jackson 2.16.x
- SLF4J/Logback for logging

## Project Structure

```
stress-test/
├── build.gradle.kts           # Module build configuration
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── com/
│       │       └── stress/
│       │           └── test/
│       │               ├── StressTestEngine.kt          # Main entry point
│       │               ├── config/
│       │               │   └── StressTestConfig.kt      # Configuration handling
│       │               ├── engine/
│       │               │   └── StressTestEngine.kt      # Test execution engine
│       │               ├── model/
│       │               │   └── StressTestResult.kt      # Result data models
│       │               └── reporter/
│       │                   └── StressTestReporter.kt    # Results processing
│       └── resources/
│           └── application.conf                         # Default configuration
```
