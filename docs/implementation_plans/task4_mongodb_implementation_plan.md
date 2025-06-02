# MongoDB Implementation Plan for Cell Management

## Overview

This document outlines the detailed implementation plan for the MongoDB schema and repository layer for the Cell Management functionality in Task4. This is the first phase of implementation that will provide the foundation for the rest of the Cell Management features.

## Table of Contents

1. [MongoDB Document Models](#1-mongodb-document-models)
2. [Document-Domain Mappers](#2-document-domain-mappers)
3. [Repository Interfaces](#3-repository-interfaces)
4. [Repository Implementations](#4-repository-implementations)
5. [MongoDB Indexes](#5-mongodb-indexes)

## 1. MongoDB Document Models

### 1.1 CellDocument

The `CellDocument` class will represent a cell in the MongoDB database:

```kotlin
package com.sheets.repositories.mongo.documents

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB document representing a cell in a sheet
 */
@Document(collection = "cells")
data class CellDocument(
    @Id
    val id: String, // Format: sheetId:row:column
    val sheetId: Long,
    val row: Int,
    val column: Int,
    val data: String,
    val dataType: String, // "PRIMITIVE" or "EXPRESSION"
    val evaluatedValue: String,
    val isInvolvedInExpression: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
```

### 1.2 CellDependencyDocument

The `CellDependencyDocument` class will represent a dependency between cells in the MongoDB database:

```kotlin
package com.sheets.repositories.mongo.documents

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB document representing a dependency between cells
 */
@Document(collection = "cell_dependencies")
data class CellDependencyDocument(
    @Id
    val id: String = "", // Auto-generated
    val sourceCellId: String, // The cell that depends on another cell
    val targetCellId: List<String>, // The li cell Ids that is being depended upon
    val createdAt: Instant,
    val updatedAt: Instant
)
```

## 2. Document-Domain Mappers

### 2.1 CellMapper

The `CellMapper` class will convert between `Cell` domain objects and `CellDocument` MongoDB documents:

```kotlin
package com.sheets.mappers

import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import com.sheets.repositories.mongo.documents.CellDocument
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Mapper for converting between Cell domain objects and CellDocument MongoDB documents
 */
@Component
class CellMapper {
    /**
     * Convert a Cell domain object to a CellDocument MongoDB document
     */
    fun toDocument(cell: Cell): CellDocument {
        return CellDocument(
            id = cell.id,
            sheetId = cell.sheetId,
            row = cell.row,
            column = cell.column,
            data = cell.data,
            dataType = cell.dataType.name,
            evaluatedValue = cell.evaluatedValue,
            isInvolvedInExpression = false, // This will be set by the dependency manager
            createdAt = cell.createdAt,
            updatedAt = cell.updatedAt
        )
    }

    /**
     * Convert a CellDocument MongoDB document to a Cell domain object
     */
    fun toDomain(document: CellDocument): Cell {
        return Cell(
            id = document.id,
            sheetId = document.sheetId,
            row = document.row,
            column = document.column,
            data = document.data,
            dataType = DataType.valueOf(document.dataType),
            evaluatedValue = document.evaluatedValue,
            createdAt = document.createdAt,
            updatedAt = document.updatedAt
        )
    }
}
```

### 2.2 CellDependencyMapper

The `CellDependencyMapper` class will convert between `CellDependency` domain objects and `CellDependencyDocument` MongoDB documents:

```kotlin
package com.sheets.mappers

import com.sheets.models.domain.CellDependency
import com.sheets.repositories.mongo.documents.CellDependencyDocument
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Mapper for converting between CellDependency domain objects and CellDependencyDocument MongoDB documents
 */
@Component
class CellDependencyMapper {
    /**
     * Convert a CellDependency domain object to a CellDependencyDocument MongoDB document
     */
    fun toDocument(dependency: CellDependency): CellDependencyDocument {
        return CellDependencyDocument(
            id = dependency.id,
            sheetId = dependency.sheetId,
            sourceCellId = dependency.sourceCellId,
            targetCellId = dependency.targetCellId,
            createdAt = dependency.createdAt,
            updatedAt = dependency.updatedAt
        )
    }

    /**
     * Convert a CellDependencyDocument MongoDB document to a CellDependency domain object
     */
    fun toDomain(document: CellDependencyDocument): CellDependency {
        return CellDependency(
            id = document.id,
            sheetId = document.sheetId,
            sourceCellId = document.sourceCellId,
            targetCellId = document.targetCellId,
            createdAt = document.createdAt,
            updatedAt = document.updatedAt
        )
    }
}
```

## 3. Repository Interfaces

### 3.1 CellRepository Interface

The `CellRepository` interface will define the operations for accessing and manipulating cells:

```kotlin
package com.sheets.repositories

import com.sheets.models.domain.Cell
import java.time.Instant

/**
 * Repository interface for Cell operations
 */
interface CellRepository {
    /**
     * Find a cell by its ID
     * @param id The ID of the cell to find
     * @return The cell if found, null otherwise
     */
    fun findById(id: String): Cell?

    /**
     * Find all cells in a sheet
     * @param sheetId The ID of the sheet
     * @return A list of cells in the sheet
     */
    fun findBySheetId(sheetId: Long): List<Cell>

    /**
     * Save a cell
     * @param cell The cell to save
     * @return The saved cell
     */
    fun save(cell: Cell): Cell

    /**
     * Save multiple cells
     * @param cells The cells to save
     * @return The saved cells
     */
    fun saveAll(cells: List<Cell>): List<Cell>

    /**
     * Delete a cell by its ID
     * @param id The ID of the cell to delete
     */
    fun deleteById(id: String)

    /**
     * Delete all cells in a sheet
     * @param sheetId The ID of the sheet
     */
    fun deleteBySheetId(sheetId: Long)

    /**
     * Find all cells in a sheet that are involved in expressions
     * @param sheetId The ID of the sheet
     * @return A list of cells involved in expressions
     */
    fun findCellsInvolvedInExpressions(sheetId: Long): List<Cell>
}
```

### 3.2 CellDependencyRepository Interface

The `CellDependencyRepository` interface will define the operations for accessing and manipulating cell dependencies:

```kotlin
package com.sheets.repositories

import com.sheets.models.domain.CellDependency
import java.time.Instant

/**
 * Repository interface for CellDependency operations
 */
interface CellDependencyRepository {
    /**
     * Find a dependency by its ID
     * @param id The ID of the dependency to find
     * @return The dependency if found, null otherwise
     */
    fun findById(id: String): CellDependency?

    /**
     * Find all dependencies where the specified cell is the source
     * @param sourceCellId The ID of the source cell
     * @return A list of dependencies
     */
    fun findBySourceCellId(sourceCellId: String): List<CellDependency>

    /**
     * Find all dependencies where the specified cell is the target
     * @param targetCellId The ID of the target cell
     * @return A list of dependencies
     */
    fun findByTargetCellId(targetCellId: String): List<CellDependency>

    /**
     * Find all dependencies in a sheet
     * @param sheetId The ID of the sheet
     * @return A list of dependencies
     */
    fun findBySheetId(sheetId: Long): List<CellDependency>

    /**
     * Save a dependency
     * @param dependency The dependency to save
     * @return The saved dependency
     */
    fun save(dependency: CellDependency): CellDependency

    /**
     * Save multiple dependencies
     * @param dependencies The dependencies to save
     * @return The saved dependencies
     */
    fun saveAll(dependencies: List<CellDependency>): List<CellDependency>

    /**
     * Delete a dependency by its ID
     * @param id The ID of the dependency to delete
     */
    fun deleteById(id: String)

    /**
     * Delete all dependencies where the specified cell is the source
     * @param sourceCellId The ID of the source cell
     */
    fun deleteBySourceCellId(sourceCellId: String)

    /**
     * Delete all dependencies where the specified cell is the target
     * @param targetCellId The ID of the target cell
     */
    fun deleteByTargetCellId(targetCellId: String)

    /**
     * Delete all dependencies in a sheet
     * @param sheetId The ID of the sheet
     */
    fun deleteBySheetId(sheetId: Long)
}
```

## 4. Repository Implementations

### 4.1 MongoCellRepository

The `MongoCellRepository` class will implement the `CellRepository` interface using MongoDB:

```kotlin
package com.sheets.repositories.impl

import com.sheets.mappers.CellMapper
import com.sheets.models.domain.Cell
import com.sheets.repositories.CellRepository
import com.sheets.repositories.mongo.documents.CellDocument
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * MongoDB implementation of the CellRepository interface
 */
@Repository
class MongoCellRepository(
    private val mongoTemplate: MongoTemplate,
    private val cellMapper: CellMapper
) : CellRepository {

    override fun findById(id: String): Cell? {
        val document = mongoTemplate.findById(id, CellDocument::class.java)
        return document?.let { cellMapper.toDomain(it) }
    }

    override fun findBySheetId(sheetId: Long): List<Cell> {
        val query = Query(Criteria.where("sheetId").`is`(sheetId))
        val documents = mongoTemplate.find(query, CellDocument::class.java)
        return documents.map { cellMapper.toDomain(it) }
    }

    override fun save(cell: Cell): Cell {
        val document = cellMapper.toDocument(cell)
        val savedDocument = mongoTemplate.save(document)
        return cellMapper.toDomain(savedDocument)
    }

    override fun saveAll(cells: List<Cell>): List<Cell> {
        val documents = cells.map { cellMapper.toDocument(it) }
        val savedDocuments = documents.map { mongoTemplate.save(it) }
        return savedDocuments.map { cellMapper.toDomain(it) }
    }

    override fun deleteById(id: String) {
        val query = Query(Criteria.where("_id").`is`(id))
        mongoTemplate.remove(query, CellDocument::class.java)
    }

    override fun deleteBySheetId(sheetId: Long) {
        val query = Query(Criteria.where("sheetId").`is`(sheetId))
        mongoTemplate.remove(query, CellDocument::class.java)
    }

    override fun findCellsInvolvedInExpressions(sheetId: Long): List<Cell> {
        val query = Query(
            Criteria.where("sheetId").`is`(sheetId)
                .and("isInvolvedInExpression").`is`(true)
        )
        val documents = mongoTemplate.find(query, CellDocument::class.java)
        return documents.map { cellMapper.toDomain(it) }
    }
}
```

### 4.2 MongoCellDependencyRepository

The `MongoCellDependencyRepository` class will implement the `CellDependencyRepository` interface using MongoDB:

```kotlin
package com.sheets.repositories.impl

import com.sheets.mappers.CellDependencyMapper
import com.sheets.models.domain.CellDependency
import com.sheets.repositories.CellDependencyRepository
import com.sheets.repositories.mongo.documents.CellDependencyDocument
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * MongoDB implementation of the CellDependencyRepository interface
 */
@Repository
class MongoCellDependencyRepository(
    private val mongoTemplate: MongoTemplate,
    private val cellDependencyMapper: CellDependencyMapper
) : CellDependencyRepository {

    override fun findById(id: String): CellDependency? {
        val document = mongoTemplate.findById(id, CellDependencyDocument::class.java)
        return document?.let { cellDependencyMapper.toDomain(it) }
    }

    override fun findBySourceCellId(sourceCellId: String): List<CellDependency> {
        val query = Query(Criteria.where("sourceCellId").`is`(sourceCellId))
        val documents = mongoTemplate.find(query, CellDependencyDocument::class.java)
        return documents.map { cellDependencyMapper.toDomain(it) }
    }

    override fun findByTargetCellId(targetCellId: String): List<CellDependency> {
        val query = Query(Criteria.where("targetCellId").`is`(targetCellId))
        val documents = mongoTemplate.find(query, CellDependencyDocument::class.java)
        return documents.map { cellDependencyMapper.toDomain(it) }
    }

    override fun findBySheetId(sheetId: Long): List<CellDependency> {
        val query = Query(Criteria.where("sheetId").`is`(sheetId))
        val documents = mongoTemplate.find(query, CellDependencyDocument::class.java)
        return documents.map { cellDependencyMapper.toDomain(it) }
    }

    override fun save(dependency: CellDependency): CellDependency {
        val document = cellDependencyMapper.toDocument(dependency)
        val savedDocument = mongoTemplate.save(document)
        return cellDependencyMapper.toDomain(savedDocument)
    }

    override fun saveAll(dependencies: List<CellDependency>): List<CellDependency> {
        val documents = dependencies.map { cellDependencyMapper.toDocument(it) }
        val savedDocuments = documents.map { mongoTemplate.save(it) }
        return savedDocuments.map { cellDependencyMapper.toDomain(it) }
    }

    override fun deleteById(id: String) {
        val query = Query(Criteria.where("_id").`is`(id))
        mongoTemplate.remove(query, CellDependencyDocument::class.java)
    }

    override fun deleteBySourceCellId(sourceCellId: String) {
        val query = Query(Criteria.where("sourceCellId").`is`(sourceCellId))
        mongoTemplate.remove(query, CellDependencyDocument::class.java)
    }

    override fun deleteByTargetCellId(targetCellId: String) {
        val query = Query(Criteria.where("targetCellId").`is`(targetCellId))
        mongoTemplate.remove(query, CellDependencyDocument::class.java)
    }

    override fun deleteBySheetId(sheetId: Long) {
        val query = Query(Criteria.where("sheetId").`is`(sheetId))
        mongoTemplate.remove(query, CellDependencyDocument::class.java)
    }
}
```

## 5. MongoDB Indexes

To optimize query performance, we'll create indexes on frequently queried fields:

```kotlin
package com.sheets.config

import com.mongodb.client.MongoDatabase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.index.IndexOperations
import javax.annotation.PostConstruct

/**
 * Configuration class for MongoDB indexes
 */
@Configuration
class MongoIndexConfig(
    private val mongoTemplate: MongoTemplate
) {

    /**
     * Create indexes for the cells collection
     */
    @PostConstruct
    fun initIndexes() {
        // Cells collection indexes
        val cellIndexOps = mongoTemplate.indexOps("cells")
        cellIndexOps.ensureIndex(Index().on("sheetId", Index.Direction.ASC))
        cellIndexOps.ensureIndex(Index().on("isInvolvedInExpression", Index.Direction.ASC))
        cellIndexOps.ensureIndex(
            Index()
                .on("sheetId", Index.Direction.ASC)
                .on("isInvolvedInExpression", Index.Direction.ASC)
        )

        // Cell dependencies collection indexes
        val dependencyIndexOps = mongoTemplate.indexOps("cell_dependencies")
        dependencyIndexOps.ensureIndex(Index().on("sheetId", Index.Direction.ASC))
        dependencyIndexOps.ensureIndex(Index().on("sourceCellId", Index.Direction.ASC))
        dependencyIndexOps.ensureIndex(Index().on("targetCellId", Index.Direction.ASC))
    }
}
```

## 6. Testing Strategy

### 6.1 Unit Tests

We'll write unit tests for the repository implementations using an embedded MongoDB instance:

```kotlin
package com.sheets.repositories.impl

import com.sheets.mappers.CellMapper
import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Instant

@DataMongoTest
@Import(CellMapper::class, MongoCellRepository::class)
class MongoCellRepositoryTest {

    @Autowired
    private lateinit var cellRepository: MongoCellRepository

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun setup() {
        mongoTemplate.dropCollection("cells")
    }

    @Test
    fun testFindById() {
        // Given
        val cell = Cell(
            id = "1:1:1",
            sheetId = 1L,
            row = 1,
            column = 1,
            data = "test",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "test",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        cellRepository.save(cell)

        // When
        val result = cellRepository.findById("1:1:1")

        // Then
        assertEquals(cell.id, result?.id)
        assertEquals(cell.sheetId, result?.sheetId)
        assertEquals(cell.row, result?.row)
        assertEquals(cell.column, result?.column)
        assertEquals(cell.data, result?.data)
        assertEquals(cell.dataType, result?.dataType)
        assertEquals(cell.evaluatedValue, result?.evaluatedValue)
    }

    // Additional tests for other repository methods
}
```

### 6.2 Integration Tests

We'll write integration tests to verify the interaction between the repository and the MongoDB database:

```kotlin
package com.sheets.repositories

import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import com.sheets.repositories.impl.MongoCellRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@SpringBootTest
@Testcontainers
class CellRepositoryIntegrationTest {

    companion object {
        @Container
        val mongoDBContainer = MongoDBContainer("mongo:4.4.6")

        @JvmStatic
        @DynamicPropertySource
        fun setProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri") { mongoDBContainer.replicaSetUrl }
        }
    }

    @Autowired
    private lateinit var cellRepository: CellRepository

    @Test
    fun testSaveAndFindById() {
        // Given
        val cell = Cell(
            id = "1:1:1",
            sheetId = 1L,
            row = 1,
            column = 1,
            data = "test",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "test",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // When
        val savedCell = cellRepository.save(cell)
        val foundCell = cellRepository.findById(cell.id)

        // Then
        assertEquals(savedCell.id, foundCell?.id)
        assertEquals(savedCell.sheetId, foundCell?.sheetId)
        assertEquals(savedCell.row, foundCell?.row)
        assertEquals(savedCell.column, foundCell?.column)
        assertEquals(savedCell.data, foundCell?.data)
        assertEquals(savedCell.dataType, foundCell?.dataType)
        assertEquals(savedCell.evaluatedValue, foundCell?.evaluatedValue)
    }

    // Additional integration tests
}
```

## Conclusion

This implementation plan provides a detailed approach to implementing the MongoDB schema and repository layer for the Cell Management functionality. By following this plan, we'll create a robust foundation for the rest of the Cell Management features.

The implementation follows best practices for Spring Boot and MongoDB, with a focus on extensibility and maintainability through the use of interfaces, mappers, and proper indexing.
