# Low-Level Design (LLD) Document - Part 3

## 6. Cell Update Algorithm

The cell update process is a critical component that requires careful design for extensibility:

```kotlin
@Service
class CellServiceImpl(
    private val cellRepository: CellRepository,
    private val dependencyRepository: DependencyRepository,
    private val sheetRepository: SheetRepository,
    private val accessRepository: AccessRepository,
    private val lockManager: LockManager,
    private val expressionEvaluator: ExpressionEvaluator
) : CellService {

    fun updateCell(sheetId: Long, cellId: String, value: String, userId: Long): CellUpdateResult {
        // 1. Check access permissions
        checkWriteAccess(sheetId, userId)
        
        // 2. Determine if value is an expression
        val isExpression = expressionEvaluator.isExpression(value)
        val dataType = if (isExpression) DataType.EXPRESSION else DataType.PRIMITIVE
        
        // 3. If expression, extract dependencies
        val dependencies = if (isExpression) {
            expressionEvaluator.extractDependencies(value)
        } else {
            emptySet()
        }
        
        // 4. Check for circular dependencies
        if (isExpression) {
            checkCircularDependencies(sheetId, cellId, dependencies)
        }
        
        // 5. Find cells that depend on this cell
        val dependentCells = findDependentCells(sheetId, cellId)
        
        // 6. Acquire locks for this cell and all dependent cells
        val cellsToLock = dependentCells.toMutableSet()
        cellsToLock.add(cellId)
        
        try {
            // 7. Acquire locks
            if (!lockManager.acquireLock(sheetId, cellsToLock)) {
                throw ConcurrentModificationException("Could not acquire lock for cells")
            }
            
            // 8. Update the cell
            val existingCell = cellRepository.findByCellIdAndSheetId(cellId, sheetId)
            val cell = existingCell?.copy(
                dataType = dataType,
                data = value,
                expression = if (isExpression) value else null,
                isInvolvedInExpression = false,
                updatedAt = Instant.now()
            ) ?: Cell(
                cellId = cellId,
                sheetId = sheetId,
                dataType = dataType,
                data = value,
                expression = if (isExpression) value else null
            )
            
            val savedCell = cellRepository.save(cell)
            
            // 9. Update dependencies
            updateDependencies(sheetId, cellId, dependencies)
            
            // 10. Mark cells as involved in expressions
            if (dependencies.isNotEmpty()) {
                markCellsAsInvolvedInExpressions(sheetId, dependencies)
            }
            
            // 11. Evaluate the expression
            val evaluatedValue = if (isExpression) {
                val context = EvaluationContext(sheetId, cellId, cellRepository)
                expressionEvaluator.evaluate(value, context).value
            } else {
                value
            }
            
            // 12. Update dependent cells recursively
            updateDependentCells(sheetId, dependentCells)
            
            // 13. Return the result
            return CellUpdateResult(
                cell = savedCell,
                evaluatedValue = evaluatedValue
            )
        } finally {
            // 14. Release locks
            lockManager.releaseLock(sheetId, cellsToLock)
        }
    }
    
    private fun checkWriteAccess(sheetId: Long, userId: Long) {
        val sheet = sheetRepository.findById(sheetId)
            ?: throw NotFoundException("Sheet not found")
            
        if (sheet.userId == userId) {
            return // Owner has write access
        }
        
        val access = accessRepository.findBySheetIdAndUserId(sheetId, userId)
            ?: throw AccessDeniedException("User does not have access to this sheet")
            
        if (access.accessType != AccessType.WRITE && access.accessType != AccessType.ADMIN) {
            throw AccessDeniedException("User does not have write access to this sheet")
        }
    }
    
    private fun checkCircularDependencies(sheetId: Long, cellId: String, dependencies: Set<String>) {
        if (dependencies.contains(cellId)) {
            throw CircularDependencyException("Cell cannot reference itself")
        }
        
        val visited = mutableSetOf<String>()
        val stack = mutableSetOf<String>()
        
        fun dfs(currentCellId: String) {
            if (currentCellId in stack) {
                throw CircularDependencyException("Circular dependency detected")
            }
            
            if (currentCellId in visited) {
                return
            }
            
            visited.add(currentCellId)
            stack.add(currentCellId)
            
            val dependency = dependencyRepository.findByCellIdAndSheetId(currentCellId, sheetId)
            dependency?.dependentCellIds?.forEach { dfs(it) }
            
            stack.remove(currentCellId)
        }
        
        dependencies.forEach { dfs(it) }
    }
    
    private fun findDependentCells(sheetId: Long, cellId: String): Set<String> {
        val dependencies = dependencyRepository.findByDependentCellIdsContainingAndSheetId(cellId, sheetId)
        return dependencies.map { it.cellId }.toSet()
    }
    
    private fun updateDependencies(sheetId: Long, cellId: String, dependencies: Set<String>) {
        val existingDependency = dependencyRepository.findByCellIdAndSheetId(cellId, sheetId)
        
        if (dependencies.isEmpty() && existingDependency != null) {
            // No dependencies, remove existing dependency
            dependencyRepository.delete(existingDependency)
            return
        }
        
        if (dependencies.isNotEmpty()) {
            val dependency = existingDependency?.copy(
                dependentCellIds = dependencies
            ) ?: CellDependency(
                cellId = cellId,
                sheetId = sheetId,
                dependentCellIds = dependencies
            )
            
            dependencyRepository.save(dependency)
        }
    }
    
    private fun markCellsAsInvolvedInExpressions(sheetId: Long, cellIds: Set<String>) {
        cellIds.forEach { cellId ->
            val cell = cellRepository.findByCellIdAndSheetId(cellId, sheetId)
            if (cell != null && !cell.isInvolvedInExpression) {
                cellRepository.save(cell.copy(isInvolvedInExpression = true))
            }
        }
    }
    
    private fun updateDependentCells(sheetId: Long, dependentCellIds: Set<String>) {
        dependentCellIds.forEach { cellId ->
            val cell = cellRepository.findByCellIdAndSheetId(cellId, sheetId)
            if (cell != null && cell.dataType == DataType.EXPRESSION) {
                val context = EvaluationContext(sheetId, cellId, cellRepository)
                expressionEvaluator.evaluate(cell.data, context)
                
                // Recursively update cells that depend on this cell
                val nextDependentCells = findDependentCells(sheetId, cellId)
                if (nextDependentCells.isNotEmpty()) {
                    updateDependentCells(sheetId, nextDependentCells)
                }
            }
        }
    }
}
```

## 7. Expression Evaluation Implementation

The expression evaluator is a key component for extensibility:

```kotlin
@Service
class SimpleExpressionEvaluator(
    private val functionRegistry: FunctionRegistry
) : ExpressionEvaluator {

    override fun isExpression(value: String): Boolean {
        return value.startsWith("=")
    }
    
    override fun extractDependencies(expression: String): Set<String> {
        // Simple implementation to extract cell references
        val cellPattern = "[A-Z]+[0-9]+"
        val regex = Regex(cellPattern)
        return regex.findAll(expression.substring(1))
            .map { it.value }
            .toSet()
    }
    
    override fun evaluate(expression: String, context: EvaluationContext): EvaluationResult {
        if (!isExpression(expression)) {
            return EvaluationResult(expression, emptySet())
        }
        
        try {
            val expr = expression.substring(1) // Remove the '=' prefix
            
            // Check for function calls
            val functionCallPattern = "([A-Z]+)\\(([^)]*)\\)"
            val functionRegex = Regex(functionCallPattern)
            val functionMatch = functionRegex.find(expr)
            
            if (functionMatch != null) {
                val (functionName, argsStr) = functionMatch.destructured
                val function = functionRegistry.get(functionName.uppercase())
                    ?: throw IllegalArgumentException("Unknown function: $functionName")
                
                val args = argsStr.split(",").map { it.trim() }
                val evaluatedArgs = args.map { evaluateArg(it, context) }
                
                val result = function.execute(evaluatedArgs)
                return EvaluationResult(result, extractDependencies(expression))
            }
            
            // Simple arithmetic expression
            return evaluateArithmetic(expr, context)
        } catch (e: Exception) {
            return EvaluationResult(
                value = "#ERROR",
                dependencies = extractDependencies(expression),
                error = e.message
            )
        }
    }
    
    private fun evaluateArg(arg: String, context: EvaluationContext): Any {
        // Check if arg is a cell reference
        val cellPattern = "[A-Z]+[0-9]+"
        if (arg.matches(Regex(cellPattern))) {
            val cell = context.cellRepository.findByCellIdAndSheetId(arg, context.sheetId)
                ?: return "#REF!"
            
            return if (cell.dataType == DataType.EXPRESSION) {
                evaluate(cell.data, context).value
            } else {
                cell.data
            }
        }
        
        // Check if arg is a number
        return arg.toDoubleOrNull() ?: arg
    }
    
    private fun evaluateArithmetic(expr: String, context: EvaluationContext): EvaluationResult {
        // Simple implementation for basic arithmetic
        // In a real implementation, use a proper expression parser/evaluator
        
        // For demonstration purposes only
        val result = 0 // Placeholder for actual evaluation
        
        return EvaluationResult(
            value = result,
            dependencies = extractDependencies("=$expr")
        )
    }
}
```

## 8. Locking Mechanism Implementation

The Redis-based locking mechanism is implemented as follows:

```kotlin
@Service
class RedisLockManager(
    private val redisTemplate: RedisTemplate<String, String>
) : LockManager {

    private val lockTimeout = 30L // 30 seconds
    
    override fun acquireLock(sheetId: Long, cellIds: Set<String>): Boolean {
        val operations = redisTemplate.opsForValue()
        val currentTime = System.currentTimeMillis().toString()
        
        // Try to acquire all locks
        val acquiredLocks = mutableListOf<String>()
        
        try {
            for (cellId in cellIds) {
                val lockKey = getLockKey(sheetId, cellId)
                val acquired = operations.setIfAbsent(lockKey, currentTime, Duration.ofSeconds(lockTimeout))
                
                if (acquired == null || !acquired) {
                    // Could not acquire lock, release all acquired locks
                    releaseLock(sheetId, acquiredLocks.toSet())
                    return false
                }
                
                acquiredLocks.add(cellId)
            }
            
            return true
        } catch (e: Exception) {
            // Release any acquired locks
            releaseLock(sheetId, acquiredLocks.toSet())
            throw e
        }
    }
    
    override fun releaseLock(sheetId: Long, cellIds: Set<String>) {
        for (cellId in cellIds) {
            val lockKey = getLockKey(sheetId, cellId)
            redisTemplate.delete(lockKey)
        }
    }
    
    override fun isLocked(sheetId: Long, cellId: String): Boolean {
        val lockKey = getLockKey(sheetId, cellId)
        return redisTemplate.hasKey(lockKey)
    }
    
    private fun getLockKey(sheetId: Long, cellId: String): String {
        return "sheet:$sheetId:lock:$cellId"
    }
}
```

## 9. OpenAPI Specification

The OpenAPI specification for the Sheet API:

```yaml
openapi: 3.0.3
info:
  title: Sheets API
  description: API for online spreadsheet management
  version: 1.0.0
servers:
  - url: /api/v1
    description: Development server
paths:
  /sheet:
    post:
      summary: Create a new sheet
      operationId: createSheet
      tags:
        - Sheet
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
    get:
      summary: Get all sheets for a user
      operationId: getSheets
      tags:
        - Sheet
      responses:
        '200':
          description: List of sheets
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/SheetSummaryResponse'
  /sheet/{sheetId}:
    get:
      summary: Get sheet details
      operationId: getSheetDetails
      tags:
        - Sheet
      parameters:
        - name: sheetId
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: Sheet details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SheetDetailsResponse'
  /sheet/share/{sheetId}:
    post:
      summary: Share a sheet with other users
      operationId: shareSheet
      tags:
        - Sheet
      parameters:
        - name: sheetId
          in: path
          required: true
          schema:
            type: integer
            format: int64
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
  /sheet/{sheetId}/cell/{cellId}:
    post:
      summary: Update cell data
      operationId: updateCell
      tags:
        - Cell
      parameters:
        - name: sheetId
          in: path
          required: true
          schema:
            type: integer
            format: int64
        - name: cellId
          in: path
          required: true
          schema:
            type: string
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateCellRequest'
      responses:
        '200':
          description: Cell updated successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CellUpdateResponse'
components:
  schemas:
    CreateSheetRequest:
      type: object
      required:
        - name
      properties:
        name:
          type: string
          example: "My Sheet"
        description:
          type: string
          example: "A sample sheet"
    SheetResponse:
      type: object
      properties:
        id:
          type: integer
          format: int64
        name:
          type: string
        description:
          type: string
        createdAt:
          type: string
          format: date-time
    SheetSummaryResponse:
      type: object
      properties:
        id:
          type: integer
          format: int64
        name:
          type: string
        description:
          type: string
        createdAt:
          type: string
          format: date-time
    SheetDetailsResponse:
      type: object
      properties:
        id:
          type: integer
          format: int64
        name:
          type: string
        description:
          type: string
        cells:
          type: array
          items:
            $ref: '#/components/schemas/CellResponse'
    CellResponse:
      type: object
      properties:
        cellId:
          type: string
        value:
          type: string
        evaluatedValue:
          type: string
        dataType:
          type: string
          enum: [PRIMITIVE, EXPRESSION]
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
        accessType:
          type: string
          enum: [READ, WRITE, ADMIN]
    ShareSheetResponse:
      type: object
      properties:
        shareLink:
          type: string
    UpdateCellRequest:
      type: object
      required:
        - value
      properties:
        value:
          type: string
    CellUpdateResponse:
      type: object
      properties:
        cellId:
          type: string
        value:
          type: string
        evaluatedValue:
          type: string
```

## 10. Testing Strategy

### 10.1 Unit Testing

```kotlin
@ExtendWith(MockKExtension::class)
class CellServiceTest {

    @MockK
    private lateinit var cellRepository: CellRepository
    
    @MockK
    private lateinit var dependencyRepository: DependencyRepository
    
    @MockK
    private lateinit var sheetRepository: SheetRepository
    
    @MockK
    private lateinit var accessRepository: AccessRepository
    
    @MockK
    private lateinit var lockManager: LockManager
    
    @MockK
    private lateinit var expressionEvaluator: ExpressionEvaluator
    
    private lateinit var cellService: CellServiceImpl
    
    @BeforeEach
    fun setup() {
        cellService = CellServiceImpl(
            cellRepository,
            dependencyRepository,
            sheetRepository,
            accessRepository,
            lockManager,
            expressionEvaluator
        )
    }
    
    @Test
    fun `updateCell should update primitive cell successfully`() {
        // Given
        val sheetId = 1L
        val cellId = "A1"
        val value = "42"
        val userId = 1L
        
        val sheet = Sheet(id = sheetId, name = "Test Sheet", description = "Test", userId = userId, maxLength = 10, maxBreadth = 10)
        val cell = Cell(cellId = cellId, sheetId = sheetId, dataType = DataType.PRIMITIVE, data = value)
        
        every { sheetRepository.findById(sheetId) } returns sheet
        every { expressionEvaluator.isExpression(value) } returns false
        every { cellRepository.findByCellIdAndSheetId(cellId, sheetId) } returns null
        every { lockManager.acquireLock(sheetId, setOf(cellId)) } returns true
        every { lockManager.releaseLock(sheetId, setOf(cellId)) } just runs
        every { cellRepository.save(any()) } returns cell
        every { dependencyRepository.findByDependentCellIdsContainingAndSheetId(cellId, sheetId) } returns emptyList()
        
        // When
        val result = cellService.updateCell(sheetId, cellId, value, userId)
        
        // Then
        assertEquals(cell, result.cell)
        assertEquals(value, result.evaluatedValue)
        
        verify { sheetRepository.findById(sheetId) }
        verify { expressionEvaluator.isExpression(value) }
        verify { cellRepository.findByCellIdAndSheetId(cellId, sheetId) }
        verify { lockManager.acquireLock(sheetId, setOf(cellId)) }
        verify { cellRepository.save(any()) }
        verify { lockManager.releaseLock(sheetId, setOf(cellId)) }
    }
    
    @Test
    fun `updateCell should update expression cell successfully`() {
        // Given
        val sheetId = 1L
        val cellId = "A1"
        val value = "=B1+C1"
        val userId = 1L
        
        val sheet = Sheet(id = sheetId, name = "Test Sheet", description = "Test", userId = userId, maxLength = 10, maxBreadth = 10)
        val cell = Cell(cellId = cellId, sheetId = sheetId, dataType = DataType.EXPRESSION, data = value, expression = value)
        val dependencies = setOf("B1", "C1")
        val evaluationResult = EvaluationResult(42, dependencies)
        
        every { sheetRepository.findById(sheetId) } returns sheet
        every { expressionEvaluator.isExpression(value) } returns true
        every { expressionEvaluator.extractDependencies(value) } returns dependencies
        every { cellRepository.findByCellIdAndSheetId(cellId, sheetId) } returns null
        every { lockManager.acquireLock(sheetId, setOf(cellId)) } returns true
        every { lockManager.releaseLock(sheetId, setOf(cellId)) } just runs
        every { cellRepository.save(any()) } returns cell
        every { dependencyRepository.findByDependentCellIdsContainingAndSheetId(cellId, sheetId) } returns emptyList()
        every { dependencyRepository.findByCellIdAndSheetId(cellId, sheetId) } returns null
        every { dependencyRepository.save(any()) } returns CellDependency(cellId = cellId, sheetId = sheetId, dependentCellIds = dependencies)
        every { expressionEvaluator.evaluate(value, any()) } returns evaluationResult
        
        // When
        val result = cellService.updateCell(sheetId, cellId, value, userId)
        
        // Then
        assertEquals(cell, result.cell)
        assertEquals(42, result.evaluatedValue)
        
        verify { sheetRepository.findById(sheetId) }
        verify { expressionEvaluator.isExpression(value) }
        verify { expressionEvaluator.extractDependencies(value) }
        verify { cellRepository.findByCellIdAndSheetId(cellId, sheetId) }
        verify { lockManager.acquireLock(sheetId, setOf(cellId)) }
        verify { cellRepository.save(any()) }
        verify { dependencyRepository.save(any()) }
        verify { expressionEvaluator.evaluate(value, any()) }
        verify { lockManager.releaseLock(sheetId, setOf(cellId)) }
    }
}
```

### 10.2 Integration Testing

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class CellControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc
    
    @MockBean
    private lateinit var cellService: CellService
    
    @Test
    fun `updateCell should return 200 OK`() {
        // Given
        val sheetId = 1L
        val cellId = "A1"
        val userId = 1L
        val value = "42"
        
        val cell = Cell(cellId = cellId, sheetId = sheetId, dataType = DataType.PRIMITIVE, data = value)
        val result = CellUpdateResult(cell = cell, evaluatedValue = value)
        
        given(cellService.updateCell(sheetId, cellId, value, userId)).willReturn(result)
        
        // When/Then
        mockMvc.perform(
            post("/sheet/$sheetId/cell/$cellId")
                .header("X-User-ID", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"$value\"}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cellId").value(cellId))
            .andExpect(jsonPath("$.value").value(value))
            .andExpect(jsonPath("$.evaluatedValue").value(value))
    }
}
```

## 11. Extensibility Considerations

### 11.1 Adding New Cell Types

To add a new cell type (e.g., Chart, Image):

1. Add a new value to the `DataType` enum:
```kotlin
enum class DataType {
    PRIMITIVE,
    EXPRESSION,
    CHART,
    IMAGE
}
```

2. Create a new implementation of the `CellType` interface:
```kotlin
@Service
class ChartCellType : CellType {
    override fun getValue(cell: Cell, context: EvaluationContext): Any {
        // Implementation for chart rendering
    }
    
    override fun validate(value: String): Boolean {
        // Validate chart configuration
    }
}
```

3. Register the new cell type in the `CellTypeRegistry`:
```kotlin
@PostConstruct
fun init() {
    register(DataType.CHART, chartCellType)
}
```

### 11.2 Adding New Expression Functions

To add a new function to the expression evaluator:

1. Create a new implementation of the `Function` interface:
```kotlin
class AverageFunction : FunctionRegistry.Function {
    override fun execute(args: List<Any>): Any {
        if (args.isEmpty()) {
            return 0.0
        }
        
        val numbers = args.mapNotNull { it.toString().toDoubleOrNull() }
        if (numbers.isEmpty()) {
            return 0.0
        }
        
        return numbers.sum() / numbers.size
    }
}
```

2. Register the function in the `FunctionRegistry`:
```kotlin
@PostConstruct
fun init() {
    register("SUM", SumFunction())
    register("AVERAGE", AverageFunction())
    register("COUNT", CountFunction())
}
```

### 11.3 Adding New Storage Backends

To add a new storage backend for cells:

1. Create a new implementation of the `CellRepository` interface:
```kotlin
@Repository
@Qualifier("cassandra")
class CassandraCellRepository(
    private val cassandraTemplate: CassandraTemplate
) : CellRepository {
    // Implementation for Cassandra
}
```

2. Configure the new repository in the application:
```kotlin
@Configuration
@Profile("cassandra")
class CassandraConfig {
    @Bean
    @Primary
    fun cellRepository(cassandraTemplate: CassandraTemplate): CellRepository {
        return CassandraCellRepository(cassandraTemplate)
    }
}
```

### 11.4 Adding Notification System

To add a notification system:

1. Create event classes:
```kotlin
data class CellUpdatedEvent(
    val sheetId: Long,
    val cellId: String,
    val userId: Long
) : ApplicationEvent(Source.CELL_UPDATE)

enum class Source {
    CELL_UPDATE,
    SHEET_SHARE
}
```

2. Create event listeners:
```kotlin
@Component
class WebSocketNotificationListener {
    @EventListener
    fun handleCellUpdatedEvent(event: CellUpdatedEvent) {
        // Send WebSocket notification to connected clients
    }
}
```

3. Publish events from the service layer:
```kotlin
@Service
class CellServiceImpl(
    // Other dependencies
    private val applicationEventPublisher: ApplicationEventPublisher
) : CellService {
    
    fun updateCell(sheetId: Long, cellId: String, value: String, userId: Long): CellUpdateResult {
        // Cell update logic
        
        // Publish event
        applicationEventPublisher.publishEvent(CellUpdatedEvent(sheetId, cellId, userId))
        
        return result
    }
}
```

## 12. Conclusion

This Low-Level Design document provides a detailed implementation plan for the Sheets project with a strong focus on extensibility. The design follows the OpenAPI-first approach, clear separation of concerns, and leverages design patterns like Strategy, Factory, and Observer to enable easy extension of the system.

Key extensibility points include:
- Expression evaluation with pluggable functions
- Cell types with a registry for new types
- Storage backends with interchangeable implementations
- Notification system with event-based architecture

The implementation also addresses the non-functional requirements:
- Performance optimization through caching and efficient algorithms
- Scalability through horizontal scaling and database sharding
- Concurrent updates through distributed locking
- High availability through redundancy and fault tolerance

By following this design, the Sheets project can be incrementally developed and extended to support new features and requirements in the future.
