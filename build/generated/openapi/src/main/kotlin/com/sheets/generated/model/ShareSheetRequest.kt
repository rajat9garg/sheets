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
 * @param userIds IDs of users to share with
 * @param accessType Type of access to grant
 */
data class ShareSheetRequest(

    @get:JsonProperty("userIds", required = true) val userIds: kotlin.collections.List<kotlin.Long>,

    @get:JsonProperty("accessType", required = true) val accessType: ShareSheetRequest.AccessType
) {

    /**
    * Type of access to grant
    * Values: READ,WRITE,ADMIN
    */
    enum class AccessType(val value: kotlin.String) {

        @JsonProperty("READ") READ("READ"),
        @JsonProperty("WRITE") WRITE("WRITE"),
        @JsonProperty("ADMIN") ADMIN("ADMIN")
    }

}

