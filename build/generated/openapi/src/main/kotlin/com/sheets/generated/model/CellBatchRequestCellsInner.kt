package com.sheets.generated.model

import java.util.Objects
import com.fasterxml.jackson.annotation.JsonProperty
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
 * @param row Row index of the cell
 * @param column Column index of the cell
 * @param &#x60;data&#x60; Cell content (primitive value or expression)
 */
data class CellBatchRequestCellsInner(

    @get:JsonProperty("row", required = true) val row: kotlin.Int,

    @get:JsonProperty("column", required = true) val column: kotlin.Int,

    @get:JsonProperty("data", required = true) val `data`: kotlin.String
) {

}

