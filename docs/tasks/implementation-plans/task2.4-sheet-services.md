# Task 2.4: Sheet Services

## Overview
This task involves creating the service layer for Sheet Management, which will handle business logic for sheet operations such as creation, retrieval, and sharing.

## Implementation Steps

### 1. Create SheetService Interface
Create `src/main/kotlin/com/sheets/services/SheetService.kt`:

```kotlin
package com.sheets.services

import com.sheets.models.domain.AccessType
import com.sheets.models.domain.Sheet

/**
 * Service interface for Sheet operations
 */
interface SheetService {
    /**
     * Create a new sheet
     * @param name Name of the sheet
     * @param description Description of the sheet
     * @param userId ID of the user creating the sheet
     * @return The created sheet
     */
    fun createSheet(name: String, description: String, userId: Long): Sheet
    
    /**
     * Get all sheets owned by or shared with a user
     * @param userId ID of the user
     * @return List of sheets owned by or shared with the user
     */
    fun getSheetsByUserId(userId: Long): List<Sheet>
    
    /**
     * Get a sheet by its ID
     * @param sheetId ID of the sheet
     * @param userId ID of the user requesting the sheet
     * @return The sheet if found and the user has access, null otherwise
     */
    fun getSheetById(sheetId: Long, userId: Long): Sheet
    
    /**
     * Share a sheet with other users
     * @param sheetId ID of the sheet to share
     * @param userIds IDs of the users to share with
     * @param accessType Type of access to grant
     * @param ownerId ID of the user sharing the sheet
     * @return Success message
     */
    fun shareSheet(sheetId: Long, userIds: List<Long>, accessType: AccessType, ownerId: Long): String
}
```

### 2. Create SheetServiceImpl
Create `src/main/kotlin/com/sheets/services/SheetServiceImpl.kt`:

```kotlin
package com.sheets.services

import com.sheets.models.domain.AccessMapping
import com.sheets.models.domain.AccessType
import com.sheets.models.domain.Sheet
import com.sheets.repositories.AccessRepository
import com.sheets.repositories.SheetRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class SheetServiceImpl(
    private val sheetRepository: SheetRepository,
    private val accessRepository: AccessRepository
) : SheetService {
    
    override fun createSheet(name: String, description: String, userId: Long): Sheet {
        val sheet = Sheet(
            name = name,
            description = description,
            userId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        return sheetRepository.save(sheet)
    }
    
    override fun getSheetsByUserId(userId: Long): List<Sheet> {
        val ownedSheets = sheetRepository.findByUserId(userId)
        val sharedSheets = sheetRepository.findSharedWithUser(userId)
        
        return (ownedSheets + sharedSheets).distinctBy { it.id }
    }
    
    override fun getSheetById(sheetId: Long, userId: Long): Sheet {
        val sheet = sheetRepository.findById(sheetId)
            ?: throw NoSuchElementException("Sheet not found with ID: $sheetId")
        
        // Check if user is the owner
        if (sheet.userId == userId) {
            return sheet
        }
        
        // Check if sheet is shared with user
        val accessMapping = accessRepository.findBySheetIdAndUserId(sheetId, userId)
            ?: throw SecurityException("User does not have access to this sheet")
        
        return sheet
    }
    
    override fun shareSheet(sheetId: Long, userIds: List<Long>, accessType: AccessType, ownerId: Long): String {
        val sheet = sheetRepository.findById(sheetId)
            ?: throw NoSuchElementException("Sheet not found with ID: $sheetId")
        
        // Check if user is the owner
        if (sheet.userId != ownerId) {
            // Check if user has admin access
            val accessMapping = accessRepository.findBySheetIdAndUserId(sheetId, ownerId)
                ?: throw SecurityException("User does not have access to this sheet")
            
            if (accessMapping.accessType != AccessType.ADMIN) {
                throw SecurityException("User does not have permission to share this sheet")
            }
        }
        
        // Share sheet with each user
        userIds.forEach { userId ->
            val existingMapping = accessRepository.findBySheetIdAndUserId(sheetId, userId)
            
            if (existingMapping != null) {
                // Update existing mapping
                accessRepository.save(existingMapping.copy(
                    accessType = accessType,
                    updatedAt = Instant.now()
                ))
            } else {
                // Create new mapping
                accessRepository.save(AccessMapping(
                    sheetId = sheetId,
                    userId = userId,
                    accessType = accessType,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                ))
            }
        }
        
        return "Sheet shared successfully with ${userIds.size} users"
    }
}
```

## Testing
1. Write unit tests for SheetServiceImpl
2. Test all service methods with various scenarios
3. Test error handling and edge cases
4. Mock repository dependencies

## Completion Criteria
- SheetService interface is created with all required methods
- SheetServiceImpl is implemented with proper business logic
- Error handling is implemented for edge cases
- Service methods are properly tested
- Code follows best practices and is well-documented
