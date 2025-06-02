# Prompt for Creating API Stress Tests

## Task Description

I have a fully functional stress testing module built with Gatling for performance testing REST APIs. I need you to create new stress test scenarios for additional API endpoints using the existing code structure with minimal changes. The module is already set up with all the necessary components for executing Gatling tests and generating performance reports.

## Existing Module Structure

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

## What I Need You To Do

1. **Create New Gatling Simulation Files**:
   - Create new Scala simulation files in `src/gatling/scala/com/stress/test/simulation/` for each API endpoint to be tested
   - Follow the pattern in the existing `PaymentInitiateSimulation.scala` file
   - Customize the HTTP method, endpoint path, request body, and headers as needed
   - Set appropriate load parameters (users per second, duration)

2. **Create Run Scripts**:
   - Create new shell scripts based on the existing `run-payment-initiate-test.sh`
   - Update the simulation class name in the script to match your new simulation

3. **DO NOT modify**:
   - The existing Kotlin code structure
   - The build.gradle.kts configuration
   - The existing simulation files

## Example API Endpoints to Test

Here are the API endpoints I want to stress test:

1. **User Profile API**:
   ```
   GET /api/v1/user/profile/{userId}
   Headers:
   - userId: test-user-123
   ```

2. **Order Creation API**:
   ```
   POST /api/v1/order/create
   Headers:
   - userId: test-user-123
   - Idempotency-Key: [unique UUID]
   
   Body:
   {
     "items": [
       {"productId": "prod-123", "quantity": 2, "price": 1000.00},
       {"productId": "prod-456", "quantity": 1, "price": 500.00}
     ],
     "shippingAddress": {
       "street": "123 Main St",
       "city": "Bangalore",
       "state": "Karnataka",
       "zipCode": "560001"
     },
     "paymentMethod": "UPI"
   }
   ```

## Implementation Guidelines

1. **Follow the Existing Pattern**:
   - Look at the existing `PaymentInitiateSimulation.scala` as a template
   - Maintain the same structure and coding style
   - Use similar load patterns (constantUsersPerSec)

2. **Request Bodies**:
   - Use realistic data in request payloads
   - Include all required fields based on API specifications
   - Use dynamic values where appropriate (UUIDs, timestamps)

3. **Load Parameters**:
   - Start with moderate load (5-10 users per second)
   - Set test duration to 30-60 seconds
   - Include proper assertions and checks

4. **Run Scripts**:
   - Name scripts according to the API being tested (e.g., `run-user-profile-test.sh`)
   - Include service availability check before running the test
   - Display test results summary after completion

## Deliverables

1. New Gatling simulation files for each API endpoint
2. Run scripts for executing each test
3. Brief documentation on how to run and interpret the test results

## Technical Constraints

- Use Gatling 3.10.3
- Use Scala 2.13.x
- Ensure compatibility with both macOS and Linux environments

Please refer to the `STRESS_TEST_USAGE_GUIDE.md` for detailed instructions on creating new test cases and common simulation patterns.

## Additional Notes

- The stress test should be able to run with minimal configuration changes
- Include proper validation of responses in the test scenarios
- Ensure the simulations can handle dynamic values in request headers and bodies
- Focus on creating realistic test scenarios that accurately reflect real-world API usage
