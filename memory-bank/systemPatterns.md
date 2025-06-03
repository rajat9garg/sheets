# System Patterns

**Created:** 2025-05-24  
**Status:** [ACTIVE]  
**Author:** [Your Name]  
**Last Modified:** 2025-06-04 02:55
**Last Updated By:** Cascade AI Assistant

## Table of Contents
- [Architectural Overview](#architectural-overview)
- [System Components](#system-components)
- [Data Flow](#data-flow)
- [Database Schema](#database-schema)
- [Repository Pattern Implementation](#repository-pattern-implementation)
- [Cell Dependency Management](#cell-dependency-management)
- [Error Handling Pattern](#error-handling-pattern)
- [Testing Patterns](#testing-patterns)
- [Design Decisions](#design-decisions)
- [Cross-Cutting Concerns](#cross-cutting-concerns)
- [Scalability Considerations](#scalability-considerations)
- [Expression Evaluation System](#expression-evaluation-system)
- [Stress Testing Architecture](#stress-testing-architecture)

## Architectural Overview
The Sheets application follows a layered architecture pattern with clear separation between API, service, and repository layers. The application uses Spring Boot as the core framework with PostgreSQL for data persistence, MongoDB for document storage, and Redis for caching.

### Architecture Diagram
```mermaid
graph TD
    Client[Client] --> |HTTP| API[API Layer]
    API --> |DTO/Domain Conversion| Service[Service Layer]
    Service --> |Domain Objects| Repository[Repository Layer]
    Repository --> |SQL Queries| PostgreSQL[(PostgreSQL Database)]
    Repository --> |Document Storage| MongoDB[(MongoDB Database)]
    Repository --> |Caching| Redis[(Redis Cache)]
    
    subgraph "API Layer"
        Controller[Controllers]
        DTOs[Generated DTOs]
        Mappers[DTO-Domain Mappers]
    end
    
    subgraph "Service Layer"
        SheetService[SheetService]
        AccessService[AccessService]
        UserService[UserService]
        CellService[CellService]
        CellDependencyService[CellDependencyService]
        ExpressionService[ExpressionService]
    end
    
    subgraph "Repository Layer"
        SheetRepo[SheetRepository]
        AccessRepo[AccessRepository]
        UserRepo[UserRepository]
        CellRepo[CellRepository]
        CellDependencyRepo[CellDependencyRepository]
        CellDependencyRedisRepo[CellDependencyRedisRepository]
    end
    
    subgraph "Domain Model"
        Sheet[Sheet]
        AccessMapping[AccessMapping]
        User[User]
        AccessType[AccessType Enum]
        Cell[Cell]
        CellDependency[CellDependency]
        DataType[DataType Enum]
    end
    
    Controller --> DTOs
    Controller --> Mappers
    Mappers --> Service
    SheetService --> SheetRepo
    SheetService --> AccessRepo
    AccessService --> AccessRepo
    UserService --> UserRepo
    CellService --> CellRepo
    CellService --> CellDependencyService
    CellDependencyService --> CellDependencyRepo
    CellDependencyService --> CellDependencyRedisRepo
    ExpressionService --> CellService
```

## System Components
### API Layer
- **Purpose:** Handles HTTP requests and responses
- **Responsibilities:**
  - Request validation
  - Response formatting
  - Error handling
  - DTO-to-domain model conversion
- **Components:**
  - Controllers
  - Generated DTOs from OpenAPI
  - Mappers between DTOs and domain models

### Service Layer
- **Purpose:** Implements business logic
- **Responsibilities:**
  - Business rule enforcement
  - Transaction management
  - Orchestration of repository calls
  - Domain model validation
- **Components:**
  - SheetServiceImpl
  - AccessServiceImpl
  - UserServiceImpl
  - CellServiceImpl
  - CellDependencyServiceImpl
  - ExpressionServiceImpl

### Repository Layer
- **Purpose:** Data access and persistence
- **Responsibilities:**
  - CRUD operations
  - Query execution
  - Type conversion between domain and database
  - Error handling for database operations
  - Caching strategies
- **Components:**
  - SheetRepositoryImpl
  - AccessRepositoryImpl
  - UserRepositoryImpl
  - CellRepositoryImpl
  - CellDependencyRepositoryImpl
  - CellDependencyRedisRepository

### Domain Model
- **Purpose:** Represents business entities and logic
- **Components:**
  - Sheet
  - AccessMapping
  - User
  - AccessType (Enum)
  - Cell
  - CellDependency
  - DataType (Enum)

## Database Schema
The database schema consists of three main tables in PostgreSQL: users, sheets, and access_mappings. Cell and CellDependency data is stored in MongoDB for flexibility.

```mermaid
erDiagram
    USERS {
        bigint id PK
        varchar name
        varchar email
        timestamp created_at
        timestamp updated_at
    }
    
    SHEETS {
        bigint id PK
        bigint user_id
        varchar name
        jsonb data
        timestamp created_at
        timestamp updated_at
    }
    
    ACCESS_MAPPINGS {
        bigint id PK
        bigint sheet_id FK
        bigint user_id
        enum access_type
        timestamp created_at
        timestamp updated_at
    }
    
    SHEETS ||--o{ ACCESS_MAPPINGS : "has"
    USERS ||--o{ SHEETS : "owns"
```

### MongoDB Collections
```mermaid
erDiagram
    CELLS {
        string id PK "sheetId:cellRef"
        long sheetId
        string cellRef
        string data
        string evaluatedValue
        enum dataType
        timestamp created_at
        timestamp updated_at
    }
    
    CELL_DEPENDENCIES {
        string id PK
        string sourceCellId
        string targetCellId
        long sheetId
        timestamp created_at
    }
    
    CELLS ||--o{ CELL_DEPENDENCIES : "depends on"
```

### Key Database Features
- **Custom Enum Type:** `access_type` with values 'READ', 'WRITE', 'ADMIN', 'OWNER'
- **Foreign Key Constraints:** Removed for development flexibility
- **Timestamps:** All tables include `created_at` and `updated_at` columns
- **Triggers:** Automatic update of `updated_at` column on record changes
- **MongoDB:** Used for storing cell data and dependencies for flexibility
- **Redis:** Used for caching cell dependencies for performance

## Repository Pattern Implementation
The repository layer implements the repository pattern with interfaces defining the contract and implementations providing the actual database interaction.

```mermaid
classDiagram
    class Repository~T~ {
        <<interface>>
        +save(T entity) T
        +findById(UUID id) T?
        +findAll() List~T~
        +update(T entity) T
        +delete(UUID id) boolean
    }
    
    class SheetRepository {
        <<interface>>
        +findByUserId(Long userId) List~Sheet~
        +findByOwnerId(Long ownerId) List~Sheet~
        +findSharedWithUser(Long userId) List~Sheet~
    }
    
    class AccessRepository {
        <<interface>>
        +findBySheetIdAndUserId(Long sheetId, Long userId) AccessMapping?
        +findByUserIdAndAccessType(Long userId, AccessType accessType) List~AccessMapping~
        +upsert(AccessMapping accessMapping) AccessMapping
        +deleteBySheetIdAndUserId(Long sheetId, Long userId) boolean
    }
    
    class CellRepository {
        <<interface>>
        +findById(String id) Cell?
        +findBySheetId(Long sheetId) List~Cell~
        +save(Cell cell) Cell
        +update(Cell cell) Cell
        +delete(String id) boolean
    }
    
    class CellDependencyRepository {
        <<interface>>
        +findById(String id) CellDependency?
        +findBySourceCellId(String sourceCellId) List~CellDependency~
        +findByTargetCellId(String targetCellId) List~CellDependency~
        +findBySourceCellIdAndTargetCellId(String sourceCellId, String targetCellId) CellDependency?
        +findBySheetId(Long sheetId) List~CellDependency~
        +save(CellDependency dependency) CellDependency
        +saveAll(List~CellDependency~ dependencies) List~CellDependency~
        +deleteBySourceCellId(String sourceCellId) int
        +deleteByTargetCellId(String targetCellId) int
        +deleteBySheetId(Long sheetId) int
    }
    
    class CellDependencyRedisRepository {
        -RedisTemplate redisTemplate
        -ObjectMapper objectMapper
        -long DEPENDENCY_CACHE_TTL
        +saveDependency(CellDependency dependency) void
        +saveDependencies(List~CellDependency~ dependencies) void
        +getDependency(String sourceCellId, String targetCellId) CellDependency?
        +getDependenciesBySourceCellId(String sourceCellId) List~CellDependency~
        +getDependenciesByTargetCellId(String targetCellId) List~CellDependency~
        +getDependenciesBySheetId(Long sheetId) List~CellDependency~
        +deleteDependency(String sourceCellId, String targetCellId) void
        +deleteBySourceCellId(String sourceCellId) void
        +deleteByTargetCellId(String targetCellId) void
        +deleteBySheetId(Long sheetId) void
    }
    
    Repository <|-- SheetRepository
    Repository <|-- AccessRepository
    Repository <|-- CellRepository
    Repository <|-- CellDependencyRepository
```

## Cell Dependency Management

### Cell Dependency Architecture
The cell dependency management system uses a dual-storage approach with MongoDB for persistence and Redis for caching.

```mermaid
graph TD
    CellService[CellService] --> |Updates| CellDependencyService[CellDependencyService]
    CellDependencyService --> |Cache First| CellDependencyRedisRepo[CellDependencyRedisRepository]
    CellDependencyService --> |Persistence| CellDependencyRepo[CellDependencyRepository]
    CellDependencyRedisRepo --> |Cache Miss| CellDependencyRepo
    CellDependencyRedisRepo --> |Read/Write| Redis[(Redis Cache)]
    CellDependencyRepo --> |Read/Write| MongoDB[(MongoDB)]
    
    subgraph "Dependency Detection"
        ExpressionParser[Expression Parser]
        CircularDependencyDetector[Circular Dependency Detector]
    end
    
    subgraph "Update Propagation"
        SyncUpdater[Synchronous Updater]
        AsyncUpdater[Asynchronous Updater]
    end
    
    CellService --> ExpressionParser
    ExpressionParser --> CircularDependencyDetector
    CellService --> SyncUpdater
    CellService --> AsyncUpdater
    
    ExpressionParser --> |Extract Dependencies| CellDependencyService
    CircularDependencyDetector --> |Validate| CellDependencyService
    SyncUpdater --> |Update| CellService
    AsyncUpdater --> |Update| CellService
```

### Circular Dependency Detection
The system uses a depth-first search algorithm to detect circular dependencies in cell references.

```mermaid
graph TD
    Start[Start] --> BuildMap[Build Dependency Map]
    BuildMap --> InitVisited[Initialize Visited Map]
    InitVisited --> InitPath[Initialize Current Path]
    InitPath --> DFS[DFS from Source Cell]
    
    subgraph "Depth-First Search"
        DFS --> CheckVisited{Already Visited?}
        CheckVisited -- Yes --> CheckInPath{In Current Path?}
        CheckInPath -- Yes --> DetectCycle[Detect Cycle]
        CheckInPath -- No --> Return[Return No Cycle]
        CheckVisited -- No --> MarkVisited[Mark as Visited]
        MarkVisited --> AddToPath[Add to Current Path]
        AddToPath --> GetDependencies[Get Dependencies]
        GetDependencies --> ForEachDep[For Each Dependency]
        ForEachDep --> RecursiveDFS[Recursive DFS]
        RecursiveDFS --> CycleFound{Cycle Found?}
        CycleFound -- Yes --> ReturnCycle[Return Cycle]
        CycleFound -- No --> NextDep[Next Dependency]
        NextDep --> ForEachDep
        NextDep -- No More Dependencies --> RemoveFromPath[Remove from Path]
        RemoveFromPath --> ReturnNoCycle[Return No Cycle]
    end
    
    DetectCycle --> BuildCycle[Build Cycle Path]
    BuildCycle --> ReturnCyclePath[Return Cycle Path]
```

### Asynchronous Update Pattern
The system uses Spring's @Async annotation to update dependent cells asynchronously.

```mermaid
sequenceDiagram
    participant Client
    participant CellController
    participant CellService
    participant CellDependencyService
    participant AsyncExecutor
    
    Client->>CellController: Update Cell
    CellController->>CellService: updateCell()
    CellService->>CellService: processExpression()
    CellService->>CellDependencyService: createDependencies()
    CellService->>CellService: updateDependentCellsAsync()
    CellService-->>CellController: Return Updated Cell
    CellController-->>Client: Return Response
    
    CellService->>AsyncExecutor: Execute Async Task
    AsyncExecutor->>CellDependencyService: getDependenciesByTargetCellId()
    CellDependencyService->>AsyncExecutor: Return Dependencies
    
    loop For Each Dependency
        AsyncExecutor->>CellService: evaluateExpression()
        CellService->>AsyncExecutor: Return Result
    end
```

### Redis Caching Strategy
The system uses Redis for caching cell dependencies with a 24-hour TTL.

```mermaid
graph TD
    Save[Save Dependency] --> CreateKey[Create Dependency Key]
    CreateKey --> StoreValue[Store Value with TTL]
    StoreValue --> AddToSourceSet[Add to Source Set]
    AddToSourceSet --> AddToTargetSet[Add to Target Set]
    AddToTargetSet --> AddToSheetSet[Add to Sheet Set]
    
    Get[Get Dependency] --> GetKey[Get Dependency Key]
    GetKey --> CheckCache{In Cache?}
    CheckCache -- Yes --> ReturnValue[Return Value]
    CheckCache -- No --> ReturnNull[Return Null]
    
    GetBySource[Get By Source] --> GetSourceSet[Get Source Set]
    GetSourceSet --> ForEachKey[For Each Key in Set]
    ForEachKey --> GetValues[Get Values]
    GetValues --> ReturnList[Return List]
    
    Delete[Delete Dependency] --> GetDependency[Get Dependency]
    GetDependency --> RemoveFromSourceSet[Remove from Source Set]
    RemoveFromSourceSet --> RemoveFromTargetSet[Remove from Target Set]
    RemoveFromTargetSet --> RemoveFromSheetSet[Remove from Sheet Set]
    RemoveFromSheetSet --> DeleteKey[Delete Key]
```

## Error Handling Pattern
### Custom Exception Hierarchy
To provide more granular and meaningful error messages, a custom exception hierarchy has been implemented. All custom exceptions inherit from a base `SheetException`.

```mermaid
graph TD
    SheetException --> ResourceLockException
    ResourceLockException --> SheetLockException
    ResourceLockException --> CellLockException
    SheetException --> CircularReferenceException
    SheetException --> CellDependencyException
    SheetException --> PersistenceException
```

- **`SheetException`**: Base abstract class for all application-specific exceptions.
- **`ResourceLockException`**: Base class for exceptions related to resource locking, including `retryAfterMs` for client guidance.
- **`SheetLockException`**: Specific exception for conflicts when acquiring a sheet-level lock.
- **`CellLockException`**: Specific exception for conflicts when acquiring a cell-level lock.
- **`CircularReferenceException`**: Thrown when a circular dependency is detected in cell formulas.
- **`CellDependencyException`**: Thrown when an operation (e.g., cell deletion) is blocked due to existing cell dependencies.
- **`PersistenceException`**: Thrown for errors encountered during data persistence operations (e.g., Redis or MongoDB).

### Global Exception Handling
All custom exceptions, as well as standard Spring exceptions, are handled centrally by the `GlobalExceptionHandler`. This ensures a consistent error response format across the entire API.

- **Centralized Handling**: A single `@ControllerAdvice` class (`GlobalExceptionHandler`) intercepts all exceptions.
- **Standardized Response**: Errors are mapped to a consistent `ErrorResponse` model (defined in `api.yaml`), which includes `status`, `error`, `message`, `timestamp`, and a `details` field for additional context.
- **HTTP Status Mapping**: Exceptions are mapped to appropriate HTTP status codes (e.g., `409 Conflict` for lock errors, `400 Bad Request` for circular dependencies).
- **User-Friendly Messages**: Error responses include clear, actionable messages for the client.

```mermaid
graph TD
    subgraph Application
        Controller[Controller] --> Service[Service Layer]
        Service --> Repository[Repository Layer]
        Repository --> Database[Database/Redis]
    end

    subgraph Exception Flow
        Exception[Exception Thrown] --> GlobalExceptionHandler[GlobalExceptionHandler]
        GlobalExceptionHandler --> ErrorResponse[Standardized ErrorResponse]
        ErrorResponse --> Client[Client]
    end

    Controller -- Throws Custom Exceptions --> Exception
    Service -- Throws Custom Exceptions --> Exception
    Repository -- Throws Custom Exceptions --> Exception
```

### Error Response Structure
The `ErrorResponse` model follows the OpenAPI specification defined in `api.yaml`:

```yaml
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
    path:
      type: string
      description: Request path
      example: "/v1/sheet"
    timestamp:
      type: string
      format: date-time
      description: Time when the error occurred
      example: "2025-06-04T00:30:00Z"
    details:
      type: object
      description: Additional error details specific to the error type
      additionalProperties: true
      example: {"resourceId": "1", "lockOwner": "user123", "retryAfterMs": 5000}
```

This pattern ensures that error handling is robust, consistent, and provides sufficient information for clients to understand and react to issues gracefully.

## Testing Patterns
The application follows a comprehensive testing strategy with a focus on unit tests, integration tests, and end-to-end tests. The testing approach uses MockK for mocking in Kotlin tests and follows the Arrange-Act-Assert pattern.

### Unit Testing Structure
```mermaid
graph TD
    Test[Test Class] --> Setup[Setup/Arrange]
    Setup --> Mocks[Configure Mocks]
    Setup --> TestData[Prepare Test Data]
    Test --> Execute[Execute/Act]
    Execute --> Method[Call Method Under Test]
    Test --> Verify[Verify/Assert]
    Verify --> Results[Check Results]
    Verify --> Interactions[Verify Mock Interactions]
```

### Mock Configuration Patterns
When testing services with dependencies, the following patterns are used:

1. **Global Mocks Setup:**
   - Configure common mocks in the setUp() method
   - Set up default behaviors for frequently used dependencies
   - Initialize test data and fixtures

2. **Flexible Matchers:**
   - Use `any()` matchers instead of exact object matching to avoid type mismatch issues
   - Employ `slot` capture for complex object verification
   - Use `match { }` for custom matching logic

3. **Verification Strategy:**
   - Focus on essential operations in verifications
   - Verify end results rather than implementation details
   - Use ordered verification only when sequence matters

### A1 Notation Testing Considerations
When testing components that use A1 notation, special care is needed for:

1. **Cell ID Format:**
   - Cell IDs follow the format `sheetId:row:column` where column is an alphabetical letter (e.g., "1:1:A")
   - Helper methods must correctly extract sheet ID and cell coordinates
   - Test data must use consistent A1 notation format

2. **Type Handling:**
   - Ensure proper type handling for timestamps (Instant vs. String)
   - Use appropriate type converters in test setup
   - Handle type coercion in mock expectations

3. **Dependency Mocking:**
   - Mock both source and target cell dependencies
   - Ensure proper handling of dependency creation and deletion
   - Use empty lists for dependencies when appropriate to simplify tests

### Test Data Generation
```mermaid
graph TD
    TestData[Test Data Generation] --> CellData[Cell Data]
    TestData --> DependencyData[Dependency Data]
    TestData --> SheetData[Sheet Data]
    
    CellData --> A1Format[A1 Notation Format]
    CellData --> DataTypes[Various Data Types]
    CellData --> Formulas[Formula Expressions]
    
    DependencyData --> SourceTarget[Source-Target Pairs]
    DependencyData --> CircularDeps[Circular Dependencies]
    DependencyData --> ComplexGraphs[Complex Dependency Graphs]
```

## Design Decisions
### Repository Pattern
- **Decision:** Use repository interfaces with multiple implementations
- **Rationale:** Enables swapping out data storage implementations without changing service layer
- **Implementation:** Each repository has a contract defined by an interface and one or more implementations

### Caching Strategy
- **Decision:** Use Redis for caching with TTL-based expiration
- **Rationale:** Provides fast access to frequently used data while ensuring eventual consistency
- **Implementation:** Cache-aside pattern with Redis as the cache and MongoDB as the source of truth

### Asynchronous Processing
- **Decision:** Use Spring's @Async for non-blocking operations
- **Rationale:** Improves user experience by returning responses quickly while processing continues in background
- **Implementation:** Async methods for dependent cell updates with proper exception handling

### Circular Dependency Detection
- **Decision:** Use depth-first search for cycle detection
- **Rationale:** Efficiently detects cycles in dependency graphs before evaluation
- **Implementation:** DFS algorithm that tracks visited nodes and current path

## Cross-Cutting Concerns
### Error Handling
- **Repository Layer:** Catches database exceptions and translates to domain exceptions
- **Service Layer:** Handles business logic exceptions and provides meaningful error messages
- **Dependency Management:** Logs dependency creation, updates, and circular dependency detection

### Logging
- **Repository Layer:** Logs method entry/exit, parameters, and results
- **Service Layer:** Logs business operations and decisions
- **Dependency Management:** Logs dependency creation, updates, and circular dependency detection

### Performance Optimization
- **Caching:** Redis caching for frequently accessed dependencies
- **Asynchronous Processing:** Background processing for dependent cell updates
- **Batch Operations:** Batch saving of dependencies for better performance

## Scalability Considerations
### Horizontal Scaling
- **Stateless Services:** All services are stateless and can be horizontally scaled
- **Redis Clustering:** Redis can be configured for clustering to handle increased load
- **MongoDB Sharding:** MongoDB collections can be sharded for horizontal scaling

### Performance Bottlenecks
- **Large Dependency Graphs:** May require optimization for spreadsheets with many dependencies
- **Circular Dependency Detection:** Algorithm complexity increases with dependency graph size
- **Redis Memory Usage:** Monitoring needed for Redis memory usage with large number of dependencies

## Expression Evaluation System
The expression evaluation system is responsible for parsing and evaluating formulas in cells. It supports arithmetic operations, function calls, cell references, and cell ranges.

### Expression Evaluation Flow
```mermaid
graph TD
    Cell[Cell with Expression] --> ExpressionService[ExpressionService]
    ExpressionService --> ExpressionEvaluator[ExpressionEvaluator]
    ExpressionEvaluator --> |Function Call?| FunctionRegistry[FunctionRegistry]
    ExpressionEvaluator --> |Arithmetic?| ArithmeticEvaluator[Arithmetic Evaluator]
    FunctionRegistry --> |Lookup Function| ExpressionFunction[ExpressionFunction]
    ExpressionFunction --> |Evaluate| CellService[CellService]
    CellService --> |Get Cell Values| CellRepository[CellRepository]
    ArithmeticEvaluator --> |Replace Cell References| CellService
    ExpressionEvaluator --> |Result| Cell
```

### Alphabetical Column Notation (A1 Style)
The system uses alphabetical column notation (A1 style) for cell references, which is the industry standard for spreadsheet applications. This notation uses letters for columns (A, B, C, ..., Z, AA, AB, ...) and numbers for rows (1, 2, 3, ...).

#### Cell Reference Format
```
[Column Letter][Row Number]
```
Examples: A1, B2, C3, AA10, etc.

#### Cell Range Format
```
[Start Cell]:[End Cell]
```
Examples: A1:C3, B2:D5, etc.

#### Implementation Details
```mermaid
graph TD
    A1Reference[A1 Reference] --> |Parse| ColumnLetter[Column Letter]
    A1Reference --> |Parse| RowNumber[Row Number]
    ColumnLetter --> |Convert| ColumnNumber[Column Number]
    ColumnNumber --> |Convert| ColumnLetter
    A1Reference --> |Format Cell ID| CellID[sheetId:row:column]
    CellID --> CellRepository[CellRepository]
```

#### Conversion Functions
The system includes utility functions to convert between column letters and column numbers:

1. **Column Letter to Number**:
   ```kotlin
   fun columnLetterToNumber(columnLetter: String): Int {
       var result = 0
       for (c in columnLetter) {
           result = result * 26 + (c - 'A' + 1)
       }
       return result
   }
   ```

2. **Column Number to Letter**:
   ```kotlin
   fun numberToColumnLetter(columnNumber: Int): String {
       var dividend = columnNumber
       var columnName = ""
       
       while (dividend > 0) {
           val modulo = (dividend - 1) % 26
           columnName = (modulo + 'A'.code).toChar() + columnName
           dividend = (dividend - modulo) / 26
       }
       
       return columnName
   }
   ```

### Expression Functions
The system supports various expression functions for formula evaluation. Each function implements the `ExpressionFunction` interface and is registered with the `FunctionRegistry`.

```mermaid
classDiagram
    class ExpressionFunction {
        <<interface>>
        +name() String
        +evaluate(args: List<String>) String
    }
    
    ExpressionFunction <|-- SumFunction
    ExpressionFunction <|-- AverageFunction
    ExpressionFunction <|-- MinFunction
    ExpressionFunction <|-- MaxFunction
    
    class SumFunction {
        -cellService: CellService
        +name() String
        +evaluate(args: List<String>) String
    }
    
    class AverageFunction {
        -cellService: CellService
        +name() String
        +evaluate(args: List<String>) String
    }
    
    class MinFunction {
        -cellService: CellService
        +name() String
        +evaluate(args: List<String>) String
    }
    
    class MaxFunction {
        -cellService: CellService
        +name() String
        +evaluate(args: List<String>) String
    }
    
    class FunctionRegistry {
        <<interface>>
        +registerFunction(function: ExpressionFunction) void
        +getFunction(name: String) ExpressionFunction?
    }
    
    class FunctionRegistryImpl {
        -functions: Map<String, ExpressionFunction>
        +registerFunction(function: ExpressionFunction) void
        +getFunction(name: String) ExpressionFunction?
    }
    
    FunctionRegistry <|-- FunctionRegistryImpl
    FunctionRegistryImpl --> ExpressionFunction : contains
```

#### Function Evaluation Process
Each expression function follows a similar pattern for evaluating cell references and ranges:

1. Extract sheetId from arguments if provided
2. Process each argument:
   - If it's a direct number, use it as is
   - If it's an A1 notation cell reference (e.g., A1), look up the cell value
   - If it's an A1 notation range (e.g., A1:C3), process all cells in the range
   - If it's a legacy numeric reference (e.g., 1:1), convert to A1 notation and look up the cell value
   - If it's a legacy numeric range (e.g., 1:1-3:3), convert to A1 notation and process all cells in the range
3. Apply the function logic to the collected values
4. Return the result as a string

### Arithmetic Expression Evaluation
Arithmetic expressions are evaluated using a tokenizer and operator precedence logic. The system supports standard operators (+, -, *, /, parentheses) and cell references.

```mermaid
graph TD
    Expression[Arithmetic Expression] --> Tokenizer[Tokenize Expression]
    Tokenizer --> ReplaceReferences[Replace Cell References]
    ReplaceReferences --> |A1 References| CellLookup[Look up Cell Values]
    CellLookup --> TokenizedExpression[Tokenized Expression]
    TokenizedExpression --> EvaluateExpression[Evaluate with Operator Precedence]
    EvaluateExpression --> Result[Result]
```

#### Cell Reference Replacement
Before evaluating an arithmetic expression, all cell references are replaced with their actual values:

```kotlin
private fun evaluateArithmeticExpression(expression: String, context: Map<String, String>): String {
    var processedExpression = expression
    val a1Pattern = "([A-Z]+)(\\d+)".toRegex()
    val a1Matches = a1Pattern.findAll(processedExpression)
    
    for (match in a1Matches) {
        val cellRef = match.value
        if (cellRef in context) {
            val value = context[cellRef] ?: "0"
            processedExpression = processedExpression.replace(cellRef, value)
        } else {
            processedExpression = processedExpression.replace(cellRef, "0")
        }
    }
    
    // Evaluate the processed expression using tokenizer and operator precedence
    // ...
}

```

## Stress Testing Architecture
The application includes a comprehensive stress testing framework using Gatling to validate API behavior under load and concurrent access scenarios.

### Stress Test Architecture
```mermaid
graph TD
    GatlingEngine[Gatling Test Engine] --> |HTTP Requests| API[API Layer]
    
    subgraph "Gatling Components"
        Scenarios[Test Scenarios]
        Feeders[Data Feeders]
        Assertions[Assertions]
        Reports[Reports Generator]
    end
    
    subgraph "Test Scenarios"
        BasicFlow[Full Workflow]
        ConcurrentPrimitives[Concurrent Primitive Updates]
        ConcurrentExpressions[Concurrent Expression Updates]
        CircularDependency[Circular Dependency Test]
    end
    
    Scenarios --> BasicFlow
    Scenarios --> ConcurrentPrimitives
    Scenarios --> ConcurrentExpressions
    Scenarios --> CircularDependency
    
    Feeders --> |User IDs| Scenarios
    Feeders --> |Cell Coordinates| Scenarios
    Feeders --> |Cell Expressions| Scenarios
    
    GatlingEngine --> Scenarios
    GatlingEngine --> Feeders
    GatlingEngine --> Assertions
    GatlingEngine --> Reports
    
    API --> |Responses| GatlingEngine
```

### Stress Test Components
- **Gatling Engine**: Core test execution engine that manages scenarios, feeders, and HTTP requests
- **Test Scenarios**: Defined workflows that simulate user behavior
  - **Full Workflow**: Basic end-to-end flow covering sheet creation, cell updates, retrieval, and deletion
  - **Concurrent Primitive Updates**: Tests concurrent updates to the same cells with primitive values
  - **Concurrent Expression Updates**: Tests concurrent updates to cells with expressions that reference other cells
  - **Circular Dependency Test**: Tests system behavior when circular dependencies are created
- **Feeders**: Data providers that generate test data
  - **User ID Feeder**: Provides consistent user IDs for proper access control
  - **Cell Coordinate Feeder**: Generates random cell coordinates and data
  - **Fixed Cell Feeder**: Provides a limited set of cell coordinates to force concurrent updates
  - **Expression Feeder**: Generates random cell expressions using A1 notation
- **Assertions**: Validates response status codes and content
- **Reports Generator**: Creates detailed HTML reports with performance metrics

### Stress Test Data Flow
```mermaid
sequenceDiagram
    participant Gatling as Gatling Engine
    participant Feeder as Data Feeders
    participant API as API Endpoints
    participant Service as Service Layer
    participant DB as Databases
    
    Gatling->>Feeder: Request test data
    Feeder-->>Gatling: Provide user IDs, cell coordinates, expressions
    
    Gatling->>API: Create sheet (POST /sheet)
    API->>Service: Process request
    Service->>DB: Store sheet
    DB-->>Service: Confirm storage
    Service-->>API: Return sheet ID
    API-->>Gatling: Return 201 Created with sheet ID
    
    Gatling->>Feeder: Get cell data
    Feeder-->>Gatling: Provide cell coordinates and values
    
    Gatling->>API: Update cell (POST /sheet/{sheetId}/cell)
    API->>Service: Process cell update
    Service->>DB: Store cell data
    Service->>Service: Update dependent cells
    DB-->>Service: Confirm storage
    Service-->>API: Return updated cell
    API-->>Gatling: Return 200 OK
    
    Gatling->>API: Get cell (GET /sheet/{sheetId}/cell/{row}/{column})
    API->>Service: Retrieve cell
    Service->>DB: Query cell data
    DB-->>Service: Return cell data
    Service-->>API: Return cell
    API-->>Gatling: Return 200 OK with cell data
    
    Gatling->>API: Delete cell (DELETE /sheet/{sheetId}/cell/{row}/{column})
    API->>Service: Process deletion
    Service->>DB: Remove cell data
    DB-->>Service: Confirm deletion
    Service-->>API: Confirm success
    API-->>Gatling: Return 204 No Content
```

### Concurrency Test Pattern
The stress test implements a specific pattern to test concurrent updates to the same resources:

```mermaid
graph TD
    CreateShared[Create Shared Sheet] --> ShareWithUsers[Share With All Test Users]
    ShareWithUsers --> PopulateBase[Populate Base Values]
    PopulateBase --> ConcurrentUpdates[Concurrent Updates by Multiple Users]
    ConcurrentUpdates --> VerifyFinal[Verify Final State]
    
    subgraph "Concurrency Test Flow"
        CreateShared
        ShareWithUsers
        PopulateBase
        ConcurrentUpdates
        VerifyFinal
    end
```

1. **Create Shared Sheet**: A single sheet is created that will be accessed by all test users
2. **Share With All Users**: The sheet is explicitly shared with all test users with WRITE access
3. **Populate Base Values**: Initial values are set in cells that will be concurrently updated
4. **Concurrent Updates**: Multiple users (20+) simultaneously update the same set of cells
5. **Verify Final State**: The final state of the cells is retrieved and verified

This pattern effectively tests:
- Locking mechanisms for concurrent cell updates
- Race conditions in cell value updates
- Expression evaluation with changing dependencies
- Conflict resolution in the API layer

### Circular Dependency Test Pattern
The stress test includes a specific scenario to test circular dependency detection:

```mermaid
graph TD
    CreateSheet[Create Test Sheet] --> SetA1[Set A1 to reference C1]
    SetA1 --> SetB1[Set B1 to reference A1]
    SetB1 --> SetC1[Set C1 to reference B1]
    SetC1 --> VerifyError[Verify Error Response]
    
    subgraph "Circular Dependency Test"
        CreateSheet
        SetA1
        SetB1
        SetC1
        VerifyError
    end
```

1. **Create Test Sheet**: A dedicated sheet is created for circular dependency testing
2. **Set A1 to reference C1**: Cell A1 is set to `=C1+1`
3. **Set B1 to reference A1**: Cell B1 is set to `=A1+1`
4. **Set C1 to reference B1**: Cell C1 is set to `=B1+1`, creating a circular dependency (A1→C1→B1→A1)
5. **Verify Error Response**: The system should detect the circular dependency and return an appropriate error

This pattern tests the system's ability to detect and handle circular dependencies in cell expressions.

{{ ... }}
