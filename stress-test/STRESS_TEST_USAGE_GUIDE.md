# Stress Test Usage Guide

## Overview

This guide explains how to use the existing stress-test module to create new API test cases with minimal changes to the codebase. The module is designed to be easily extended for testing different API endpoints.

## Existing Structure

The stress-test module has the following structure:

```
stress-test/
├── build.gradle.kts            # Gradle configuration with Gatling plugin
├── src/
│   ├── main/kotlin/com/stress/test/
│   │   ├── StressTestEngine.kt # Core engine for running tests
│   │   ├── StressTestConfig.kt # Configuration loading
│   │   └── StressTestReporter.kt # Results processing
│   ├── gatling/scala/com/stress/test/simulation/
│   │   └── PaymentInitiateSimulation.scala # Existing simulation
│   └── main/resources/         # Configuration files
└── run-payment-initiate-test.sh # Test execution script
```

## Creating a New API Test Case

To create a stress test for a new API endpoint, you only need to:

1. Create a new Gatling simulation file
2. Create a new run script

### Step 1: Create a New Simulation File

Create a new file in `src/gatling/scala/com/stress/test/simulation/` named after your API (e.g., `UserProfileSimulation.scala`):

```scala
package com.stress.test.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.util.UUID
import scala.language.postfixOps

class UserProfileSimulation extends Simulation {
  
  // Base URL and common headers
  val httpProtocol = http
    .baseUrl("http://localhost:8080/api/v1")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("StressTest/1.0")
    .header("userId", "test-user-123")
  
  // Define your API scenario
  val userProfileScenario = scenario("User Profile API Test")
    .exec(
      http("Get User Profile Request")
        .get("/user/profile/{userId}")
        .header("Idempotency-Key", session => UUID.randomUUID().toString)
        .routeParam("userId", "12345")
        .check(status.is(200))
    )
  
  // Test setup
  setUp(
    userProfileScenario.inject(
      constantUsersPerSec(10) during(30.seconds)
    ).protocols(httpProtocol)
  ).maxDuration(60.seconds)
}
```

Key points to customize:
- Change the class name to match your API
- Update the HTTP method (GET, POST, PUT, DELETE)
- Set the correct endpoint path
- Customize request parameters, headers, and body as needed
- Adjust the load parameters (users per second, duration)

### Step 2: Create a Run Script

Copy the existing run script and modify it for your new test:

```bash
cp run-payment-initiate-test.sh run-user-profile-test.sh
```

Edit the new script to point to your simulation class:

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
./gradlew :stress-test:gatlingRun-com.stress.test.simulation.UserProfileSimulation --info

# Display results
LATEST_REPORT=$(find "$PROJECT_ROOT/stress-test/build/reports/gatling" -type d -name "userprofilesimulation-*" | sort -r | head -1)
if [ -n "$LATEST_REPORT" ]; then
  echo "Test completed successfully!"
  echo "To view the HTML report, open: $LATEST_REPORT/index.html"
  echo "Quick summary:"
  grep "request count" "$LATEST_REPORT/simulation.log" | tail -1
  grep "response time" "$LATEST_REPORT/simulation.log" | tail -2
else
  echo "Test may have failed. No report directory was found."
  echo "Check the logs above for errors."
fi
```

Make the script executable:

```bash
chmod +x run-user-profile-test.sh
```

### Step 3: Run the Test

Execute your new test script:

```bash
./run-user-profile-test.sh
```

## Common Simulation Patterns

### GET Request

```scala
http("Get Request")
  .get("/endpoint/{id}")
  .routeParam("id", "12345")
  .check(status.is(200))
```

### POST Request with JSON Body

```scala
http("Post Request")
  .post("/endpoint")
  .header("Idempotency-Key", session => UUID.randomUUID().toString)
  .body(StringBody("""
    {
      "key1": "value1",
      "key2": 123,
      "key3": true
    }
  """))
  .check(status.is(200))
```

### PUT Request

```scala
http("Put Request")
  .put("/endpoint/{id}")
  .routeParam("id", "12345")
  .body(StringBody("""{"status": "updated"}"""))
  .check(status.is(200))
```

### DELETE Request

```scala
http("Delete Request")
  .delete("/endpoint/{id}")
  .routeParam("id", "12345")
  .check(status.is(204))
```

## Advanced Simulation Features

### Multiple Scenarios with Different Weights

```scala
setUp(
  scenario1.inject(constantUsersPerSec(5) during(30.seconds)).protocols(httpProtocol),
  scenario2.inject(constantUsersPerSec(3) during(30.seconds)).protocols(httpProtocol),
  scenario3.inject(constantUsersPerSec(2) during(30.seconds)).protocols(httpProtocol)
).maxDuration(60.seconds)
```

### Dynamic Values in Request Bodies

```scala
http("Dynamic Request")
  .post("/endpoint")
  .body(StringBody(session => {
    val userId = session("userId").as[String]
    val timestamp = System.currentTimeMillis()
    s"""{"userId": "$userId", "timestamp": $timestamp}"""
  }))
```

### Response Validation

```scala
http("Validated Request")
  .get("/endpoint")
  .check(
    status.is(200),
    jsonPath("$.status").is("success"),
    jsonPath("$.data[0].id").exists
  )
```

## Analyzing Test Results

After running a test, Gatling generates a detailed HTML report in the `build/reports/gatling` directory. This report includes:

- Request count and distribution
- Response time statistics (min, max, mean, percentiles)
- Error rates and types
- Requests per second
- Visual charts and graphs

Key metrics to analyze:
1. **Response Time**: Look at mean, median, and 95th percentile
2. **Throughput**: Requests per second the system can handle
3. **Error Rate**: Percentage of failed requests
4. **Distribution**: How response times are distributed

## Troubleshooting

### Common Issues

1. **Simulation Not Found**:
   - Verify the simulation class name in the run script matches the actual class name
   - Check that the file is in the correct directory

2. **Connection Errors**:
   - Confirm the target service is running
   - Verify the base URL is correct

3. **HTTP 500 Errors**:
   - Review server logs for exceptions
   - Validate request payload format
   - Check for valid combinations of request parameters

4. **No Reports Generated**:
   - Ensure Gatling execution completed successfully
   - Check for write permissions in the reports directory
