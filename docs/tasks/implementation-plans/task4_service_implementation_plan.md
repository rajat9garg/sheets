# Service Layer Implementation Plan for Cell Management

## Overview

This document outlines the detailed implementation plan for the service layer of the Cell Management functionality in Task4. The service layer will handle the business logic for cell operations, including expression evaluation, dependency management, and concurrency control.

## Table of Contents

1. [Requirements](#1-requirements)
2. [Design Approach](#2-design-approach)
3. [Service Interfaces](#3-service-interfaces)
4. [Service Implementations](#4-service-implementations)
5. [Dependency Management](#5-dependency-management)
6. [Concurrency Control](#6-concurrency-control)
7. [Error Handling](#7-error-handling)
8. [Testing Strategy](#8-testing-strategy)

## 1. Requirements

The service layer for Cell Management must:

1. Support CRUD operations for cells
2. Parse and evaluate cell expressions
3. Manage cell dependencies
4. Handle concurrent updates to cells
5. Detect and prevent circular dependencies
6. Propagate changes to dependent cells
7. Provide clear error messages for failures

## 2. Design Approach

We'll use the following design patterns for this implementation:

1. **Strategy Pattern**: For different cell evaluation strategies
2. **Observer Pattern**: For propagating changes to dependent cells
3. **Chain of Responsibility**: For handling different types of expressions
4. **Factory Pattern**: For creating cell objects
5. **Repository Pattern**: For data access
6. **Mediator Pattern**: For coordinating cell updates and dependency management

## 3. Service Interfaces

### 3.1 CellService Interface

```kotlin
package com.sheets.services

import com.sheets.models.domain.Cell
import java.util.concurrent.CompletableFuture

/**
 * Service interface for cell operations
 */
interface CellService {
    /**
     * Create or update a cell
     * @param userId The ID of the user making the request
     * @param sheetId The ID of the sheet
     * @param row The row index of the cell
     * @param column The column index of the cell
     * @param data The cell data
     * @param dataType The type of data in the cell
     * @return The created or updated cell
     */
    fun createOrUpdateCell(
        userId: String,
        sheetId: Long,
        row: Int,
        column: Int,
        data: String,
        dataType: String
    ): Cell
    
    /**
     * Get a cell by ID
     * @param userId The ID of the user making the request
     * @param cellId The ID of the cell
     * @return The cell, or null if not found
     */
    fun getCell(userId: String, cellId: String): Cell?
    
    /**
     * Get all cells in a sheet
     * @param userId The ID of the user making the request
     * @param sheetId The ID of the sheet
     * @return A list of cells in the sheet
     */
    fun getCellsBySheetId(userId: String, sheetId: Long): List<Cell>
    
    /**
     * Delete a cell
     * @param userId The ID of the user making the request
     * @param cellId The ID of the cell
     * @return True if the cell was deleted, false otherwise
     */
    fun deleteCell(userId: String, cellId: String): Boolean
    
    /**
     * Asynchronously update dependent cells
     * @param sheetId The ID of the sheet
     * @param cellId The ID of the cell that was updated
     * @return A CompletableFuture that completes when all dependent cells have been updated
     */
    fun updateDependentCells(sheetId: Long, cellId: String): CompletableFuture<Void>
}
```

### 3.2 CellDependencyService Interface

```kotlin
package com.sheets.services

import com.sheets.models.domain.CellDependency

/**
 * Service interface for cell dependency operations
 */
interface CellDependencyService {
    /**
     * Get dependencies for a cell
     * @param sheetId The ID of the sheet
     * @param cellId The ID of the cell
     * @return A list of cell dependencies
     */
    fun getDependenciesForCell(sheetId: Long, cellId: String): List<CellDependency>
    
    /**
     * Get dependent cells for a cell
     * @param sheetId The ID of the sheet
     * @param cellId The ID of the cell
     * @return A list of cell dependencies
     */
    fun getDependentCells(sheetId: Long, cellId: String): List<CellDependency>
    
    /**
     * Create a dependency between two cells
     * @param sheetId The ID of the sheet
     * @param sourceCellId The ID of the source cell
     * @param targetCellId The ID of the target cell
     * @return The created cell dependency
     */
    fun createDependency(sheetId: Long, sourceCellId: String, targetCellId: String): CellDependency
    
    /**
     * Delete dependencies for a cell
     * @param sheetId The ID of the sheet
     * @param cellId The ID of the cell
     * @return The number of dependencies deleted
     */
    fun deleteDependenciesForCell(sheetId: Long, cellId: String): Int
    
    /**
     * Check if adding a dependency would create a circular dependency
     * @param sheetId The ID of the sheet
     * @param sourceCellId The ID of the source cell
     * @param targetCellId The ID of the target cell
     * @return True if a circular dependency would be created, false otherwise
     */
    fun wouldCreateCircularDependency(
        sheetId: Long,
        sourceCellId: String,
        targetCellId: String
    ): Boolean
}
```

### 3.3 CellLockService Interface

```kotlin
package com.sheets.services

/**
 * Service interface for cell locking operations
 */
interface CellLockService {
    /**
     * Acquire a lock on a cell
     * @param sheetId The ID of the sheet
     * @param cellId The ID of the cell
     * @param userId The ID of the user acquiring the lock
     * @param timeoutMs The timeout in milliseconds
     * @return True if the lock was acquired, false otherwise
     */
    fun acquireLock(sheetId: Long, cellId: String, userId: String, timeoutMs: Long = 5000): Boolean
    
    /**
     * Release a lock on a cell
     * @param sheetId The ID of the sheet
     * @param cellId The ID of the cell
     * @param userId The ID of the user releasing the lock
     * @return True if the lock was released, false otherwise
     */
    fun releaseLock(sheetId: Long, cellId: String, userId: String): Boolean
    
    /**
     * Check if a cell is locked
     * @param sheetId The ID of the sheet
     * @param cellId The ID of the cell
     * @return True if the cell is locked, false otherwise
     */
    fun isLocked(sheetId: Long, cellId: String): Boolean
    
    /**
     * Get the user ID of the user who has a lock on a cell
     * @param sheetId The ID of the sheet
     * @param cellId The ID of the cell
     * @return The user ID, or null if the cell is not locked
     */
    fun getLockOwner(sheetId: Long, cellId: String): String?
}
```

## 4. Service Implementations

### 4.1 CellServiceImpl

```kotlin
package com.sheets.services.impl

import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import com.sheets.repositories.CellRepository
import com.sheets.services.CellDependencyService
import com.sheets.services.CellLockService
import com.sheets.services.CellService
import com.sheets.services.SheetService
import com.sheets.services.expression.ExpressionEvaluator
import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.exception.CircularDependencyException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Implementation of the CellService interface
 */
@Service
class CellServiceImpl(
    private val cellRepository: CellRepository,
    private val sheetService: SheetService,
    private val cellDependencyService: CellDependencyService,
    private val cellLockService: CellLockService,
    private val expressionParser: ExpressionParser,
    private val expressionEvaluator: ExpressionEvaluator
) : CellService {
    
    // Executor for asynchronous operations
    private val executor = Executors.newFixedThreadPool(10)
    
    override fun createOrUpdateCell(
        userId: String,
        sheetId: Long,
        row: Int,
        column: Int,
        data: String,
        dataType: String
    ): Cell {
        // Check if the sheet exists and the user has access
        val sheet = sheetService.getSheet(userId, sheetId)
            ?: throw IllegalArgumentException("Sheet not found or access denied")
        
        // Create the cell ID
        val cellId = "$sheetId:$row:$column"
        
        // Try to acquire a lock on the cell
        if (!cellLockService.acquireLock(sheetId, cellId, userId)) {
            throw IllegalStateException("Cell is locked by another user")
        }
        
        try {
            // Determine the data type
            val cellDataType = DataType.valueOf(dataType)
            
            // Parse the expression if the data type is EXPRESSION
            val cellReferences = if (cellDataType == DataType.EXPRESSION) {
                expressionParser.parse(data)
            } else {
                emptyList()
            }
            
            // Check for circular dependencies
            for (ref in cellReferences) {
                val refCellId = getCellIdFromReference(sheetId, ref)
                if (cellDependencyService.wouldCreateCircularDependency(sheetId, cellId, refCellId)) {
                    throw CircularDependencyException(
                        listOf(cellId, refCellId)
                    )
                }
            }
            
            // Evaluate the expression
            val evaluatedValue = if (cellDataType == DataType.EXPRESSION) {
                // Get the values of the referenced cells
                val context = cellReferences.associateWith { ref ->
                    val refCellId = getCellIdFromReference(sheetId, ref)
                    val refCell = cellRepository.findById(refCellId)
                    refCell?.evaluatedValue ?: "0"
                }
                
                // Evaluate the expression
                expressionEvaluator.evaluate(data, context)
            } else {
                data
            }
            
            // Get the existing cell or create a new one
            val existingCell = cellRepository.findById(cellId)
            val now = Instant.now()
            
            val cell = if (existingCell != null) {
                existingCell.copy(
                    data = data,
                    dataType = cellDataType,
                    evaluatedValue = evaluatedValue,
                    updatedAt = now
                )
            } else {
                Cell(
                    id = cellId,
                    sheetId = sheetId,
                    row = row,
                    column = column,
                    data = data,
                    dataType = cellDataType,
                    evaluatedValue = evaluatedValue,
                    createdAt = now,
                    updatedAt = now
                )
            }
            
            // Save the cell
            val savedCell = cellRepository.save(cell)
            
            // Delete existing dependencies
            cellDependencyService.deleteDependenciesForCell(sheetId, cellId)
            
            // Create new dependencies
            for (ref in cellReferences) {
                val refCellId = getCellIdFromReference(sheetId, ref)
                cellDependencyService.createDependency(sheetId, cellId, refCellId)
            }
            
            // Update dependent cells asynchronously
            updateDependentCells(sheetId, cellId)
            
            return savedCell
        } finally {
            // Release the lock
            cellLockService.releaseLock(sheetId, cellId, userId)
        }
    }
    
    override fun getCell(userId: String, cellId: String): Cell? {
        // Extract sheet ID from cell ID
        val sheetId = cellId.split(":")[0].toLong()
        
        // Check if the sheet exists and the user has access
        val sheet = sheetService.getSheet(userId, sheetId)
            ?: return null
        
        // Get the cell
        return cellRepository.findById(cellId)
    }
    
    override fun getCellsBySheetId(userId: String, sheetId: Long): List<Cell> {
        // Check if the sheet exists and the user has access
        val sheet = sheetService.getSheet(userId, sheetId)
            ?: return emptyList()
        
        // Get all cells in the sheet
        return cellRepository.findBySheetId(sheetId)
    }
    
    override fun deleteCell(userId: String, cellId: String): Boolean {
        // Extract sheet ID from cell ID
        val sheetId = cellId.split(":")[0].toLong()
        
        // Check if the sheet exists and the user has access
        val sheet = sheetService.getSheet(userId, sheetId)
            ?: return false
        
        // Try to acquire a lock on the cell
        if (!cellLockService.acquireLock(sheetId, cellId, userId)) {
            throw IllegalStateException("Cell is locked by another user")
        }
        
        try {
            // Get the cell
            val cell = cellRepository.findById(cellId) ?: return false
            
            // Delete the cell
            cellRepository.delete(cell)
            
            // Delete dependencies
            cellDependencyService.deleteDependenciesForCell(sheetId, cellId)
            
            // Update dependent cells asynchronously
            updateDependentCells(sheetId, cellId)
            
            return true
        } finally {
            // Release the lock
            cellLockService.releaseLock(sheetId, cellId, userId)
        }
    }
    
    override fun updateDependentCells(sheetId: Long, cellId: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            // Get dependent cells
            val dependencies = cellDependencyService.getDependentCells(sheetId, cellId)
            
            // Update each dependent cell
            for (dependency in dependencies) {
                val dependentCellId = dependency.sourceCellId
                val dependentCell = cellRepository.findById(dependentCellId) ?: continue
                
                // Skip non-expression cells
                if (dependentCell.dataType != DataType.EXPRESSION) {
                    continue
                }
                
                // Parse the expression
                val cellReferences = expressionParser.parse(dependentCell.data)
                
                // Get the values of the referenced cells
                val context = cellReferences.associateWith { ref ->
                    val refCellId = getCellIdFromReference(sheetId, ref)
                    val refCell = cellRepository.findById(refCellId)
                    refCell?.evaluatedValue ?: "0"
                }
                
                // Evaluate the expression
                val evaluatedValue = expressionEvaluator.evaluate(dependentCell.data, context)
                
                // Update the cell
                val updatedCell = dependentCell.copy(
                    evaluatedValue = evaluatedValue,
                    updatedAt = Instant.now()
                )
                
                // Save the cell
                cellRepository.save(updatedCell)
                
                // Recursively update dependent cells
                updateDependentCells(sheetId, dependentCellId)
            }
        }, executor)
    }
    
    /**
     * Convert a cell reference (e.g., A1) to a cell ID
     * @param sheetId The ID of the sheet
     * @param cellReference The cell reference
     * @return The cell ID
     */
    private fun getCellIdFromReference(sheetId: Long, cellReference: String): String {
        // Extract row and column from the cell reference
        val column = cellReference.replace("\\d".toRegex(), "")
        val row = cellReference.replace("\\D".toRegex(), "").toInt()
        
        // Convert column to index
        val columnIndex = columnToIndex(column)
        
        // Create the cell ID
        return "$sheetId:$row:$columnIndex"
    }
    
    /**
     * Convert a column letter to a 0-based index
     * @param column The column letter (e.g., A, B, C, etc.)
     * @return The 0-based index of the column
     */
    private fun columnToIndex(column: String): Int {
        var result = 0
        for (c in column) {
            result = result * 26 + (c - 'A' + 1)
        }
        return result - 1
    }
}
```

### 4.2 CellDependencyServiceImpl

```kotlin
package com.sheets.services.impl

import com.sheets.models.domain.CellDependency
import com.sheets.repositories.CellDependencyRepository
import com.sheets.services.CellDependencyService
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Implementation of the CellDependencyService interface
 */
@Service
class CellDependencyServiceImpl(
    private val cellDependencyRepository: CellDependencyRepository
) : CellDependencyService {
    
    override fun getDependenciesForCell(sheetId: Long, cellId: String): List<CellDependency> {
        return cellDependencyRepository.findBySourceCellId(cellId)
    }
    
    override fun getDependentCells(sheetId: Long, cellId: String): List<CellDependency> {
        return cellDependencyRepository.findByTargetCellId(cellId)
    }
    
    override fun createDependency(sheetId: Long, sourceCellId: String, targetCellId: String): CellDependency {
        // Check if the dependency already exists
        val existingDependency = cellDependencyRepository.findBySourceCellIdAndTargetCellId(
            sourceCellId, targetCellId
        )
        
        if (existingDependency != null) {
            return existingDependency
        }
        
        // Create a new dependency
        val now = Instant.now()
        val dependency = CellDependency(
            sheetId = sheetId,
            sourceCellId = sourceCellId,
            targetCellId = targetCellId,
            createdAt = now,
            updatedAt = now
        )
        
        // Save the dependency
        return cellDependencyRepository.save(dependency)
    }
    
    override fun deleteDependenciesForCell(sheetId: Long, cellId: String): Int {
        return cellDependencyRepository.deleteBySourceCellId(cellId)
    }
    
    override fun wouldCreateCircularDependency(
        sheetId: Long,
        sourceCellId: String,
        targetCellId: String
    ): Boolean {
        // If the source and target are the same, it's a circular dependency
        if (sourceCellId == targetCellId) {
            return true
        }
        
        // Check if the target depends on the source
        return hasPath(targetCellId, sourceCellId, mutableSetOf())
    }
    
    /**
     * Check if there is a path from source to target
     * @param source The source cell ID
     * @param target The target cell ID
     * @param visited The set of visited cell IDs
     * @return True if there is a path, false otherwise
     */
    private fun hasPath(source: String, target: String, visited: MutableSet<String>): Boolean {
        // If we've already visited this cell, stop to avoid infinite recursion
        if (source in visited) {
            return false
        }
        
        // Add the source to the visited set
        visited.add(source)
        
        // Get the dependencies of the source
        val dependencies = cellDependencyRepository.findBySourceCellId(source)
        
        // Check if any of the dependencies is the target
        for (dependency in dependencies) {
            if (dependency.targetCellId == target) {
                return true
            }
            
            // Recursively check the dependencies of the dependency
            if (hasPath(dependency.targetCellId, target, visited)) {
                return true
            }
        }
        
        return false
    }
}
```

### 4.3 CellLockServiceImpl

```kotlin
package com.sheets.services.impl

import com.sheets.services.CellLockService
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Implementation of the CellLockService interface using Redis
 */
@Service
class CellLockServiceImpl(
    private val redisTemplate: RedisTemplate<String, String>
) : CellLockService {
    
    // Key prefix for cell locks
    private val LOCK_KEY_PREFIX = "cell_lock:"
    
    override fun acquireLock(sheetId: Long, cellId: String, userId: String, timeoutMs: Long): Boolean {
        val lockKey = getLockKey(sheetId, cellId)
        
        // Try to set the lock key with the user ID
        val result = redisTemplate.opsForValue().setIfAbsent(lockKey, userId, timeoutMs, TimeUnit.MILLISECONDS)
        
        return result ?: false
    }
    
    override fun releaseLock(sheetId: Long, cellId: String, userId: String): Boolean {
        val lockKey = getLockKey(sheetId, cellId)
        
        // Get the current lock owner
        val currentOwner = redisTemplate.opsForValue().get(lockKey)
        
        // Only the owner can release the lock
        if (currentOwner == userId) {
            redisTemplate.delete(lockKey)
            return true
        }
        
        return false
    }
    
    override fun isLocked(sheetId: Long, cellId: String): Boolean {
        val lockKey = getLockKey(sheetId, cellId)
        
        // Check if the lock key exists
        return redisTemplate.hasKey(lockKey)
    }
    
    override fun getLockOwner(sheetId: Long, cellId: String): String? {
        val lockKey = getLockKey(sheetId, cellId)
        
        // Get the lock owner
        return redisTemplate.opsForValue().get(lockKey)
    }
    
    /**
     * Get the lock key for a cell
     * @param sheetId The ID of the sheet
     * @param cellId The ID of the cell
     * @return The lock key
     */
    private fun getLockKey(sheetId: Long, cellId: String): String {
        return "$LOCK_KEY_PREFIX$sheetId:$cellId"
    }
}
```

## 5. Dependency Management

The dependency management system will track dependencies between cells and ensure that changes are propagated correctly. It will also detect and prevent circular dependencies.

### 5.1 Cell Dependency Model

The `CellDependency` model represents a dependency between two cells:

```kotlin
data class CellDependency(
    val id: String = "", // Auto-generated
    val sheetId: Long,
    val sourceCellId: String, // The cell that depends on another cell
    val targetCellId: String, // The cell that is being depended upon
    val createdAt: Instant,
    val updatedAt: Instant
)
```

### 5.2 Dependency Repository

The `CellDependencyRepository` interface provides methods for managing cell dependencies:

```kotlin
interface CellDependencyRepository {
    fun save(cellDependency: CellDependency): CellDependency
    fun findBySourceCellId(sourceCellId: String): List<CellDependency>
    fun findByTargetCellId(targetCellId: String): List<CellDependency>
    fun findBySourceCellIdAndTargetCellId(sourceCellId: String, targetCellId: String): CellDependency?
    fun deleteBySourceCellId(sourceCellId: String): Int
    fun deleteByTargetCellId(targetCellId: String): Int
}
```

### 5.3 Dependency Management Flow

1. When a cell is updated, the system parses the expression to identify cell references.
2. For each cell reference, a dependency is created between the current cell and the referenced cell.
3. The system checks for circular dependencies before creating new dependencies.
4. When a cell is updated, the system propagates the changes to all dependent cells.

## 6. Concurrency Control

The concurrency control system will use Redis for distributed locking to prevent concurrent updates to the same cell.

### 6.1 Lock Management

1. When a cell is being updated, the system acquires a lock on the cell.
2. If the lock cannot be acquired (because another user is updating the cell), the update fails.
3. Once the update is complete, the lock is released.

### 6.2 Lock Expiration

To prevent deadlocks, locks will have an expiration time. If a lock is not released within the expiration time, it will be automatically released.

## 7. Error Handling

The service layer will handle various error conditions:

### 7.1 Cell Not Found

If a cell is not found, the service will return null or throw an appropriate exception.

### 7.2 Sheet Not Found or Access Denied

If a sheet is not found or the user does not have access to it, the service will throw an appropriate exception.

### 7.3 Circular Dependencies

If a circular dependency is detected, the service will throw a `CircularDependencyException`.

### 7.4 Concurrent Updates

If a cell is locked by another user, the service will throw an `IllegalStateException`.

### 7.5 Expression Parsing Errors

If an expression cannot be parsed, the service will throw an `ExpressionParseException`.

### 7.6 Expression Evaluation Errors

If an expression cannot be evaluated, the service will throw an `ExpressionEvaluationException`.

## 8. Testing Strategy

### 8.1 Unit Tests

Write unit tests for each service method to ensure they handle requests correctly and return the expected responses. Mock the repository layer to isolate the service logic.

### 8.2 Integration Tests

Write integration tests for the service layer to ensure it works correctly with the repository layer and expression evaluation components. Use an in-memory database for testing.

### 8.3 Concurrency Tests

Write tests to ensure the concurrency control system works correctly and prevents concurrent updates to the same cell.

### 8.4 Error Handling Tests

Write tests to ensure the service layer handles errors correctly and throws the expected exceptions.

## Conclusion

This implementation plan provides a detailed approach to implementing the service layer for the Cell Management functionality. By following this plan, we'll create a robust and extensible system for managing cells, expressions, and dependencies.
