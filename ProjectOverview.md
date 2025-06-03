# Spreadsheet Application - Project Overview

**Last Updated:** June 4, 2025  
**Created By:** Cascade AI Assistant

## What This Project Does

The Spreadsheet Application is a modern, web-based spreadsheet system similar to Google Sheets or Microsoft Excel Online. It allows users to:

- Create, edit, and share spreadsheets
- Enter data into cells using a familiar row/column grid interface
- Use formulas to perform calculations (like `=SUM(A1:A5)` or `=AVERAGE(B1:B10)`)
- Share spreadsheets with other users with different permission levels (read, write, admin, owner)
- Access spreadsheets from any device with a web browser

### Key Features

- **Real-time Formula Calculation**: When you change a cell value, all dependent formulas automatically recalculate
- **A1 Notation Support**: Use familiar cell references like A1, B2, C3 (instead of row/column numbers)
- **Function Library**: Built-in functions like SUM, AVERAGE, MIN, MAX
- **Concurrent Editing**: Multiple users can edit the same spreadsheet simultaneously
- **Fast Performance**: Optimized for speed even with large spreadsheets
- **Robust Error Handling**: Clear error messages for circular references and invalid formulas

## Detailed Functional Capabilities

### Formula Functions

The application supports a comprehensive set of formula functions:

1. **Mathematical Functions**
   - `SUM(range)`: Adds all values in the specified range (e.g., `=SUM(A1:A10)`)
   - `AVERAGE(range)`: Calculates the average of values in the range (e.g., `=AVERAGE(B1:B5)`)
   - `MIN(range)`: Finds the minimum value in the range (e.g., `=MIN(C1:C20)`)
   - `MAX(range)`: Finds the maximum value in the range (e.g., `=MAX(D1:D20)`)
   - `COUNT(range)`: Counts the number of non-empty cells in the range (e.g., `=COUNT(A1:D10)`)

2. **Arithmetic Operations**
   - Addition: `=A1+B1`
   - Subtraction: `=A1-B1`
   - Multiplication: `=A1*B1`
   - Division: `=A1/B1`
   - Parentheses for order of operations: `=(A1+B1)*(C1-D1)`

3. **Cell Reference Types**
   - Individual cell references: `=A1`
   - Range references: `=SUM(A1:C5)`
   - Mixed references in formulas: `=A1+SUM(B2:B10)`
   - References across different parts of the sheet: `=MAX(A1:A10)+MIN(D1:D10)`

### User Scenarios

#### Scenario 1: Financial Dashboard

A finance team uses the spreadsheet to create a quarterly financial dashboard:

1. They enter revenue data in cells A1:A12 (one for each month)
2. In cell B1, they create a formula `=SUM(A1:A3)` for Q1 total
3. Similarly, they create formulas for Q2, Q3, and Q4 totals
4. In cell B5, they create a formula `=AVERAGE(B1:B4)` to get the average quarterly revenue
5. They share the sheet with executives with READ access
6. When monthly revenue figures are updated, all quarterly totals and averages automatically recalculate

**Technical Implementation:**
- The system detects dependencies between cells (A1:A12 → B1:B4 → B5)
- When any cell in A1:A12 changes, the system uses topological sorting to determine which cells need recalculation
- Redis caching ensures fast access to frequently viewed cells
- Permission system ensures executives can view but not modify the data

#### Scenario 2: Collaborative Budget Planning

A team collaboratively works on next year's budget:

1. User A creates a budget spreadsheet with department categories in column A
2. User A enters initial budget figures in column B
3. User A shares the sheet with Users B, C, and D with WRITE access
4. Users B, C, and D simultaneously update different cells with their department budgets
5. User A creates a formula in B20: `=SUM(B1:B19)` to calculate the total budget
6. As users make changes, the total automatically updates

**Technical Implementation:**
- Redis-based distributed locking prevents conflicts when multiple users edit simultaneously
- Sheet-level locks are acquired before cell-level locks to prevent deadlocks
- When users update cells, the system checks for dependencies and updates the total in B20
- Each user sees real-time updates as they happen, with <30ms latency for subsequent operations

#### Scenario 3: Complex Data Analysis

A data analyst performs complex calculations:

1. Raw data is entered in cells A1:E100
2. In cell F1, the analyst creates a formula `=AVERAGE(A1:A100)` to get the average of column A
3. In cell F2, they use `=MAX(B1:B100)-MIN(B1:B100)` to calculate the range of values in column B
4. In cell G1, they create a nested formula `=SUM(C1:C100)/COUNT(D1:D100)` 
5. In cell H1, they reference other calculated cells: `=F1*G1`

**Technical Implementation:**
- The formula parser breaks down complex expressions into tokens and operations
- The system builds a dependency graph to track relationships between cells
- When raw data changes, the system uses topological sorting to recalculate dependent cells in the correct order
- Circular dependency detection prevents infinite calculation loops

### API Capabilities

The application exposes a comprehensive REST API:

1. **Sheet Management**
   - `POST /sheet`: Create a new sheet
   - `GET /sheet/{sheetId}`: Retrieve a sheet
   - `DELETE /sheet/{sheetId}`: Delete a sheet
   - `GET /sheets`: List all accessible sheets

2. **Cell Operations**
   - `POST /sheet/{sheetId}/cell`: Update a cell
   - `GET /sheet/{sheetId}/cell/{row}/{column}`: Get a specific cell
   - `DELETE /sheet/{sheetId}/cell/{row}/{column}`: Delete a cell
   - `GET /sheet/{sheetId}/cell`: Get all cells in a sheet

3. **Access Control**
   - `POST /sheet/{sheetId}/share`: Share a sheet with another user
   - `GET /sheet/{sheetId}/access`: List all users with access to a sheet
   - `DELETE /sheet/{sheetId}/access/{userId}`: Remove a user's access

4. **Health and Monitoring**
   - `GET /health`: Check system health
   - `GET /metrics`: Get system performance metrics

## How It Works

### Core Components

1. **API Layer**: RESTful endpoints for all spreadsheet operations
2. **Service Layer**: Business logic for spreadsheet manipulation
3. **Repository Layer**: Data storage and retrieval
4. **Database Layer**: Persistent storage using PostgreSQL, MongoDB, and Redis

### Special Technical Features

#### Multi-Database Architecture

The application uses three different databases, each optimized for specific purposes:

- **PostgreSQL**: Stores user accounts, spreadsheet metadata, and access permissions
- **MongoDB**: Stores cell data, which can be of various types (text, numbers, formulas)
- **Redis**: Provides ultra-fast caching and handles distributed locking for concurrent edits

#### Smart Concurrency Handling

When multiple users edit the same spreadsheet simultaneously, the system:

1. Uses distributed locks to prevent conflicts
2. Applies a "topological sort" algorithm to determine the correct order to update cells
3. Prevents deadlocks by acquiring locks in a consistent order
4. Returns appropriate error messages (HTTP 409) when conflicts can't be resolved

#### Formula Evaluation System

The formula engine:

1. Parses expressions like `=SUM(A1:C5)+10`
2. Identifies cell dependencies (which cells are referenced by the formula)
3. Evaluates the formula to produce a result
4. Updates all dependent cells when a referenced cell changes

#### Redis-First Persistence

For lightning-fast performance:

1. Cell updates are written to Redis first
2. Changes are then asynchronously saved to MongoDB
3. This approach ensures immediate availability of updated cells
4. The system maintains consistency between Redis and MongoDB

## Edge Cases and How We Handle Them

### Circular References

**Problem**: When cell A1 references B1, which references C1, which references A1 (creating a loop)

**Solution**:
- The system detects circular dependencies using a graph-based algorithm
- Returns a clear error message (HTTP 400) instead of entering an infinite calculation loop
- Preserves the previous valid state of affected cells

### Concurrent Edits to the Same Cell

**Problem**: Two users try to update the same cell at the same time

**Solution**:
- Distributed locking system using Redis ensures only one update succeeds
- The first request acquires the lock and completes
- The second request receives a conflict response (HTTP 409)
- User can retry the operation with the latest cell value

### Complex Dependency Chains

**Problem**: Updating one cell requires recalculating many dependent cells in the correct order

**Solution**:
- System builds a dependency graph of all affected cells
- Uses topological sorting to determine the correct update sequence
- Ensures cells are always calculated after their dependencies
- Prevents partial or inconsistent updates

### Large Spreadsheets

**Problem**: Spreadsheets with thousands of cells can be slow to load and update

**Solution**:
- Redis caching provides fast access to frequently used cells
- Lazy loading of cell data reduces initial load time
- Efficient dependency tracking minimizes unnecessary recalculations
- Asynchronous updates for non-critical operations

### Nested Formula Evaluation

**Problem**: Complex nested formulas like `=SUM(A1:A5) * (MAX(B1:B10) - MIN(C1:C5))`

**Solution**:
- The formula parser breaks down the expression into an abstract syntax tree
- Each function and operation is evaluated in the correct order
- Results from inner functions are fed into outer functions
- The system maintains proper operator precedence
- Error handling at each step ensures robust evaluation

### Invalid Formula Syntax

**Problem**: Users may enter formulas with syntax errors like `=SUM(A1:A5` (missing closing parenthesis)

**Solution**:
- The formula parser performs syntax validation before evaluation
- Detailed error messages identify the specific syntax issue
- The system preserves the previous valid state of the cell
- Users receive clear feedback on how to correct the formula

### Permission Conflicts

**Problem**: Users attempting operations they don't have permission for

**Solution**:
- Permission checks at the API level prevent unauthorized access
- Clear HTTP 403 Forbidden responses with explanatory messages
- Proper authentication and authorization flow
- Different access levels (READ, WRITE, ADMIN, OWNER) with appropriate restrictions

## Testing and Quality Assurance

The application includes extensive testing at multiple levels:

### Unit Tests

- Over 500 unit tests covering all major components
- Specific tests for formula parsing, evaluation, and error handling
- Mock-based testing for database interactions

### Integration Tests

- End-to-end tests for complete workflows
- Database integration tests with real PostgreSQL, MongoDB, and Redis instances
- API contract tests ensuring the API matches its specification

### Stress Tests

The application includes comprehensive stress testing using Gatling:

- **Concurrent User Simulation**: Tests with up to 100 simultaneous users
- **Mixed Workload Tests**: Combines reads, writes, and formula calculations
- **Long-Duration Tests**: Runs for extended periods to detect memory leaks
- **Performance Metrics**: Measures response times, throughput, and error rates

Recent stress test results show excellent performance:
- 750 requests with 0% failure rate
- Average response time of 24ms
- 95th percentile response time of 102ms
- Throughput of ~27 requests per second

### Performance Testing Scenarios

Our stress tests include specific scenarios to validate system performance:

1. **High-Volume Formula Recalculation**
   - Creates a sheet with 1,000+ cells
   - Sets up complex dependency chains
   - Updates root cells to trigger cascading recalculations
   - Measures time to complete all updates

2. **Concurrent User Editing**
   - Simulates 20+ users simultaneously editing the same sheet
   - Users target both unique cells and the same cells
   - Verifies locking mechanisms prevent data corruption
   - Measures response times under heavy concurrent load

3. **Large Range Function Testing**
   - Tests functions like SUM and AVERAGE with very large ranges (1,000+ cells)
   - Measures performance impact of large range operations
   - Verifies correctness of results with large datasets

4. **Redis Cache Effectiveness**
   - Measures performance with and without Redis caching
   - Verifies <30ms latency for cached operations
   - Tests cache invalidation when underlying data changes
   - Measures cache hit rate under various access patterns

## Running the Project

### Prerequisites

- **Java 21**: The application requires Java 21 or higher
- **Docker and Docker Compose**: For running the database services
- **Gradle**: For building and running the application (included as a wrapper)

### Step-by-Step Setup

1. **Clone the Repository**
   ```bash
   git clone https://github.com/example/sheets.git
   cd sheets
   ```

2. **Start the Database Services**
   ```bash
   docker-compose up -d
   ```
   This starts PostgreSQL, MongoDB, and Redis in Docker containers.

3. **Build the Application**
   ```bash
   ./gradlew build
   ```

4. **Run the Application**
   ```bash
   ./gradlew bootRun
   ```

5. **Access the Application**
   Open your browser and navigate to:
   ```
   http://localhost:8080
   ```

### Running the Stress Tests

To run the stress tests and see how the system performs under load:

1. **Ensure the application is running**

2. **Run the stress test script**
   ```bash
   cd stress-test
   ./run-spreadsheet-test.sh
   ```

3. **View the Results**
   The script will display the path to the HTML report with detailed performance metrics.

## Design Patterns Used

The application follows several industry-standard design patterns:

- **Repository Pattern**: Separates data access logic from business logic
- **Service Layer Pattern**: Encapsulates business logic in dedicated services
- **Dependency Injection**: Uses Spring's DI container for loose coupling
- **Observer Pattern**: For updating dependent cells when source cells change
- **Strategy Pattern**: For different formula evaluation strategies
- **Factory Pattern**: For creating appropriate cell types based on input
- **Cache-Aside Pattern**: For efficient Redis caching with MongoDB as the source of truth

## Conclusion

This Spreadsheet Application demonstrates modern software engineering practices with a focus on:

- **Performance**: Using Redis for speed and caching
- **Concurrency**: Handling multiple simultaneous users with distributed locking
- **Robustness**: Comprehensive error handling and edge case management
- **Scalability**: Designed to handle large spreadsheets and many users
- **Maintainability**: Clean architecture and well-documented code

The extensive test suite ensures reliability, while the stress testing confirms the system can handle significant load without degradation in performance.
