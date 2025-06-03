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

echo -e "\nAll tests completed!"
