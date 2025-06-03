#!/bin/bash

# Get the absolute path to the project root directory
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Ensure the target service is running
echo "Checking if service is running..."
if ! curl -s http://localhost:8080/v1/health > /dev/null; then
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
echo "Running spreadsheet stress test with Gatling..."
./gradlew :stress-test:gatlingRun-com.stress.test.simulation.SpreadsheetSimulation --info

# Display results
LATEST_REPORT=$(find "$PROJECT_ROOT/stress-test/build/reports/gatling" -type d -name "spreadsheetsimulation-*" | sort -r | head -1)
if [ -n "$LATEST_REPORT" ]; then
  echo "Test completed successfully!"
  echo "To view the HTML report, open: $LATEST_REPORT/index.html"
  echo "Quick summary:"
  grep "request count" "$LATEST_REPORT/simulation.log" | tail -1
  grep "response time" "$LATEST_REPORT/simulation.log" | tail -2
  
  # Extract key metrics for quick analysis
  echo "=== KEY METRICS ==="
  echo "Request count:"
  grep "request count" "$LATEST_REPORT/simulation.log" | tail -1
  
  echo "Mean response time:"
  grep "mean response time" "$LATEST_REPORT/simulation.log" | tail -1
  
  echo "95th percentile response time:"
  grep "95th percentile" "$LATEST_REPORT/simulation.log" | tail -1
  
  echo "Error percentage:"
  grep "percentage of failed" "$LATEST_REPORT/simulation.log" | tail -1
else
  echo "Test may have failed. No report directory was found."
  echo "Check the logs above for errors."
fi
