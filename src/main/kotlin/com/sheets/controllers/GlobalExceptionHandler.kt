package com.sheets.controllers

import com.sheets.generated.model.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.OffsetDateTime

@ControllerAdvice
class GlobalExceptionHandler {

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

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        val errorResponse = createErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred"
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
}
