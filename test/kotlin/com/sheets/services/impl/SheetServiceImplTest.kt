package com.sheets.services.impl

import com.sheets.models.domain.AccessMapping
import com.sheets.models.domain.AccessType
import com.sheets.models.domain.Sheet
import com.sheets.models.dto.SheetAndAccessType
import com.sheets.repositories.AccessRepository
import com.sheets.repositories.SheetRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.NoSuchElementException

class SheetServiceImplTest {

    @MockK
    private lateinit var sheetRepository: SheetRepository

    @MockK
    private lateinit var accessRepository: AccessRepository

    @InjectMockKs
    private lateinit var sheetService: SheetServiceImpl

    private val testUserId = 1L
    private val testSheetId = 1L
    private val timestamp = Instant.now()

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `test createSheet creates and returns a new sheet`() {
        // Given
        val name = "Test Sheet"
        val description = "Test Description"
        val maxRows = 100
        val maxColumns = 26
        
        val expectedSheet = Sheet(
            id = testSheetId,
            name = name,
            description = description,
            maxLength = maxRows,
            maxBreadth = maxColumns,
            userId = testUserId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        every { sheetRepository.save(any()) } returns expectedSheet.copy(id = testSheetId)
        
        // When
        val result = sheetService.createSheet(name, description, maxRows, maxColumns, testUserId)
        
        // Then
        verify(exactly = 1) { sheetRepository.save(any()) }
        assertEquals(testSheetId, result.id)
        assertEquals(name, result.name)
        assertEquals(description, result.description)
        assertEquals(maxRows, result.maxLength)
        assertEquals(maxColumns, result.maxBreadth)
        assertEquals(testUserId, result.userId)
    }
    
    @Test
    fun `test createSheet with null description uses empty string`() {
        // Given
        val name = "Test Sheet"
        val maxRows = 100
        val maxColumns = 26
        
        val expectedSheet = Sheet(
            id = testSheetId,
            name = name,
            description = "",
            maxLength = maxRows,
            maxBreadth = maxColumns,
            userId = testUserId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        every { sheetRepository.save(any()) } returns expectedSheet.copy(id = testSheetId)
        
        // When
        val result = sheetService.createSheet(name, null, maxRows, maxColumns, testUserId)
        
        // Then
        verify(exactly = 1) { sheetRepository.save(any()) }
        assertEquals("", result.description)
    }
    
    @Test
    fun `test getSheetsByUserId returns owned and shared sheets`() {
        // Given
        val ownedSheet1 = Sheet(
            id = 1L,
            name = "Owned Sheet 1",
            description = "Description 1",
            maxLength = 100,
            maxBreadth = 26,
            userId = testUserId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val ownedSheet2 = Sheet(
            id = 2L,
            name = "Owned Sheet 2",
            description = "Description 2",
            maxLength = 100,
            maxBreadth = 26,
            userId = testUserId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val sharedSheet = Sheet(
            id = 3L,
            name = "Shared Sheet",
            description = "Shared Description",
            maxLength = 100,
            maxBreadth = 26,
            userId = 2L,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val accessMapping = AccessMapping(
            sheetId = 3L,
            userId = testUserId,
            accessType = AccessType.READ,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        every { sheetRepository.findByOwnerId(testUserId) } returns listOf(ownedSheet1, ownedSheet2)
        every { accessRepository.findByUserIdAndAccessTypeNot(testUserId, AccessType.OWNER) } returns listOf(accessMapping)
        every { sheetRepository.findById(3L) } returns sharedSheet
        
        // When
        val result = sheetService.getSheetsByUserId(testUserId)
        
        // Then
        assertEquals(3, result.size)
        
        // Verify owned sheets have OWNER access type
        val ownedResults = result.filter { it.sheet.userId == testUserId }
        assertEquals(2, ownedResults.size)
        ownedResults.forEach { assertEquals(AccessType.OWNER, it.accessType) }
        
        // Verify shared sheet has correct access type
        val sharedResults = result.filter { it.sheet.userId != testUserId }
        assertEquals(1, sharedResults.size)
        assertEquals(AccessType.READ, sharedResults[0].accessType)
    }
    
    @Test
    fun `test getSheetById returns sheet with owner access when user is owner`() {
        // Given
        val sheet = Sheet(
            id = testSheetId,
            name = "Test Sheet",
            description = "Description",
            maxLength = 100,
            maxBreadth = 26,
            userId = testUserId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        every { sheetRepository.findById(testSheetId) } returns sheet
        
        // When
        val result = sheetService.getSheetById(testSheetId, testUserId)
        
        // Then
        assertEquals(sheet, result.sheet)
        assertEquals(AccessType.OWNER, result.accessType)
    }
    
    @Test
    fun `test getSheetById returns sheet with correct access type when user has access`() {
        // Given
        val ownerId = 2L
        val sheet = Sheet(
            id = testSheetId,
            name = "Test Sheet",
            description = "Description",
            maxLength = 100,
            maxBreadth = 26,
            userId = ownerId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val accessMapping = AccessMapping(
            sheetId = testSheetId,
            userId = testUserId,
            accessType = AccessType.WRITE,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        every { sheetRepository.findById(testSheetId) } returns sheet
        every { accessRepository.findBySheetIdAndUserId(testSheetId, testUserId) } returns accessMapping
        
        // When
        val result = sheetService.getSheetById(testSheetId, testUserId)
        
        // Then
        assertEquals(sheet, result.sheet)
        assertEquals(AccessType.WRITE, result.accessType)
    }
    
    @Test
    fun `test getSheetById throws NoSuchElementException when sheet not found`() {
        // Given
        every { sheetRepository.findById(testSheetId) } returns null
        
        // When/Then
        assertThrows<NoSuchElementException> {
            sheetService.getSheetById(testSheetId, testUserId)
        }
    }
    
    @Test
    fun `test getSheetById throws SecurityException when user does not have access`() {
        // Given
        val ownerId = 2L
        val sheet = Sheet(
            id = testSheetId,
            name = "Test Sheet",
            description = "Description",
            maxLength = 100,
            maxBreadth = 26,
            userId = ownerId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        every { sheetRepository.findById(testSheetId) } returns sheet
        every { accessRepository.findBySheetIdAndUserId(testSheetId, testUserId) } returns null
        
        // When/Then
        assertThrows<SecurityException> {
            sheetService.getSheetById(testSheetId, testUserId)
        }
    }
    
    @Test
    fun `test shareSheet shares sheet with multiple users`() {
        // Given
        val sheet = Sheet(
            id = testSheetId,
            name = "Test Sheet",
            description = "Description",
            maxLength = 100,
            maxBreadth = 26,
            userId = testUserId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val userIds = listOf(2L, 3L)
        val accessType = AccessType.READ
        
        every { sheetRepository.findById(testSheetId) } returns sheet
        every { accessRepository.upsert(any()) } just runs
        
        // When
        val result = sheetService.shareSheet(testSheetId, userIds, accessType, testUserId)
        
        // Then
        verify(exactly = 2) { accessRepository.upsert(any()) }
        assertTrue(result.contains("Sheet shared successfully with 2 users"))
    }
    
    @Test
    fun `test shareSheet filters out owner from users to share with`() {
        // Given
        val sheet = Sheet(
            id = testSheetId,
            name = "Test Sheet",
            description = "Description",
            maxLength = 100,
            maxBreadth = 26,
            userId = testUserId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val userIds = listOf(testUserId, 2L)
        val accessType = AccessType.READ
        
        every { sheetRepository.findById(testSheetId) } returns sheet
        every { accessRepository.upsert(any()) } just runs
        
        // When
        val result = sheetService.shareSheet(testSheetId, userIds, accessType, testUserId)
        
        // Then
        verify(exactly = 1) { accessRepository.upsert(any()) }
        assertTrue(result.contains("Sheet shared successfully with 1 users"))
        assertTrue(result.contains("Owner's access"))
    }
    
    @Test
    fun `test shareSheet throws NoSuchElementException when sheet not found`() {
        // Given
        val userIds = listOf(2L, 3L)
        val accessType = AccessType.READ
        
        every { sheetRepository.findById(testSheetId) } returns null
        
        // When/Then
        assertThrows<NoSuchElementException> {
            sheetService.shareSheet(testSheetId, userIds, accessType, testUserId)
        }
    }
    
    @Test
    fun `test shareSheet throws SecurityException when user is not owner`() {
        // Given
        val ownerId = 2L
        val sheet = Sheet(
            id = testSheetId,
            name = "Test Sheet",
            description = "Description",
            maxLength = 100,
            maxBreadth = 26,
            userId = ownerId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val userIds = listOf(3L, 4L)
        val accessType = AccessType.READ
        
        every { sheetRepository.findById(testSheetId) } returns sheet
        
        // When/Then
        assertThrows<SecurityException> {
            sheetService.shareSheet(testSheetId, userIds, accessType, testUserId)
        }
    }
    
    @Test
    fun `test shareSheet returns message when no users provided`() {
        // Given
        val sheet = Sheet(
            id = testSheetId,
            name = "Test Sheet",
            description = "Description",
            maxLength = 100,
            maxBreadth = 26,
            userId = testUserId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val userIds = emptyList<Long>()
        val accessType = AccessType.READ
        
        every { sheetRepository.findById(testSheetId) } returns sheet
        
        // When
        val result = sheetService.shareSheet(testSheetId, userIds, accessType, testUserId)
        
        // Then
        verify(exactly = 0) { accessRepository.upsert(any()) }
        assertEquals("No users provided to share the sheet with.", result)
    }
}
