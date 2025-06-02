# Task 2.6: Sheet Controller

## Overview
This task involves implementing the Sheet Controller that will handle HTTP requests for sheet operations. Following the OpenAPI-first approach, the controller will implement the interfaces generated from the OpenAPI specification.

## Implementation Steps

### 1. Create SheetController
Create `src/main/kotlin/com/sheets/controllers/SheetController.kt`:

```kotlin
package com.sheets.controllers

import com.sheets.generated.api.SheetApi
import com.sheets.generated.model.*
import com.sheets.mappers.SheetMapper
import com.sheets.models.domain.AccessType
import com.sheets.services.SheetService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RestController
class SheetController(
    private val sheetService: SheetService,
    private val sheetMapper: SheetMapper
) : SheetApi {

    override fun createSheet(
        xUserId: Long,
        createSheetRequest: CreateSheetRequest
    ): ResponseEntity<SheetResponse> {
        val sheet = sheetService.createSheet(
            name = createSheetRequest.name,
            description = createSheetRequest.description ?: "",
            userId = xUserId
        )
        
        val response = sheetMapper.toSheetResponse(sheet)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    override fun getSheets(xUserId: Long): ResponseEntity<List<SheetSummaryResponse>> {
        val sheets = sheetService.getSheetsByUserId(xUserId)
        val response = sheets.map { sheetMapper.toSheetSummaryResponse(it) }
        return ResponseEntity.ok(response)
    }

    override fun getSheetDetails(
        xUserId: Long,
        sheetId: Long
    ): ResponseEntity<SheetDetailsResponse> {
        return try {
            val sheet = sheetService.getSheetById(sheetId, xUserId)
            val response = sheetMapper.toSheetDetailsResponse(sheet)
            ResponseEntity.ok(response)
        } catch (e: NoSuchElementException) {
            val errorResponse = ErrorResponse(
                status = 404,
                error = "Not Found",
                message = e.message ?: "Sheet not found",
                timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            )
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse) as ResponseEntity<SheetDetailsResponse>
        } catch (e: SecurityException) {
            val errorResponse = ErrorResponse(
                status = 403,
                error = "Forbidden",
                message = e.message ?: "Access denied",
                timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            )
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse) as ResponseEntity<SheetDetailsResponse>
        } catch (e: Exception) {
            val errorResponse = ErrorResponse(
                status = 500,
                error = "Internal Server Error",
                message = "An unexpected error occurred",
                timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse) as ResponseEntity<SheetDetailsResponse>
        }
    }

    override fun shareSheet(
        xUserId: Long,
        sheetId: Long,
        shareSheetRequest: ShareSheetRequest
    ): ResponseEntity<ShareSheetResponse> {
        return try {
            val accessType = when (shareSheetRequest.accessType) {
                ShareSheetRequest.AccessType.READ -> AccessType.READ
                ShareSheetRequest.AccessType.WRITE -> AccessType.WRITE
                ShareSheetRequest.AccessType.ADMIN -> AccessType.ADMIN
                else -> throw IllegalArgumentException("Invalid access type")
            }
            
            val message = sheetService.shareSheet(
                sheetId = sheetId,
                userIds = shareSheetRequest.userIds,
                accessType = accessType,
                ownerId = xUserId
            )
            
            val response = ShareSheetResponse(message = message)
            ResponseEntity.ok(response)
        } catch (e: NoSuchElementException) {
            val errorResponse = ErrorResponse(
                status = 404,
                error = "Not Found",
                message = e.message ?: "Sheet not found",
                timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            )
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse) as ResponseEntity<ShareSheetResponse>
        } catch (e: SecurityException) {
            val errorResponse = ErrorResponse(
                status = 403,
                error = "Forbidden",
                message = e.message ?: "Access denied",
                timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            )
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse) as ResponseEntity<ShareSheetResponse>
        } catch (e: IllegalArgumentException) {
            val errorResponse = ErrorResponse(
                status = 400,
                error = "Bad Request",
                message = e.message ?: "Invalid request parameters",
                timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            )
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse) as ResponseEntity<ShareSheetResponse>
        } catch (e: Exception) {
            val errorResponse = ErrorResponse(
                status = 500,
                error = "Internal Server Error",
                message = "An unexpected error occurred",
                timestamp = OffsetDateTime.now(ZoneOffset.UTC)
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse) as ResponseEntity<ShareSheetResponse>
        }
    }
}
```

### 2. Create Error Handler
Create `src/main/kotlin/com/sheets/controllers/GlobalExceptionHandler.kt`:

```kotlin
package com.sheets.controllers

import com.sheets.generated.model.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFoundException(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = 404,
            error = "Not Found",
            message = ex.message ?: "Resource not found",
            timestamp = OffsetDateTime.now(ZoneOffset.UTC)
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    @ExceptionHandler(SecurityException::class)
    fun handleSecurityException(ex: SecurityException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = 403,
            error = "Forbidden",
            message = ex.message ?: "Access denied",
            timestamp = OffsetDateTime.now(ZoneOffset.UTC)
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = 400,
            error = "Bad Request",
            message = ex.message ?: "Invalid request parameters",
            timestamp = OffsetDateTime.now(ZoneOffset.UTC)
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = 500,
            error = "Internal Server Error",
            message = "An unexpected error occurred",
            timestamp = OffsetDateTime.now(ZoneOffset.UTC)
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}
```

### 3. Create Controller Test
Create `src/test/kotlin/com/sheets/controllers/SheetControllerTest.kt`:

```kotlin
package com.sheets.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.sheets.generated.model.CreateSheetRequest
import com.sheets.generated.model.ShareSheetRequest
import com.sheets.mappers.SheetMapper
import com.sheets.models.domain.AccessType
import com.sheets.models.domain.Sheet
import com.sheets.services.SheetService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant

@WebMvcTest(SheetController::class)
class SheetControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var sheetService: SheetService

    @MockBean
    private lateinit var sheetMapper: SheetMapper

    @Test
    fun `createSheet should return 201 Created`() {
        // Given
        val userId = 1L
        val request = CreateSheetRequest(name = "Test Sheet", description = "Test Description")
        val sheet = Sheet(
            id = 1L,
            name = "Test Sheet",
            description = "Test Description",
            userId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        // Mock service and mapper
        `when`(sheetService.createSheet("Test Sheet", "Test Description", userId)).thenReturn(sheet)
        
        // When & Then
        mockMvc.perform(
            post("/api/v1/sheet")
                .header("X-User-ID", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `getSheets should return 200 OK`() {
        // Given
        val userId = 1L
        
        // When & Then
        mockMvc.perform(
            get("/api/v1/sheet")
                .header("X-User-ID", userId)
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `getSheetDetails should return 200 OK when sheet exists`() {
        // Given
        val userId = 1L
        val sheetId = 1L
        val sheet = Sheet(
            id = sheetId,
            name = "Test Sheet",
            description = "Test Description",
            userId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        // Mock service
        `when`(sheetService.getSheetById(sheetId, userId)).thenReturn(sheet)
        
        // When & Then
        mockMvc.perform(
            get("/api/v1/sheet/$sheetId")
                .header("X-User-ID", userId)
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `getSheetDetails should return 404 Not Found when sheet does not exist`() {
        // Given
        val userId = 1L
        val sheetId = 999L
        
        // Mock service
        `when`(sheetService.getSheetById(sheetId, userId)).thenThrow(NoSuchElementException("Sheet not found"))
        
        // When & Then
        mockMvc.perform(
            get("/api/v1/sheet/$sheetId")
                .header("X-User-ID", userId)
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `shareSheet should return 200 OK when successful`() {
        // Given
        val userId = 1L
        val sheetId = 1L
        val request = ShareSheetRequest(
            userIds = listOf(2L, 3L),
            accessType = ShareSheetRequest.AccessType.READ
        )
        
        // Mock service
        `when`(sheetService.shareSheet(sheetId, listOf(2L, 3L), AccessType.READ, userId))
            .thenReturn("Sheet shared successfully with 2 users")
        
        // When & Then
        mockMvc.perform(
            post("/api/v1/sheet/share/$sheetId")
                .header("X-User-ID", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
    }
}
```

## Testing
2. Test all endpoints with valid and invalid inputs
3. Verify error handling for various scenarios
4. Test with different user IDs and access types

## Completion Criteria
- SheetController implements all methods from the generated SheetApi interface
- GlobalExceptionHandler is implemented for proper error handling
- All controller methods return appropriate HTTP status codes
- Controller tests pass for all scenarios
- Code follows best practices and is well-documented
