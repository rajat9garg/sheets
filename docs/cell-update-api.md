# Cell Update API Documentation

## Overview

The Cell Update API provides two endpoints for updating cell values in a spreadsheet:

1. **Update by Row/Column**: `/sheet/{sheetId}/cell` (POST)
2. **Update by Cell ID**: `/sheet/{sheetId}/cell/{cellId}` (POST)

This document focuses on the implementation details, request/response formats, and error handling for both endpoints.

## API Endpoints

### 1. Update Cell by Row/Column

**Endpoint**: `POST /v1/sheet/{sheetId}/cell`

**Description**: Updates a cell by specifying its row and column coordinates.

**Request Parameters**:
- `sheetId` (path parameter): ID of the sheet containing the cell
- `X-User-ID` (header): ID of the user making the request

**Request Body** (`CellRequest`):
```json
{
  "row": 0,
  "column": 0,
  "data": "Hello, World!"
}
```

**Response** (`CellResponse`):
```json
{
  "id": "12:0:0",
  "sheetId": 12,
  "row": 0,
  "column": 0,
  "data": "Hello, World!",
  "dataType": "STRING",
  "evaluatedValue": "Hello, World!",
  "updatedAt": "2025-06-02T11:36:44Z"
}
```

### 2. Update Cell by ID

**Endpoint**: `POST /v1/sheet/{sheetId}/cell/{cellId}`

**Description**: Updates a cell by directly specifying its ID (format: `sheetId:row:column`).

**Request Parameters**:
- `sheetId` (path parameter): ID of the sheet containing the cell
- `cellId` (path parameter): ID of the cell to update (format: `sheetId:row:column`)
- `X-User-ID` (header): ID of the user making the request

**Request Body** (`CellDataRequest`):
```json
{
  "value": "Hello, World!"
}
```

**Response** (`CellResponse`):
```json
{
  "id": "12:0:0",
  "sheetId": 12,
  "row": 0,
  "column": 0,
  "data": "Hello, World!",
  "dataType": "STRING",
  "evaluatedValue": "Hello, World!",
  "updatedAt": "2025-06-02T11:36:44Z"
}
```

## Implementation Details

### Controller Implementation

The `CellController` class implements both update methods:

#### 1. `updateCell` Method

```kotlin
override fun updateCell(
    sheetId: Long,
    xUserID: Long,
    cellRequest: CellRequest
): ResponseEntity<CellResponse> {
    // Implementation details...
}
```

#### 2. `updateCellById` Method

```kotlin
override fun updateCellById(
    sheetId: Long,
    cellId: String,
    xUserID: Long,
    cellDataRequest: CellDataRequest
): ResponseEntity<CellResponse> {
    // Implementation details...
}
```

### Processing Flow

Both methods follow a similar flow:

1. **Request Validation**:
   - Validate that the sheet exists and the user has access
   - For `updateCellById`, validate that the cell ID belongs to the specified sheet
   - Validate that the data/value is not null

2. **Cell Retrieval**:
   - Retrieve the existing cell from the database
   - Return 404 Not Found if the cell doesn't exist

3. **Cell Update**:
   - Update the cell with the new data/value
   - Process expressions if the data starts with `=`
   - Update dependencies if the cell contains an expression
   - Save the updated cell to the database

4. **Response Generation**:
   - Convert the updated cell to a response object
   - Return the response with HTTP 200 OK

### Error Handling

Both methods handle various error scenarios:

1. **400 Bad Request**:
   - Invalid cell ID format
   - Null data/value
   - Circular dependency detected in expression
   - Expression parsing error

2. **403 Forbidden**:
   - User doesn't have access to the sheet

3. **404 Not Found**:
   - Sheet not found
   - Cell not found

4. **409 Conflict**:
   - Cell is locked by another user

5. **500 Internal Server Error**:
   - Unexpected errors during processing

### Logging

Comprehensive logging is implemented throughout the methods:

1. **Info Level**:
   - Request received
   - Operation successful

2. **Debug Level**:
   - Sheet access validation
   - Cell retrieval
   - Cell update
   - Response generation

3. **Warn Level**:
   - Invalid cell ID
   - Null data/value
   - Cell not found

4. **Error Level**:
   - Circular dependency
   - Expression error
   - Lock acquisition failure
   - Unexpected errors

## Service Layer

The controller methods delegate to the `CellService` for the actual cell update logic:

```kotlin
val updatedCell = cellService.updateCell(
    existingCell.copy(
        data = cellValue
    ),
    xUserID.toString()
)
```

The `CellServiceImpl` handles:

1. **Locking Mechanism**:
   - Acquires locks on the cell and its dependencies
   - Prevents concurrent conflicting updates

2. **Expression Processing**:
   - Parses expressions (formulas starting with `=`)
   - Evaluates expressions to produce a result
   - Detects circular dependencies

3. **Dependency Management**:
   - Tracks dependencies between cells
   - Updates dependent cells when a cell changes

4. **Persistence**:
   - Saves the updated cell to MongoDB
   - Updates the cell's metadata (updatedAt, dataType, etc.)

## Example Usage

### Update by Row/Column

```bash
curl -X POST "http://localhost:8080/v1/sheet/12/cell" \
  -H "X-User-ID: 123" \
  -H "Content-Type: application/json" \
  -d '{
    "row": 0,
    "column": 0,
    "data": "Hello, World!"
  }'
```

### Update by Cell ID

```bash
curl -X POST "http://localhost:8080/v1/sheet/12/cell/12:0:0" \
  -H "X-User-ID: 123" \
  -H "Content-Type: application/json" \
  -d '{
    "value": "Hello, World!"
  }'
```

### Using Expressions

Both endpoints support expressions (formulas) by prefixing the data/value with `=`:

```bash
# Update by Row/Column with an expression
curl -X POST "http://localhost:8080/v1/sheet/12/cell" \
  -H "X-User-ID: 123" \
  -H "Content-Type: application/json" \
  -d '{
    "row": 0,
    "column": 0,
    "data": "=SUM(0:1, 0:2)"
  }'

# Update by Cell ID with an expression
curl -X POST "http://localhost:8080/v1/sheet/12/cell/12:0:0" \
  -H "X-User-ID: 123" \
  -H "Content-Type: application/json" \
  -d '{
    "value": "=SUM(0:1, 0:2)"
  }'
```

## Data Types

The API supports various data types for cell values:

1. **STRING**: Text values
2. **NUMBER**: Numeric values
3. **BOOLEAN**: Boolean values (true/false)
4. **ERROR**: Error values (e.g., from invalid expressions)
5. **EXPRESSION**: Formula expressions (starting with `=`)

## Dependencies

Cell dependencies are automatically tracked when using expressions. For example, if cell A1 contains `=B1+C1`, then A1 depends on B1 and C1. When B1 or C1 changes, A1 will be automatically recalculated.
