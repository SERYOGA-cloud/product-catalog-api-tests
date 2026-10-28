package com.example.catalog.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTO corresponding strictly to `#/components/schemas/Product` in `docs/openapi.json`.
 * Only fields documented in the OpenAPI contract are declared.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
data class Product(
    @JsonProperty("id")
    val id: Long? = null,

    @JsonProperty("name_ru")
    val nameRu: String? = null,

    @JsonProperty("name_kk")
    val nameKk: String? = null,

    @JsonProperty("gtin")
    val gtin: String? = null,

    @JsonProperty("ntin_code")
    val ntinCode: String? = null,

    @JsonProperty("is_social")
    val isSocial: Boolean? = null,

    @JsonProperty("modified")
    val modified: Long? = null,

    @JsonProperty("status")
    val status: ProductStatus? = null,

    @JsonProperty("is_markedeac")
    val isMarkedEac: Boolean? = null,

    @JsonProperty("measure")
    val measure: Measure? = null,

    @JsonProperty("ntin_isdeactivated")
    val ntinIsDeactivated: Boolean? = null,

    @JsonProperty("ntin_deactivationreason")
    val ntinDeactivationReason: String? = null,

    @JsonProperty("ntin_duplicateofproduct")
    val ntinDuplicateOfProduct: String? = null
)
