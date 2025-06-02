# Task 2.1: Sheet Domain Models

## Overview
This task involves creating the domain models for Sheet Management, including Sheet, AccessMapping, and AccessType.

## Implementation Steps

### 1. Create AccessType Enum
Create `src/main/kotlin/com/sheets/models/domain/AccessType.kt`:

```kotlin
package com.sheets.models.domain

/**
 * Represents the access level a user has to a sheet
 */
enum class AccessType {
    /**
     * Read-only access to the sheet
     */
    READ,
    
    /**
     * Read and write access to the sheet
     */
    WRITE,
    
    /**
     * Full administrative access to the sheet, including sharing
     */
    ADMIN
}
```

### 2. Create Sheet Domain Model
Create `src/main/kotlin/com/sheets/models/domain/Sheet.kt`:

```kotlin
package com.sheets.models.domain

import java.time.Instant

/**
 * Represents a spreadsheet in the system
 */
data class Sheet(
    /**
     * Unique identifier for the sheet
     */
    val id: Long? = null,
    
    /**
     * Name of the sheet
     */
    val name: String,
    
    /**
     * Description of the sheet
     */
    val description: String,
    
    /**
     * Maximum number of rows in the sheet
     */
    val maxLength: Int = 100,
    
    /**
     * Maximum number of columns in the sheet
     */
    val maxBreadth: Int = 100,
    
    /**
     * ID of the user who owns the sheet
     */
    val userId: Long,
    
    /**
     * Time when the sheet was created
     */
    val createdAt: Instant = Instant.now(),
    
    /**
     * Time when the sheet was last updated
     */
    val updatedAt: Instant = Instant.now()
)
```

### 3. Create AccessMapping Domain Model
Create `src/main/kotlin/com/sheets/models/domain/AccessMapping.kt`:

```kotlin
package com.sheets.models.domain

import java.time.Instant

/**
 * Represents an access permission mapping between a user and a sheet
 */
data class AccessMapping(
    /**
     * Unique identifier for the access mapping
     */
    val id: Long? = null,
    
    /**
     * ID of the sheet being accessed
     */
    val sheetId: Long,
    
    /**
     * ID of the user being granted access
     */
    val userId: Long,
    
    /**
     * Type of access being granted
     */
    val accessType: AccessType,
    
    /**
     * Time when the access mapping was created
     */
    val createdAt: Instant = Instant.now(),
    
    /**
     * Time when the access mapping was last updated
     */
    val updatedAt: Instant = Instant.now()
)
```

### 4. Create Cell Domain Model
Create `src/main/kotlin/com/sheets/models/domain/Cell.kt`:

```kotlin
package com.sheets.models.domain

import java.time.Instant

/**
 * Represents a cell in a spreadsheet
 */
data class Cell(
    /**
     * MongoDB document ID
     */
    val id: String? = null,
    
    /**
     * Cell identifier in row:column format (e.g., "A1", "B2")
     */
    val cellId: String,
    
    /**
     * ID of the sheet this cell belongs to
     */
    val sheetId: Long,
    
    /**
     * Type of data stored in the cell
     */
    val dataType: DataType,
    
    /**
     * Raw data stored in the cell (primitive value or expression)
     */
    val data: String,
    
    /**
     * Whether this cell is referenced in an expression in another cell
     */
    val isInvolvedInExpression: Boolean = false,
    
    /**
     * Expression stored in the cell (if dataType is EXPRESSION)
     */
    val expression: String? = null,
    
    /**
     * Time when the cell was created
     */
    val createdAt: Instant = Instant.now(),
    
    /**
     * Time when the cell was last updated
     */
    val updatedAt: Instant = Instant.now()
)

/**
 * Types of data that can be stored in a cell
 */
enum class DataType {
    /**
     * Primitive value (string, number, boolean)
     */
    PRIMITIVE,
    
    /**
     * Expression that needs to be evaluated
     */
    EXPRESSION
}
```

### 5. Create CellDependency Domain Model
Create `src/main/kotlin/com/sheets/models/domain/CellDependency.kt`:

```kotlin
package com.sheets.models.domain

/**
 * Represents dependencies between cells in a spreadsheet
 */
data class CellDependency(
    /**
     * MongoDB document ID
     */
    val id: String? = null,
    
    /**
     * ID of the cell that contains an expression
     */
    val cellId: String,
    
    /**
     * IDs of cells that are referenced in the expression
     */
    val dependentCellIds: Set<String>,
    
    /**
     * ID of the sheet these cells belong to
     */
    val sheetId: Long
)
```

## Testing
- Create unit tests for domain models to ensure they can be properly instantiated and manipulated
- Verify that all required fields are present
- Test serialization and deserialization of domain models

## Completion Criteria
- All domain models are created with proper documentation
- Models include all necessary fields as specified in the design documents
- Models use appropriate data types
- Models are properly organized in the correct package
