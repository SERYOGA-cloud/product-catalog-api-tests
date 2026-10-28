package com.example.catalog.extensions

import com.example.catalog.config.RestAssuredConfig
import com.example.catalog.config.TestConfig
import com.example.catalog.data.ProductTestData
import com.example.catalog.models.Measure
import com.example.catalog.models.Product
import com.example.catalog.models.ProductStatus
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI

/**
 * Local WireMock stub server implementing the `docs/openapi.json` contract (`catalog-controller`).
 *
 * Used ONLY when `USE_LOCAL_MOCK=true` is explicitly passed via configuration/CLI.
 * By default (`USE_LOCAL_MOCK=false`), tests connect directly to the configured `BASE_URL`.
 */
object LocalContractStubServer {

    private val logger = LoggerFactory.getLogger(LocalContractStubServer::class.java)

    @Volatile
    private var server: WireMockServer? = null

    val baseUrl: String
        get() = checkNotNull(server?.baseUrl()) { "LocalContractStubServer is not started." }

    @Synchronized
    fun ensureStarted(): String {
        val current = server
        if (current != null && current.isRunning) {
            return current.baseUrl()
        }

        val wm = WireMockServer(wireMockConfig().dynamicPort())
        wm.start()
        registerOpenApiStubs(wm)
        server = wm
        logger.info("Started LocalContractStubServer at {}", wm.baseUrl())
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { wm.stop() }
        })
        return wm.baseUrl()
    }

    fun isRemoteReachable(baseUrl: String, timeoutMs: Int = 1500): Boolean {
        return try {
            val uri = URI("$baseUrl/api/product/${ProductTestData.activeGtin}")
            val conn = (uri.toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 100..599
        } catch (ex: Exception) {
            logger.debug("Target server {} is not reachable: {}", baseUrl, ex.message)
            false
        }
    }

    private fun registerOpenApiStubs(wm: WireMockServer) {
        val mapper = RestAssuredConfig.strictObjectMapper

        val activeProduct = ProductTestData.sampleActiveProduct()
        val activeJson = mapper.writeValueAsString(activeProduct)

        val inactiveProduct = ProductTestData.sampleInactiveProduct()
        val inactiveJson = mapper.writeValueAsString(inactiveProduct)

        val deactivatedProduct = ProductTestData.sampleDeactivatedNtinProduct()
        val deactivatedJson = mapper.writeValueAsString(deactivatedProduct)

        val largeInt64Product = ProductTestData.sampleLargeInt64Product()
        val largeInt64Json = mapper.writeValueAsString(largeInt64Product)

        val minimalProduct = ProductTestData.sampleMinimalNullableProduct()
        val minimalJson = mapper.writeValueAsString(minimalProduct)

        val gtin8Product = Product(
            id = 200008L,
            nameRu = "Хлеб пшеничный формовой",
            nameKk = "Пішінді бидай наны",
            gtin = ProductTestData.GTIN_8_CODE,
            ntinCode = "0290000000008",
            isSocial = true,
            modified = 1729900000000L,
            status = ProductStatus.ACTIVE,
            isMarkedEac = false,
            measure = Measure(id = 796L, name = "Штука", shortName = "шт", code = "796"),
            ntinIsDeactivated = false
        )

        val gtin12Product = activeProduct.copy(id = 200012L, gtin = ProductTestData.GTIN_12_CODE)
        val gtin14Product = activeProduct.copy(id = 200014L, gtin = ProductTestData.GTIN_14_CODE)

        // Default fallback for unknown / malformed product codes -> 404 Not Found
        wm.stubFor(
            get(urlMatching("/api/product/.*"))
                .atPriority(10)
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"timestamp":"2026-10-27T10:00:00.000+00:00","status":404,"error":"Not Found","path":"/api/product"}"""
                        )
                )
        )

        // Empty code segment -> 404 Not Found
        wm.stubFor(
            get(urlEqualTo("/api/product/"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status":404,"error":"Not Found","path":"/api/product/"}""")
                )
        )

        // Active product by GTIN and by NTIN (returns identical product card)
        listOf(TestConfig.instance.testActiveGtin, TestConfig.instance.testActiveNtin).forEach { code ->
            wm.stubFor(
                get(urlEqualTo("/api/product/$code"))
                    .atPriority(1)
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(activeJson)
                    )
            )
        }

        // Inactive product by GTIN
        wm.stubFor(
            get(urlEqualTo("/api/product/${TestConfig.instance.testInactiveGtin}"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(inactiveJson)
                )
        )

        // Deactivated NTIN product
        wm.stubFor(
            get(urlEqualTo("/api/product/${TestConfig.instance.testDeactivatedNtin}"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(deactivatedJson)
                )
        )

        // Boundary GTIN-8, GTIN-12, GTIN-14, Large Int64, Minimal Nullable
        wm.stubFor(
            get(urlEqualTo("/api/product/${ProductTestData.GTIN_8_CODE}"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(gtin8Product))
                )
        )
        wm.stubFor(
            get(urlEqualTo("/api/product/${ProductTestData.GTIN_12_CODE}"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(gtin12Product))
                )
        )
        wm.stubFor(
            get(urlEqualTo("/api/product/${ProductTestData.GTIN_14_CODE}"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mapper.writeValueAsString(gtin14Product))
                )
        )
        wm.stubFor(
            get(urlEqualTo("/api/product/${ProductTestData.LARGE_INT64_PRODUCT_CODE}"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(largeInt64Json)
                )
        )
        wm.stubFor(
            get(urlEqualTo("/api/product/${ProductTestData.MINIMAL_NULLABLE_PRODUCT_CODE}"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(minimalJson)
                )
        )

        // Unsupported HTTP methods on /api/product/{code} -> 405 Method Not Allowed
        listOf(
            post(urlMatching("/api/product/[^/]+")),
            put(urlMatching("/api/product/[^/]+")),
            patch(urlMatching("/api/product/[^/]+")),
            delete(urlMatching("/api/product/[^/]+"))
        ).forEach { builder ->
            wm.stubFor(
                builder.atPriority(2).willReturn(
                    aResponse()
                        .withStatus(405)
                        .withHeader("Allow", "GET")
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status":405,"error":"Method Not Allowed"}""")
                )
            )
        }

        // DELETE /api/product/download/time/ -> 200 OK
        wm.stubFor(
            delete(urlEqualTo("/api/product/download/time/"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                )
        )

        // DELETE /api/product/download/link/ -> 200 OK
        wm.stubFor(
            delete(urlEqualTo("/api/product/download/link/"))
                .atPriority(1)
                .willReturn(
                    aResponse()
                        .withStatus(200)
                )
        )

        // Unsupported HTTP methods on /api/product/download/time/ and /api/product/download/link/ -> 405
        listOf("/api/product/download/time/", "/api/product/download/link/").forEach { path ->
            listOf(get(urlEqualTo(path)), post(urlEqualTo(path)), put(urlEqualTo(path)), patch(urlEqualTo(path))).forEach { builder ->
                wm.stubFor(
                    builder.atPriority(1).willReturn(
                        aResponse()
                            .withStatus(405)
                            .withHeader("Allow", "DELETE")
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"status":405,"error":"Method Not Allowed"}""")
                    )
                )
            }
        }
    }
}
