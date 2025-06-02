# Low-Level Design (LLD) Document

## 1. Implementation Overview

### Current Implementation Analysis
The project is in the early stages with basic infrastructure set up. It includes a Spring Boot application with PostgreSQL and Redis integration, along with OpenAPI specification for API documentation. The current implementation includes only a health check endpoint.

### Code Organization
The codebase follows a standard Spring Boot project structure:
- `com.sheets.config`: Configuration classes
- `com.sheets.controllers`: API controllers
- `com.sheets.mappers`: DTO to domain model mappers
- `com.sheets.models`: Domain models and DTOs
- `com.sheets.services`: Business logic services
- `com.sheets.repositories`: Data access layer (to be implemented)

### Development Workflow
- OpenAPI-first approach with code generation
- Database schema management with Flyway
- Type-safe SQL with JOOQ code generation
- Gradle for build and dependency management

### Implementation Patterns
- Dependency injection with Spring
- Interface-based programming
- DTO pattern for API communication
- Repository pattern for data access

### Technical Debt Areas
- Limited test coverage
- Incomplete implementation of required features
- Missing documentation for implementation details

## 2. Detailed Component Design

### 2.1 Sheet Management Component

#### Domain Model

```kotlin
// Sheet entity
data class Sheet(
    val id: Long? = null,
    val name: String,
    val description: String,
    val maxLength: Int,
    val maxBreadth: Int,
    val userId: Long,  // Owner ID
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

// Access mapping entity
data class AccessMapping(
    val id: Long? = null,
    val sheetId: Long,
    val userId: Long,
    val accessType: AccessType,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

// Access type enum
enum class AccessType {
    READ,
    WRITE,
    ADMIN
}
```

#### Service Layer

```kotlin
interface SheetService {
    fun createSheet(name: String, description: String, userId: Long): Sheet
    fun getSheetsByUserId(userId: Long): List<Sheet>
    fun getSheetById(sheetId: Long, userId: Long): Sheet
    fun shareSheet(sheetId: Long, userIds: List<Long>, accessType: AccessType, ownerId: Long): String
}

@Service
class SheetServiceImpl(
    private val sheetRepository: SheetRepository,
    private val accessRepository: AccessRepository
) : SheetService {
    // Implementation details
}
```

#### Repository Layer

```kotlin
interface SheetRepository {
    fun save(sheet: Sheet): Sheet
    fun findByUserId(userId: Long): List<Sheet>
    fun findById(id: Long): Sheet?
    fun findSharedWithUser(userId: Long): List<Sheet>
}

interface AccessRepository {
    fun save(accessMapping: AccessMapping): AccessMapping
    fun findBySheetIdAndUserId(sheetId: Long, userId: Long): AccessMapping?
    fun findBySheetId(sheetId: Long): List<AccessMapping>
}
```

#### Controller Layer

```kotlin
@RestController
@RequestMapping("/sheet")
class SheetController(
    private val sheetService: SheetService,
    private val sheetMapper: SheetMapper
) {
    @PostMapping("/")
    fun createSheet(
        @RequestBody request: CreateSheetRequest,
        @RequestHeader("X-User-ID") userId: Long
    ): ResponseEntity<SheetResponse>
    
    @GetMapping("/")
    fun getSheets(
        @RequestHeader("X-User-ID") userId: Long
    ): ResponseEntity<List<SheetSummaryResponse>>
    
    @GetMapping("/{sheetId}")
    fun getSheetDetails(
        @PathVariable sheetId: Long,
        @RequestHeader("X-User-ID") userId: Long
    ): ResponseEntity<SheetDetailsResponse>
    
    @PostMapping("/share/{sheetId}")
    fun shareSheet(
        @PathVariable sheetId: Long,
        @RequestBody request: ShareSheetRequest,
        @RequestHeader("X-User-ID") userId: Long
    ): ResponseEntity<ShareSheetResponse>
}
```

### 2.2 Cell Management Component

#### Domain Model

```kotlin
// Cell entity
data class Cell(
    val id: String? = null,  // MongoDB document ID
    val cellId: String,      // row:column format
    val sheetId: Long,
    val dataType: DataType,
    val data: String,        // Raw data (primitive or expression)
    val isInvolvedInExpression: Boolean = false,
    val expression: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

// Data type enum
enum class DataType {
    PRIMITIVE,
    EXPRESSION
}

// Dependency entity
data class CellDependency(
    val id: String? = null,  // MongoDB document ID
    val cellId: String,
    val dependentCellIds: Set<String>,
    val sheetId: Long
)
```

#### Service Layer

```kotlin
interface CellService {
    fun updateCell(sheetId: Long, cellId: String, value: String, userId: Long): CellUpdateResult
    fun getCellsBySheetId(sheetId: Long): List<Cell>
}

@Service
class CellServiceImpl(
    private val cellRepository: CellRepository,
    private val dependencyRepository: DependencyRepository,
    private val sheetRepository: SheetRepository,
    private val accessRepository: AccessRepository,
    private val lockManager: LockManager,
    private val expressionEvaluator: ExpressionEvaluator
) : CellService {
    // Implementation details
}
```

#### Repository Layer

```kotlin
interface CellRepository {
    fun save(cell: Cell): Cell
    fun findByCellIdAndSheetId(cellId: String, sheetId: Long): Cell?
    fun findBySheetId(sheetId: Long): List<Cell>
    fun findByIsInvolvedInExpressionAndSheetId(isInvolved: Boolean, sheetId: Long): List<Cell>
}

interface DependencyRepository {
    fun save(dependency: CellDependency): CellDependency
    fun findByCellIdAndSheetId(cellId: String, sheetId: Long): CellDependency?
    fun findByDependentCellIdsContainingAndSheetId(cellId: String, sheetId: Long): List<CellDependency>
}
```

#### Controller Layer

```kotlin
@RestController
@RequestMapping("/sheet/{sheetId}/cell")
class CellController(
    private val cellService: CellService,
    private val cellMapper: CellMapper
) {
    @PostMapping("/{cellId}")
    fun updateCell(
        @PathVariable sheetId: Long,
        @PathVariable cellId: String,
        @RequestBody request: UpdateCellRequest,
        @RequestHeader("X-User-ID") userId: Long
    ): ResponseEntity<CellUpdateResponse>
}
```
