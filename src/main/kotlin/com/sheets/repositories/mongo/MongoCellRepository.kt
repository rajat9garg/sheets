package com.sheets.repositories.mongo

import com.sheets.mappers.CellMapper
import com.sheets.models.domain.Cell
import com.sheets.models.document.CellDocument
import com.sheets.repositories.CellRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * MongoDB implementation of the CellRepository interface
 */
@Repository
class MongoCellRepository(
    private val mongoTemplate: MongoTemplate,
    private val cellMapper: CellMapper
) : CellRepository {

    override fun findById(id: String): Cell? {
        val document = mongoTemplate.findById(id, CellDocument::class.java)
        return document?.let { cellMapper.toDomain(it) }
    }

    override fun findBySheetId(sheetId: Long): List<Cell> {
        val query = Query(Criteria.where("sheetId").`is`(sheetId))
        val documents = mongoTemplate.find(query, CellDocument::class.java)
        return documents.map { cellMapper.toDomain(it) }
    }

    override fun save(cell: Cell): Cell {
        val document = cellMapper.toDocument(cell)
        val savedDocument = mongoTemplate.save(document)
        return cellMapper.toDomain(savedDocument)
    }

    override fun saveAll(cells: List<Cell>): List<Cell> {
        val documents = cells.map { cellMapper.toDocument(it) }
        val savedDocuments = documents.map { mongoTemplate.save(it) }
        return savedDocuments.map { cellMapper.toDomain(it) }
    }

    override fun deleteById(id: String) {
        val query = Query(Criteria.where("_id").`is`(id))
        mongoTemplate.remove(query, CellDocument::class.java)
    }

    override fun deleteBySheetId(sheetId: Long) {
        val query = Query(Criteria.where("sheetId").`is`(sheetId))
        mongoTemplate.remove(query, CellDocument::class.java)
    }
}
