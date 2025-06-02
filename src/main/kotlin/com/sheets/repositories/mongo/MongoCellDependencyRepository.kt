package com.sheets.repositories.mongo

import com.sheets.mappers.CellDependencyMapper
import com.sheets.models.document.CellDependencyDocument
import com.sheets.models.domain.CellDependency
import com.sheets.repositories.CellDependencyRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * MongoDB implementation of the CellDependencyRepository interface
 */
@Repository
class MongoCellDependencyRepository(
    private val mongoTemplate: MongoTemplate,
    private val cellDependencyMapper: CellDependencyMapper
) : CellDependencyRepository {

    override fun findById(id: String): CellDependency? {
        val document = mongoTemplate.findById(id, CellDependencyDocument::class.java)
        return document?.let { cellDependencyMapper.toDomain(it) }
    }

    override fun findBySourceCellId(sourceCellId: String): List<CellDependency> {
        val query = Query(Criteria.where("sourceCellId").`is`(sourceCellId))
        val documents = mongoTemplate.find(query, CellDependencyDocument::class.java)
        return documents.map { cellDependencyMapper.toDomain(it) }
    }

    override fun findByTargetCellId(targetCellId: String): List<CellDependency> {
        val query = Query(Criteria.where("targetCellId").`is`(targetCellId))
        val documents = mongoTemplate.find(query, CellDependencyDocument::class.java)
        return documents.map { cellDependencyMapper.toDomain(it) }
    }

    override fun findBySourceCellIdAndTargetCellId(sourceCellId: String, targetCellId: String): CellDependency? {
        val query = Query(
            Criteria.where("sourceCellId").`is`(sourceCellId)
                .and("targetCellId").`is`(targetCellId)
        )
        val document = mongoTemplate.findOne(query, CellDependencyDocument::class.java)
        return document?.let { cellDependencyMapper.toDomain(it) }
    }

    override fun findBySheetId(sheetId: Long): List<CellDependency> {
        val query = Query(Criteria.where("sheetId").`is`(sheetId))
        val documents = mongoTemplate.find(query, CellDependencyDocument::class.java)
        return documents.map { cellDependencyMapper.toDomain(it) }
    }

    override fun save(dependency: CellDependency): CellDependency {
        val document = cellDependencyMapper.toDocument(dependency)
        // Generate ID if not present
        val documentWithId = if (document.id.isBlank()) {
            document.copy(id = UUID.randomUUID().toString())
        } else {
            document
        }
        val savedDocument = mongoTemplate.save(documentWithId)
        return cellDependencyMapper.toDomain(savedDocument)
    }

    override fun saveAll(dependencies: List<CellDependency>): List<CellDependency> {
        val documents = dependencies.map { 
            val document = cellDependencyMapper.toDocument(it)
            // Generate ID if not present
            if (document.id.isBlank()) {
                document.copy(id = UUID.randomUUID().toString())
            } else {
                document
            }
        }
        val savedDocuments = documents.map { mongoTemplate.save(it) }
        return savedDocuments.map { cellDependencyMapper.toDomain(it) }
    }

    override fun deleteById(id: String) {
        val query = Query(Criteria.where("_id").`is`(id))
        mongoTemplate.remove(query, CellDependencyDocument::class.java)
    }

    override fun deleteBySourceCellId(sourceCellId: String): Int {
        val query = Query(Criteria.where("sourceCellId").`is`(sourceCellId))
        val result = mongoTemplate.remove(query, CellDependencyDocument::class.java)
        return result.deletedCount.toInt()
    }

    override fun deleteByTargetCellId(targetCellId: String): Int {
        val query = Query(Criteria.where("targetCellId").`is`(targetCellId))
        val result = mongoTemplate.remove(query, CellDependencyDocument::class.java)
        return result.deletedCount.toInt()
    }

    override fun deleteBySheetId(sheetId: Long): Int {
        val query = Query(Criteria.where("sheetId").`is`(sheetId))
        val result = mongoTemplate.remove(query, CellDependencyDocument::class.java)
        return result.deletedCount.toInt()
    }
}
