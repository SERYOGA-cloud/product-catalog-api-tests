package com.example.catalog.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTO corresponding strictly to `#/components/schemas/Measure` in `docs/openapi.json`.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
data class Measure(
    @JsonProperty("id")
    val id: Long? = null,

    @JsonProperty("name")
    val name: String? = null,

    @JsonProperty("short_name")
    val shortName: String? = null,

    @JsonProperty("code")
    val code: String? = null
)
