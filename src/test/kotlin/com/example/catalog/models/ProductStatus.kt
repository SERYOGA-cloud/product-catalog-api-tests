package com.example.catalog.models

import com.fasterxml.jackson.annotation.JsonProperty

enum class ProductStatus {
    @JsonProperty("ACTIVE")
    ACTIVE,

    @JsonProperty("INACTIVE")
    INACTIVE
}
