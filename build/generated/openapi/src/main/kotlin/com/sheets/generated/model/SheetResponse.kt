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
 * @param id Unique identifier for the sheet
 * @param name Name of the sheet
 * @param description Description of the sheet
 * @param maxLength Maximum number of rows
 * @param maxBreadth Maximum number of columns
 * @param userId ID of the user who owns the sheet
 * @param createdAt Time when the sheet was created
 * @param updatedAt Time when the sheet was last updated
 */
data class SheetResponse(

    @get:JsonProperty("id") val id: kotlin.Long? = null,

    @get:JsonProperty("name") val name: kotlin.String? = null,

    @get:JsonProperty("description") val description: kotlin.String? = null,

    @get:JsonProperty("maxLength") val maxLength: kotlin.Int? = null,

    @get:JsonProperty("maxBreadth") val maxBreadth: kotlin.Int? = null,

    @get:JsonProperty("userId") val userId: kotlin.Long? = null,

    @get:JsonProperty("createdAt") val createdAt: java.time.OffsetDateTime? = null,

    @get:JsonProperty("updatedAt") val updatedAt: java.time.OffsetDateTime? = null
) {

}

