# Low-Level Design (LLD) Document - Part 2

## 3. Extension Mechanisms

### 3.1 Expression Evaluation Extensions

The expression evaluation component is designed for extensibility using the Strategy pattern:

```kotlin
// Base interface
interface ExpressionEvaluator {
    fun evaluate(expression: String, context: EvaluationContext): EvaluationResult
    fun isExpression(value: String): Boolean
    fun extractDependencies(expression: String): Set<String>
}

// Simple implementation
@Service
@Primary
class SimpleExpressionEvaluator : ExpressionEvaluator {
    // Basic implementation
}

// Advanced implementation (future extension)
@Service
@Qualifier("advanced")
class AdvancedExpressionEvaluator : ExpressionEvaluator {
    // Advanced implementation with more functions
}

// Function registry for custom functions
@Service
class FunctionRegistry {
    private val functions = mutableMapOf<String, Function>()
    
    fun register(name: String, function: Function) {
        functions[name] = function
    }
    
    fun get(name: String): Function? = functions[name]
    
    interface Function {
        fun execute(args: List<Any>): Any
    }
}
```

### 3.2 Cell Type Extensions

The cell component is designed for extensibility using the Factory pattern:

```kotlin
// Cell type interface
interface CellType {
    fun getValue(cell: Cell, context: EvaluationContext): Any
    fun validate(value: String): Boolean
}

// Registry for cell types
@Service
class CellTypeRegistry {
    private val types = mutableMapOf<DataType, CellType>()
    
    fun register(type: DataType, cellType: CellType) {
        types[type] = cellType
    }
    
    fun get(type: DataType): CellType = types[type] ?: throw IllegalArgumentException("Unknown cell type: $type")
}

// Primitive cell type
@Service
class PrimitiveCellType : CellType {
    // Implementation
}

// Expression cell type
@Service
class ExpressionCellType(
    private val expressionEvaluator: ExpressionEvaluator
) : CellType {
    // Implementation
}
```

### 3.3 Storage Backend Extensions

The repository interfaces are designed to allow different implementations:

```kotlin
// Repository interfaces
interface CellRepository {
    fun save(cell: Cell): Cell
    fun findByCellIdAndSheetId(cellId: String, sheetId: Long): Cell?
    fun findBySheetId(sheetId: Long): List<Cell>
}

// MongoDB implementation
@Repository
@Primary
class MongoCellRepository(
    private val mongoTemplate: MongoTemplate
) : CellRepository {
    // MongoDB implementation
}

// In-memory implementation (for testing)
@Repository
@Qualifier("inMemory")
class InMemoryCellRepository : CellRepository {
    // In-memory implementation
}
```

### 3.4 Notification System Extensions

A notification system can be added with the Observer pattern:

```kotlin
// Notification interface
interface NotificationService {
    fun notifyCellUpdated(sheetId: Long, cellId: String, userId: Long)
    fun notifySheetShared(sheetId: Long, targetUserId: Long, sourceUserId: Long)
}

// Event publisher
@Service
class CellUpdateEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    fun publishCellUpdated(sheetId: Long, cellId: String, userId: Long) {
        applicationEventPublisher.publishEvent(CellUpdatedEvent(sheetId, cellId, userId))
    }
}

// Event listeners
@Component
class EmailNotificationListener {
    @EventListener
    fun handleCellUpdatedEvent(event: CellUpdatedEvent) {
        // Send email notification
    }
}

@Component
class InAppNotificationListener {
    @EventListener
    fun handleCellUpdatedEvent(event: CellUpdatedEvent) {
        // Send in-app notification
    }
}
```

## 4. Database Schema Design

### 4.1 PostgreSQL Schema

```sql
-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Sheets table
CREATE TABLE sheets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    max_length INTEGER NOT NULL DEFAULT 100,
    max_breadth INTEGER NOT NULL DEFAULT 100,
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Access mapping table
CREATE TABLE access_mappings (
    id BIGSERIAL PRIMARY KEY,
    sheet_id BIGINT NOT NULL REFERENCES sheets(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    access_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(sheet_id, user_id)
);

-- Indexes
CREATE INDEX idx_sheets_user_id ON sheets(user_id);
CREATE INDEX idx_access_mappings_sheet_id ON access_mappings(sheet_id);
CREATE INDEX idx_access_mappings_user_id ON access_mappings(user_id);
```

### 4.2 MongoDB Schema

```javascript
// Cell collection
{
  "_id": ObjectId("..."),
  "cellId": "A1",
  "sheetId": 123,
  "dataType": "EXPRESSION",
  "data": "=SUM(B1:B5)",
  "expression": "=SUM(B1:B5)",
  "isInvolvedInExpression": false,
  "createdAt": ISODate("..."),
  "updatedAt": ISODate("...")
}

// Cell dependency collection
{
  "_id": ObjectId("..."),
  "cellId": "A1",
  "sheetId": 123,
  "dependentCellIds": ["B1", "B2", "B3", "B4", "B5"]
}

// Indexes
db.cells.createIndex({ "sheetId": 1 });
db.cells.createIndex({ "cellId": 1, "sheetId": 1 }, { unique: true });
db.cells.createIndex({ "isInvolvedInExpression": 1, "sheetId": 1 });
db.cellDependencies.createIndex({ "cellId": 1, "sheetId": 1 }, { unique: true });
db.cellDependencies.createIndex({ "dependentCellIds": 1, "sheetId": 1 });
```

### 4.3 Redis Data Structure

```
// Cell locks
sheet:{sheetId}:lock:{cellId} -> {userId}

// Cell dependencies (for quick lookup)
sheet:{sheetId}:cell:{cellId}:dependencies -> Set<cellId>

// Cells depending on a cell (for quick lookup)
sheet:{sheetId}:cell:{cellId}:dependents -> Set<cellId>

// Cell values cache
sheet:{sheetId}:cell:{cellId}:value -> {value}
```

## 5. API Implementation Details

### 5.1 Sheet Management API

#### Create Sheet Endpoint

```kotlin
@PostMapping("/")
fun createSheet(
    @RequestBody request: CreateSheetRequest,
    @RequestHeader("X-User-ID") userId: Long
): ResponseEntity<SheetResponse> {
    val sheet = sheetService.createSheet(
        name = request.name,
        description = request.description,
        userId = userId
    )
    return ResponseEntity.status(HttpStatus.CREATED).body(sheetMapper.toSheetResponse(sheet))
}
```

#### Get Sheets Endpoint

```kotlin
@GetMapping("/")
fun getSheets(
    @RequestHeader("X-User-ID") userId: Long
): ResponseEntity<List<SheetSummaryResponse>> {
    val sheets = sheetService.getSheetsByUserId(userId)
    return ResponseEntity.ok(sheets.map { sheetMapper.toSheetSummaryResponse(it) })
}
```

#### Get Sheet Details Endpoint

```kotlin
@GetMapping("/{sheetId}")
fun getSheetDetails(
    @PathVariable sheetId: Long,
    @RequestHeader("X-User-ID") userId: Long
): ResponseEntity<SheetDetailsResponse> {
    val sheet = sheetService.getSheetById(sheetId, userId)
    val cells = cellService.getCellsBySheetId(sheetId)
    return ResponseEntity.ok(sheetMapper.toSheetDetailsResponse(sheet, cells))
}
```

#### Share Sheet Endpoint

```kotlin
@PostMapping("/share/{sheetId}")
fun shareSheet(
    @PathVariable sheetId: Long,
    @RequestBody request: ShareSheetRequest,
    @RequestHeader("X-User-ID") userId: Long
): ResponseEntity<ShareSheetResponse> {
    val shareLink = sheetService.shareSheet(
        sheetId = sheetId,
        userIds = request.userIds,
        accessType = request.accessType,
        ownerId = userId
    )
    return ResponseEntity.ok(ShareSheetResponse(shareLink))
}
```

### 5.2 Cell Management API

#### Update Cell Endpoint

```kotlin
@PostMapping("/{cellId}")
fun updateCell(
    @PathVariable sheetId: Long,
    @PathVariable cellId: String,
    @RequestBody request: UpdateCellRequest,
    @RequestHeader("X-User-ID") userId: Long
): ResponseEntity<CellUpdateResponse> {
    val result = cellService.updateCell(
        sheetId = sheetId,
        cellId = cellId,
        value = request.value,
        userId = userId
    )
    return ResponseEntity.ok(cellMapper.toCellUpdateResponse(result))
}
```
