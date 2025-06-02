# System Patterns

**Created:** 2025-05-24  
**Status:** [ACTIVE]  
**Author:** [Your Name]  
**Last Modified:** 2025-06-02
**Last Updated By:** Cascade AI Assistant

## Table of Contents
- [Architectural Overview](#architectural-overview)
- [System Components](#system-components)
- [Data Flow](#data-flow)
- [Database Schema](#database-schema)
- [Repository Pattern Implementation](#repository-pattern-implementation)
- [Cell Dependency Management](#cell-dependency-management)
- [Design Decisions](#design-decisions)
- [Cross-Cutting Concerns](#cross-cutting-concerns)
- [Scalability Considerations](#scalability-considerations)

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
- **Controller Layer:** Converts exceptions to HTTP status codes and error messages

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
