package com.sheets.generated.model

import java.util.Objects
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import jakarta.validation.Valid

/**
 * 
 * @param id Unique identifier for the cell (format sheetId:row:column)
 * @param sheetId ID of the sheet containing the cell
 * @param row Row index of the cell
 * @param column Column index of the cell
 * @param &#x60;data&#x60; Raw cell content (primitive value or expression)
 * @param dataType Type of data in the cell
 * @param evaluatedValue Evaluated value of the cell
 * @param createdAt Time when the cell was created
 * @param updatedAt Time when the cell was last updated
 */
data class CellResponse(

    @get:JsonProperty("id") val id: kotlin.String? = null,

    @get:JsonProperty("sheetId") val sheetId: kotlin.Long? = null,

    @get:JsonProperty("row") val row: kotlin.Int? = null,

    @get:JsonProperty("column") val column: kotlin.Int? = null,

    @get:JsonProperty("data") val `data`: kotlin.String? = null,

    @get:JsonProperty("dataType") val dataType: CellResponse.DataType? = null,

    @get:JsonProperty("evaluatedValue") val evaluatedValue: kotlin.String? = null,

    @get:JsonProperty("createdAt") val createdAt: java.time.OffsetDateTime? = null,

    @get:JsonProperty("updatedAt") val updatedAt: java.time.OffsetDateTime? = null
) {

    /**
    * Type of data in the cell
    * Values: PRIMITIVE,EXPRESSION
    */
    enum class DataType(val value: kotlin.String) {

        @JsonProperty("PRIMITIVE") PRIMITIVE("PRIMITIVE"),
        @JsonProperty("EXPRESSION") EXPRESSION("EXPRESSION")
    }

}

