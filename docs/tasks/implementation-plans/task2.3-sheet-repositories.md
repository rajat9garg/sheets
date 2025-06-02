# Task 2.3: Sheet Repositories

## Overview
This task involves creating the repository interfaces for Sheet, AccessMapping, and Cell entities.

## Implementation Steps

### 1. Create SheetRepository Interface
Create `src/main/kotlin/com/sheets/repositories/SheetRepository.kt`:

```kotlin
package com.sheets.repositories

import com.sheets.models.domain.Sheet

/**
 * Repository interface for Sheet entity operations
 */
interface SheetRepository {
    /**
     * Save a sheet to the database
     * @param sheet The sheet to save
     * @return The saved sheet with ID populated
     */
    fun save(sheet: Sheet): Sheet
    
    /**
     * Find sheets owned by a specific user
     * @param userId The ID of the user
     * @return List of sheets owned by the user
     */
    fun findByUserId(userId: Long): List<Sheet>
    
    /**
     * Find a sheet by its ID
     * @param id The ID of the sheet
     * @return The sheet if found, null otherwise
     */
    fun findById(id: Long): Sheet?
    
    /**
     * Find sheets shared with a specific user
     * @param userId The ID of the user
     * @return List of sheets shared with the user
     */
    fun findSharedWithUser(userId: Long): List<Sheet>
}
```

### 2. Create AccessRepository Interface
Create `src/main/kotlin/com/sheets/repositories/AccessRepository.kt`:

```kotlin
package com.sheets.repositories

import com.sheets.models.domain.AccessMapping

/**
 * Repository interface for AccessMapping entity operations
 */
interface AccessRepository {
    /**
     * Save an access mapping to the database
     * @param accessMapping The access mapping to save
     * @return The saved access mapping with ID populated
     */
    fun save(accessMapping: AccessMapping): AccessMapping
    
    /**
     * Find an access mapping by sheet ID and user ID
     * @param sheetId The ID of the sheet
     * @param userId The ID of the user
     * @return The access mapping if found, null otherwise
     */
    fun findBySheetIdAndUserId(sheetId: Long, userId: Long): AccessMapping?
    
    /**
     * Find all access mappings for a specific sheet
     * @param sheetId The ID of the sheet
     * @return List of access mappings for the sheet
     */
    fun findBySheetId(sheetId: Long): List<AccessMapping>
}
```

### 3. Create CellRepository Interface
Create `src/main/kotlin/com/sheets/repositories/CellRepository.kt`:

```kotlin
package com.sheets.repositories

import com.sheets.models.domain.Cell

/**
 * Repository interface for Cell entity operations
 */
interface CellRepository {
    /**
     * Save a cell to the database
     * @param cell The cell to save
     * @return The saved cell with ID populated
     */
    fun save(cell: Cell): Cell
    
    /**
     * Find a cell by its ID and sheet ID
     * @param cellId The ID of the cell (e.g., "A1", "B2")
     * @param sheetId The ID of the sheet
     * @return The cell if found, null otherwise
     */
    fun findByCellIdAndSheetId(cellId: String, sheetId: Long): Cell?
    
    /**
     * Find all cells for a specific sheet
     * @param sheetId The ID of the sheet
     * @return List of cells for the sheet
     */
    fun findBySheetId(sheetId: Long): List<Cell>
}
```

## Testing
1. Write unit tests for each repository implementation
2. Test CRUD operations for each repository
3. Test query methods with various parameters
4. Test edge cases like empty results or invalid inputs

## Completion Criteria
- All repository interfaces are created (SheetRepository, AccessRepository, CellRepository)
- Repository methods are properly defined with appropriate documentation
- Code follows best practices and is well-documented
