package com.sheets.exceptions

import com.sheets.generated.model.ErrorResponse
import com.sheets.services.expression.exception.CircularDependencyException
import com.sheets.services.expression.exception.ExpressionException
import com.sheets.exceptions.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.OffsetDateTime

@ControllerAdvice
class GlobalExceptionHandler {
    
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        val errorResponse = createErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.message ?: "Resource not found"
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    @ExceptionHandler(SecurityException::class)
    fun handleSecurityException(ex: SecurityException): ResponseEntity<ErrorResponse> {
        val errorResponse = createErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            "Forbidden",
            ex.message ?: "Access denied"
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        val errorResponse = createErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.message ?: "Invalid request parameters"
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errorMessage = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        
        val errorResponse = createErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Error",
            errorMessage
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }
    
    @ExceptionHandler(CircularDependencyException::class, CircularReferenceException::class)
    fun handleCircularDependencyException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.warn("Circular dependency detected: {}", ex.message)
        
        val details = when (ex) {
            is CircularReferenceException -> ex.details
            else -> null
        }
        
        val errorResponse = createErrorResponseWithDetails(
            HttpStatus.BAD_REQUEST.value(),
            "Circular Dependency",
            ex.message ?: "Circular dependency detected in cell references",
            details
        )
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .header("X-Error-Reason", "CIRCULAR_DEPENDENCY")
            .body(errorResponse)
    }
    
    @ExceptionHandler(ExpressionException::class)
    fun handleExpressionException(ex: ExpressionException): ResponseEntity<ErrorResponse> {
        logger.warn("Expression evaluation error: {}", ex.message)
        val errorResponse = createErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Expression Error",
            ex.message ?: "Error evaluating cell expression"
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }
    
    @ExceptionHandler(ResourceLockException::class, SheetLockException::class, CellLockException::class)
    fun handleLockException(ex: SheetException): ResponseEntity<ErrorResponse> {
        logger.warn("Lock conflict: {}", ex.message)
        
        val errorResponse = createErrorResponseWithDetails(
            HttpStatus.CONFLICT.value(),
            "Resource Locked",
            ex.message ?: "Resource is locked by another user",
            ex.details
        )
        
        val headers = mutableMapOf<String, String>()
        headers["X-Error-Reason"] = "RESOURCE_LOCKED"
        
        if (ex is ResourceLockException) {
            headers["Retry-After"] = "${ex.retryAfterMs / 1000}"
        }
        
        val responseBuilder = ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(errorResponse)
        
        headers.forEach { (key, value) ->
            responseBuilder.headers.add(key, value)
        }
        
        return responseBuilder
    }
    
    @ExceptionHandler(CellDependencyException::class)
    fun handleCellDependencyException(ex: CellDependencyException): ResponseEntity<ErrorResponse> {
        logger.warn("Cell dependency issue: {}", ex.message)
        
        val errorResponse = createErrorResponseWithDetails(
            HttpStatus.CONFLICT.value(),
            "Cell Dependency Conflict",
            ex.message ?: "Cell has dependencies that prevent this operation",
            ex.details
        )
        
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .header("X-Error-Reason", "CELL_DEPENDENCY")
            .body(errorResponse)
    }
    
    @ExceptionHandler(PersistenceException::class)
    fun handlePersistenceException(ex: PersistenceException): ResponseEntity<ErrorResponse> {
        logger.error("Persistence error: {}", ex.message)
        
        val errorResponse = createErrorResponseWithDetails(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Persistence Error",
            ex.message ?: "Error persisting data",
            ex.details
        )
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .header("X-Error-Reason", "PERSISTENCE_ERROR")
            .body(errorResponse)
    }
    
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(ex: IllegalStateException): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal state: {}", ex.message)
        
        // Check if this is a lock conflict
        if (ex.message?.contains("lock") == true || ex.message?.contains("Lock") == true) {
            val errorResponse = createErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Resource Conflict",
                "Please try again in a few moments. The resource is currently being modified by another user."
            )
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .header("X-Error-Reason", "RESOURCE_LOCKED")
                .header("Retry-After", "5")
                .body(errorResponse)
        }
        
        // For other illegal states
        val errorResponse = createErrorResponse(
            HttpStatus.CONFLICT.value(),
            "Conflict",
            ex.message ?: "Operation cannot be completed due to a conflict"
        )
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred", ex)
        val errorResponse = createErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred. Please try again later."
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    private fun createErrorResponse(status: Int, error: String, message: String): ErrorResponse {
        return ErrorResponse(
            status = status,
            error = error,
            message = message,
            timestamp = OffsetDateTime.now()
        )
    }
    
    private fun createErrorResponseWithDetails(
        status: Int, 
        error: String, 
        message: String,
        details: Map<String, Any>?
    ): ErrorResponse {
        // Note: This assumes ErrorResponse has a details field
        // If it doesn't, you'll need to modify the OpenAPI spec and regenerate the model
        val response = createErrorResponse(status, error, message)
        
        // Add details if the ErrorResponse class supports it
        // If not, this will need to be modified after updating the ErrorResponse class
        if (details != null) {
            // This is a placeholder - the actual implementation depends on your ErrorResponse structure
            // You may need to modify this after updating your ErrorResponse model
            return response
        }
        
        return response
    }
}
