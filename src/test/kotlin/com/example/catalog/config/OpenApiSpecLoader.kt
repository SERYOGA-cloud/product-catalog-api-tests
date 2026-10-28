package com.example.catalog.config

import com.fasterxml.jackson.databind.JsonNode
import io.restassured.RestAssured.given
import java.io.File
import java.net.URI

/**
 * Resolves and loads the OpenAPI specification from `SWAGGER_URL`.
 *
 * Supports:
 * 1. Direct OpenAPI JSON URL (e.g. `https://.../v3/api-docs` or `https://.../openapi.json`).
 * 2. Swagger UI HTML page URL (e.g. `https://.../swagger-ui/index.html` or `https://.../swagger-ui.html`) —
 *    automatically discovers the underlying OpenAPI specification URL via `swagger-config` or common relative paths.
 * 3. Local file path or `file://` URI (e.g. `docs/openapi.json`).
 */
object OpenApiSpecLoader {

    fun loadOpenApiSpecNode(config: TestConfig = TestConfig.instance): JsonNode {
        val rawSource = config.swaggerUrl.trim()
        val rawContent = when {
            rawSource.startsWith("http://", ignoreCase = true) ||
                rawSource.startsWith("https://", ignoreCase = true) -> {
                fetchFromRemoteUrl(rawSource, config)
            }
            rawSource.startsWith("file://", ignoreCase = true) -> {
                File(URI(rawSource)).readText(Charsets.UTF_8)
            }
            else -> {
                val file = File(rawSource)
                if (file.exists()) {
                    file.readText(Charsets.UTF_8)
                } else {
                    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(rawSource)
                        ?: throw ConfigurationException("OpenAPI specification file not found at '$rawSource'.")
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            }
        }

        return RestAssuredConfig.lenientObjectMapper.readTree(rawContent)
    }

    /**
     * Resolves candidate OpenAPI JSON URLs when given a Swagger UI HTML URL.
     * Does not assume Swagger UI is hosted on the same host as `BASE_URL` — derives candidate paths
     * relative to `swaggerUiUrl` itself.
     */
    fun deriveCandidateSpecUrlsFromSwaggerUi(swaggerUiUrl: String): List<String> {
        val uri = URI(swaggerUiUrl.trim())
        val origin = buildString {
            append(uri.scheme).append("://").append(uri.authority)
        }
        val path = uri.path.orEmpty()
        val baseContext = when {
            path.contains("/swagger-ui/") -> path.substringBefore("/swagger-ui/")
            path.endsWith("/swagger-ui.html") -> path.removeSuffix("/swagger-ui.html")
            else -> path.substringBeforeLast('/', "")
        }

        return listOf(
            "$origin$baseContext/v3/api-docs",
            "$origin$baseContext/v3/api-docs/swagger-config",
            "$origin$baseContext/openapi.json",
            "$origin/v3/api-docs"
        ).distinct()
    }

    private fun fetchFromRemoteUrl(url: String, config: TestConfig): String {
        val response = given()
            .config(RestAssuredConfig.createRestAssuredConfig(config))
            .`when`()
            .get(url)
            .andReturn()

        val body = response.body.asString().trim()
        if (response.statusCode == 200 && body.startsWith("{")) {
            val node = runCatching { RestAssuredConfig.lenientObjectMapper.readTree(body) }.getOrNull()
            if (node != null && (node.has("openapi") || node.has("swagger"))) {
                return body
            }
        }

        // If SWAGGER_URL points to a Swagger UI HTML page, probe candidate OpenAPI JSON endpoints on that host
        for (candidate in deriveCandidateSpecUrlsFromSwaggerUi(url)) {
            val candidateResp = runCatching {
                given()
                    .config(RestAssuredConfig.createRestAssuredConfig(config))
                    .`when`()
                    .get(candidate)
                    .andReturn()
            }.getOrNull() ?: continue

            val candidateBody = candidateResp.body.asString().trim()
            if (candidateResp.statusCode == 200 && candidateBody.startsWith("{")) {
                val node = runCatching { RestAssuredConfig.lenientObjectMapper.readTree(candidateBody) }.getOrNull()
                if (node != null && (node.has("openapi") || node.has("swagger"))) {
                    return candidateBody
                }
            }
        }

        throw ConfigurationException(
            "Failed to load a valid OpenAPI JSON specification from SWAGGER_URL='$url' (HTTP ${response.statusCode})."
        )
    }
}
