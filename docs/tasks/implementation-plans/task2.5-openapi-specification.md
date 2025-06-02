# Task 2.5: OpenAPI Specification

## Overview
This task involves updating the OpenAPI specification in `api.yaml` to include Sheet Management endpoints. Following the OpenAPI-first approach, we'll define all API endpoints, request/response models, and error responses before implementing the controllers.

## Implementation Steps

### 1. Update api.yaml with Sheet Management Endpoints
Update `src/main/resources/openapi/api.yaml` to include the following endpoints:

```yaml
openapi: 3.0.3
info:
  title: Sheets API
  description: API for collaborative spreadsheet application
  version: 1.0.0
  contact:
    name: API Support
    email: support@example.com
  license:
    name: Proprietary

servers:
  - url: /api/v1
    description: Development server

paths:
  /health:
    get:
      summary: Health check endpoint
      description: Returns the health status of the application
      operationId: healthCheck
      tags:
        - Health
      responses:
        '200':
          description: Application is healthy
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/HealthResponse'

  /sheet:
    post:
      summary: Create a new sheet
      description: Creates a new sheet with the specified name and description
      operationId: createSheet
      tags:
        - Sheet
      parameters:
        - name: X-User-ID
          in: header
          required: true
          schema:
            type: integer
            format: int64
          description: ID of the user creating the sheet
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateSheetRequest'
      responses:
        '201':
          description: Sheet created successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SheetResponse'
        '400':
          description: Invalid request
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '500':
          description: Internal server error
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
    
    get:
      summary: Get all sheets
      description: Returns all sheets owned by or shared with the user
      operationId: getSheets
      tags:
        - Sheet
      parameters:
        - name: X-User-ID
          in: header
          required: true
          schema:
            type: integer
            format: int64
          description: ID of the user requesting sheets
      responses:
        '200':
          description: List of sheets
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/SheetSummaryResponse'
        '500':
          description: Internal server error
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /sheet/{sheetId}:
    get:
      summary: Get sheet details
      description: Returns details of a specific sheet
      operationId: getSheetDetails
      tags:
        - Sheet
      parameters:
        - name: X-User-ID
          in: header
          required: true
          schema:
            type: integer
            format: int64
          description: ID of the user requesting sheet details
        - name: sheetId
          in: path
          required: true
          schema:
            type: integer
            format: int64
          description: ID of the sheet to retrieve
      responses:
        '200':
          description: Sheet details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SheetDetailsResponse'
        '404':
          description: Sheet not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '403':
          description: Access denied
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '500':
          description: Internal server error
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

  /sheet/share/{sheetId}:
    post:
      summary: Share a sheet
      description: Shares a sheet with other users
      operationId: shareSheet
      tags:
        - Sheet
      parameters:
        - name: X-User-ID
          in: header
          required: true
          schema:
            type: integer
            format: int64
          description: ID of the user sharing the sheet
        - name: sheetId
          in: path
          required: true
          schema:
            type: integer
            format: int64
          description: ID of the sheet to share
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ShareSheetRequest'
      responses:
        '200':
          description: Sheet shared successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ShareSheetResponse'
        '404':
          description: Sheet not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '403':
          description: Access denied
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '400':
          description: Invalid request
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '500':
          description: Internal server error
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

components:
  schemas:
    HealthResponse:
      type: object
      properties:
        status:
          type: string
          example: "UP"
          description: Current health status of the service
        timestamp:
          type: string
          format: date-time
          example: "2025-05-24T12:00:00Z"
          description: Current server time

    CreateSheetRequest:
      type: object
      required:
        - name
      properties:
        name:
          type: string
          maxLength: 100
          description: Name of the sheet
          example: "Budget 2025"
        description:
          type: string
          description: Description of the sheet
          example: "Annual budget planning for 2025"
        maxLength:
          type: integer
          description: Maximum number of rows
          default: 100
          example: 100
        maxBreadth:
          type: integer
          description: Maximum number of columns
          default: 100
          example: 100

    SheetResponse:
      type: object
      properties:
        id:
          type: integer
          format: int64
          description: Unique identifier for the sheet
          example: 1
        name:
          type: string
          description: Name of the sheet
          example: "Budget 2025"
        description:
          type: string
          description: Description of the sheet
          example: "Annual budget planning for 2025"
        maxLength:
          type: integer
          description: Maximum number of rows
          example: 100
        maxBreadth:
          type: integer
          description: Maximum number of columns
          example: 100
        userId:
          type: integer
          format: int64
          description: ID of the user who owns the sheet
          example: 1001
        createdAt:
          type: string
          format: date-time
          description: Time when the sheet was created
          example: "2025-05-24T12:00:00Z"
        updatedAt:
          type: string
          format: date-time
          description: Time when the sheet was last updated
          example: "2025-05-24T12:00:00Z"

    SheetSummaryResponse:
      type: object
      properties:
        id:
          type: integer
          format: int64
          description: Unique identifier for the sheet
          example: 1
        name:
          type: string
          description: Name of the sheet
          example: "Budget 2025"
        description:
          type: string
          description: Description of the sheet
          example: "Annual budget planning for 2025"
        accessType:
          type: string
          enum: [READ, WRITE, ADMIN, OWNER]
          description: Type of access the user has to the sheet
          example: "OWNER"
        createdAt:
          type: string
          format: date-time
          description: Time when the sheet was created
          example: "2025-05-24T12:00:00Z"
        updatedAt:
          type: string
          format: date-time
          description: Time when the sheet was last updated
          example: "2025-05-24T12:00:00Z"

    SheetDetailsResponse:
      type: object
      properties:
        id:
          type: integer
          format: int64
          description: Unique identifier for the sheet
          example: 1
        name:
          type: string
          description: Name of the sheet
          example: "Budget 2025"
        description:
          type: string
          description: Description of the sheet
          example: "Annual budget planning for 2025"
        maxLength:
          type: integer
          description: Maximum number of rows
          example: 100
        maxBreadth:
          type: integer
          description: Maximum number of columns
          example: 100
        userId:
          type: integer
          format: int64
          description: ID of the user who owns the sheet
          example: 1001
        accessType:
          type: string
          enum: [READ, WRITE, ADMIN, OWNER]
          description: Type of access the user has to the sheet
          example: "OWNER"
        cells:
          type: array
          items:
            $ref: '#/components/schemas/CellResponse'
          description: Cells in the sheet
        createdAt:
          type: string
          format: date-time
          description: Time when the sheet was created
          example: "2025-05-24T12:00:00Z"
        updatedAt:
          type: string
          format: date-time
          description: Time when the sheet was last updated
          example: "2025-05-24T12:00:00Z"

    CellResponse:
      type: object
      properties:
        cellId:
          type: string
          description: ID of the cell (e.g., "A1", "B2")
          example: "A1"
        data:
          type: string
          description: Raw data in the cell
          example: "100"
        dataType:
          type: string
          enum: [PRIMITIVE, EXPRESSION]
          description: Type of data in the cell
          example: "PRIMITIVE"
        evaluatedValue:
          type: string
          description: Evaluated value of the cell
          example: "100"

    ShareSheetRequest:
      type: object
      required:
        - userIds
        - accessType
      properties:
        userIds:
          type: array
          items:
            type: integer
            format: int64
          description: IDs of users to share with
          example: [1002, 1003]
        accessType:
          type: string
          enum: [READ, WRITE, ADMIN]
          description: Type of access to grant
          example: "READ"

    ShareSheetResponse:
      type: object
      properties:
        message:
          type: string
          description: Success message
          example: "Sheet shared successfully with 2 users"

    ErrorResponse:
      type: object
      properties:
        status:
          type: integer
          description: HTTP status code
          example: 400
        error:
          type: string
          description: Error type
          example: "Bad Request"
        message:
          type: string
          description: Error message
          example: "Invalid request parameters"
        timestamp:
          type: string
          format: date-time
          description: Time when the error occurred
          example: "2025-05-24T12:00:00Z"
```

### 2. Generate OpenAPI Code
Run the OpenAPI code generation task to generate interfaces and models:

```bash
./gradlew openApiGenerate
```

This will generate:
- API interfaces in `build/generated/openapi/src/main/kotlin/learn/ai/generated/api`
- Model classes in `build/generated/openapi/src/main/kotlin/learn/ai/generated/model`

### 3. Verify Generated Code
Verify that the following interfaces and models are generated:
- `SheetApi` interface with methods for all sheet endpoints
- Request/response models for all API operations

## Testing
1. Validate the OpenAPI specification using a validator tool
2. Ensure all required endpoints and models are defined
3. Verify that the generated code compiles without errors

## Completion Criteria
- OpenAPI specification is updated with all Sheet Management endpoints
- Request and response models are properly defined
- Error responses are properly defined
- OpenAPI code generation task runs successfully
- Generated code compiles without errors
