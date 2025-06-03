package com.sheets.exceptions

/**
 * Base exception class for all sheet-related exceptions
 */
abstract class SheetException(message: String, val details: Map<String, Any>? = null) : RuntimeException(message)

/**
 * Exception thrown when a resource lock cannot be acquired
 */
open class ResourceLockException(
    message: String,
    val resourceId: String,
    val lockOwner: String?,
    val retryAfterMs: Long = 5000,
    details: Map<String, Any>? = null
) : SheetException(message, details ?: mapOf(
    "resourceId" to resourceId,
    "lockOwner" to (lockOwner ?: "unknown"),
    "retryAfterMs" to retryAfterMs
))

/**
 * Exception thrown when a sheet lock cannot be acquired
 */
class SheetLockException(
    sheetId: Long,
    lockOwner: String?,
    retryAfterMs: Long = 5000
) : ResourceLockException(
    "Could not acquire lock on sheet: $sheetId. Current lock owner: $lockOwner. Please try again in ${retryAfterMs/1000} seconds.",
    sheetId.toString(),
    lockOwner,
    retryAfterMs
)

/**
 * Exception thrown when a cell lock cannot be acquired
 */
class CellLockException(
    cellId: String,
    lockOwner: String?,
    retryAfterMs: Long = 5000
) : ResourceLockException(
    "Could not acquire lock on cell: $cellId. Current lock owner: $lockOwner. Please try again in ${retryAfterMs/1000} seconds.",
    cellId,
    lockOwner,
    retryAfterMs
)

/**
 * Exception thrown when a circular dependency is detected
 */
class CircularReferenceException(
    path: List<String>,
    details: Map<String, Any>? = null
) : SheetException(
    "Circular dependency detected: ${path.joinToString(" -> ")}. Please fix the circular reference in your formulas.",
    details ?: mapOf("path" to path)
)

/**
 * Exception thrown when a cell has dependent cells that prevent an operation
 */
class CellDependencyException(
    cellId: String,
    dependentCells: List<String>
) : SheetException(
    "Cannot perform operation on cell $cellId as it is referenced by other cells: ${dependentCells.joinToString(", ")}",
    mapOf("cellId" to cellId, "dependentCells" to dependentCells)
)

/**
 * Exception thrown when there's an issue with Redis persistence
 */
class PersistenceException(
    message: String,
    val operation: String,
    val entityId: String,
    details: Map<String, Any>? = null
) : SheetException(message, details ?: mapOf(
    "operation" to operation,
    "entityId" to entityId
))
