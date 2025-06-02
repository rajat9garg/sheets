# Stress Test Module Guide

## Overview

This document provides comprehensive instructions for implementing and using the Stress Test Module, a flexible framework for performance testing REST APIs. The module uses Gatling for executing high-volume concurrent requests and provides detailed performance reports.

## Table of Contents

1. [Module Structure](#module-structure)
2. [Key Components](#key-components)
3. [Configuration System](#configuration-system)
4. [Creating New Stress Tests](#creating-new-stress-tests)
5. [Running Stress Tests](#running-stress-tests)
6. [Analyzing Results](#analyzing-results)
7. [Implementation Guidelines](#implementation-guidelines)
8. [Troubleshooting](#troubleshooting)

## Module Structure

```
stress-test/
├── build.gradle.kts            # Gradle build configuration with Gatling plugin
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/stress/test/
│   │   │       ├── config/     # Configuration loading and parsing
│   │   │       ├── engine/     # Test execution engine
│   │   │       ├── model/      # Data models for test configuration
│   │   │       └── reporter/   # Result processing and reporting
│   │   └── resources/          # Configuration files
│   ├── test/                   # Unit tests for the framework
│   └── gatling/
│       └── scala/
│           └── com/stress/test/simulation/  # Gatling simulation files
└── run-*.sh                    # Test execution scripts
```

## Key Components

### 1. StressTestConfig

Responsible for loading and parsing test configurations from files. Supports both YAML and HOCON formats.

Key features:
- Loading from file path or classpath
- Default configuration generation
- Support for dynamic headers (e.g., idempotency keys)
- Weighted scenario distribution

### 2. StressTestEngine

Core component that:
- Initializes Gatling with proper configuration
- Executes test scenarios
- Processes and reports results

### 3. Gatling Simulations

Scala files that define the actual test scenarios, including:
- HTTP requests with dynamic parameters
- Request bodies and headers
- Response validation
- User injection profiles (constant rate, ramp-up, etc.)

## Configuration System

Test configurations are defined in `.conf` or `.yaml` files with the following structure:

```hocon
stress-test {
  baseUrl = "http://localhost:8080/api/v1"
  concurrentUsers = 100
  rampUpSeconds = 30
  durationSeconds = 120
  reportDirectory = "build/reports/gatling"
  
  headers {
    "Content-Type" = "application/json"
    "userId" = "test-user-123"
  }
  
  scenarios = [
    {
      name = "Example API Call"
      endpoint = "/example"
      method = "POST"
      weight = 70
      expectedStatus = 200
      dynamicHeaders = true
      body = """{"key": "value"}"""
    }
  ]
}
```

## Creating New Stress Tests

### Option 1: Configuration-based Tests

1. Create a new configuration file in `src/main/resources/` (e.g., `my-api-test.conf`)
2. Define your test scenarios in the configuration
3. Create a run script that uses the StressTestEngine with your configuration

### Option 2: Custom Gatling Simulations

For more complex scenarios:

1. Create a new Scala file in `src/gatling/scala/com/stress/test/simulation/` (e.g., `MyApiSimulation.scala`)
2. Define your simulation class extending Gatling's `Simulation`
3. Implement custom scenarios with specific request patterns
4. Create a run script that executes your simulation

## Running Stress Tests

Use the provided run scripts or create new ones following this pattern:

```bash
#!/bin/bash

# Get the absolute path to the project root directory
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Ensure the target service is running
echo "Checking if service is running..."
if ! curl -s http://localhost:8080/api/v1/health > /dev/null; then
  echo "Service does not appear to be running"
  exit 1
fi

# Build the stress-test module
echo "Building stress-test module..."
cd "$PROJECT_ROOT"
./gradlew :stress-test:build

# Clean previous reports
echo "Cleaning previous Gatling reports..."
rm -rf "$PROJECT_ROOT/stress-test/build/reports/gatling/"

# Run the Gatling simulation
echo "Running stress test with Gatling..."
./gradlew :stress-test:gatlingRun-com.stress.test.simulation.MyApiSimulation --info

# Display results
LATEST_REPORT=$(find "$PROJECT_ROOT/stress-test/build/reports/gatling" -type d -name "myapisimulation-*" | sort -r | head -1)
if [ -n "$LATEST_REPORT" ]; then
  echo "Test completed successfully!"
  echo "To view the HTML report, open: $LATEST_REPORT/index.html"
  echo "Quick summary:"
  grep "request count" "$LATEST_REPORT/simulation.log" | tail -1
  grep "response time" "$LATEST_REPORT/simulation.log" | tail -2
else
  echo "Test may have failed. No report directory was found."
fi
```

## Analyzing Results

After running a test, Gatling generates detailed HTML reports in the `build/reports/gatling` directory. These reports include:

- Request count and distribution
- Response time statistics (min, max, mean, percentiles)
- Error rates and types
- Requests per second
- Visual charts and graphs

## Implementation Guidelines

### Creating a New API Test

1. **Identify API Endpoint**:
   - Determine the endpoint URL, method, and expected response
   - Identify required headers and request body structure

2. **Create Simulation File**:
   ```scala
   package com.stress.test.simulation
   
   import io.gatling.core.Predef._
   import io.gatling.http.Predef._
   import scala.concurrent.duration._
   import java.util.UUID
   import scala.language.postfixOps
   
   class MyApiSimulation extends Simulation {
     
     // Base URL and common headers
     val httpProtocol = http
       .baseUrl("http://localhost:8080/api/v1")
       .acceptHeader("application/json")
       .contentTypeHeader("application/json")
       .userAgentHeader("StressTest/1.0")
       .header("userId", "test-user-123")
     
     // Define scenario
     val myApiScenario = scenario("My API Test")
       .exec(
         http("My API Request")
           .post("/my-endpoint")
           .header("Idempotency-Key", session => UUID.randomUUID().toString)
           .body(StringBody("""{"key": "value"}"""))
           .check(status.is(200))
       )
     
     // Test setup
     setUp(
       myApiScenario.inject(
         constantUsersPerSec(10) during(30.seconds)
       ).protocols(httpProtocol)
     ).maxDuration(60.seconds)
   }
   ```

3. **Create Run Script**:
   - Copy and modify an existing script to run your new simulation

4. **Execute and Analyze**:
   - Run the script and review the generated reports

### Dynamic Headers

For APIs requiring unique values per request (like idempotency keys):

```scala
.header("Idempotency-Key", session => UUID.randomUUID().toString)
```

### Request Body Templates

For complex request bodies with dynamic values:

```scala
.body(StringBody("""
  {
    "amount": ${amount},
    "reference": "${reference}",
    "timestamp": "${timestamp}"
  }
""")).asJson
```

Where `amount`, `reference`, and `timestamp` are session variables set earlier in the scenario.

## Troubleshooting

### Common Issues

1. **Compilation Errors**:
   - Ensure Scala syntax is correct
   - Import all required Gatling classes
   - Add `import scala.language.postfixOps` for duration syntax

2. **Simulation Not Found**:
   - Verify the simulation class is in the correct package
   - Check that the file is in `src/gatling/scala/com/stress/test/simulation/`

3. **Connection Errors**:
   - Confirm the target service is running
   - Verify the base URL is correct
   - Check network connectivity and firewall settings

4. **HTTP 500 Errors**:
   - Review server logs for exceptions
   - Validate request payload format
   - Check for valid combinations of request parameters

5. **No Reports Generated**:
   - Ensure Gatling execution completed successfully
   - Check for write permissions in the reports directory
   - Verify the Gatling plugin configuration in build.gradle.kts

### Debugging Tips

1. Use `--info` or `--debug` flags with Gradle for more detailed output
2. Add `.check(bodyString.saveAs("responseBody"))` to inspect response contents
3. Use `.exec(session => { println(session); session })` to debug session variables
