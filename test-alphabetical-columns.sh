#!/bin/bash

# Set common variables
USER_ID=123
BASE_URL="http://localhost:8080/v1"

echo "=== Creating a new sheet ==="
SHEET_RESPONSE=$(curl -s --location "$BASE_URL/sheet" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "name": "Expression Test Sheet",
    "description": "Sheet for testing expressions with alphabetical columns"
  }')

echo "$SHEET_RESPONSE"
SHEET_ID=$(echo "$SHEET_RESPONSE" | grep -o '"id":[0-9]*' | sed 's/"id"://')
echo "Created sheet with ID: $SHEET_ID"

echo -e "\n=== Test 1: SUM function ==="
echo "Creating cell A1 with value 10"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 1,
    "column": "A",
    "data": "10"
  }'

echo -e "\nCreating cell B1 with value 20"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 1,
    "column": "B",
    "data": "20"
  }'

echo -e "\nCreating cell C1 with SUM expression =SUM(A1,B1)"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 1,
    "column": "C",
    "data": "=SUM(A1,B1)"
  }'

echo -e "\nUpdating cell A1 to value 100"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 1,
    "column": "A",
    "data": "100"
  }'

echo -e "\nGetting cell C1 to check if it was updated"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/1/C" \
  --header "X-User-ID: $USER_ID"

echo -e "\nTrying to delete cell A1 (should be prevented due to dependency)"
curl -s -i --location --request DELETE "$BASE_URL/sheet/$SHEET_ID/cell/1/A" \
  --header "X-User-ID: $USER_ID"

echo -e "\n=== Test 2: AVERAGE function ==="
echo "Creating cell A2 with value 10"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 2,
    "column": "A",
    "data": "10"
  }'

echo -e "\nCreating cell B2 with value 20"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 2,
    "column": "B",
    "data": "20"
  }'

echo -e "\nCreating cell C2 with value 30"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 2,
    "column": "C",
    "data": "30"
  }'

echo -e "\nCreating cell D2 with AVERAGE expression =AVERAGE(A2,B2,C2)"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 2,
    "column": "D",
    "data": "=AVERAGE(A2,B2,C2)"
  }'

echo -e "\nUpdating cell B2 to value 40"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 2,
    "column": "B",
    "data": "40"
  }'

echo -e "\nGetting cell D2 to check if it was updated"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/2/D" \
  --header "X-User-ID: $USER_ID"

echo -e "\n=== Test 3: MIN and MAX functions ==="
echo "Creating cell A3 with value 15"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 3,
    "column": "A",
    "data": "15"
  }'

echo -e "\nCreating cell B3 with value 5"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 3,
    "column": "B",
    "data": "5"
  }'

echo -e "\nCreating cell C3 with value 25"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 3,
    "column": "C",
    "data": "25"
  }'

echo -e "\nCreating cell D3 with MIN expression =MIN(A3,B3,C3)"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 3,
    "column": "D",
    "data": "=MIN(A3,B3,C3)"
  }'

echo -e "\nCreating cell E3 with MAX expression =MAX(A3,B3,C3)"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 3,
    "column": "E",
    "data": "=MAX(A3,B3,C3)"
  }'

echo -e "\nUpdating cell B3 to value 1"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 3,
    "column": "B",
    "data": "1"
  }'

echo -e "\nUpdating cell C3 to value 50"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 3,
    "column": "C",
    "data": "50"
  }'

echo -e "\nGetting cell D3 to check if MIN was updated"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/3/D" \
  --header "X-User-ID: $USER_ID"

echo -e "\nGetting cell E3 to check if MAX was updated"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/3/E" \
  --header "X-User-ID: $USER_ID"

echo -e "\n=== Test 4: Arithmetic operations ==="
echo "Creating cell A4 with value 10"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 4,
    "column": "A",
    "data": "10"
  }'

echo -e "\nCreating cell B4 with value 5"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 4,
    "column": "B",
    "data": "5"
  }'

echo -e "\nCreating cell C4 with multiplication expression =A4*B4"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 4,
    "column": "C",
    "data": "=A4*B4"
  }'

echo -e "\nCreating cell D4 with division expression =A4/B4"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 4,
    "column": "D",
    "data": "=A4/B4"
  }'

echo -e "\nUpdating cell A4 to value 20"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 4,
    "column": "A",
    "data": "20"
  }'

echo -e "\nGetting cell C4 to check if multiplication was updated"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/4/C" \
  --header "X-User-ID: $USER_ID"

echo -e "\nGetting cell D4 to check if division was updated"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/4/D" \
  --header "X-User-ID: $USER_ID"

echo -e "\n=== Test 5: Cell ranges ==="
echo "Creating cell A5 with value 5"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 5,
    "column": "A",
    "data": "5"
  }'

echo -e "\nCreating cell B5 with value 10"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 5,
    "column": "B",
    "data": "10"
  }'

echo -e "\nCreating cell C5 with value 15"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 5,
    "column": "C",
    "data": "15"
  }'

echo -e "\nCreating cell D5 with SUM range expression =SUM(A5:C5)"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 5,
    "column": "D",
    "data": "=SUM(A5:C5)"
  }'

echo -e "\nUpdating cell B5 to value 20"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
  --header "Content-Type: application/json" \
  --header "X-User-ID: $USER_ID" \
  --data '{
    "row": 5,
    "column": "B",
    "data": "20"
  }'

echo -e "\nGetting cell D5 to check if range sum was updated"
curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/5/D" \
  --header "X-User-ID: $USER_ID"

#echo -e "\n=== Test 4: Concurrency and Locking ==="
#echo "This test verifies that the system properly handles concurrent updates and locking"
#
## Create a new cell
#echo -e "\nCreating cell D4 with value 100"
#curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 4,
#    "column": "D",
#    "data": "100"
#  }'
#
#echo -e "\nCreating cell E4 with expression =D4*2"
#curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 4,
#    "column": "E",
#    "data": "=D4*2"
#  }'
#
## Simulate concurrent updates from different users
#echo -e "\nSimulating concurrent updates from different users"
#USER_ID_2=456
#
## Start first update in background (will acquire lock)
#echo -e "\nStarting first update (USER_ID=$USER_ID)"
#(curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 4,
#    "column": "D",
#    "data": "200"
#  }' > /tmp/update1_response.txt) &
#
## Small delay to ensure first request starts processing
#sleep 0.5
#
## Try second update while first is in progress
#echo -e "\nAttempting second update while first is in progress (USER_ID=$USER_ID_2)"
#CONCURRENT_RESPONSE=$(curl -s -i --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID_2" \
#  --data '{
#    "row": 4,
#    "column": "D",
#    "data": "300"
#  }')
#
## Wait for first update to complete
#wait
#
#echo -e "\nFirst update response:"
#cat /tmp/update1_response.txt
#
#echo -e "\nSecond update response (should indicate lock failure):"
#echo "$CONCURRENT_RESPONSE"
#
## Check if the second update was rejected due to locking
#if echo "$CONCURRENT_RESPONSE" | grep -q "Could not acquire lock"; then
#  echo -e "\n✅ Locking test passed: Second update was correctly rejected due to lock"
#else
#  echo -e "\n❌ Locking test failed: Second update was not rejected as expected"
#fi
#
## Check the final value to ensure it's 200 (from first update)
#echo -e "\nChecking final value of cell D4 (should be 200)"
#D4_VALUE=$(curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/4/D" \
#  --header "X-User-ID: $USER_ID")
#echo "$D4_VALUE"
#
## Check if dependent cell E4 was updated automatically
#echo -e "\nChecking if dependent cell E4 was updated automatically (should be 400)"
#E4_VALUE=$(curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/4/E" \
#  --header "X-User-ID: $USER_ID")
#echo "$E4_VALUE"
#
#echo -e "\n=== Test 5: Redis-First Persistence ==="
#echo "This test verifies that data is written to Redis before being persisted to MongoDB"
#
## Create a new cell
#echo -e "\nCreating cell F5 with value 50"
#curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 5,
#    "column": "F",
#    "data": "50"
#  }'
#
## Immediately retrieve the cell to verify it's in Redis
#echo -e "\nImmediately retrieving cell F5 to verify it's in Redis"
#F5_VALUE=$(curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/5/F" \
#  --header "X-User-ID: $USER_ID")
#echo "$F5_VALUE"
#
## Check if we got a valid response (Redis should have the value)
#if echo "$F5_VALUE" | grep -q '"data":"50"'; then
#  echo -e "\n✅ Redis-first test passed: Cell value was immediately available from Redis"
#else
#  echo -e "\n❌ Redis-first test failed: Cell value was not immediately available"
#fi
#
#echo -e "\n=== Test 6: Deadlock Prevention ==="
#echo "This test verifies that the system prevents deadlocks when updating multiple cells"
#
## Create cells for deadlock test
#echo -e "\nCreating cell A6 with value 10"
#curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 6,
#    "column": "A",
#    "data": "10"
#  }'
#
#echo -e "\nCreating cell B6 with expression =A6+5"
#curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 6,
#    "column": "B",
#    "data": "=A6+5"
#  }'
#
#echo -e "\nCreating cell C6 with expression =B6+5"
#curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 6,
#    "column": "C",
#    "data": "=B6+5"
#  }'
#
#echo -e "\nCreating circular reference: A6 with expression =C6+5 (should be rejected)"
#CIRCULAR_RESPONSE=$(curl -s -i --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 6,
#    "column": "A",
#    "data": "=C6+5"
#  }')
#
#echo "$CIRCULAR_RESPONSE"
#
## Check if circular reference was rejected
#if echo "$CIRCULAR_RESPONSE" | grep -q "circular reference" || echo "$CIRCULAR_RESPONSE" | grep -q "Circular dependency"; then
#  echo -e "\n✅ Deadlock prevention test passed: Circular reference was correctly rejected"
#else
#  echo -e "\n❌ Deadlock prevention test failed: Circular reference was not rejected as expected"
#fi
#
#echo -e "\n=== Test 7: Multiple Concurrent Users ==="
#echo "This test verifies that multiple users can work on different cells simultaneously"
#
## Create cells for multiple users test
#echo -e "\nCreating cells G7, H7, I7 with initial values"
#curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 7,
#    "column": "G",
#    "data": "1"
#  }'
#
#curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 7,
#    "column": "H",
#    "data": "2"
#  }'
#
#curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 7,
#    "column": "I",
#    "data": "3"
#  }'
#
## Simulate multiple users updating different cells simultaneously
#echo -e "\nSimulating multiple users updating different cells simultaneously"
#USER_ID_3=789
#
## Start updates in background
#echo -e "\nStarting concurrent updates from different users on different cells"
#(curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID" \
#  --data '{
#    "row": 7,
#    "column": "G",
#    "data": "10"
#  }' > /tmp/update_g7.txt) &
#
#(curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID_2" \
#  --data '{
#    "row": 7,
#    "column": "H",
#    "data": "20"
#  }' > /tmp/update_h7.txt) &
#
#(curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell" \
#  --header "Content-Type: application/json" \
#  --header "X-User-ID: $USER_ID_3" \
#  --data '{
#    "row": 7,
#    "column": "I",
#    "data": "30"
#  }' > /tmp/update_i7.txt) &
#
## Wait for all background processes to complete
#wait
#
#echo -e "\nChecking final values of cells G7, H7, I7"
#G7_VALUE=$(curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/7/G" --header "X-User-ID: $USER_ID")
#H7_VALUE=$(curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/7/H" --header "X-User-ID: $USER_ID")
#I7_VALUE=$(curl -s --location "$BASE_URL/sheet/$SHEET_ID/cell/7/I" --header "X-User-ID: $USER_ID")
#
#echo "G7 value: $G7_VALUE"
#echo "H7 value: $H7_VALUE"
#echo "I7 value: $I7_VALUE"
#
## Check if all updates were successful
#if echo "$G7_VALUE" | grep -q '"data":"10"' && \
#   echo "$H7_VALUE" | grep -q '"data":"20"' && \
#   echo "$I7_VALUE" | grep -q '"data":"30"'; then
#  echo -e "\n✅ Multiple users test passed: All cells were updated successfully"
#else
#  echo -e "\n❌ Multiple users test failed: Not all cells were updated correctly"
#fi
#
#echo -e "\n=== Test 4: Concurrency and Locking Test ==="
#echo "This test verifies that locks are properly acquired before updates and concurrent conflicting updates are rejected."
#
## Create a test sheet
#echo "Creating test sheet..."
#SHEET_ID=$(curl -s -X POST -H "Content-Type: application/json" -H "X-User-ID: 123" \
#  -d '{"name":"Concurrency Test Sheet","description":"Testing concurrency"}' \
#  http://localhost:8080/v1/sheets | jq -r '.id')
#
#echo "Sheet created with ID: $SHEET_ID"
#
## Create a cell
#echo "Creating initial cell..."
#curl -s -X POST -H "Content-Type: application/json" -H "X-User-ID: 123" \
#  -d '{"row":1,"column":"A","data":"Initial Value"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells > /dev/null
#
## First user starts updating the cell
#echo "User 1 updating cell..."
#curl -s -X POST -H "Content-Type: application/json" -H "X-User-ID: 123" \
#  -d '{"row":1,"column":"A","data":"User 1 Value"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells > /dev/null
#
## Second user tries to update the same cell immediately (should fail due to lock)
#echo "User 2 attempting to update the same cell concurrently (should be rejected)..."
#RESPONSE=$(curl -s -w "%{http_code}" -X POST -H "Content-Type: application/json" -H "X-User-ID: 456" \
#  -d '{"row":1,"column":"A","data":"User 2 Value"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells)
#
#HTTP_CODE=$(echo "$RESPONSE" | tail -c 4)
#RESPONSE_BODY=$(echo "$RESPONSE" | head -c -4)
#
#if [ "$HTTP_CODE" == "409" ]; then
#  echo -e "${GREEN}✓ Success: Second update was correctly rejected with HTTP 409 Conflict${NC}"
#else
#  echo -e "${RED}✗ Failed: Second update was not rejected with HTTP 409 Conflict. Got HTTP $HTTP_CODE instead${NC}"
#  echo "Response body: $RESPONSE_BODY"
#fi
#
## Wait for locks to be released
#echo "Waiting for locks to be released..."
#sleep 2
#
## Verify the cell value is from the first user
#echo "Verifying cell value..."
#CELL_VALUE=$(curl -s -X GET -H "X-User-ID: 123" \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells/1/A | jq -r '.data')
#
#if [ "$CELL_VALUE" == "User 1 Value" ]; then
#  echo -e "${GREEN}✓ Success: Cell value is correct: $CELL_VALUE${NC}"
#else
#  echo -e "${RED}✗ Failed: Cell value is incorrect: $CELL_VALUE${NC}"
#fi
#
#echo -e "\n=== Test 5: Redis-First Persistence Test ==="
#echo "This test verifies that cell data is written to Redis before MongoDB for immediate availability."
#
## Create a new cell
#echo "Creating a new cell..."
#curl -s -X POST -H "Content-Type: application/json" -H "X-User-ID: 123" \
#  -d '{"row":2,"column":"B","data":"Redis Test Value"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells > /dev/null
#
## Immediately read the cell back
#echo "Reading cell immediately after creation..."
#CELL_VALUE=$(curl -s -X GET -H "X-User-ID: 123" \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells/2/B | jq -r '.data')
#
#if [ "$CELL_VALUE" == "Redis Test Value" ]; then
#  echo -e "${GREEN}✓ Success: Cell value is immediately available in Redis: $CELL_VALUE${NC}"
#else
#  echo -e "${RED}✗ Failed: Cell value is not immediately available: $CELL_VALUE${NC}"
#fi
#
#echo -e "\n=== Test 6: Deadlock Prevention Test ==="
#echo "This test verifies that circular references (which could cause deadlocks) are detected and rejected."
#
## Create cells for circular reference test
#echo "Creating cells for circular reference test..."
#curl -s -X POST -H "Content-Type: application/json" -H "X-User-ID: 123" \
#  -d '{"row":3,"column":"A","data":"10"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells > /dev/null
#
#curl -s -X POST -H "Content-Type: application/json" -H "X-User-ID: 123" \
#  -d '{"row":3,"column":"B","data":"=A3+5"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells > /dev/null
#
## Try to create a circular reference (A3 referencing B3, which already references A3)
#echo "Attempting to create a circular reference (should be rejected)..."
#RESPONSE=$(curl -s -w "%{http_code}" -X POST -H "Content-Type: application/json" -H "X-User-ID: 123" \
#  -d '{"row":3,"column":"A","data":"=B3+5"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells)
#
#HTTP_CODE=$(echo "$RESPONSE" | tail -c 4)
#RESPONSE_BODY=$(echo "$RESPONSE" | head -c -4)
#
#if [ "$HTTP_CODE" == "400" ]; then
#  echo -e "${GREEN}✓ Success: Circular reference was correctly rejected with HTTP 400${NC}"
#else
#  echo -e "${RED}✗ Failed: Circular reference was not rejected. Got HTTP $HTTP_CODE instead${NC}"
#  echo "Response body: $RESPONSE_BODY"
#fi
#
#echo -e "\n=== Test 7: Multiple Concurrent Users Test ==="
#echo "This test verifies that multiple users can update different cells concurrently."
#
## Create multiple cells concurrently with different users
#echo "Creating multiple cells concurrently with different users..."
#
## Use background processes to simulate concurrent requests
#curl -s -X POST -H "Content-Type: application/json" -H "X-User-ID: 123" \
#  -d '{"row":4,"column":"A","data":"User 1 Cell"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells > /dev/null &
#
#curl -s -X POST -H "Content-Type: application/json" -H "X-User-ID: 456" \
#  -d '{"row":4,"column":"B","data":"User 2 Cell"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells > /dev/null &
#
#curl -s -X POST -H "Content-Type: application/json" -H "X-User-ID: 789" \
#  -d '{"row":4,"column":"C","data":"User 3 Cell"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells > /dev/null &
#
## Wait for all background processes to complete
#wait
#
## Verify all cells were created correctly
#echo "Verifying all cells were created correctly..."
#CELL_A_VALUE=$(curl -s -X GET -H "X-User-ID: 123" \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells/4/A | jq -r '.data')
#
#CELL_B_VALUE=$(curl -s -X GET -H "X-User-ID: 123" \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells/4/B | jq -r '.data')
#
#CELL_C_VALUE=$(curl -s -X GET -H "X-User-ID: 123" \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells/4/C | jq -r '.data')
#
#if [ "$CELL_A_VALUE" == "User 1 Cell" ] && [ "$CELL_B_VALUE" == "User 2 Cell" ] && [ "$CELL_C_VALUE" == "User 3 Cell" ]; then
#  echo -e "${GREEN}✓ Success: All cells were created correctly${NC}"
#  echo "Cell A: $CELL_A_VALUE"
#  echo "Cell B: $CELL_B_VALUE"
#  echo "Cell C: $CELL_C_VALUE"
#else
#  echo -e "${RED}✗ Failed: Not all cells were created correctly${NC}"
#  echo "Cell A: $CELL_A_VALUE"
#  echo "Cell B: $CELL_B_VALUE"
#  echo "Cell C: $CELL_C_VALUE"
#fi
#
#echo -e "\n=== Test 8: Lock Timeout Test ==="
#echo "This test verifies that locks are automatically released after timeout."
#
## Create a test cell
#echo "Creating a test cell..."
#curl -s -X POST -H "Content-Type: application/json" -H "X-User-ID: 123" \
#  -d '{"row":5,"column":"A","data":"Lock Timeout Test"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells > /dev/null
#
## Wait for lock timeout (should be 30 seconds, but we'll wait just 5 for testing)
#echo "Waiting for lock timeout (5 seconds)..."
#sleep 5
#
## Another user should be able to update the cell after timeout
#echo "Another user updating the cell after timeout..."
#RESPONSE=$(curl -s -w "%{http_code}" -X POST -H "Content-Type: application/json" -H "X-User-ID: 456" \
#  -d '{"row":5,"column":"A","data":"Updated After Timeout"}' \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells)
#
#HTTP_CODE=$(echo "$RESPONSE" | tail -c 4)
#
#if [ "$HTTP_CODE" == "200" ]; then
#  echo -e "${GREEN}✓ Success: Cell was updated after lock timeout${NC}"
#else
#  echo -e "${RED}✗ Failed: Cell could not be updated after timeout. Got HTTP $HTTP_CODE${NC}"
#fi
#
## Verify the cell value was updated
#CELL_VALUE=$(curl -s -X GET -H "X-User-ID: 123" \
#  http://localhost:8080/v1/sheets/$SHEET_ID/cells/5/A | jq -r '.data')
#
#if [ "$CELL_VALUE" == "Updated After Timeout" ]; then
#  echo -e "${GREEN}✓ Success: Cell value was updated correctly: $CELL_VALUE${NC}"
#else
#  echo -e "${RED}✗ Failed: Cell value was not updated correctly: $CELL_VALUE${NC}"
#fi
#
#echo -e "\n${GREEN}All concurrency and deadlock tests completed!${NC}"
