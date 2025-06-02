package com.sheets.repositories.impl

import com.sheets.models.domain.AccessMapping
import com.sheets.models.domain.AccessType
import com.sheets.repositories.AccessRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class AccessRepositoryImpl(
    private val dsl: DSLContext
) : AccessRepository {

    private val logger = LoggerFactory.getLogger(AccessRepositoryImpl::class.java)

    override fun save(accessMapping: AccessMapping): AccessMapping {
        try {
            val id = accessMapping.id ?: UUID.randomUUID()
            
            val record = dsl.insertInto(DSL.table("access_mappings"))
                .columns(
                    DSL.field("user_id"),
                    DSL.field("sheet_id"),
                    DSL.field("access_type")
                )
                .values(
                    accessMapping.userId,
                    accessMapping.sheetId,
                    DSL.field("?::access_type", String::class.java, accessMapping.accessType.name)
                )
                .returningResult(
                    DSL.field("id"),
                    DSL.field("created_at"),
                    DSL.field("updated_at")
                )
                .fetchOne()

            val dbId = record?.get(0, Long::class.java)
            val createdAt = convertToInstant(record?.get(1))
            val updatedAt = convertToInstant(record?.get(2))

            return accessMapping.copy(
                id = id,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            logger.error("Error saving access mapping: ${e.message}", e)
            throw e
        }
    }

    override fun findById(id: UUID): AccessMapping? {
        try {
            val record = dsl.select()
                .from(DSL.table("access_mappings"))
                .where(DSL.field("id").eq(id.mostSignificantBits))
                .fetchOne()
                ?: return null

            return AccessMapping(
                id = id,
                userId = record.get("user_id", Long::class.java),
                sheetId = record.get("sheet_id", Long::class.java),
                accessType = AccessType.valueOf(record.get("access_type", String::class.java)),
                createdAt = convertToInstant(record.get("created_at")),
                updatedAt = convertToInstant(record.get("updated_at"))
            )
        } catch (e: Exception) {
            logger.error("Error finding access mapping by ID $id: ${e.message}", e)
            throw e
        }
    }

    override fun findBySheetIdAndUserId(sheetId: Long, userId: Long): AccessMapping? {
        try {
            val record = dsl.select()
                .from(DSL.table("access_mappings"))
                .where(DSL.field("sheet_id").eq(sheetId))
                .and(DSL.field("user_id").eq(userId))
                .fetchOne()
                ?: return null

            return AccessMapping(
                id = UUID.randomUUID(), // Generate a new UUID since we don't store UUIDs in the database
                userId = record.get("user_id", Long::class.java),
                sheetId = record.get("sheet_id", Long::class.java),
                accessType = AccessType.valueOf(record.get("access_type", String::class.java)),
                createdAt = convertToInstant(record.get("created_at")),
                updatedAt = convertToInstant(record.get("updated_at"))
            )
        } catch (e: Exception) {
            logger.error("Error finding access mapping by sheet ID $sheetId and user ID $userId: ${e.message}", e)
            throw e
        }
    }

    override fun findBySheetId(sheetId: Long): List<AccessMapping> {
        try {
            return dsl.select()
                .from(DSL.table("access_mappings"))
                .where(DSL.field("sheet_id").eq(sheetId))
                .fetch()
                .map { record ->
                    AccessMapping(
                        id = UUID.randomUUID(), // Generate a new UUID since we don't store UUIDs in the database
                        userId = record.get("user_id", Long::class.java),
                        sheetId = record.get("sheet_id", Long::class.java),
                        accessType = AccessType.valueOf(record.get("access_type", String::class.java)),
                        createdAt = convertToInstant(record.get("created_at")),
                        updatedAt = convertToInstant(record.get("updated_at"))
                    )
                }
        } catch (e: Exception) {
            logger.error("Error finding access mappings by sheet ID $sheetId: ${e.message}", e)
            throw e
        }
    }

    override fun findByUserId(userId: Long): List<AccessMapping> {
        try {
            return dsl.select()
                .from(DSL.table("access_mappings"))
                .where(DSL.field("user_id").eq(userId))
                .fetch()
                .map { record ->
                    AccessMapping(
                        id = UUID.randomUUID(), // Generate a new UUID since we don't store UUIDs in the database
                        userId = record.get("user_id", Long::class.java),
                        sheetId = record.get("sheet_id", Long::class.java),
                        accessType = AccessType.valueOf(record.get("access_type", String::class.java)),
                        createdAt = convertToInstant(record.get("created_at")),
                        updatedAt = convertToInstant(record.get("updated_at"))
                    )
                }
        } catch (e: Exception) {
            logger.error("Error finding access mappings by user ID $userId: ${e.message}", e)
            throw e
        }
    }

    override fun findByUserIdAndAccessType(userId: Long, accessType: AccessType): List<AccessMapping> {
        try {
            return dsl.select()
                .from(DSL.table("access_mappings"))
                .where(DSL.field("user_id").eq(userId))
                .and(DSL.field("access_type").eq(DSL.field("?::access_type", String::class.java, accessType.name)))
                .fetch()
                .map { record ->
                    AccessMapping(
                        id = UUID.randomUUID(), // Generate a new UUID since we don't store UUIDs in the database
                        userId = record.get("user_id", Long::class.java),
                        sheetId = record.get("sheet_id", Long::class.java),
                        accessType = AccessType.valueOf(record.get("access_type", String::class.java)),
                        createdAt = convertToInstant(record.get("created_at")),
                        updatedAt = convertToInstant(record.get("updated_at"))
                    )
                }
        } catch (e: Exception) {
            logger.error("Error finding access mappings by user ID $userId and access type $accessType: ${e.message}", e)
            throw e
        }
    }

    override fun findByUserIdAndAccessTypeNot(userId: Long, accessType: AccessType): List<AccessMapping> {
        try {
            return dsl.select()
                .from(DSL.table("access_mappings"))
                .where(DSL.field("user_id").eq(userId))
                .and(DSL.field("access_type").ne(DSL.field("?::access_type", String::class.java, accessType.name)))
                .fetch()
                .map { record ->
                    AccessMapping(
                        id = UUID.randomUUID(), // Generate a new UUID since we don't store UUIDs in the database
                        userId = record.get("user_id", Long::class.java),
                        sheetId = record.get("sheet_id", Long::class.java),
                        accessType = AccessType.valueOf(record.get("access_type", String::class.java)),
                        createdAt = convertToInstant(record.get("created_at")),
                        updatedAt = convertToInstant(record.get("updated_at"))
                    )
                }
        } catch (e: Exception) {
            logger.error("Error finding access mappings by user ID $userId and access type not $accessType: ${e.message}", e)
            throw e
        }
    }

    override fun update(accessMapping: AccessMapping): AccessMapping {
        try {
            // We don't use the UUID here, we find by sheet_id and user_id
            dsl.update(DSL.table("access_mappings"))
                .set(DSL.field("access_type"), DSL.field("?::access_type", String::class.java, accessMapping.accessType.name))
                .where(DSL.field("sheet_id").eq(accessMapping.sheetId))
                .and(DSL.field("user_id").eq(accessMapping.userId))
                .execute()

            // Return the updated mapping
            return findBySheetIdAndUserId(accessMapping.sheetId, accessMapping.userId) 
                ?: throw NoSuchElementException("Access mapping not found for sheet ID: ${accessMapping.sheetId} and user ID: ${accessMapping.userId}")
        } catch (e: Exception) {
            logger.error("Error updating access mapping for sheet ID ${accessMapping.sheetId} and user ID ${accessMapping.userId}: ${e.message}", e)
            throw e
        }
    }

    override fun upsert(accessMapping: AccessMapping) {
        try {
            val existing = findBySheetIdAndUserId(accessMapping.sheetId, accessMapping.userId)
            
            if (existing != null) {
                update(accessMapping.copy(id = existing.id))
            } else {
                save(accessMapping)
            }
        } catch (e: Exception) {
            logger.error("Error upserting access mapping: ${e.message}", e)
            throw e
        }
    }

    override fun delete(id: UUID) {
        try {
            // Since we don't use UUIDs in the database, we can't delete by UUID
            // This is a placeholder implementation
            logger.warn("Delete by UUID is not supported in this implementation")
        } catch (e: Exception) {
            logger.error("Error deleting access mapping with ID $id: ${e.message}", e)
            throw e
        }
    }

    override fun deleteBySheetIdAndUserId(sheetId: Long, userId: Long) {
        try {
            dsl.deleteFrom(DSL.table("access_mappings"))
                .where(DSL.field("sheet_id").eq(sheetId))
                .and(DSL.field("user_id").eq(userId))
                .execute()
        } catch (e: Exception) {
            logger.error("Error deleting access mapping by sheet ID $sheetId and user ID $userId: ${e.message}", e)
            throw e
        }
    }
    
    private fun convertToInstant(value: Any?): Instant {
        return when (value) {
            is Timestamp -> value.toInstant()
            is LocalDateTime -> value.toInstant(ZoneOffset.UTC)
            is java.time.OffsetDateTime -> value.toInstant()
            else -> Instant.now()
        }
    }
}
