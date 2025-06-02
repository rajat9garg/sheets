This repo contains all the design and work for online spreadsheet projects.

Functional Requirements

Users should be able to create a shared sheet.
Users should be able to do snippet-based evaluation.
Users should be able to get all the user shared sheets.
Users should be able to share the sheet and grant different access.
Users should be able to concurrently update the sheet.

NON-Functional Requirements:

The system should be able to hold up to 10 concurrent updates on a sheet.
The system should maintain high availability for sheet data.
The system should allow updates (read and write) only for logged-in users.
The 99th percentile latency of saving the sheet is < 100 ms.
The 99th percentile latency of getting the sheet details < 200 ms.
The system should be scalable for 10M records and 1M writes.
The server can have up to 50 sheets.

Core Entities:
- Sheet This is like a google spreadsheet
- User
- Cell this is a cell in a sheet


**We are not gonna use web sockets to implement this. This will be a simple project.** 

**- 1. Sheet Management:**
   1. User can create a sheet
   2. user can get all the sheets registered to him and shared to him
   3. User can share the sheet to other users 
   4.  User can get the sheet data

USERID: this will come in the headers. this will be userID of the owner


Apis for sheet management
1. Create a sheet POST /sheet/
   request body
   1. sheet name
   2. sheet description

2. Get all sheets GET /sheet/
   response body
   1. list of sheets


3. Get sheet details GET /sheet/{sheetId}
   response body
   1. list of cells


4 POST /v1/sheet/share/{sheetId}
   request body
   1. list of users to share the sheet with
   2. AccessType 
   response body
   1. link to share

Db Tables:
- Sheet
  - ID
  - name
  - description
  - maxLength
  - maxBreadth
  - userID // owner userID

- User
  - id 
  - name
  - email

- AccessMapping
  - id
  - sheetID
  - userID
  - accessType

Cell Management
1. Using mongo DB to store the cell data

- Cell
  - id
  - cellId //row:column
  - sheetID
  - dataType (Primitive or expression)
  - data // can be primitive or expression
  - isInvolvedInExpression // Boolean
  - expression
  - createdAt
  - updatedAt

- DependencyManagement
  - id
  - cellID
  - set of Dependent CellIDs


APIs for cell management
1. Update/add data to cell POST /sheet/{sheetID}/cell/cellId
   request body
  --  value
   response body
   1. value
   2. cellValue // calculated value if the cell is expression
- This API checks the value is a primitive or expression
- This API checks if the cell is involved in any expression and then take a lock in Redis all the cells
- Does the calculations and create relevant updates in the table and Redis
- Update the dependencies of the cell