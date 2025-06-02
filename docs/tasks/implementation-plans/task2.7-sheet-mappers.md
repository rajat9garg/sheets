# Task 2.7: Sheet Mappers

## Overview
This task involves creating mappers to convert between domain models and OpenAPI-generated models. Following the OpenAPI-first approach, these mappers will ensure proper separation between the API layer and the domain layer.

## Implementation Steps

### 1. Create SheetMapper Interface
Create `src/main/kotlin/com/sheets/mappers/SheetMapper.kt`:

```kotlin
package com.sheets.mappers

import com.sheets.generated.model.SheetDetailsResponse
import com.sheets.generated.model.SheetResponse
import com.sheets.generated.model.SheetSummaryResponse
import com.sheets.models.domain.Sheet

/**
 * Mapper interface for converting between domain and API models for Sheet
 */
interface SheetMapper {
    /**
     * Convert domain Sheet model to API SheetResponse model
     * @param sheet Domain Sheet model
     * @return API SheetResponse model
     */
    fun toSheetResponse(sheet: Sheet): SheetResponse
    
    /**
     * Convert domain Sheet model to API SheetSummaryResponse model
     * @param sheet Domain Sheet model
     * @return API SheetSummaryResponse model
     */
    fun toSheetSummaryResponse(sheet: Sheet): SheetSummaryResponse
    
    /**
     * Convert domain Sheet model to API SheetDetailsResponse model
     * @param sheet Domain Sheet model
     * @return API SheetDetailsResponse model
     */
    fun toSheetDetailsResponse(sheet: Sheet): SheetDetailsResponse
}
```

### 2. Create SheetMapperImpl
Create `src/main/kotlin/com/sheets/mappers/SheetMapperImpl.kt`:

```kotlin
package com.sheets.mappers

import com.sheets.generated.model.*
import com.sheets.models.domain.AccessType
import com.sheets.models.domain.Sheet
import com.sheets.repositories.AccessRepository
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
class SheetMapperImpl(
    private val accessRepository: AccessRepository
) : SheetMapper {
    
    override fun toSheetResponse(sheet: Sheet): SheetResponse {
        return SheetResponse(
            id = sheet.id,
            name = sheet.name,
            description = sheet.description,
            maxLength = sheet.maxLength,
            maxBreadth = sheet.maxBreadth,
            userId = sheet.userId,
            createdAt = OffsetDateTime.ofInstant(sheet.createdAt, ZoneOffset.UTC),
            updatedAt = OffsetDateTime.ofInstant(sheet.updatedAt, ZoneOffset.UTC)
        )
    }
    
    override fun toSheetSummaryResponse(sheet: Sheet): SheetSummaryResponse {
        // Determine access type
        val accessType = if (sheet.userId == sheet.requestUserId) {
            SheetSummaryResponse.AccessType.OWNER
        } else {
            val accessMapping = accessRepository.findBySheetIdAndUserId(sheet.id, sheet.requestUserId)
            when (accessMapping?.accessType) {
                AccessType.READ -> SheetSummaryResponse.AccessType.READ
                AccessType.WRITE -> SheetSummaryResponse.AccessType.WRITE
                AccessType.ADMIN -> SheetSummaryResponse.AccessType.ADMIN
                else -> SheetSummaryResponse.AccessType.READ // Default to READ if not found
            }
        }
        
        return SheetSummaryResponse(
            id = sheet.id,
            name = sheet.name,
            description = sheet.description,
            accessType = accessType,
            createdAt = OffsetDateTime.ofInstant(sheet.createdAt, ZoneOffset.UTC),
            updatedAt = OffsetDateTime.ofInstant(sheet.updatedAt, ZoneOffset.UTC)
        )
    }
    
    override fun toSheetDetailsResponse(sheet: Sheet): SheetDetailsResponse {
        // Determine access type
        val accessType = if (sheet.userId == sheet.requestUserId) {
            SheetDetailsResponse.AccessType.OWNER
        } else {
            val accessMapping = accessRepository.findBySheetIdAndUserId(sheet.id, sheet.requestUserId)
            when (accessMapping?.accessType) {
                AccessType.READ -> SheetDetailsResponse.AccessType.READ
                AccessType.WRITE -> SheetDetailsResponse.AccessType.WRITE
                AccessType.ADMIN -> SheetDetailsResponse.AccessType.ADMIN
                else -> SheetDetailsResponse.AccessType.READ // Default to READ if not found
            }
        }
        
        // For now, return empty cells array as cell implementation is not part of this task
        val cells = emptyList<CellResponse>()
        
        return SheetDetailsResponse(
            id = sheet.id,
            name = sheet.name,
            description = sheet.description,
            maxLength = sheet.maxLength,
            maxBreadth = sheet.maxBreadth,
            userId = sheet.userId,
            accessType = accessType,
            cells = cells,
            createdAt = OffsetDateTime.ofInstant(sheet.createdAt, ZoneOffset.UTC),
            updatedAt = OffsetDateTime.ofInstant(sheet.updatedAt, ZoneOffset.UTC)
        )
    }
}
```

### 3. Update Sheet Domain Model
Update `src/main/kotlin/com/sheets/models/domain/Sheet.kt` to include the requesting user ID:

```kotlin
package com.sheets.models.domain

import java.time.Instant

/**
 * Domain model for Sheet
 */
data class Sheet(
    val id: Long = 0,
    val name: String,
    val description: String,
    val maxLength: Int = 100,
    val maxBreadth: Int = 100,
    val userId: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    // This field is used for mapping purposes and is not stored in the database
    val requestUserId: Long = userId
)
```

### 4. Create Mapper Tests
Create `src/test/kotlin/com/sheets/mappers/SheetMapperTest.kt`:

```kotlin
package com.sheets.mappers

import com.sheets.models.domain.AccessType
import com.sheets.models.domain.Sheet
import com.sheets.repositories.AccessRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.time.Instant

class SheetMapperTest {

    private val accessRepository = mock(AccessRepository::class.java)
    private val sheetMapper = SheetMapperImpl(accessRepository)

    @Test
    fun `toSheetResponse should map domain model to API model`() {
        // Given
        val sheet = Sheet(
            id = 1L,
            name = "Test Sheet",
            description = "Test Description",
            userId = 1L,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        // When
        val response = sheetMapper.toSheetResponse(sheet)
        
        // Then
        assertEquals(sheet.id, response.id)
        assertEquals(sheet.name, response.name)
        assertEquals(sheet.description, response.description)
        assertEquals(sheet.userId, response.userId)
    }

    @Test
    fun `toSheetSummaryResponse should set OWNER access type for sheet owner`() {
        // Given
        val userId = 1L
        val sheet = Sheet(
            id = 1L,
            name = "Test Sheet",
            description = "Test Description",
            userId = userId,
            requestUserId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        // When
        val response = sheetMapper.toSheetSummaryResponse(sheet)
        
        // Then
        assertEquals(sheet.id, response.id)
        assertEquals(sheet.name, response.name)
        assertEquals(sheet.description, response.description)
        assertEquals(com.sheets.generated.model.SheetSummaryResponse.AccessType.OWNER, response.accessType)
    }

    @Test
    fun `toSheetSummaryResponse should set correct access type for shared sheet`() {
        // Given
        val ownerId = 1L
        val userId = 2L
        val sheet = Sheet(
            id = 1L,
            name = "Test Sheet",
            description = "Test Description",
            userId = ownerId,
            requestUserId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val accessMapping = com.sheets.models.domain.AccessMapping(
            id = 1L,
            sheetId = 1L,
            userId = userId,
            accessType = AccessType.WRITE,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        // Mock repository
        `when`(accessRepository.findBySheetIdAndUserId(1L, userId)).thenReturn(accessMapping)
        
        // When
        val response = sheetMapper.toSheetSummaryResponse(sheet)
        
        // Then
        assertEquals(sheet.id, response.id)
        assertEquals(sheet.name, response.name)
        assertEquals(sheet.description, response.description)
        assertEquals(com.sheets.generated.model.SheetSummaryResponse.AccessType.WRITE, response.accessType)
    }

    @Test
    fun `toSheetDetailsResponse should include empty cells array`() {
        // Given
        val userId = 1L
        val sheet = Sheet(
            id = 1L,
            name = "Test Sheet",
            description = "Test Description",
            userId = userId,
            requestUserId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        // When
        val response = sheetMapper.toSheetDetailsResponse(sheet)
        
        // Then
        assertEquals(sheet.id, response.id)
        assertEquals(sheet.name, response.name)
        assertEquals(sheet.description, response.description)
        assertEquals(com.sheets.generated.model.SheetDetailsResponse.AccessType.OWNER, response.accessType)
        assertEquals(0, response.cells.size)
    }
}
```

## Testing
1. Run unit tests for the mapper
2. Test all mapping scenarios (owner access, shared access, etc.)
3. Verify correct conversion between domain and API models
4. Test with various input data

## Completion Criteria
- SheetMapper interface is created with all required methods
- SheetMapperImpl is implemented with proper mapping logic
- Sheet domain model is updated to include requestUserId field
- Mapper tests pass for all scenarios
- Code follows best practices and is well-documented

## Notes
- The implementation assumes that the Sheet domain model will be extended with a requestUserId field that is not stored in the database but used for mapping purposes
- The mapper handles conversion between domain AccessType and API AccessType enums
- Cell mapping is minimal as cell implementation is not part of the current task scope
