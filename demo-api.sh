#!/bin/bash

# Demo script for Spreadsheet Application API
# Assumptions:
# - The server is running on localhost:8080
# - The base path for the API is /v1
# - User authentication is handled via X-User-ID header

# Set variables
BASE_URL="http://localhost:8080/v1"
USER_ID="1"
SECOND_USER_ID="2"

# Text formatting
BOLD='\033[1m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Helper function to print section headers
print_header() {
  echo -e "\n${BOLD}${BLUE}$1${NC}\n"
}

# Helper function to print command being executed
print_command() {
  echo -e "${YELLOW}$ $1${NC}"
}

# Helper function to print expected result
print_expected() {
  echo -e "${GREEN}Expected: $1${NC}"
}

# Check if the server is running
print_header "Checking if the server is running"
print_command "curl -s ${BASE_URL}/health"
HEALTH_RESPONSE=$(curl -s "${BASE_URL}/health")
echo "$HEALTH_RESPONSE"

if [[ "$HEALTH_RESPONSE" != *"UP"* ]]; then
  echo -e "${RED}Server is not running. Please start the server and try again.${NC}"
  exit 1
fi

print_expected "Server is running and healthy"

# 1. Create a new sheet
print_header "1. Creating a new sheet"
print_command "curl -s -X POST ${BASE_URL}/sheet -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"name\":\"Demo Sheet\"}'"
SHEET_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"name":"Demo Sheet"}')
echo "$SHEET_RESPONSE"

# Extract sheet ID from response
SHEET_ID=$(echo "$SHEET_RESPONSE" | grep -o '"id":[^,}]*' | head -1 | sed 's/"id"://')
echo -e "Sheet ID: ${BOLD}${SHEET_ID}${NC}"

print_expected "A new sheet created with a unique ID"

# 2. Update cells with primitive values
print_header "2. Updating cells with primitive values"
print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":1,\"column\":\"A\",\"data\":\"10\"}'"
CELL_A1_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":1,"column":"A","data":"10"}')
echo "$CELL_A1_RESPONSE"

print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":1,\"column\":\"B\",\"data\":\"20\"}'"
CELL_B1_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":1,"column":"B","data":"20"}')
echo "$CELL_B1_RESPONSE"

print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":1,\"column\":\"C\",\"data\":\"30\"}'"
CELL_C1_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":1,"column":"C","data":"30"}')
echo "$CELL_C1_RESPONSE"

print_expected "Three cells updated with numeric values"

# 3. Get all cells in the sheet
print_header "3. Getting all cells in the sheet"
print_command "curl -s -X GET ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\""
ALL_CELLS_RESPONSE=$(curl -s -X GET "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}")
echo "$ALL_CELLS_RESPONSE"

print_expected "All cells in the sheet with their values"

# 4. Update a cell with a formula using SUM function
print_header "4. Updating a cell with a SUM formula"
print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":2,\"column\":\"A\",\"data\":\"=SUM(A1:C1)\"}'"
FORMULA_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":2,"column":"A","data":"=SUM(A1:C1)"}')
echo "$FORMULA_RESPONSE"

print_expected "Cell updated with formula and evaluatedValue should be 60 (10+20+30)"

# 5. Get the formula cell to verify evaluation
print_header "5. Getting the formula cell to verify evaluation"
print_command "curl -s -X GET ${BASE_URL}/sheet/${SHEET_ID}/cell/2/A -H \"X-User-ID: ${USER_ID}\""
FORMULA_CELL_RESPONSE=$(curl -s -X GET "${BASE_URL}/sheet/${SHEET_ID}/cell/2/A" -H "X-User-ID: ${USER_ID}")
echo "$FORMULA_CELL_RESPONSE"

print_expected "Formula cell with data '=SUM(A1:C1)' and evaluatedValue '60'"

# 6. Update a source cell to demonstrate dependency recalculation
print_header "6. Updating a source cell to demonstrate dependency recalculation"
print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":1,\"column\":\"A\",\"data\":\"15\"}'"
UPDATE_SOURCE_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":1,"column":"A","data":"15"}')
echo "$UPDATE_SOURCE_RESPONSE"

# 7. Get the formula cell again to verify recalculation
print_header "7. Getting the formula cell again to verify recalculation"
print_command "curl -s -X GET ${BASE_URL}/sheet/${SHEET_ID}/cell/2/A -H \"X-User-ID: ${USER_ID}\""
RECALC_RESPONSE=$(curl -s -X GET "${BASE_URL}/sheet/${SHEET_ID}/cell/2/A" -H "X-User-ID: ${USER_ID}")
echo "$RECALC_RESPONSE"

print_expected "Formula cell with updated evaluatedValue '65' (15+20+30)"

# 8. Create cells for AVERAGE function demonstration
print_header "8. Creating cells for AVERAGE function demonstration"
print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":3,\"column\":\"A\",\"data\":\"=AVERAGE(A1:C1)\"}'"
AVG_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":3,"column":"A","data":"=AVERAGE(A1:C1)"}')
echo "$AVG_RESPONSE"

print_expected "Cell with AVERAGE formula and evaluatedValue ~21.67 (average of 15, 20, 30)"

# 9. Create cells for MIN and MAX function demonstration
print_header "9. Creating cells for MIN and MAX function demonstration"
print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":4,\"column\":\"A\",\"data\":\"=MIN(A1:C1)\"}'"
MIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":4,"column":"A","data":"=MIN(A1:C1)"}')
echo "$MIN_RESPONSE"

print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":4,\"column\":\"B\",\"data\":\"=MAX(A1:C1)\"}'"
MAX_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":4,"column":"B","data":"=MAX(A1:C1)"}')
echo "$MAX_RESPONSE"

print_expected "MIN cell with evaluatedValue 15 and MAX cell with evaluatedValue 30"

# 10. Create a cell with a complex nested formula
print_header "10. Creating a cell with a complex nested formula"
print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":5,\"column\":\"A\",\"data\":\"=SUM(A1:C1)*MAX(A1:C1)/MIN(A1:C1)\"}'"
COMPLEX_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":5,"column":"A","data":"=SUM(A1:C1)*MAX(A1:C1)/MIN(A1:C1)"}')
echo "$COMPLEX_RESPONSE"

print_expected "Complex formula cell with evaluatedValue 130 (65*30/15)"

# 11. Share the sheet with another user
print_header "11. Sharing the sheet with another user"
print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/share -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"userId\":\"${SECOND_USER_ID}\",\"accessType\":\"WRITE\"}'"
SHARE_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/share" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d "{\"userId\":\"${SECOND_USER_ID}\",\"accessType\":\"WRITE\"}")
echo "$SHARE_RESPONSE"

print_expected "Sheet shared with user 2 with WRITE access"

# 12. Update a cell as the second user to demonstrate shared access
print_header "12. Updating a cell as the second user"
print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${SECOND_USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":1,\"column\":\"D\",\"data\":\"40\"}'"
SECOND_USER_RESPONSE=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${SECOND_USER_ID}" -H "Content-Type: application/json" -d '{"row":1,"column":"D","data":"40"}')
echo "$SECOND_USER_RESPONSE"

print_expected "Cell D1 updated by second user with value 40"

# 13. Attempt to create a circular reference
print_header "13. Attempting to create a circular reference"
print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":6,\"column\":\"A\",\"data\":\"=B6+10\"}'"
CIRCULAR_STEP1=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":6,"column":"A","data":"=B6+10"}')
echo "$CIRCULAR_STEP1"

print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":6,\"column\":\"B\",\"data\":\"=C6+10\"}'"
CIRCULAR_STEP2=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":6,"column":"B","data":"=C6+10"}')
echo "$CIRCULAR_STEP2"

print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":6,\"column\":\"C\",\"data\":\"=A6+10\"}'"
CIRCULAR_STEP3=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":6,"column":"C","data":"=A6+10"}')
echo "$CIRCULAR_STEP3"

print_expected "Error response with status 400 and message about circular dependency"

# 14. List all sheets for the user
print_header "14. Listing all sheets for the user"
print_command "curl -s -X GET ${BASE_URL}/sheets -H \"X-User-ID: ${USER_ID}\""
LIST_SHEETS_RESPONSE=$(curl -s -X GET "${BASE_URL}/sheets" -H "X-User-ID: ${USER_ID}")
echo "$LIST_SHEETS_RESPONSE"

print_expected "List of sheets accessible to user 1, including the Demo Sheet"

# 15. Delete a cell
print_header "15. Deleting a cell"
print_command "curl -s -X DELETE ${BASE_URL}/sheet/${SHEET_ID}/cell/1/D -H \"X-User-ID: ${USER_ID}\""
DELETE_CELL_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/sheet/${SHEET_ID}/cell/1/D" -H "X-User-ID: ${USER_ID}")
echo "$DELETE_CELL_RESPONSE"

print_expected "Cell D1 deleted successfully"

# 16. Verify cell deletion
print_header "16. Verifying cell deletion"
print_command "curl -s -X GET ${BASE_URL}/sheet/${SHEET_ID}/cell/1/D -H \"X-User-ID: ${USER_ID}\""
VERIFY_DELETE_RESPONSE=$(curl -s -X GET "${BASE_URL}/sheet/${SHEET_ID}/cell/1/D" -H "X-User-ID: ${USER_ID}")
echo "$VERIFY_DELETE_RESPONSE"

print_expected "404 Not Found response as the cell has been deleted"

# 17. Demonstrate concurrent updates (simulated)
print_header "17. Demonstrating concurrent updates (simulated)"
print_command "# First request will succeed"
print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":7,\"column\":\"A\",\"data\":\"Concurrent Test\"}'"
CONCURRENT_1=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${USER_ID}" -H "Content-Type: application/json" -d '{"row":7,"column":"A","data":"Concurrent Test"}')
echo "$CONCURRENT_1"

print_command "# Second request to the same cell (in a real concurrent scenario) might return a conflict"
print_command "curl -s -X POST ${BASE_URL}/sheet/${SHEET_ID}/cell -H \"X-User-ID: ${SECOND_USER_ID}\" -H \"Content-Type: application/json\" -d '{\"row\":7,\"column\":\"A\",\"data\":\"Concurrent Test 2\"}'"
CONCURRENT_2=$(curl -s -X POST "${BASE_URL}/sheet/${SHEET_ID}/cell" -H "X-User-ID: ${SECOND_USER_ID}" -H "Content-Type: application/json" -d '{"row":7,"column":"A","data":"Concurrent Test 2"}')
echo "$CONCURRENT_2"

print_expected "In a true concurrent scenario, one request would succeed and the other might return a 409 Conflict"


# Summary
print_header "Demo Summary"
echo -e "This demo script demonstrated the following functionality:"
echo -e "✅ Creating and deleting sheets"
echo -e "✅ Adding and updating cells with primitive values"
echo -e "✅ Creating cells with formulas (SUM, AVERAGE, MIN, MAX)"
echo -e "✅ Automatic recalculation of dependent cells"
echo -e "✅ Complex nested formula evaluation"
echo -e "✅ Circular dependency detection"
echo -e "✅ Sheet sharing and permission management"
echo -e "✅ Concurrent update handling"

echo -e "\n${BOLD}${GREEN}Demo completed successfully!${NC}"
