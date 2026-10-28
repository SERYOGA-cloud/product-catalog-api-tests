package com.example.catalog.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.qameta.allure.Allure
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.HttpClientConfig
import io.restassured.config.LogConfig
import io.restassured.config.ObjectMapperConfig
import io.restassured.config.RestAssuredConfig as RaConfig
import io.restassured.filter.Filter
import io.restassured.filter.FilterContext
import io.restassured.http.ContentType
import io.restassured.response.Response
import io.restassured.specification.FilterableRequestSpecification
import io.restassured.specification.FilterableResponseSpecification
import io.restassured.specification.RequestSpecification
import org.slf4j.LoggerFactory

/**
 * Central factory for REST Assured specifications, Jackson serialization,
 * connection/socket timeouts, and sanitized Allure diagnostic attachments.
 */
object RestAssuredConfig {

    private val logger = LoggerFactory.getLogger(RestAssuredConfig::class.java)

    val SENSITIVE_HEADERS = setOf(
        "Authorization",
        "Proxy-Authorization",
        "X-API-Key",
        "Cookie",
        "Set-Cookie"
    )

    /**
     * Strict ObjectMapper that fails on unknown properties to catch undocumented contract changes.
     */
    val strictObjectMapper: ObjectMapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
    }

    /**
     * Lenient ObjectMapper for general deserialization when needed.
     */
    val lenientObjectMapper: ObjectMapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    fun createRestAssuredConfig(config: TestConfig = TestConfig.instance): RaConfig {
        val httpClientConfig = HttpClientConfig.httpClientConfig()
            .setParam("http.connection.timeout", config.connectTimeoutMs)
            .setParam("http.socket.timeout", config.readTimeoutMs)
            .setParam("http.connection-manager.timeout", config.connectTimeoutMs.toLong())

        val logConfig = LogConfig.logConfig()
            .blacklistHeaders(SENSITIVE_HEADERS)
            .enableLoggingOfRequestAndResponseIfValidationFails()

        val objectMapperConfig = ObjectMapperConfig.objectMapperConfig()
            .jackson2ObjectMapperFactory { _, _ -> strictObjectMapper }

        return RaConfig.config()
            .httpClient(httpClientConfig)
            .logConfig(logConfig)
            .objectMapperConfig(objectMapperConfig)
    }

    fun baseRequestSpec(
        baseUrlOverride: String? = null,
        config: TestConfig = TestConfig.instance
    ): RequestSpecification {
        val effectiveBaseUrl = baseUrlOverride ?: config.baseUrl
        return RequestSpecBuilder()
            .setBaseUri(effectiveBaseUrl)
            .setAccept(ContentType.JSON)
            .setContentType(ContentType.JSON)
            .setConfig(createRestAssuredConfig(config))
            .addFilter(SanitizedAllureDiagnosticFilter())
            .build()
    }

    /**
     * Wraps every HTTP request in an Allure step (`HTTP <METHOD> <URI>`) and attaches:
     * - `HTTP request` (method, URI, sanitized headers, query parameters, pretty body)
     * - `cURL` (reproducible curl command with redacted secret headers)
     * - `HTTP response` (status code, response time in ms, content-type, sanitized headers)
     * - `HTTP response body` (pretty-printed JSON or plain text)
     */
    class SanitizedAllureDiagnosticFilter : Filter {
        override fun filter(
            requestSpec: FilterableRequestSpecification,
            responseSpec: FilterableResponseSpecification,
            ctx: FilterContext
        ): Response {
            val method = requestSpec.method
            val uri = requestSpec.uri
            val stepName = "HTTP $method $uri"

            val httpCall = Allure.ThrowableRunnable<Response> {
                logger.info("HTTP Request: {} {}", method, uri)
                attachRequest(requestSpec)

                val response = ctx.next(requestSpec, responseSpec)

                logger.info("HTTP Response: {} {} -> status {}", method, uri, response.statusCode)
                attachResponse(response)
                response
            }

            return Allure.step(stepName, httpCall)
        }

        private fun attachRequest(request: FilterableRequestSpecification) {
            val body = request.getBody<Any?>()
            val safeHeaders = formatHeaders(request.headers.asList().map { it.name to it.value })
            val queryParams = request.queryParams ?: emptyMap()
            val formattedQuery = if (queryParams.isEmpty()) {
                "<empty>"
            } else {
                queryParams.entries.joinToString("\n") { "${it.key}=${it.value}" }
            }

            runCatching {
                Allure.addAttachment(
                    "HTTP request",
                    "text/plain",
                    """
                    ${request.method} ${request.uri}

                    Headers:
                    $safeHeaders

                    Query parameters:
                    $formattedQuery

                    Body:
                    ${body?.toString()?.let { prettyBody(it, request.contentType) } ?: "<empty>"}
                    """.trimIndent()
                )
                Allure.addAttachment("cURL", "text/plain", toCurl(request))
            }
        }

        private fun attachResponse(response: Response) {
            val contentType = response.contentType.orEmpty()
            val body = response.body?.asString().orEmpty()
            val safeHeaders = formatHeaders(response.headers.asList().map { it.name to it.value })

            runCatching {
                Allure.addAttachment(
                    "HTTP response",
                    "text/plain",
                    """
                    HTTP ${response.statusCode} (${response.statusLine})
                    Time: ${response.time} ms
                    Content-Type: ${contentType.ifBlank { "<empty>" }}

                    Headers:
                    $safeHeaders
                    """.trimIndent()
                )

                if (contentType.lowercase().contains("json") && body.isNotBlank()) {
                    Allure.addAttachment(
                        "HTTP response body",
                        "application/json",
                        prettyBody(body, contentType),
                        "json"
                    )
                } else {
                    Allure.addAttachment(
                        "HTTP response body",
                        "text/plain",
                        body.ifBlank { "<empty>" },
                        "txt"
                    )
                }
            }
        }

        private fun formatHeaders(headers: List<Pair<String, String>>): String {
            if (headers.isEmpty()) return "<empty>"
            return headers.joinToString("\n") { (name, value) ->
                "$name: ${maskHeader(name, value)}"
            }
        }

        private fun maskHeader(name: String, value: String): String {
            return if (SENSITIVE_HEADERS.any { it.equals(name, ignoreCase = true) }) {
                "***REDACTED***"
            } else {
                value
            }
        }

        private fun toCurl(request: FilterableRequestSpecification): String {
            val curl = StringBuilder("curl -i -X ").append(shellQuote(request.method))
            request.headers.asList().forEach { header ->
                val masked = maskHeader(header.name, header.value)
                curl.append(" -H ").append(shellQuote("${header.name}: $masked"))
            }
            request.getBody<Any?>()?.let {
                curl.append(" --data-raw ").append(shellQuote(it.toString()))
            }
            return curl.append(" ").append(shellQuote(request.uri)).toString()
        }

        private fun shellQuote(value: String): String =
            "'${value.replace("'", "'\"'\"'")}'"

        private fun prettyBody(body: String, contentType: String?): String {
            if (contentType?.lowercase()?.contains("json") != true) return body
            return runCatching {
                val tree = lenientObjectMapper.readTree(body)
                lenientObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree)
            }.getOrDefault(body)
        }
    }
}
