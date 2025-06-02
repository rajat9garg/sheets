#!/bin/bash

# Script to run stress test for payment initiate endpoint

# Get the absolute path to the project root directory
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Ensure the payment service is running
echo "Checking if payment service is running..."
if ! curl -s http://localhost:8080/api/v1/health > /dev/null; then
  echo "Payment service does not appear to be running on http://localhost:8080/api/v1/health"
  echo "Please start the payment service before running this test."
  exit 1
fi

# Build the stress-test module if needed
echo "Building stress-test module..."
cd "$PROJECT_ROOT"
./gradlew :stress-test:build

# Clean any previous Gatling reports
echo "Cleaning previous Gatling reports..."
rm -rf "$PROJECT_ROOT/stress-test/build/reports/gatling/"

# Run the Gatling simulation directly with verbose output
echo "Running payment initiate stress test with Gatling..."
echo "This will generate real traffic to your server!"
echo "Sending 10 requests per second (5 UPI, 3 Credit Card, 2 Debit Card) for 30 seconds..."

# Run with debug logging enabled
./gradlew :stress-test:gatlingRun-com.stress.test.simulation.PaymentInitiateSimulation --info

# Find the latest report directory
LATEST_REPORT=$(find "$PROJECT_ROOT/stress-test/build/reports/gatling" -type d -name "paymentinitatesimulation-*" | sort -r | head -1)

if [ -n "$LATEST_REPORT" ]; then
  echo "Test completed successfully!"
  echo "To view the HTML report, open: $LATEST_REPORT/index.html"
  
  # Extract some basic stats from the simulation.log file
  echo "Quick summary:"
  grep "request count" "$LATEST_REPORT/simulation.log" | tail -1
  grep "response time" "$LATEST_REPORT/simulation.log" | tail -2
else
  echo "Test may have failed. No report directory was found."
  echo "Check the logs above for errors."
fi
