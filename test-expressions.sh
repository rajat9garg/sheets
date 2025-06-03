#!/bin/bash

# Set common variables
USER_ID=123
BASE_URL="http://localhost:8080/v1"
CONTENT_TYPE="Content-Type: application/json"
USER_HEADER="X-User-ID: $USER_ID"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_message() {
  local color=$1
  local message=$2
  echo -e "${color}${message}${NC}"
}

# Function to check if the application is running
check_app_running() {
  if ! curl -s "$BASE_URL/health" > /dev/null; then
    print_message "$RED" "Error: The application is not running. Please start it first."
    exit 1
  fi
  print_message "$GREEN" "✓ Application is running"
}

# Function to create a sheet and return the sheet ID
create_sheet() {
  print_message "$BLUE" "\n=== Creating a new sheet ==="
  local response=$(curl -s --location "$BASE_URL/sheet" \
    --header "$CONTENT_TYPE" \
    --header "$USER_HEADER" \
    --data '{
      "name": "Expression Test Sheet",
      "description": "Sheet for testing expressions with alphabetical columns"
    }')
  
  echo "Response: $response" # Debug output
  
  # Extract sheet ID using a more robust method
  local sheet_id=$(echo "$response" | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://')
  
  if [ -z "$sheet_id" ]; then
    print_message "$RED" "Error: Failed to create sheet"
    exit 1
  fi
  
  print_message "$GREEN" "✓ Created sheet with ID: $sheet_id"
  echo "$sheet_id"
}

# Function to create or update a cell
update_cell() {
  local sheet_id=$1
  local row=$2
  local column=$3
  local data=$4
  local description=$5
  
  print_message "$BLUE" "\n=== $description ==="
  print_message "$YELLOW" "Setting cell $column$row to: $data"
  
  local response=$(curl -s --location "$BASE_URL/sheet/$sheet_id/cell" \
    --header "$CONTENT_TYPE" \
    --header "$USER_HEADER" \
    --data "{
      \"row\": $row,
      \"column\": \"$column\",
      \"data\": \"$data\"
    }")
  
  local evaluated_value=$(echo "$response" | grep -o '"evaluatedValue":"[^"]*"' | head -1 | cut -d':' -f2 | tr -d '"')
  
  if [ -z "$evaluated_value" ]; then
    print_message "$RED" "Error: Failed to update cell $column$row"
  else
    print_message "$GREEN" "✓ Cell $column$row updated, evaluated value: $evaluated_value"
  fi
}

# Function to get a cell
get_cell() {
  local sheet_id=$1
  local row=$2
  local column=$3
  local description=$4
  
  print_message "$BLUE" "\n=== $description ==="
  
  local response=$(curl -s --location "$BASE_URL/sheet/$sheet_id/cell/$row/$column" \
    --header "$USER_HEADER")
  
  local data=$(echo "$response" | grep -o '"data":"[^"]*"' | head -1 | cut -d':' -f2 | tr -d '"')
  local evaluated_value=$(echo "$response" | grep -o '"evaluatedValue":"[^"]*"' | head -1 | cut -d':' -f2 | tr -d '"')
  
  print_message "$YELLOW" "Cell $column$row data: $data"
  print_message "$GREEN" "✓ Cell $column$row evaluated value: $evaluated_value"
}

# Function to try deleting a cell with dependencies
try_delete_cell() {
  local sheet_id=$1
  local row=$2
  local column=$3
  local description=$4
  
  print_message "$BLUE" "\n=== $description ==="
  
  local response=$(curl -s --location --request DELETE "$BASE_URL/sheet/$sheet_id/cell/$row/$column" \
    --header "$USER_HEADER")
  
  local status_code=$(curl -s -o /dev/null -w "%{http_code}" --location --request DELETE "$BASE_URL/sheet/$sheet_id/cell/$row/$column" \
    --header "$USER_HEADER")
  
  if [ "$status_code" == "409" ]; then
    print_message "$GREEN" "✓ Successfully prevented deletion of cell $column$row due to dependencies"
  elif [ "$status_code" == "204" ]; then
    print_message "$RED" "! Cell $column$row was deleted, but it should have been prevented due to dependencies"
  else
    print_message "$YELLOW" "? Unexpected status code $status_code when trying to delete cell $column$row"
  fi
}

# Main test script
main() {
  print_message "$BLUE" "Starting expression function tests with alphabetical columns"
  
  # Check if the application is running
  check_app_running
  
  # Create a new sheet
  SHEET_ID=$(create_sheet)
  
  # Test 1: SUM function
  print_message "$BLUE" "\n======= TEST 1: SUM FUNCTION ======="
  update_cell $SHEET_ID 1 "A" "10" "Setting value for A1"
  update_cell $SHEET_ID 1 "B" "20" "Setting value for B1"
  update_cell $SHEET_ID 1 "C" "=SUM(A1,B1)" "Testing SUM function with A1 notation"
  update_cell $SHEET_ID 1 "A" "100" "Updating A1 to test dependency update"
  get_cell $SHEET_ID 1 "C" "Checking if C1 was automatically updated"
  try_delete_cell $SHEET_ID 1 "A" "Trying to delete A1 which is referenced in C1"
  
  # Test 2: AVERAGE function
  print_message "$BLUE" "\n======= TEST 2: AVERAGE FUNCTION ======="
  update_cell $SHEET_ID 2 "A" "10" "Setting value for A2"
  update_cell $SHEET_ID 2 "B" "20" "Setting value for B2"
  update_cell $SHEET_ID 2 "C" "30" "Setting value for C2"
  update_cell $SHEET_ID 2 "D" "=AVERAGE(A2,B2,C2)" "Testing AVERAGE function with A1 notation"
  update_cell $SHEET_ID 2 "B" "40" "Updating B2 to test dependency update"
  get_cell $SHEET_ID 2 "D" "Checking if D2 was automatically updated"
  
  # Test 3: MIN and MAX functions
  print_message "$BLUE" "\n======= TEST 3: MIN and MAX FUNCTIONS ======="
  update_cell $SHEET_ID 3 "A" "15" "Setting value for A3"
  update_cell $SHEET_ID 3 "B" "5" "Setting value for B3"
  update_cell $SHEET_ID 3 "C" "25" "Setting value for C3"
  update_cell $SHEET_ID 3 "D" "=MIN(A3,B3,C3)" "Testing MIN function with A1 notation"
  update_cell $SHEET_ID 3 "E" "=MAX(A3,B3,C3)" "Testing MAX function with A1 notation"
  update_cell $SHEET_ID 3 "B" "1" "Updating B3 to test dependency update for MIN"
  update_cell $SHEET_ID 3 "C" "50" "Updating C3 to test dependency update for MAX"
  get_cell $SHEET_ID 3 "D" "Checking if D3 (MIN) was automatically updated"
  get_cell $SHEET_ID 3 "E" "Checking if E3 (MAX) was automatically updated"
  
  # Test 4: MULTIPLY and DIVIDE functions
  print_message "$BLUE" "\n======= TEST 4: MULTIPLY and DIVIDE FUNCTIONS ======="
  update_cell $SHEET_ID 4 "A" "10" "Setting value for A4"
  update_cell $SHEET_ID 4 "B" "5" "Setting value for B4"
  update_cell $SHEET_ID 4 "C" "=A4*B4" "Testing multiplication with A1 notation"
  update_cell $SHEET_ID 4 "D" "=A4/B4" "Testing division with A1 notation"
  update_cell $SHEET_ID 4 "A" "20" "Updating A4 to test dependency update"
  get_cell $SHEET_ID 4 "C" "Checking if C4 (multiplication) was automatically updated"
  get_cell $SHEET_ID 4 "D" "Checking if D4 (division) was automatically updated"
  
  # Test 5: Nested expressions
  print_message "$BLUE" "\n======= TEST 5: NESTED EXPRESSIONS ======="
  update_cell $SHEET_ID 5 "A" "10" "Setting value for A5"
  update_cell $SHEET_ID 5 "B" "20" "Setting value for B5"
  update_cell $SHEET_ID 5 "C" "30" "Setting value for C5"
  update_cell $SHEET_ID 5 "D" "=SUM(A5,B5)" "Setting intermediate SUM expression"
  update_cell $SHEET_ID 5 "E" "=AVERAGE(C5,D5)" "Testing nested expression with A1 notation"
  update_cell $SHEET_ID 5 "A" "40" "Updating A5 to test cascading dependency update"
  get_cell $SHEET_ID 5 "D" "Checking if D5 (SUM) was automatically updated"
  get_cell $SHEET_ID 5 "E" "Checking if E5 (AVERAGE) was automatically updated"
  
  # Test 6: Cell ranges
  print_message "$BLUE" "\n======= TEST 6: CELL RANGES ======="
  update_cell $SHEET_ID 6 "A" "5" "Setting value for A6"
  update_cell $SHEET_ID 6 "B" "10" "Setting value for B6"
  update_cell $SHEET_ID 6 "C" "15" "Setting value for C6"
  update_cell $SHEET_ID 6 "D" "20" "Setting value for D6"
  update_cell $SHEET_ID 6 "E" "=SUM(A6:D6)" "Testing SUM with cell range in A1 notation"
  update_cell $SHEET_ID 6 "B" "25" "Updating B6 to test dependency update with range"
  get_cell $SHEET_ID 6 "E" "Checking if E6 was automatically updated"
  
  # Test 7: Multiple sheets referencing (if supported)
  print_message "$BLUE" "\n======= TEST 7: CROSS-SHEET REFERENCES (if supported) ======="
  update_cell $SHEET_ID 7 "A" "=A1+B1" "Testing reference to cells in another row"
  get_cell $SHEET_ID 7 "A" "Checking cross-row reference"
  
  print_message "$GREEN" "\n✓ All tests completed!"
}

# Run the main function
main
