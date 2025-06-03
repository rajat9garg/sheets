#!/bin/bash

# Test script for verifying locking and circular dependency detection

BASE_URL="http://localhost:8080/v1"
SHEET_ID=1
USER1="user1"
USER2="user2"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting locking and circular dependency tests...${NC}"

# Function to create a cell
create_cell() {
  local sheet_id=$1
  local row=$2
  local col=$3
  local data=$4
  local user=$5

  echo -e "${YELLOW}Creating cell $sheet_id:$row:$col with data '$data' as user $user${NC}"
  
  response=$(curl -s -w "%{http_code}" -X POST "$BASE_URL/sheet/$sheet_id/cell" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: $user" \
    -d "{\"row\": $row, \"col\": \"$col\", \"data\": \"$data\"}")
  
  http_code=${response: -3}
  response_body=${response:0:${#response}-3}
  
  echo "HTTP Status: $http_code"
  echo "Response: $response_body"
  
  if [[ "$http_code" == "200" || "$http_code" == "201" ]]; then
    echo -e "${GREEN}Cell created successfully${NC}"
    return 0
  else
    echo -e "${RED}Failed to create cell${NC}"
    return 1
  fi
}

# Function to update a cell
update_cell() {
  local sheet_id=$1
  local row=$2
  local col=$3
  local data=$4
  local user=$5
  local expected_status=$6

  echo -e "${YELLOW}Updating cell $sheet_id:$row:$col with data '$data' as user $user${NC}"
  
  response=$(curl -s -w "%{http_code}" -X PUT "$BASE_URL/sheet/$sheet_id/cell/$row/$col" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: $user" \
    -d "{\"data\": \"$data\"}")
  
  http_code=${response: -3}
  response_body=${response:0:${#response}-3}
  
  echo "HTTP Status: $http_code"
  echo "Response: $response_body"
  
  if [[ "$http_code" == "$expected_status" ]]; then
    echo -e "${GREEN}Test passed: Received expected status code $expected_status${NC}"
    return 0
  else
    echo -e "${RED}Test failed: Expected status code $expected_status but got $http_code${NC}"
    return 1
  fi
}

# Delete all cells in the sheet to start fresh
echo -e "${YELLOW}Cleaning up sheet $SHEET_ID...${NC}"
curl -s -X DELETE "$BASE_URL/sheet/$SHEET_ID" -H "X-User-Id: $USER1"
echo "Sheet cleaned"

# Test 1: Create basic cells
echo -e "\n${YELLOW}Test 1: Creating basic cells${NC}"
create_cell $SHEET_ID 1 "A" "10" $USER1
create_cell $SHEET_ID 1 "B" "20" $USER1
create_cell $SHEET_ID 1 "C" "=A1+B1" $USER1

# Test 2: Circular dependency detection
echo -e "\n${YELLOW}Test 2: Testing circular dependency detection${NC}"
update_cell $SHEET_ID 1 "A" "=C1" $USER1 400

# Test 3: Lock conflict detection
echo -e "\n${YELLOW}Test 3: Testing lock conflict detection${NC}"

# Start a background process that holds a lock
(
  echo "Background process: Updating cell A1 and sleeping for 5 seconds..."
  curl -s -X PUT "$BASE_URL/sheet/$SHEET_ID/cell/1/A" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: $USER1" \
    -d "{\"data\": \"15\"}" > /dev/null
  
  # Sleep to simulate a long-running operation
  sleep 5
  
  echo "Background process: Finished"
) &

# Wait a moment for the background process to acquire the lock
sleep 1

# Try to update the same cell with a different user
echo "Foreground process: Attempting to update the same cell while it's locked..."
update_cell $SHEET_ID 1 "A" "25" $USER2 409

# Wait for background process to complete
wait

# Test 4: Sheet-level locking
echo -e "\n${YELLOW}Test 4: Testing sheet-level locking${NC}"

# Start a background process that holds a sheet lock
(
  echo "Background process: Updating cell B1 and sleeping for 5 seconds..."
  curl -s -X PUT "$BASE_URL/sheet/$SHEET_ID/cell/1/B" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: $USER1" \
    -d "{\"data\": \"25\"}" > /dev/null
  
  # Sleep to simulate a long-running operation
  sleep 5
  
  echo "Background process: Finished"
) &

# Wait a moment for the background process to acquire the lock
sleep 1

# Try to update a different cell in the same sheet
echo "Foreground process: Attempting to update a different cell while sheet is locked..."
update_cell $SHEET_ID 1 "C" "=A1+B1+10" $USER2 409

# Wait for background process to complete
wait

echo -e "\n${GREEN}All tests completed!${NC}"
