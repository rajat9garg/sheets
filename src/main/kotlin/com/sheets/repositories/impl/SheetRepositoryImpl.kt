package com.sheets.repositories.impl

import com.sheets.models.domain.Sheet
import com.sheets.repositories.SheetRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class SheetRepositoryImpl(
    private val dsl: DSLContext
) : SheetRepository {

    private val logger = LoggerFactory.getLogger(SheetRepositoryImpl::class.java)

    override fun save(sheet: Sheet): Sheet {
        try {
            val record = dsl.insertInto(DSL.table("sheets"))
                .columns(
                    DSL.field("name"),
                    DSL.field("description"),
                    DSL.field("max_length"),
                    DSL.field("max_breadth"),
                    DSL.field("user_id")
                )
                .values(
                    sheet.name,
                    sheet.description,
                    sheet.maxLength,
                    sheet.maxBreadth,
                    sheet.userId
                )
                .returningResult(
                    DSL.field("id"),
                    DSL.field("created_at"),
                    DSL.field("updated_at")
                )
                .fetchOne()

            val id = record?.get(0, Long::class.java) ?: 0
            val createdAt = convertToInstant(record?.get(1))
            val updatedAt = convertToInstant(record?.get(2))

            return sheet.copy(
                id = id,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            logger.error("Error saving sheet: ${e.message}", e)
            throw e
        }
    }

    override fun findById(id: Long): Sheet? {
        try {
            val record = dsl.select()
                .from(DSL.table("sheets"))
                .where(DSL.field("id").eq(id))
                .fetchOne()
                ?: return null

            return Sheet(
                id = record.get("id", Long::class.java),
                name = record.get("name", String::class.java),
                description = record.get("description", String::class.java),
                maxLength = record.get("max_length", Int::class.java),
                maxBreadth = record.get("max_breadth", Int::class.java),
                userId = record.get("user_id", Long::class.java),
                createdAt = convertToInstant(record.get("created_at")),
                updatedAt = convertToInstant(record.get("updated_at"))
            )
        } catch (e: Exception) {
            logger.error("Error finding sheet by ID $id: ${e.message}", e)
            throw e
        }
    }

    override fun findByUserId(userId: Long): List<Sheet> {
        try {
            return dsl.select()
                .from(DSL.table("sheets"))
                .where(DSL.field("user_id").eq(userId))
                .fetch()
                .map { record ->
                    Sheet(
                        id = record.get("id", Long::class.java),
                        name = record.get("name", String::class.java),
                        description = record.get("description", String::class.java),
                        maxLength = record.get("max_length", Int::class.java),
                        maxBreadth = record.get("max_breadth", Int::class.java),
                        userId = record.get("user_id", Long::class.java),
                        createdAt = convertToInstant(record.get("created_at")),
                        updatedAt = convertToInstant(record.get("updated_at"))
                    )
                }
        } catch (e: Exception) {
            logger.error("Error finding sheets by user ID $userId: ${e.message}", e)
            throw e
        }
    }
    
    override fun findByOwnerId(ownerId: Long): List<Sheet> {
        return findByUserId(ownerId)
    }

    override fun findSharedWithUser(userId: Long): List<Sheet> {
        try {
            return dsl.select(DSL.table("sheets").asterisk())
                .from(DSL.table("sheets"))
                .join(DSL.table("access_mappings"))
                .on(DSL.field("sheets.id").eq(DSL.field("access_mappings.sheet_id")))
                .where(DSL.field("access_mappings.user_id").eq(userId))
                .fetch()
                .map { record ->
                    Sheet(
                        id = record.get("id", Long::class.java),
                        name = record.get("name", String::class.java),
                        description = record.get("description", String::class.java),
                        maxLength = record.get("max_length", Int::class.java),
                        maxBreadth = record.get("max_breadth", Int::class.java),
                        userId = record.get("user_id", Long::class.java),
                        createdAt = convertToInstant(record.get("created_at")),
                        updatedAt = convertToInstant(record.get("updated_at"))
                    )
                }
        } catch (e: Exception) {
            logger.error("Error finding sheets shared with user ID $userId: ${e.message}", e)
            throw e
        }
    }

    override fun update(sheet: Sheet): Sheet {
        try {
            dsl.update(DSL.table("sheets"))
                .set(DSL.field("name"), sheet.name)
                .set(DSL.field("description"), sheet.description)
                .set(DSL.field("max_length"), sheet.maxLength)
                .set(DSL.field("max_breadth"), sheet.maxBreadth)
                .where(DSL.field("id").eq(sheet.id))
                .execute()

            return findById(sheet.id) ?: throw NoSuchElementException("Sheet not found with ID: ${sheet.id}")
        } catch (e: Exception) {
            logger.error("Error updating sheet with ID ${sheet.id}: ${e.message}", e)
            throw e
        }
    }

    override fun delete(id: Long) {
        try {
            dsl.deleteFrom(DSL.table("sheets"))
                .where(DSL.field("id").eq(id))
                .execute()
        } catch (e: Exception) {
            logger.error("Error deleting sheet with ID $id: ${e.message}", e)
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
