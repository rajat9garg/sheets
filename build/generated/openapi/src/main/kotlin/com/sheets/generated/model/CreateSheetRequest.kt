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
 * @param name Name of the sheet
 * @param maxRows Maximum number of rows in the sheet
 * @param maxColumns Maximum number of columns in the sheet
 * @param description Description of the sheet
 */
data class CreateSheetRequest(

    @get:JsonProperty("name", required = true) val name: kotlin.String,

    @get:Min(1)
    @get:Max(1000)
    @get:JsonProperty("maxRows", required = true) val maxRows: kotlin.Int = 100,

    @get:Min(1)
    @get:Max(100)
    @get:JsonProperty("maxColumns", required = true) val maxColumns: kotlin.Int = 26,

    @get:JsonProperty("description") val description: kotlin.String? = null
) {

}

